const functions = require("firebase-functions");
const admin = require("firebase-admin");
const crypto = require("crypto");
const {google} = require("googleapis");
const {defineSecret} = require("firebase-functions/params");

admin.initializeApp();

const REGION = "us-central1";
const APP_PACKAGE_NAME = "com.jjundev.oneclickeng";
const PLAY_SCOPE = "https://www.googleapis.com/auth/androidpublisher";
const playServiceAccount = defineSecret("PLAY_SERVICE_ACCOUNT");
const CREDIT_BY_PRODUCT_ID = Object.freeze({
  "credit_10": 10,
  "credit_20": 20,
  "credit_50": 50,
});

exports.deleteUserData = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  const userDocRef = admin.firestore().collection("users").doc(uid);

  await admin.firestore().recursiveDelete(userDocRef);

  console.log(`Deleted data for user: ${uid}`);
});

/**
 * Google Play purchase verification endpoint for INAPP credits.
 * Contract: android/docs/credit-billing-server-contract.md
 */
exports.verifyCreditPurchase = functions
    .runWith({
      "serviceAccount": "play-purchase-verifier@one-click-eng.iam.gserviceaccount.com",
      "secrets": [playServiceAccount],
    })
    .region(REGION)
    .https
    .onRequest(async (req, res) => {
  setCors(res);
  if (req.method === "OPTIONS") {
    res.status(204).send("");
    return;
  }
  if (req.method !== "POST") {
    sendJson(
        res,
        405,
        buildResult("REJECTED", 0, 0, "", "", "Method not allowed"),
    );
    return;
  }

  try {
    const idToken = extractBearerToken(req.get("Authorization"));
    if (!idToken) {
      sendJson(
          res,
          401,
          buildResult("REJECTED", 0, 0, "", "", "Missing Firebase ID token"),
      );
      return;
    }

    let decodedToken;
    try {
      decodedToken = await admin.auth().verifyIdToken(idToken);
    } catch (error) {
      functions.logger.warn("verifyCreditPurchase auth verification failed", error);
      sendJson(
          res,
          401,
          buildResult("REJECTED", 0, 0, "", "", "Invalid Firebase ID token"),
      );
      return;
    }

    const uid = normalizeString(decodedToken.uid);
    if (!uid) {
      sendJson(
          res,
          401,
          buildResult("REJECTED", 0, 0, "", "", "Missing uid in Firebase token"),
      );
      return;
    }

    const payload = req.body && typeof req.body === "object" ? req.body : {};
    const packageName = normalizeString(payload.packageName);
    const productId = normalizeString(payload.productId);
    const purchaseToken = normalizeString(payload.purchaseToken);
    const orderId = normalizeString(payload.orderId);
    const purchaseTimeMillis = normalizeEpochMillis(payload.purchaseTimeMillis);
    const purchaseState = normalizeInt(payload.purchaseState, -1);

    if (!packageName || !productId || !purchaseToken) {
      sendJson(
          res,
          200,
          buildResult("INVALID", 0, 0, "", purchaseToken, "Missing required fields"),
      );
      return;
    }

    if (packageName !== APP_PACKAGE_NAME) {
      sendJson(
          res,
          200,
          buildResult("INVALID", 0, 0, "", purchaseToken, "Package name mismatch"),
      );
      return;
    }

    if (!Object.prototype.hasOwnProperty.call(CREDIT_BY_PRODUCT_ID, productId)) {
      sendJson(
          res,
          200,
          buildResult("INVALID", 0, 0, "", purchaseToken, "Unsupported productId"),
      );
      return;
    }

    const serviceAccountCredentials = getPlayServiceAccountCredentials();
    const playVerification = await verifyWithGooglePlay(
        packageName,
        productId,
        purchaseToken,
        serviceAccountCredentials,
    );
    if (playVerification.status === "INVALID") {
      sendJson(
          res,
          200,
          buildResult("INVALID", 0, 0, "", purchaseToken, playVerification.message),
      );
      return;
    }

    if (playVerification.status === "PENDING") {
      sendJson(
          res,
          200,
          buildResult("PENDING", 0, 0, "", purchaseToken, playVerification.message),
      );
      return;
    }

    if (playVerification.status === "REJECTED") {
      sendJson(
          res,
          200,
          buildResult("REJECTED", 0, 0, "", purchaseToken, playVerification.message),
      );
      return;
    }

    if (playVerification.status === "SERVER_ERROR") {
      sendJson(
          res,
          200,
          buildResult("SERVER_ERROR", 0, 0, "", purchaseToken, playVerification.message),
      );
      return;
    }

    const verifiedOrderId =
      normalizeString(playVerification.playData.orderId) || orderId;
    const verifiedPurchaseTimeMillis = normalizeEpochMillis(
        playVerification.playData.purchaseTimeMillis,
    ) || purchaseTimeMillis;
    const verifiedQuantity = normalizeQuantity(playVerification.playData.quantity);
    const verifiedPurchaseState = normalizeInt(playVerification.playData.purchaseState, purchaseState);
    const grantedCredits = CREDIT_BY_PRODUCT_ID[productId] * verifiedQuantity;
    const grantResult = await grantCreditsWithIdempotency({
      "uid": uid,
      "packageName": packageName,
      "productId": productId,
      "purchaseToken": purchaseToken,
      "orderId": verifiedOrderId,
      "purchaseTimeMillis": verifiedPurchaseTimeMillis,
      "quantity": verifiedQuantity,
      "purchaseState": verifiedPurchaseState,
      "playPurchaseState": normalizeInt(playVerification.playData.purchaseState, -1),
      "grantedCredits": grantedCredits,
    });

    sendJson(
        res,
        200,
        buildResult(
            grantResult.status,
            grantResult.grantedCredits,
            grantResult.currentCreditBalance,
            grantResult.eventId,
            purchaseToken,
            grantResult.message,
        ),
    );
  } catch (error) {
    functions.logger.error("verifyCreditPurchase failed unexpectedly", error);
    sendJson(
        res,
        500,
        buildResult("REJECTED", 0, 0, "", "", "Internal server error"),
    );
  }
});

async function verifyWithGooglePlay(
    packageName,
    productId,
    purchaseToken,
    serviceAccountCredentials,
) {
  const auth = new google.auth.GoogleAuth({
    "credentials": serviceAccountCredentials,
    "scopes": [PLAY_SCOPE],
  });
  const authClient = await auth.getClient();
  const publisher = google.androidpublisher({
    "version": "v3",
    "auth": authClient,
  });

  try {
    const response = await publisher.purchases.products.get({
      packageName,
      productId,
      "token": purchaseToken,
    });
    const playData = response && response.data ? response.data : {};
    const playPurchaseState = normalizeInt(playData.purchaseState, -1);
    if (playPurchaseState === 2) {
      return {
        "status": "PENDING",
        playData,
        "message": "Purchase is pending in Google Play",
      };
    }
    if (playPurchaseState === 1) {
      return {
        "status": "REJECTED",
        playData,
        "message": "Purchase is canceled in Google Play",
      };
    }
    if (playPurchaseState !== 0) {
      return {
        "status": "REJECTED",
        playData,
        "message": "Unexpected purchase state from Google Play",
      };
    }
    return {
      "status": "VALID",
      playData,
      "message": "ok",
    };
  } catch (error) {
    const code = normalizeInt(error && error.code, -1);
    const status = normalizeInt(
        error && (error.status ||
          (error.response && error.response.status)),
        -1,
    );
    const message = normalizeString(
        error && (error.message ||
          (error.response && error.response.statusText)),
    );
    const reason = normalizeString(
        error &&
        error.response &&
        error.response.data &&
        error.response.data.error &&
        Array.isArray(error.response.data.error.errors) &&
        error.response.data.error.errors[0] &&
        error.response.data.error.errors[0].reason,
    );
    const normalizedCode = code >= 0 ? code : status;
    const logFields = {
      "apiCode": normalizedCode,
      "apiStatus": status,
      "apiMessage": message,
      "apiReason": reason,
      "productId": productId,
      "packageName": packageName,
      "purchaseTokenPrefix": normalizeString(purchaseToken).substring(0, 8),
      "serviceAccountEmail": normalizeString(
          serviceAccountCredentials && serviceAccountCredentials.client_email,
      ),
    };
    if (normalizedCode === 400 || normalizedCode === 404) {
      functions.logger.warn("verifyWithGooglePlay invalid purchase", logFields);
      return {
        "status": "INVALID",
        "playData": {},
        "message": "Invalid purchase token or product mismatch",
      };
    }
    if (normalizedCode === 401 || normalizedCode === 403) {
      functions.logger.warn("verifyWithGooglePlay permission/config issue", logFields);
      return {
        "status": "SERVER_ERROR",
        "playData": {},
        "message":
          "Google Play API authorization failed. " +
          "Grant Android Publisher access to the Functions service account.",
      };
    }
    functions.logger.error(error, "verifyWithGooglePlay unexpected error", logFields);
    throw error;
  }
}

function getPlayServiceAccountCredentials() {
  const rawSecret = playServiceAccount.value();
  const secretJson = normalizeString(rawSecret);
  if (!secretJson) {
    throw new Error("PLAY_SERVICE_ACCOUNT missing");
  }

  let parsedSecret;
  try {
    parsedSecret = JSON.parse(secretJson);
  } catch (error) {
    throw new Error("PLAY_SERVICE_ACCOUNT invalid JSON");
  }

  if (!parsedSecret || typeof parsedSecret !== "object") {
    throw new Error("PLAY_SERVICE_ACCOUNT invalid credential shape");
  }

  const clientEmail = normalizeString(parsedSecret.client_email);
  const projectId = normalizeString(parsedSecret.project_id);
  const privateKeyRaw =
    typeof parsedSecret.private_key === "string" ? parsedSecret.private_key : "";
  const privateKey = privateKeyRaw.includes("\\n") ?
    privateKeyRaw.replace(/\\n/g, "\n") :
    privateKeyRaw;

  if (!clientEmail || !projectId || !privateKey) {
    throw new Error("PLAY_SERVICE_ACCOUNT invalid credential shape");
  }

  return {
    ...parsedSecret,
    "client_email": clientEmail,
    "project_id": projectId,
    "private_key": privateKey,
  };
}

async function grantCreditsWithIdempotency(params) {
  const db = admin.firestore();
  const purchaseRef = db.collection("billing_purchases").doc(params.purchaseToken);
  const userRef = db.collection("users").doc(params.uid);
  const nowEpochMs = Date.now();
  const result = {
    "status": "REJECTED",
    "grantedCredits": 0,
    "currentCreditBalance": 0,
    "eventId": "",
    "message": "unknown",
  };

  await db.runTransaction(async (transaction) => {
    const purchaseSnapshot = await transaction.get(purchaseRef);
    const userSnapshot = await transaction.get(userRef);

    const currentCredit =
      userSnapshot.exists && userSnapshot.data() ? numberOrZero(userSnapshot.data().credit) : 0;

    if (purchaseSnapshot.exists) {
      const purchaseData = purchaseSnapshot.data() || {};
      const existingUid = normalizeString(purchaseData.uid);
      const existingStatus = normalizeString(purchaseData.status);
      if (existingUid && existingUid !== params.uid) {
        result.status = "REJECTED";
        result.message = "Purchase token belongs to another user";
        result.currentCreditBalance = currentCredit;
        return;
      }

      if (existingStatus === "granted" || existingStatus === "already_granted") {
        result.status = "ALREADY_GRANTED";
        result.grantedCredits = 0;
        result.currentCreditBalance = currentCredit;
        result.eventId = normalizeString(purchaseData.last_event_id);
        result.message = "already granted";
        return;
      }
    }

    const newCreditBalance = currentCredit + params.grantedCredits;
    const eventId = buildEventId();
    const ledgerRef =
      db.collection("billing_ledger").doc(params.uid).collection("events").doc(eventId);

    transaction.set(userRef, {"credit": newCreditBalance}, {"merge": true});
    transaction.set(
        purchaseRef,
        {
          "uid": params.uid,
          "product_id": params.productId,
          "status": "granted",
          "granted_credits": params.grantedCredits,
          "clawed_back_credits": 0,
          "order_id": params.orderId,
          "updated_at_epoch_ms": nowEpochMs,
          "package_name": params.packageName,
          "purchase_time_millis": params.purchaseTimeMillis,
          "quantity": params.quantity,
          "purchase_state": params.purchaseState,
          "play_purchase_state": params.playPurchaseState,
          "last_event_id": eventId,
        },
        {"merge": true},
    );
    transaction.set(ledgerRef, {
      "purchase_token": params.purchaseToken,
      "delta_credits": params.grantedCredits,
      "reason": "purchase_grant",
      "created_at_epoch_ms": nowEpochMs,
    });

    result.status = "GRANTED";
    result.grantedCredits = params.grantedCredits;
    result.currentCreditBalance = newCreditBalance;
    result.eventId = eventId;
    result.message = "ok";
  });

  return result;
}

function sendJson(res, statusCode, payload) {
  res.status(statusCode).json(payload);
}

function setCors(res) {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Headers", "Authorization, Content-Type");
  res.set("Access-Control-Allow-Methods", "POST, OPTIONS");
}

function extractBearerToken(authorizationHeader) {
  const header = normalizeString(authorizationHeader);
  const prefix = "Bearer ";
  if (!header.startsWith(prefix)) {
    return "";
  }
  return normalizeString(header.substring(prefix.length));
}

function buildResult(status, grantedCredits, currentCreditBalance, eventId, purchaseToken, message) {
  return {
    status,
    grantedCredits,
    currentCreditBalance,
    eventId,
    purchaseToken,
    message,
  };
}

function normalizeString(value) {
  if (typeof value !== "string") {
    return "";
  }
  return value.trim();
}

function normalizeInt(value, fallback) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return fallback;
  }
  return Math.trunc(parsed);
}

function normalizeEpochMillis(value) {
  const parsed = normalizeInt(value, 0);
  return Math.max(0, parsed);
}

function normalizeQuantity(value) {
  const parsed = normalizeInt(value, 1);
  return Math.max(1, parsed);
}

function numberOrZero(value) {
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    return 0;
  }
  return parsed;
}

function buildEventId() {
  return `evt_${Date.now()}_${crypto.randomUUID().replace(/-/g, "").substring(0, 8)}`;
}

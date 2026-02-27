const { onUserDeleted } = require("firebase-functions/v2/auth");
const admin = require("firebase-admin");

admin.initializeApp();

exports.deleteUserData = onUserDeleted(async (event) => {
  const uid = event.data.uid;
  const db = admin.firestore();

  const userDocRef = db.collection("users").doc(uid);

  console.log(`Deleting Firestore data for user: ${uid}`);

  await admin.firestore().recursiveDelete(userDocRef);

  console.log(`Successfully deleted data for user: ${uid}`);
});

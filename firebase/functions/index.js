const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.deleteUserData = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  const userDocRef = admin.firestore().collection("users").doc(uid);

  await admin.firestore().recursiveDelete(userDocRef);

  console.log(`Deleted data for user: ${uid}`);
});

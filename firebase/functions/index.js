const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.deleteUserData = functions.auth.user().onDelete(async (user) => {
  const uid = user.uid;
  const db = admin.firestore();

  const userDocRef = db.collection("users").doc(uid);

  console.log(`Deleting Firestore data for user: ${uid}`);

  // 하위 컬렉션까지 전부 재귀 삭제
  await admin.firestore().recursiveDelete(userDocRef);

  console.log(`Successfully deleted data for user: ${uid}`);
});
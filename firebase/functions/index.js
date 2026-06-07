const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * When a notification doc is created in Firestore, send FCM to the recipient's device.
 * Uses a data-only payload so Android always delivers to onMessageReceived.
 * Deploy: firebase deploy --only functions (from repo root with firebase.json).
 */
exports.onNotificationCreated = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const recipientUid = data.recipientUid;
    if (!recipientUid) return;

    const userSnap = await getFirestore().collection("users").doc(recipientUid).get();
    const token = userSnap.get("fcmToken");
    if (!token) {
      console.warn("No fcmToken for user", recipientUid);
      return;
    }

    const propertyId = data.propertyId || "";
    await getMessaging().send({
      token,
      data: {
        title: String(data.title || "ProofNest"),
        body: String(data.body || ""),
        type: String(data.type || "GENERIC"),
        propertyId: String(propertyId),
        notificationId: snap.id,
      },
      android: {
        priority: "high",
      },
    });
  },
);

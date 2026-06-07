const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const crypto = require("crypto");

initializeApp();

const db = () => getFirestore();

function propertyData(propertyId) {
  return db().collection("properties").doc(propertyId).get();
}

async function assertPropertyMember(propertyId, uid) {
  const snap = await propertyData(propertyId);
  if (!snap.exists) {
    throw new HttpsError("not-found", "Property not found.");
  }
  const data = snap.data();
  if (data.landlordId !== uid && data.tenantId !== uid) {
    throw new HttpsError("permission-denied", "Not a member of this property.");
  }
  return data;
}

/**
 * Mint signed Cloudinary upload params. Set env vars before deploy:
 *   CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET
 */
exports.getCloudinaryUploadParams = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const apiSecret = process.env.CLOUDINARY_API_SECRET;
  const apiKey = process.env.CLOUDINARY_API_KEY;
  const cloudName = process.env.CLOUDINARY_CLOUD_NAME || "dspdmxdaq";
  if (!apiSecret || !apiKey) {
    throw new HttpsError(
      "failed-precondition",
      "Cloudinary is not configured. Set CLOUDINARY_API_KEY and CLOUDINARY_API_SECRET.",
    );
  }
  const folder = String(request.data?.folder || "");
  const timestamp = Math.round(Date.now() / 1000);
  const toSign = folder
    ? `folder=${folder}&timestamp=${timestamp}`
    : `timestamp=${timestamp}`;
  const signature = crypto
    .createHash("sha1")
    .update(toSign + apiSecret)
    .digest("hex");
  return { cloudName, apiKey, timestamp, signature, folder };
});

/**
 * Creates a notification document server-side (client Firestore create is denied).
 */
exports.sendNotification = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Sign in required.");
  }
  const senderUid = request.auth.uid;
  const data = request.data || {};
  const recipientUid = String(data.recipientUid || "");
  const type = String(data.type || "GENERIC");
  const title = String(data.title || "").trim();
  const body = String(data.body || "").trim();
  const propertyId = data.propertyId ? String(data.propertyId) : null;

  if (!recipientUid || !title) {
    throw new HttpsError("invalid-argument", "recipientUid and title are required.");
  }

  if (type === "LEASE_ENDING") {
    if (recipientUid !== senderUid) {
      throw new HttpsError("permission-denied", "Lease reminders are self-only.");
    }
    if (propertyId) {
      await assertPropertyMember(propertyId, senderUid);
    }
  } else {
    if (!propertyId) {
      throw new HttpsError("invalid-argument", "propertyId is required.");
    }
    const property = await assertPropertyMember(propertyId, senderUid);
    if (recipientUid === senderUid) {
      throw new HttpsError("permission-denied", "Cannot notify yourself.");
    }
    if (recipientUid !== property.landlordId && recipientUid !== property.tenantId) {
      throw new HttpsError("permission-denied", "Recipient must be the landlord or tenant.");
    }
  }

  const notificationId = db().collection("notifications").doc().id;
  const now = Date.now();
  await db().collection("notifications").doc(notificationId).set({
    id: notificationId,
    recipientUid,
    type,
    title,
    body,
    propertyId,
    createdAtMillis: now,
    read: false,
  });
  return { id: notificationId };
});

/**
 * When a notification doc is created in Firestore, send FCM to the recipient's device.
 */
exports.onNotificationCreated = onDocumentCreated(
  "notifications/{notificationId}",
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const data = snap.data();
    const recipientUid = data.recipientUid;
    if (!recipientUid) return;

    const userSnap = await db().collection("users").doc(recipientUid).get();
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

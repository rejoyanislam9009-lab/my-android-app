const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

async function sendToUser(uid, data, ttlMs = 60000) {
  const db = getFirestore();
  const deviceRef = db.collection("devices").doc(uid);
  const device = await deviceRef.get();
  const token = String(device.get("fcmToken") || "");
  if (!token) return false;

  try {
    await getMessaging().send({
      token,
      data,
      android: {
        priority: "high",
        ttl: ttlMs,
      },
    });
    return true;
  } catch (error) {
    const code = String(error && error.code ? error.code : "");
    if (
      code.includes("registration-token-not-registered") ||
      code.includes("invalid-registration-token")
    ) {
      await deviceRef.set(
        {
          fcmToken: "",
          tokenInvalidatedAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
    }
    console.error("GlobalCall push failed", uid, code, error);
    return false;
  }
}

exports.pushIncomingCall = onDocumentCreated(
  {
    document: "calls/{callId}",
    region: "us-central1",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const call = snapshot.data() || {};
    if (call.status !== "ringing") return;

    const calleeUid = String(call.calleeUid || "");
    const callerUid = String(call.callerUid || "");
    const callerName = String(call.callerName || "GlobalCall user");
    const callId = event.params.callId;
    if (!calleeUid || !callerUid || !callId) return;

    const sent = await sendToUser(
      calleeUid,
      {
        type: "incoming_call",
        callId,
        callerUid,
        callerName,
        video: String(call.video !== false),
      },
      60000
    );

    if (sent) {
      await snapshot.ref.set(
        { pushSentAt: FieldValue.serverTimestamp() },
        { merge: true }
      );
    }
  }
);

exports.pushChatMessage = onDocumentCreated(
  {
    document: "conversations/{conversationId}/messages/{messageId}",
    region: "us-central1",
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const message = snapshot.data() || {};
    const senderUid = String(message.senderUid || "");
    const receiverUid = String(message.receiverUid || "");
    const text = String(message.text || "").slice(0, 180);
    if (!senderUid || !receiverUid || !text) return;

    const db = getFirestore();
    const sender = await db.collection("users").doc(senderUid).get();
    const senderName = String(sender.get("displayName") || "GlobalCall contact");

    await sendToUser(
      receiverUid,
      {
        type: "chat_message",
        conversationId: event.params.conversationId,
        messageId: event.params.messageId,
        senderUid,
        senderName,
        text,
      },
      24 * 60 * 60 * 1000
    );
  }
);

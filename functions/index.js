const { onDocumentCreated, onDocumentUpdated } = require("firebase-functions/v2/firestore");
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

exports.syncCallState = onDocumentUpdated(
  {
    document: "calls/{callId}",
    region: "us-central1",
  },
  async (event) => {
    const before = event.data && event.data.before ? event.data.before.data() || {} : {};
    const afterSnapshot = event.data && event.data.after ? event.data.after : null;
    if (!afterSnapshot) return;
    const after = afterSnapshot.data() || {};
    if (before.status === after.status) return;

    const callId = event.params.callId;
    const participants = Array.isArray(after.participantUids) ? after.participantUids : [];
    if (!callId || participants.length !== 2) return;

    const db = getFirestore();
    if (after.status === "accepted") {
      const batch = db.batch();
      for (const uid of participants) {
        batch.set(
          db.collection("users").doc(String(uid)),
          {
            callState: "active",
            currentCallId: callId,
            callStateUpdatedAt: FieldValue.serverTimestamp(),
            updatedAt: FieldValue.serverTimestamp(),
          },
          { merge: true }
        );
      }
      await batch.commit();
      return;
    }

    if (!["ended", "declined", "busy", "missed"].includes(String(after.status || ""))) return;

    await Promise.all(
      participants.map(async (rawUid) => {
        const uid = String(rawUid || "");
        if (!uid) return;
        const userRef = db.collection("users").doc(uid);
        await db.runTransaction(async (tx) => {
          const user = await tx.get(userRef);
          if (!user.exists) return;
          const currentCallId = String(user.get("currentCallId") || "");
          if (currentCallId && currentCallId !== callId) return;
          tx.set(
            userRef,
            {
              callState: "idle",
              currentCallId: "",
              callStateUpdatedAt: FieldValue.serverTimestamp(),
              updatedAt: FieldValue.serverTimestamp(),
            },
            { merge: true }
          );
        });
      })
    );
  }
);

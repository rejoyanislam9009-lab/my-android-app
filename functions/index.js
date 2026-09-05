const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

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

    const db = getFirestore();
    const deviceRef = db.collection("devices").doc(calleeUid);
    const device = await deviceRef.get();
    const token = String(device.get("fcmToken") || "");
    if (!token) return;

    try {
      await getMessaging().send({
        token,
        data: {
          type: "incoming_call",
          callId,
          callerUid,
          callerName,
          video: String(call.video !== false),
        },
        android: {
          priority: "high",
          ttl: 60000,
        },
      });

      await snapshot.ref.set(
        {
          pushSentAt: FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
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
      console.error("GlobalCall incoming push failed", callId, code, error);
    }
  }
);

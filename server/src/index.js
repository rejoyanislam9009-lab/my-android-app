import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { applicationDefault, getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, getFirestore } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { AccessToken } from 'livekit-server-sdk';

const required = ['LIVEKIT_URL', 'LIVEKIT_API_KEY', 'LIVEKIT_API_SECRET'];
for (const key of required) {
  if (!process.env[key]) {
    throw new Error(`Missing required environment variable: ${key}`);
  }
}

if (!getApps().length) {
  initializeApp({ credential: applicationDefault() });
}

const auth = getAuth();
const db = getFirestore();
const messaging = getMessaging();
const app = express();

app.set('trust proxy', 1);
app.use(helmet());
app.use(cors({ origin: false }));
app.use(express.json({ limit: '16kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: 'draft-8', legacyHeaders: false }));

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'globalcall-api' });
});

async function authenticatedUser(req) {
  const authHeader = req.headers.authorization || '';
  if (!authHeader.startsWith('Bearer ')) {
    const error = new Error('Missing bearer token');
    error.status = 401;
    throw error;
  }
  return auth.verifyIdToken(authHeader.slice('Bearer '.length), true);
}

async function createParticipantToken({ uid, name, roomName }) {
  const accessToken = new AccessToken(
    process.env.LIVEKIT_API_KEY,
    process.env.LIVEKIT_API_SECRET,
    {
      identity: uid,
      name: name || uid,
      ttl: '10m'
    }
  );

  accessToken.addGrant({
    roomJoin: true,
    room: roomName,
    canPublish: true,
    canSubscribe: true,
    canPublishData: true
  });

  return accessToken.toJwt();
}

app.post('/api/calls/start', async (req, res) => {
  try {
    const decoded = await authenticatedUser(req);
    const calleeUid = String(req.body?.calleeUid || '').trim();
    const video = req.body?.video !== false;

    if (!/^[A-Za-z0-9:_-]{6,128}$/.test(calleeUid) || calleeUid === decoded.uid) {
      return res.status(400).json({ error: 'Invalid callee' });
    }

    const [callerSnap, calleeSnap, callerBlockSnap, calleeBlockSnap] = await Promise.all([
      db.collection('users').doc(decoded.uid).get(),
      db.collection('users').doc(calleeUid).get(),
      db.collection('blocks').doc(`${decoded.uid}_${calleeUid}`).get(),
      db.collection('blocks').doc(`${calleeUid}_${decoded.uid}`).get()
    ]);

    if (!calleeSnap.exists) {
      return res.status(404).json({ error: 'User not found' });
    }
    if (callerBlockSnap.exists || calleeBlockSnap.exists) {
      return res.status(403).json({ error: 'Calling is unavailable for this contact' });
    }

    const caller = callerSnap.data() || {};
    const callee = calleeSnap.data() || {};
    const callerName = String(caller.displayName || decoded.name || decoded.email || 'GlobalCall user');
    const calleeName = String(callee.displayName || callee.email || 'GlobalCall user');

    const callRef = db.collection('calls').doc();
    const roomName = `call_${callRef.id}`;
    await callRef.set({
      callerUid: decoded.uid,
      callerName,
      calleeUid,
      calleeName,
      participantUids: [decoded.uid, calleeUid],
      roomName,
      status: 'ringing',
      video,
      createdAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp()
    });

    const fcmToken = String(callee.fcmToken || '');
    if (fcmToken) {
      try {
        await messaging.send({
          token: fcmToken,
          data: {
            type: 'incoming_call',
            callId: callRef.id,
            callerUid: decoded.uid,
            callerName,
            video: String(video)
          },
          android: {
            priority: 'high',
            ttl: 60_000
          }
        });
      } catch (pushError) {
        console.warn('FCM ringing notification failed', pushError?.message || pushError);
      }
    }

    const participantToken = await createParticipantToken({
      uid: decoded.uid,
      name: callerName,
      roomName
    });

    return res.status(201).json({
      callId: callRef.id,
      serverUrl: process.env.LIVEKIT_URL,
      participantToken
    });
  } catch (error) {
    console.error('start call failed', error);
    return res.status(error.status || 401).json({ error: error.message || 'Unable to start call' });
  }
});

app.post('/api/token', async (req, res) => {
  try {
    const decoded = await authenticatedUser(req);
    const callId = String(req.body?.callId || '').trim();

    if (!/^[A-Za-z0-9_-]{8,128}$/.test(callId)) {
      return res.status(400).json({ error: 'Invalid callId' });
    }

    const callSnap = await db.collection('calls').doc(callId).get();
    if (!callSnap.exists) {
      return res.status(404).json({ error: 'Call not found' });
    }

    const call = callSnap.data();
    const participantUids = Array.isArray(call.participantUids) ? call.participantUids : [];
    if (!participantUids.includes(decoded.uid)) {
      return res.status(403).json({ error: 'Not a participant in this call' });
    }

    if (!['ringing', 'accepted'].includes(call.status)) {
      return res.status(409).json({ error: 'Call is no longer joinable' });
    }

    const roomName = String(call.roomName || '');
    if (!roomName) {
      return res.status(500).json({ error: 'Call room is missing' });
    }

    const participantToken = await createParticipantToken({
      uid: decoded.uid,
      name: decoded.name || decoded.email || decoded.uid,
      roomName
    });

    return res.json({
      serverUrl: process.env.LIVEKIT_URL,
      participantToken
    });
  } catch (error) {
    console.error('token endpoint failed', error);
    return res.status(error.status || 401).json({ error: error.message || 'Unauthorized or token generation failed' });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, '0.0.0.0', () => {
  console.log(`GlobalCall API listening on :${port}`);
});

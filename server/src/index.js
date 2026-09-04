import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import { rateLimit } from 'express-rate-limit';
import { applicationDefault, getApps, initializeApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';
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
const app = express();

app.set('trust proxy', 1);
app.use(helmet());
app.use(cors({ origin: false }));
app.use(express.json({ limit: '16kb' }));
app.use(rateLimit({ windowMs: 60_000, limit: 60, standardHeaders: 'draft-8', legacyHeaders: false }));

app.get('/health', (_req, res) => {
  res.json({ ok: true, service: 'globalcall-token-server' });
});

app.post('/api/token', async (req, res) => {
  try {
    const authHeader = req.headers.authorization || '';
    if (!authHeader.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing bearer token' });
    }

    const firebaseToken = authHeader.slice('Bearer '.length);
    const decoded = await auth.verifyIdToken(firebaseToken, true);
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

    const accessToken = new AccessToken(
      process.env.LIVEKIT_API_KEY,
      process.env.LIVEKIT_API_SECRET,
      {
        identity: decoded.uid,
        name: decoded.name || decoded.email || decoded.uid,
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

    return res.json({
      serverUrl: process.env.LIVEKIT_URL,
      participantToken: await accessToken.toJwt()
    });
  } catch (error) {
    console.error('token endpoint failed', error);
    return res.status(401).json({ error: 'Unauthorized or token generation failed' });
  }
});

const port = Number(process.env.PORT || 8080);
app.listen(port, '0.0.0.0', () => {
  console.log(`GlobalCall token server listening on :${port}`);
});

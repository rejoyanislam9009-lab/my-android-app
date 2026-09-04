# GlobalCall

GlobalCall is a production-oriented Android video calling foundation built with Kotlin, Jetpack Compose, Firebase and LiveKit.

## Included in this branch

- Email/password account creation and sign-in with Firebase Authentication
- User profiles stored in Cloud Firestore
- Searchable signed-in user directory
- One-to-one outgoing call creation
- Foreground incoming-call dialog using Firestore realtime listeners
- Accept / decline / end-call state handling
- LiveKit camera and microphone publishing
- Remote and local video rendering
- Mic mute/unmute and camera on/off controls
- Firestore participant security rules
- Firebase-authenticated backend token endpoint
- Backend verification that only the caller/callee can receive a LiveKit room token
- English, Bengali and Arabic resources with RTL support
- Docker-ready Node.js token service
- GitHub Actions Android APK build

## Architecture

```text
Android app
  |-- Firebase Auth -------- user identity
  |-- Cloud Firestore ------ profiles + call invitations/status/history data
  |-- Token API ------------ verifies Firebase ID token + call membership
  |-- LiveKit -------------- realtime WebRTC media transport / SFU

Token API
  |-- Firebase Admin ------- verifies users and reads call membership
  |-- LiveKit Server SDK --- issues short-lived room-scoped participant tokens
```

The app never stores `LIVEKIT_API_SECRET` on the device. LiveKit participant tokens are issued by the backend only after Firebase authentication and participant verification.

## Required services

1. Create a Firebase project and Android app with package name `com.globalcall.app`.
2. Enable Firebase Authentication -> Email/Password.
3. Create a Cloud Firestore database.
4. Deploy `firestore.rules`.
5. Download your real `google-services.json` into `app/google-services.json`. Do **not** commit private service-account credentials.
6. Create a LiveKit Cloud project or deploy LiveKit yourself.
7. Deploy the `server/` service with Firebase Admin credentials and LiveKit environment variables.
8. Replace `https://YOUR_DOMAIN.example/api/token` in `app/build.gradle.kts` with your deployed HTTPS token endpoint.

## Token server

```bash
cd server
cp .env.example .env
npm install
npm start
```

Environment variables:

```text
LIVEKIT_URL=wss://your-project.livekit.cloud
LIVEKIT_API_KEY=...
LIVEKIT_API_SECRET=...
GOOGLE_APPLICATION_CREDENTIALS=/path/to/firebase-service-account.json
```

For Google Cloud Run / other Google-managed runtimes, prefer workload/application-default credentials instead of shipping a service-account JSON inside the container.

## Android build

CI uses JDK 17 and Gradle 8.9:

```bash
gradle assembleDebug
```

The generated APK is uploaded by GitHub Actions as `globalcall-debug-apk`.

## Production work still required before public release

This branch is a functional architecture/MVP foundation, not yet a finished App Store / Play Store release. Before international launch, add:

- FCM background incoming-call notifications and Android full-screen call notification UX
- Phone-number / Google / Apple authentication if required
- Contact syncing and username/QR discovery
- Call history screen and missed-call badges
- Block/report controls and abuse moderation
- TURN/SFU regional capacity planning (LiveKit Cloud already handles this if selected)
- Network quality indicators, reconnection UX and device switching
- Optional end-to-end encryption policy and key management
- Privacy policy, Terms of Service, account deletion and data export
- Rate limits / abuse detection for call creation
- Observability, crash reporting, backend logs and alerting
- Automated tests and real-device matrix testing
- Release signing, Play Integrity/App Check and Play Store compliance

## Current dependency references

- Firebase Android BoM: `34.18.0`
- LiveKit Android SDK: `2.28.0`
- LiveKit Server SDK (Node): `2.18.0`

Never commit Firebase service-account keys, LiveKit API secrets, signing keystores or production `.env` files.

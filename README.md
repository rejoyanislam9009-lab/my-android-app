# GlobalCall

GlobalCall is a production-oriented Android voice and video calling application built with Kotlin, Jetpack Compose, Firebase and LiveKit.

## Calling app experience included

- Firebase email/password account creation and sign-in
- Password reset and email verification
- Editable user profile and bio
- Searchable signed-in user directory
- Online presence indicator
- One-to-one voice calls
- One-to-one video calls
- Outgoing ringing state
- Foreground incoming-call dialog
- High-priority FCM background incoming-call notifications
- Android call-style notification with Answer / Decline actions
- Full-screen incoming-call intent support
- Accept / decline / end-call state handling
- LiveKit microphone and camera publishing
- Remote and local video rendering
- Voice-only call screen with call timer
- Mic mute/unmute
- Speaker on/off
- Camera on/off
- Recent call history
- Incoming / outgoing / missed-call presentation
- One-tap redial for voice or video
- Block / unblock users
- Report users
- Account deletion control
- English, Bengali and Arabic UI resources with RTL support
- Light and dark Material 3 UI

## Security model

- LiveKit API secrets never enter the Android application.
- Call creation is server-only; Firestore client rules do not allow users to forge new call documents.
- The backend validates Firebase ID tokens before creating calls or issuing LiveKit tokens.
- The backend checks blocking in both directions before a call can be created.
- A LiveKit token is issued only to a participant listed on that call.
- Participant tokens are short-lived and room-scoped.
- FCM registration tokens are stored in private `devices/{uid}` documents, not in the public user directory.
- Call documents restrict client updates to allowed status/timestamp transitions while participant and room fields remain immutable.
- Abuse reports are write-only from the client.

## Architecture

```text
Android app
  |-- Firebase Auth -------- identity / accounts
  |-- Cloud Firestore ------ profiles + calls + blocks + reports
  |-- Firebase Messaging --- background incoming-call delivery
  |-- GlobalCall API ------- authenticated call creation + LiveKit tokens
  |-- LiveKit -------------- realtime WebRTC audio/video transport

GlobalCall API
  |-- Firebase Admin ------- verifies users, call membership and device tokens
  |-- Firebase Messaging --- high-priority incoming call push
  |-- LiveKit Server SDK --- short-lived room-scoped participant tokens
```

## Required production configuration

1. Create a Firebase project and Android app with package name `com.globalcall.app`.
2. Enable Firebase Authentication -> Email/Password.
3. Create Cloud Firestore.
4. Deploy this repository's `firestore.rules`.
5. Add the real Firebase Android config as `app/google-services.json`.
6. Create a LiveKit Cloud project or deploy a LiveKit server.
7. Deploy the `server/` service with Firebase Admin application-default credentials.
8. Configure `LIVEKIT_URL`, `LIVEKIT_API_KEY` and `LIVEKIT_API_SECRET` on the server.
9. Set `API_BASE_URL` in `app/build.gradle.kts` to the deployed HTTPS GlobalCall API base URL, for example `https://api.example.com`.

Never commit service-account JSON files, LiveKit API secrets, release keystores or production `.env` files.

## Backend

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

On Google-managed infrastructure such as Cloud Run, prefer workload/application-default credentials instead of shipping a service-account file in the container.

## Android build

CI uses JDK 17 and Gradle 8.9:

```bash
gradle assembleDebug
```

The debug APK is uploaded by GitHub Actions as a workflow artifact.

## Remaining launch-hardening work

The main calling product flow is implemented. Before a large international public launch, complete the environment-specific release work below:

- Add phone-number / Google authentication only if the product requires those sign-in methods
- Add username/QR discovery or optional address-book contact matching if desired
- Add network-quality indicators, explicit reconnection UX and audio-device switching
- Add App Check / Play Integrity and stricter automated abuse/rate controls
- Publish Privacy Policy and Terms of Service and define data-retention/export processes
- Add automated unit/UI tests plus real-device and weak-network call testing
- Configure Crashlytics/monitoring dashboards and backend alerting
- Configure release signing, Play Console declarations and production rollout tracks
- Plan regional LiveKit/SFU capacity if self-hosting instead of LiveKit Cloud

## Current dependency references

- Firebase Android BoM: `34.18.0`
- LiveKit Android SDK: `2.28.0`
- LiveKit Server SDK (Node): `2.18.0`

# Bangla VPN (Android + WireGuard)

An Android WireGuard client for connecting to Bangladesh-hosted VPN infrastructure that you own or are authorized to operate. The normal user flow is intentionally simple: choose a preconfigured server and tap **CONNECT**.

## Important scope

This project is for lawful VPN use, privacy, remote access, testing, and routing through infrastructure you are authorized to use. It is **not** designed to defeat KYC, anti-fraud, geographic access controls, or other protections used by financial or third-party services. Those services may block VPN traffic or require official overseas-access methods.

## Current app

- Android 7.0+ (`minSdk 24`)
- Compiles and targets Android 16 / API 36
- WireGuard userspace backend via `com.wireguard.android:tunnel:1.0.20260102`
- Android VPN consent flow using `VpnService.prepare()`
- Bangladesh-themed UI and launcher icon
- Three selectable server slots: `Bangladesh 1`, `Bangladesh 2`, `Bangladesh 3`
- One-tap user flow: server dropdown -> CONNECT
- No manual endpoint/key fields in the normal UI
- Connect / disconnect controls
- IPv4 full tunnel with `0.0.0.0/0`
- GitHub Actions debug APK build

## Preconfigured server values

Server values are injected into `BuildConfig` from environment variables. The GitHub Actions workflow reads the same names from repository Actions secrets.

For each server slot `1`, `2`, and `3`, configure:

```text
BD_VPN_1_NAME
BD_VPN_1_ENDPOINT
BD_VPN_1_SERVER_PUBLIC_KEY
BD_VPN_1_CLIENT_PRIVATE_KEY
BD_VPN_1_CLIENT_ADDRESS
```

Repeat with `_2_` and `_3_` for the other server slots.

Example endpoint format:

```text
203.0.113.10:51820
```

If a slot is missing any required value, the app displays it as `Setup pending` and refuses to connect with that incomplete profile.

### Security note

Do not commit live private keys to the repository. For a single private/test build, build-time secrets can provide a simple one-tap experience. For a real multi-user production VPN, do **not** distribute one shared client private key inside the APK; use secure per-device enrollment/provisioning instead.

## Build

The project uses Android Gradle Plugin 8.13.2, Gradle 8.13, and JDK 17.

```bash
gradle :app:assembleDebug
```

The APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Server

See [`server/ubuntu-wireguard.md`](server/ubuntu-wireguard.md) and [`server/bootstrap-wireguard.sh`](server/bootstrap-wireguard.sh) for an authorized Ubuntu WireGuard server setup. For Bangladesh egress, the VPS itself needs a public IP actually geolocated in Bangladesh.

## Before production

A production release should add:

- secure account/enrollment API
- per-device WireGuard keys and peer provisioning
- key rotation and peer revocation
- encrypted local secret storage where needed
- server health checks and automatic failover
- abuse controls and rate limits
- kill-switch / always-on behavior where appropriate
- DNS leak and IPv6 testing
- privacy policy, logging policy, terms, and support workflow
- signed release builds and Play Store compliance review

## Branch

Development is on `feature/bangladesh-wireguard-vpn-mvp` until reviewed and merged.

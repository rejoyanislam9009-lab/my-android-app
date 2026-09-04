# Bangla VPN MVP (Android + WireGuard)

A minimal Android WireGuard client for connecting to an authorized VPN server that you control. The app accepts a standard client private key, address, DNS, server public key, endpoint, and AllowedIPs, then asks Android for normal VPN permission before bringing the tunnel up.

## Important scope

This project is for lawful VPN use, privacy, remote access, testing, and routing through infrastructure you are authorized to use. It is **not** designed to defeat KYC, anti-fraud, geographic access controls, or other protections used by financial or third-party services. Those services may block VPN traffic or require official overseas-access methods.

## Current MVP

- Android 7.0+ (`minSdk 24`)
- Compiles and targets Android 16 / API 36
- WireGuard userspace backend via `com.wireguard.android:tunnel:1.0.20260102`
- Android VPN consent flow using `VpnService.prepare()`
- Connect / disconnect controls
- Full-tunnel support with `0.0.0.0/0, ::/0`
- Keys are entered at runtime and are not persisted by the MVP
- `.conf`, keystore, and environment-secret files are ignored by Git
- GitHub Actions debug APK build

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

See [`server/ubuntu-wireguard.md`](server/ubuntu-wireguard.md) for a standard Ubuntu WireGuard server configuration. To use a Bangladesh egress IP for legitimate routing, the VPS itself needs a public IP geolocated in Bangladesh.

Never commit server/client private keys to this repository.

## Before production

This is an MVP rather than a commercial VPN platform. A production release should add:

- secure account/enrollment API
- per-device keys and peer provisioning
- key rotation and peer revocation
- encrypted local secret storage if credentials are persisted
- multiple authorized server locations and health checks
- abuse controls and rate limits
- kill-switch / always-on behavior where appropriate
- DNS leak and IPv6 testing
- privacy policy, logging policy, terms, and support workflow
- signed release builds and Play Store compliance review

## Branch

Development is on `feature/bangladesh-wireguard-vpn-mvp` until reviewed and merged.

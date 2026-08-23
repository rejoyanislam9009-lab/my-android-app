# Guide

Guide is an offline-first Android personal routine companion.

## Guide v2.5
- Scrollable Bengali sidebar navigation
- Local profile + 4-digit PIN login
- Premium dashboard with live clock
- Five daily prayer times on the dashboard with live next-prayer countdown
- Fresh automatic location request for prayer setup
- Daily routines and meal schedules
- Timestamped attendance history with clear Bengali monthly labels
- Income/expense accounts and course tracking
- Custom alarms with live countdowns
- Per-alarm ringtone, vibration-only, or ringtone + vibration mode
- Choose alarm ringtone from the phone
- Full-screen alarm controls, notification Stop action and foreground Volume/Back stop support
- Offline prayer-time calculation with Fajr, Dhuhr, Asr, Maghrib and Isha alarm controls
- Phone-selected Azan audio plus two built-in Guide alert-tone options
- Automatic daily local backup, manual backup/export and restore center
- PDF exports for attendance, accounts, routines, and a combined report
- Android/Google system backup remains supported when enabled on the device
- Water tracker, daily progress and streak overview
- Works without any paid API for core features

## Cloud account note
True email/password registration, password-reset email and server cloud-sync require a configured online authentication/database project such as Firebase or Supabase. Guide does not fake these features without backend configuration.

## Build
Every push to the feature branch runs GitHub Actions. Download the generated APK artifact and install it on Android.

Package: `com.guide.app`

Mobile-only CI validation enabled. v2.5 patch validation active.

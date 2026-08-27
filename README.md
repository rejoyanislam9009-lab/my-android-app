# Professional WebView Android App

A production-oriented Android WebView shell for a website.

## Included
- HTTPS-only WebView loading
- JavaScript, DOM storage, cookies and third-party cookies
- Back-button browser navigation
- HTML file upload support
- Android Download Manager integration
- External `tel:`, `mailto:`, `sms:`, `intent:` and app links
- Offline/load-error screen with retry
- Page loading progress indicator
- WebView state restoration
- GitHub Actions APK build artifact

## Final branding/configuration needed
Before release, replace the placeholder values:

1. `WEBVIEW_URL` in `gradle.properties` with the exact HTTPS website URL.
2. `app_name` in `app/src/main/res/values/strings.xml`.
3. `app/src/main/res/drawable/ic_launcher.xml` with the final brand icon/logo.
4. Change `applicationId` in `app/build.gradle` if a custom Play Store package ID is required.

## Build
The `webview-app` branch automatically runs the **Build WebView APK** GitHub Actions workflow. The generated debug APK is uploaded as the `professional-webview-apk` workflow artifact.

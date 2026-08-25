# Store Manager v1

A lightweight Android WooCommerce manager designed to be built entirely in GitHub Actions and used from a phone.

## Features

- Secure first-run store connection using HTTPS + WooCommerce REST API keys
- API credentials encrypted with Android Keystore
- Recent order dashboard
- Order number, customer, email, phone, products, quantity, payment method and total
- Search orders by number, customer or product
- Filter by WooCommerce order status
- Change order status from the app
- Automatic refresh every 60 seconds while the app is open
- New-order notification when a newer order is detected
- No paid API or external backend required for v1

## Connect your WooCommerce website

1. In WordPress admin, open WooCommerce > Settings > Advanced > REST API.
2. Add a key for an administrator/store manager account.
3. Choose Read/Write permission so the app can both view orders and update status.
4. Copy the Consumer Key and Consumer Secret.
5. Install the APK, enter the HTTPS website URL and both keys, then tap **Test & Connect**.

Never paste the Consumer Secret into a public GitHub file or issue. Enter it only inside the installed app.

## Build

The workflow `.github/workflows/store-manager-apk.yml` builds a debug APK on every push to the `store-manager` branch and uploads it as the `StoreManager-v1-debug` Actions artifact.

## Current limitation

Version 1 checks for new orders every 60 seconds while the app is open. True instant push notifications while the app is fully closed will require a webhook/push layer (for example WooCommerce webhooks + Firebase Cloud Messaging) in a later version.

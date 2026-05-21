# Privacy Policy – AppVault

Last updated: 2026-05-21

## 1. Controller
Daniel Sichler (Darexsh)  
Email: sichler.daniel@gmail.com

## 2. Scope
This policy applies to the AppVault Android app (`com.darexsh.appvault`) distributed via this repository/APK releases.

## 3. What AppVault processes
AppVault reads app metadata available on your device to provide its features, including:
- app name
- package name
- version information
- install/update dates
- requested permissions
- APK source paths and related technical metadata

## 4. Local data storage
AppVault creates local APK backups only when you trigger backup actions.

Backups are written to device storage (currently in `App_Backups`, e.g. `/storage/emulated/0/App_Backups`), including split APK files where applicable.

AppVault does not upload these backup files to the developer.

## 5. Permissions used
AppVault currently declares:
- `QUERY_ALL_PACKAGES` (to list installed apps)
- `REQUEST_DELETE_PACKAGES` (to initiate uninstall flows)
- `MANAGE_EXTERNAL_STORAGE` (Android 11+ backup storage access)
- `WRITE_EXTERNAL_STORAGE` (legacy, max SDK 28)

Permissions are used to provide app inventory, uninstall, and backup functionality.

## 6. Analytics, tracking, and crash reporting
AppVault currently does **not** include third-party analytics SDKs or third-party crash reporting SDKs (such as Firebase Analytics/Crashlytics, Sentry, Mixpanel, etc.) in this project.

## 7. Network and external links
AppVault does not implement a dedicated backend API client in this project.

On user action, AppVault can open external destinations via system intents, including:
- email (`mailto:`)
- Linktree
- Telegram
- GitHub
- Buy Me a Coffee

When you open external links, the privacy policies of those third-party services apply.

### Third-party services referenced by links
- GitHub: https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement
- Telegram: https://telegram.org/privacy
- Linktree: https://linktr.ee/s/privacy
- Buy Me a Coffee: https://www.buymeacoffee.com/privacy-policy

## 8. Data sharing
Based on the current app implementation in this repository, AppVault does not transmit your backup files to the developer.

## 9. Backup disclaimer
The backup feature is provided without guarantee of successful restoration on every device/Android version/app variant.

In particular, split APK backups may require split-aware installation methods. You are responsible for verifying backup integrity and compatibility before relying on backups.

## 10. Your choices
- You can decide whether to use backup features.
- You can deny/revoke relevant storage permissions in Android settings.
- You can avoid opening external links.

## 11. Changes to this policy
This policy may be updated if app functionality or legal requirements change.

# VANTA — Android Security Companion

**Codename:** VOID · OLED black + electric cyan

VANTA is an Android security companion with Android's real biometric/device-credential prompt and an explicit Device Admin action to lock the phone. It is not a replacement for Android's lock screen.

## Implemented in v0.3.0
- AndroidX BiometricPrompt with device-credential fallback when supported.
- Explicit Device Admin activation and **LOCK DEVICE NOW** using Android's `DevicePolicyManager.lockNow()`.
- **WhatsApp Mode** using an Android `NotificationListenerService`.
- Captures readable notification title/body previews from WhatsApp and WhatsApp Business only.
- Stores up to 300 previews in VANTA's private app storage.
- Screen to enable notification access and clear the WhatsApp archive.
- No network permission, advertising, or analytics.

## WhatsApp Mode: capabilities and limits
- The user must explicitly enable Notification Access in Android Settings.
- VANTA captures only WhatsApp notification content Android exposes after access is enabled.
- If previews are hidden, disabled, grouped without readable text, or withheld, VANTA cannot save unavailable message text.
- If a message is deleted after its readable preview was captured, the saved preview may remain. This is not guaranteed for every notification.
- VANTA cannot inspect WhatsApp's private chat database, bypass end-to-end encryption, or recover messages that were never captured.
- Notification access does not provide actual voice-note audio files or other media. A separate user-authorized media/backup import workflow would be required; it is not implemented.
- Clear the archive to remove any older entries captured by the previous all-app listener.

## Privacy and security
- Notification access is a powerful Android special permission; grant it only if you understand the access.
- Saved previews are stored in VANTA's private app storage. They are **not encrypted by this implementation**; device-level Android encryption and the phone's secure lock screen remain important.
- Clear the archive from WhatsApp Mode or remove VANTA's app data to erase saved previews.
- VANTA does not collect, store, or handle biometric templates, PINs, passwords, or pattern secrets.
- VANTA's in-app authenticated state is an app UI gate only. It does not prevent leaving the app or replace Android's secure lock screen.

## Build
The project uses Android Gradle Plugin 8.7.3 and Java 17. GitHub Actions is configured to build a debug APK and upload it as an artifact when a commit is pushed or manually triggered.

## Next verification
- [ ] CI build succeeds and APK artifact is available
- [ ] Install and test on the target Android phone
- [ ] Confirm Notification Access opt-in and WhatsApp-only incoming preview capture
- [ ] Test hidden-preview, grouped-notification, and deleted-after-capture behavior
- [ ] Decide on a separate, user-authorized audio/media import workflow
- [ ] Accessibility, reduced-motion, and lock/re-auth lifecycle testing
- [ ] Explore approved Device Owner / dedicated-device deployment separately; factory reset must never be attempted without backup and explicit approval

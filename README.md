# VANTA — Android Security Companion

**Codename:** VOID · OLED black + electric cyan

VANTA is an Android security companion with Android's real biometric/device-credential prompt and an explicit Device Admin action to lock the phone. It is not a replacement for Android's lock screen.

## Implemented in v0.3.0
- AndroidX BiometricPrompt with device-credential fallback when supported.
- Explicit Device Admin activation and **LOCK DEVICE NOW** using Android's `DevicePolicyManager.lockNow()`.
- VOID Message Vault with an Android NotificationListenerService.
- Local-only storage of readable notification title/body previews (up to 300 entries).
- Vault screen to enable notification access and clear saved previews.
- No network permission, advertising, or analytics.

## Message Vault limitations and privacy
- Notification access is an Android special access that the user must explicitly enable in system settings.
- VANTA can only capture readable notification content delivered after access is enabled. It cannot recover notifications or messages from before setup.
- Some apps hide previews, and some notifications contain no message text; those cannot be saved.
- This is not a WhatsApp database reader and does not bypass encryption or app security. It cannot recover deleted WhatsApp audio messages, voice notes, or media files.
- Saved notification previews remain in VANTA's private app storage and can be cleared from the Vault. Android app data removal also removes them.

## Important security boundary
VANTA's in-app authenticated state is an app UI gate only. It does **not** prevent leaving the app, entering other apps, or replace the Android lock screen. Android's own secure lock screen remains the device protection. VANTA does not collect, store, or handle biometric templates, PINs, passwords, or pattern secrets.

## Build
The project uses Android Gradle Plugin 8.7.3 and Java 17. GitHub Actions builds a debug APK and uploads it as an artifact when a commit is pushed or manually triggered.

## Next verification
- [ ] CI build succeeds and APK artifact is available
- [ ] Install and test on the target Android 12 phone
- [ ] Confirm Notification Access opt-in, incoming preview capture, persistence, and clear action
- [ ] Test notification privacy settings and hidden-preview behavior
- [ ] Accessibility, reduced-motion, and lock/re-auth lifecycle testing
- [ ] Explore approved Device Owner / dedicated-device deployment separately; factory reset must never be attempted without backup and explicit approval

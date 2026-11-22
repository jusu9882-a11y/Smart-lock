# VANTA — Android Security Companion

**Codename:** VOID · OLED black + electric cyan

VANTA is an Android security companion with Android's real biometric/device-credential prompt and an explicit Device Admin action to lock the phone. It is not a replacement for Android's lock screen.

## Implemented in v0.5.0
- AndroidX BiometricPrompt with device-credential fallback when supported.
- Explicit Device Admin activation and **LOCK DEVICE NOW** using Android's `DevicePolicyManager.lockNow()`.
- **WhatsApp Mode** using an Android `NotificationListenerService`.
- Captures readable notification title/body previews from WhatsApp and WhatsApp Business only.
- Stores up to 300 previews encrypted with AES-GCM using a key held by Android Keystore.
- Screen to enable notification access and clear the WhatsApp archive.
- User-selected audio import through Android's system document picker; new imports are encrypted with AES-GCM using a separate Android Keystore key.
- Audio playback decrypts into a temporary cache file and removes that file after playback or when the screen closes; users can delete imported audio.
- 50 MB per-file import limit.
- No network permission, advertising, or analytics.

## WhatsApp Mode: capabilities and limits
- The user must explicitly enable Notification Access in Android Settings.
- VANTA captures only WhatsApp notification content Android exposes after access is enabled.
- If previews are hidden, disabled, grouped without readable text, or withheld, VANTA cannot save unavailable message text.
- If a message is deleted after its readable preview was captured, the saved preview may remain. This is not guaranteed for every notification.
- VANTA cannot inspect WhatsApp's private chat database, bypass end-to-end encryption, or recover messages that were never captured.
- Notification access does not provide actual voice-note audio files or other media. VANTA supports manually importing audio files that the user can already access through Android's system file picker. It does not automatically extract audio from WhatsApp or restore deleted media.

## Privacy and security
- Notification access is a powerful Android special permission; grant it only if you understand the access.
- Message previews are encrypted at rest using AES-GCM with a key stored in Android Keystore.
- New audio imports are encrypted at rest using AES-GCM with a separate Android Keystore key. During playback, a temporary decrypted copy exists in the app cache and is deleted after playback or when the screen closes.
- Audio files imported by older VANTA versions may remain unencrypted until the user deletes and imports them again. They are identified as **LEGACY FILE** in the list.
- Clear the message archive or delete audio files from WhatsApp Mode to remove saved items. Removing app data also removes app-private files and the app's Keystore keys.
- VANTA does not collect, store, or handle biometric templates, PINs, passwords, or pattern secrets.
- The in-app authenticated state is an app UI gate only. It does not prevent leaving the app or replace Android's secure lock screen.

## Build
The project uses Android Gradle Plugin 8.7.3 and Java 17. GitHub Actions builds a debug APK and uploads it as an artifact when a commit is pushed or manually triggered.

## Next verification
- [ ] CI build succeeds and APK artifact is available
- [ ] Install and test on the target Android phone
- [ ] Confirm Notification Access opt-in and WhatsApp-only incoming preview capture
- [ ] Test hidden-preview, grouped-notification, and deleted-after-capture behavior
- [ ] Test encrypted audio import, playback, deletion, and legacy-file handling
- [ ] Accessibility, reduced-motion, and lock/re-auth lifecycle testing
- [ ] Explore approved Device Owner / dedicated-device deployment separately; factory reset must never be attempted without backup and explicit approval

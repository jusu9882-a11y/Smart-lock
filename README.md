# VANTA — Android Security Companion

**Codename:** VOID · OLED black + electric cyan

VANTA is an Android app with a real Android biometric/device-credential prompt and an explicit Device Admin action to lock the phone. It is not a replacement for Android's lock screen and cannot secure the entire phone merely by showing a custom Compose screen.

## Implemented in v0.2
- AndroidX BiometricPrompt: system biometric prompt with device-credential fallback (when available on the device/OS).
- Device Admin receiver with the force-lock policy. The user explicitly activates it from Android's Device Admin confirmation screen.
- **LOCK DEVICE NOW** invokes Android's `DevicePolicyManager.lockNow()` after activation.
- Offline UI; no network permission, advertising, or analytics.

## Important security boundary
VANTA's in-app authenticated state is an app UI gate only. It does **not** prevent leaving the app, entering other apps, or replace the Android lock screen. The only device-wide lock action in this build is Android's system `lockNow()`. Android's own secure lock screen remains the device protection.

VANTA does not collect, store, or handle biometric templates, PINs, passwords, or pattern secrets.

## Build
The repository currently uses Android Gradle Plugin 8.7.3 and Java 17. GitHub Actions builds the debug APK and uploads it as an artifact when a commit is pushed or manually triggered.

## Roadmap
- [x] Initial Compose VOID visual foundation
- [x] Android biometric/device-credential prompt integration
- [x] Explicit Device Admin system-lock action
- [ ] CI build verified and installable APK
- [ ] Device testing on Android 12 and API 26–35
- [ ] Lock/re-auth state lifecycle behavior, accessibility, and reduced-motion
- [ ] Explore approved Android device-owner/kiosk deployment options (not a normal consumer lockscreen replacement)

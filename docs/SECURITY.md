# VANTA security boundary and threat model

## Current implementation
- Authentication UI uses AndroidX BiometricPrompt with strong biometrics and device-credential fallback where supported.
- Device-wide lock is requested only through the Android Device Admin receiver's declared `force-lock` policy and the explicit `DevicePolicyManager.lockNow()` action.
- Device Admin activation is user-confirmed by Android.
- No biometric templates or device credentials are stored or handled by VANTA.
- No internet permission is requested; the app is intended to work offline.

## Limitations (must not be hidden)
- VANTA is an ordinary Android application, not a privileged system lock-screen implementation.
- Its in-app authenticated state is not a device-wide access-control boundary. Users can exit the app or switch applications.
- Android's secure lock screen remains responsible for protecting the device. VANTA can invoke the system lock when its Device Admin permission is active, but cannot replace that lock screen.
- Device Admin activation and behavior vary by Android version/device manufacturer and must be tested on actual hardware.

## Safety requirements
- Do not use accessibility service tricks, overlays, stealth, boot persistence, or device-admin misuse to simulate system security.
- Do not store PINs/passwords/biometric data.
- Treat any future custom dial/gesture as UX only unless backed by a properly designed cryptographic authentication system.

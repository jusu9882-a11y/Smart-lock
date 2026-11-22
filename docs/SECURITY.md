# Security notes

The first milestone is an app UI prototype and does not lock the device.

## Security guardrails
- Never misrepresent an overlay or launcher activity as the Android lock screen.
- Keep device-level authentication with Android's system lock and supported credential/biometric APIs.
- Do not store PINs, passwords, biometric data, or secrets in app code or logs.
- Treat gesture patterns as supplementary UX, not cryptographic/device security.
- Investigate Android platform limitations and device compatibility before proposing a lock-screen replacement.
- Avoid accessibility-service misuse, stealth, persistence tricks, or bypassing the system lock.

# VANTA MESH

VANTA MESH is an Android-to-Android messaging app designed for direct offline communication. The app's user-facing entry point is the chat screen; the old lock, vault, and app-guard screens are no longer part of the launcher experience.

## Current implementation

- Native dark chat interface with local and peer message bubbles.
- Bluetooth Classic RFCOMM transport for paired Android phones.
- Wi-Fi Direct transport for nearby Android phones, using a private group-owner socket. Bluetooth remains available.
- Text messages, voice recording, and general file selection.
- Incoming file storage in the app-private `filesDir/mesh_received` directory.
- Optional AirWave phone-motion gesture to send the currently armed draft or attachment.
- Encrypted, app-private message history using AES-GCM and Android Keystore, limited to the latest 500 text messages.
- Local Android notifications for incoming messages/files while the chat activity remains alive in the background.
- Built-in help screen and a splash screen displaying **Powered by JUSU tech team**.
- No account, cloud server, mobile data, advertising, analytics, or public Internet service.

## Offline networking and permissions

Bluetooth uses Android's bonded-device RFCOMM connection. Wi-Fi Direct creates a local peer-to-peer group and opens a TCP socket only to the negotiated group-owner address.

Android requires the `INTERNET` manifest permission for app TCP sockets, including local Wi-Fi Direct sockets. VANTA does not use this permission to contact public Internet hosts or remote servers. The Wi-Fi Direct transport is restricted in code to the negotiated local group-owner address.

Wi-Fi Direct is local-range communication. It does not provide town-to-town messaging by itself. That would require network infrastructure or physical relays, neither of which is part of this offline-only design.

## AirWave scope

AirWave is opt-in and requires the user to arm a prepared draft or attachment. It detects a deliberate phone movement using the linear-acceleration sensor and sends once. It does **not** recognise a fist or open hand. Reliable fist-to-record and open-hand-to-stop gestures require optional on-device camera hand-pose recognition, which is not yet implemented. Touch recording remains available.

## Known limitations requiring verification or further work

- Wi-Fi Direct and Bluetooth must be tested with two physical Android phones; CI cannot verify radio discovery, pairing, routing, or transfer.
- Camera-based hand-pose recognition is not implemented.
- The current file protocol uses Base64 line frames with a 15 MB limit; it does not yet provide streaming, progress, resume, or integrity checks.
- Received files are listed in the chat, audio files can be played in-app, and any received file can be exported through Android's system document picker.
- Notifications are available only while the chat process/session remains alive. There is no always-on background messaging service or store-and-forward relay.
- Chat history stores text only; attachment payloads and voice recordings are not persisted in the transcript.
- Bluetooth and Wi-Fi Direct are local transports; there is no long-distance town-to-town mode without external network infrastructure.
- Android OEM behavior, permission differences, battery restrictions, and screen-size adaptation require physical-device testing.

## Build

The project uses Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35, and Java 17. GitHub Actions validates the manifest, runs unit tests, builds a debug APK, verifies the APK file, and uploads it as an artifact.

Run from the repository root:

```sh
gradle testDebugUnitTest
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

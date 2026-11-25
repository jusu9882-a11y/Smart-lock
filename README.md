# VANTA MESH — Offline One-to-One Chat

VANTA is now a focused Android chat app. The app opens directly into its offline chat screen. Phone locking, biometric authentication, WhatsApp notification archiving, audio vault, and app-locking modules are excluded from this chat-only build.

## Current chat milestone (v0.7.0)

- Direct one-to-one text messaging over Android Bluetooth RFCOMM.
- Designed to work without mobile data, Wi-Fi internet, an account, a cloud service, or Google Play services.
- Android Bluetooth bonding is used for peer authentication and Bluetooth link security.
- No INTERNET permission, advertising, analytics, or remote service.
- Android 8.0 (API 26) or later.

## Connect two phones

1. Turn on Bluetooth on both phones.
2. Pair the phones in Android Bluetooth settings.
3. Open VANTA MESH on the receiving phone and tap **MAKE THIS PHONE DISCOVERABLE**. Accept Android's prompt and leave the screen open.
4. On the sending phone, open VANTA MESH, choose the paired phone from the list, and tap **CONNECT TO PAIRED DEVICE**.
5. When the connection status says CONNECTED, exchange messages.

## Important current limitations

- This milestone supports one direct connection at a time; it is not yet multi-hop mesh.
- Both devices must be paired in Android settings first and remain within Bluetooth range.
- The receiver must keep the MESH screen open while waiting; background delivery is not implemented.
- Chat history is held in memory and clears when the chat screen closes.
- The transport relies on Android's bonded Bluetooth link security. A separate application-layer end-to-end encryption protocol and in-app identity fingerprint verification are not yet implemented.
- Durable offline queues, delivery/read receipts, media messages, group chat, and relay forwarding are not implemented yet.

## Permissions

The app requests Bluetooth Connect, Scan, and Advertise permissions on Android 12 and later. On older Android versions, it uses the legacy Bluetooth permissions. Bluetooth is the only app capability declared; the manifest does not request internet access, notification access, accessibility access, or device administration.

## Build

Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17. GitHub Actions builds a debug APK and uploads it as `vanta-debug-apk`.

## Manual test checklist

- [ ] Pair two Android phones in system Bluetooth settings.
- [ ] Grant Bluetooth permissions on both phones.
- [ ] Start discoverable listener on receiving phone.
- [ ] Connect from sending phone.
- [ ] Send messages in both directions, including emoji and punctuation.
- [ ] Verify chat works with mobile data and Wi-Fi internet disabled.
- [ ] Verify permission denial is handled without a crash.
- [ ] Verify disconnect and reconnect behavior.

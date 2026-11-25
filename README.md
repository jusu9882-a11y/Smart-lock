# VANTA MESH — Offline Chat

VANTA is a focused Android messaging app. The chat-only branch excludes the former phone-lock, app-lock, WhatsApp notification archive, and vault features.

## Implemented in this branch

- Direct one-to-one text chat over Bluetooth Classic RFCOMM, without internet, accounts, cloud services, or Google Play services.
- Application-layer ECDH (P-256) session-key agreement and AES-256-GCM message encryption.
- First-contact peer fingerprint confirmation; previously accepted peer keys are pinned to the paired Bluetooth device address.
- Local message history encrypted with an Android Keystore-protected AES-GCM key.
- Persistent outgoing queue and message acknowledgements; queued messages are retried when a secure session is re-established.
- Duplicate incoming message suppression using message IDs.

## Pair and connect

1. Enable Bluetooth on both Android phones and pair them in Android Bluetooth settings.
2. On the receiving phone, open VANTA and tap **MAKE THIS PHONE DISCOVERABLE**.
3. On the sending phone, select the paired device and tap **CONNECT TO PAIRED DEVICE**.
4. On first connection, compare the peer fingerprint shown by both phones through a trusted channel, then accept it on each phone.
5. Once the status shows **END-TO-END ENCRYPTED**, send messages. Messages typed while disconnected are stored in the encrypted local outbox for the next connection.

## Current limitations — not yet complete

- Transport is still direct one-to-one Bluetooth. Multi-hop mesh relay and store-and-forward between intermediate phones are not implemented.
- The app currently requires the chat screen to remain open for listening and delivery. Reliable background service delivery and automatic reconnect are not implemented.
- Group chats, attachments/media, voice messages, typing indicators, read receipts, contact management, and conversation export are not implemented.
- The ECDH identity keys are long-lived; this initial protocol does not yet provide forward secrecy, key rotation, or a formal audited protocol implementation. The fingerprint check is important and must be done out-of-band before trusting a new peer.
- Bluetooth pairing and compatible Android hardware are prerequisites. A successful CI build does not prove two physical phones can connect.

## Permissions and privacy

The app requests only Bluetooth permissions. It does not request internet, notification-listener, accessibility, or device-administrator access. Chat content and the local identity private key are encrypted at rest; the app does not upload messages.

## Build

Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compile/target SDK 35, minimum SDK 26. GitHub Actions builds a debug APK and publishes it as the `vanta-debug-apk` artifact.

## Device test checklist (after feature work is complete)

- [ ] Pair two Android phones and grant Bluetooth permissions.
- [ ] Verify first-contact fingerprint matching and rejection of a mismatched peer key.
- [ ] Exchange messages in both directions with mobile data and Wi-Fi internet disabled.
- [ ] Verify encrypted history survives force-close and app restart.
- [ ] Queue messages while disconnected and verify delivery after reconnect.
- [ ] Verify duplicate frames do not duplicate messages.
- [ ] Verify disconnect/reconnect, Bluetooth permission denial, and Bluetooth-off behavior.
- [ ] Test supported Android versions and long-running/background behavior.

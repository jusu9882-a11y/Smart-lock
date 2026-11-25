# VANTA MESH — Offline Chat

VANTA is a focused Android messaging app. The chat-only branch excludes the former phone-lock, app-lock, WhatsApp notification archive, and vault features.

## Implemented

- Direct one-to-one chat over Bluetooth Classic and local Wi-Fi Direct.
- No accounts, cloud server, mobile data, or Internet endpoints. The Android INTERNET permission is used only to open the local TCP socket required by Wi-Fi Direct.
- ECDH P-256 identity keys, AES-GCM message encryption, and sender ECDSA signatures for store-and-forward mesh packets.
- First-contact fingerprint confirmation for direct peers and for the origin of a received mesh message.
- Encrypted local chat history, encrypted durable outbox, message acknowledgements, and duplicate suppression.
- AAC voice notes (up to 20 seconds / 256 KB), encrypted in transit and at rest.
- Persistent encrypted relay queue. A trusted recipient can be selected from previously verified peers; participating phones carry opaque packets between connections. Packets expire after 7 days and are limited to 8 hops.
- Local Android notifications; no push service or FCM.

## Direct connections

1. For Bluetooth, pair phones in Android Settings, make the receiving phone discoverable, then connect from VANTA.
2. For Wi-Fi Direct, turn Wi-Fi on, discover nearby VANTA phones, select a peer, and connect. No router or Internet hotspot is needed.
3. Compare fingerprints through a trusted separate channel before accepting a new identity.
4. Keep VANTA open while exchanging messages.

## Send through the offline mesh

1. Connect to and verify the intended recipient at least once so their public identity is saved as trusted.
2. Type a message and tap **SEND VIA OFFLINE MESH**.
3. Choose the recipient fingerprint. VANTA encrypts the content for that recipient and signs the packet with the sender identity.
4. Connect to a nearby participating VANTA phone. The encrypted packet is stored and forwarded when that phone later connects to another VANTA phone.
5. The recipient verifies the sender fingerprint before accepting the message. A signed delivery receipt is routed back through the mesh.

Each hop requires two participating phones to be physically within Bluetooth or Wi-Fi Direct range. A chain of phones can extend delivery beyond one radio link, but VANTA cannot guarantee a town-to-town connection if no relay phones meet. This release requires the user to keep the app open and manually connect each hop; it does not relay in the background.

## Security and limitations

- Relay nodes can see packet metadata (recipient fingerprint, expiry, hop count) but cannot decrypt the message body.
- Relay messages are signed by the originating identity and encrypted for the intended recipient. First-time sender fingerprints must be checked out-of-band.
- The initial protocol uses long-lived P-256 identity keys and does not yet provide forward secrecy or audited key rotation.
- Relay hop count is bounded to 8 and packet expiry to 7 days. Delivery receipts are also carried as encrypted, signed relay packets.
- Voice notes currently use the direct chat send/queue path; the mesh relay UI currently sends text messages.
- Background listening, automatic reconnect, group chat, file attachments, and automatic multi-hop routing are not implemented. Keep VANTA open and manually connect each hop.
- Wi-Fi Direct and Bluetooth range depend on device hardware, Android version, and the environment. CI success does not replace real two-phone / multi-phone testing.

## Permissions and build

- Bluetooth / Nearby Devices for Bluetooth.
- Nearby Wi-Fi Devices on Android 13+ or Location permission on older Android versions for Wi-Fi Direct discovery.
- Microphone permission only when recording a voice note.
- Notifications permission for local message alerts.
- The INTERNET manifest permission is required by Android's local Wi-Fi Direct TCP socket implementation; VANTA does not open Internet endpoints or use mobile data, cloud services, FCM, or a central server.
- Android Gradle Plugin 8.7.3, Kotlin 2.0.21, Java 17, compile/target SDK 35, minimum SDK 26.

## Device test checklist

- [ ] Bluetooth text and voice note transfer between two phones.
- [ ] Wi-Fi Direct text transfer between two phones with mobile data disabled.
- [ ] Verify sender fingerprint confirmation for first mesh delivery.
- [ ] Three-phone store-and-forward test: A → B → C, with A and C never directly connected.
- [ ] Verify delivery receipt returns C → B → A.
- [ ] Confirm duplicate packets do not duplicate messages and expired packets are discarded.
- [ ] Verify encrypted history and relay queue survive app restart.
- [ ] Test permissions and supported Android versions.

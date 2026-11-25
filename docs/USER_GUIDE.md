# VANTA MESH — User Guide

**Powered by JUSU tech team**

## Before you start
- Install VANTA MESH on both Android phones.
- Turn Bluetooth on for both phones.
- Open Android Settings → Bluetooth and pair the two phones.
- Keep both phones close together during setup and messaging.
- VANTA's current release is direct one-to-one text chat. It does not need mobile data, Wi-Fi internet, an account, or a cloud server.

## Connect two phones
1. On the receiving phone, open VANTA MESH.
2. Tap **MAKE THIS PHONE DISCOVERABLE** and accept Android's visibility prompt.
3. Leave VANTA open on the receiving phone.
4. On the sending phone, choose the receiving phone from the paired-device list.
5. Tap **CONNECT TO PAIRED DEVICE**.
6. On first contact, compare the peer fingerprint displayed by the two phones using a trusted separate channel (for example, compare it in person).
7. Accept the new peer only when the fingerprints match. If they do not match, reject the connection.
8. Wait for **END-TO-END ENCRYPTED** to appear.

## Send and receive
1. Tap the message field and type your message.
2. Tap **SEND / QUEUE MESSAGE**.
3. Received and sent messages appear in the conversation area.
4. A message queued while disconnected is stored in encrypted local storage and is sent when you reconnect to that peer and establish a secure session.

## Security and privacy
- Message contents are encrypted between the two paired VANTA identities using an ECDH-derived AES-GCM session key.
- Local conversation history and the local private identity key are encrypted using an Android Keystore-protected key.
- First-time peer fingerprints must be checked out-of-band. Do not approve an unknown or unexpected identity.
- Pairing in Android Bluetooth settings is required.
- The app does not request internet, notification-listener, accessibility, or device-administrator permissions.

## Troubleshooting
| Problem | What to do |
|---|---|
| No paired devices listed | Pair the phones in Android Settings → Bluetooth, then reopen VANTA. |
| Receiver is not found | Tap **MAKE THIS PHONE DISCOVERABLE** on the receiving phone and keep VANTA open. |
| Connection fails | Check Bluetooth is on for both phones, confirm pairing, move closer, and retry. |
| Permission prompt appears | Allow the requested Nearby devices/Bluetooth permissions. |
| Peer fingerprint changed | Reject the connection until you have independently verified the peer identity. |
| Message remains queued | Reconnect to the same peer, accept only its verified identity, and keep both app screens open. |

## Current release limitations
- Direct one-to-one Bluetooth text chat only.
- No multi-hop mesh relay, group chat, photo/file attachments, voice messages, or typing/read receipts.
- Background listening and automatic reconnection are not implemented; keep VANTA open while connecting and exchanging messages.
- Bluetooth availability and behavior depend on the Android device and OS version.

**Important:** CI success means the APK compiled. It does not replace real two-phone testing of Bluetooth behavior.

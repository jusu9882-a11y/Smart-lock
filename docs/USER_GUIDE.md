# VANTA MESH — User Guide

**Powered by JUSU tech team**

## Before you start
- Install VANTA MESH on both Android phones.
- Turn Bluetooth on for both phones and pair them in Android Settings → Bluetooth for the Bluetooth option.
- For Wi-Fi Direct, turn Wi-Fi on on both phones; a router, mobile data plan, and Internet connection are not needed.
- Keep both phones within direct radio range.
- Keep VANTA open while connecting and exchanging messages.

## Connect two phones
1. On the receiving phone, open VANTA MESH.
2. Tap **MAKE THIS PHONE DISCOVERABLE** and accept Android's visibility prompt.
3. Leave VANTA open on the receiving phone.
4. On the sending phone, choose the receiving phone from the paired-device list.
5. Tap **CONNECT TO PAIRED DEVICE**.
6. On first contact, compare the peer fingerprint displayed by the two phones using a trusted separate channel (for example, compare it in person).
7. Accept the new peer only when the fingerprints match. If they do not match, reject the connection.
8. Wait for **END-TO-END ENCRYPTED** to appear.

## Use Wi-Fi Direct (offline)
1. Turn Wi-Fi on on both phones. Do not connect to a router or Internet hotspot for this feature.
2. Tap **DISCOVER NEARBY WI-FI DIRECT PHONES** on both phones.
3. On one phone, select the other phone from the Wi-Fi Direct list and tap **CONNECT OVER WI-FI DIRECT**.
4. Grant Nearby Wi-Fi devices permission on Android 13+ or Location permission on older Android versions. Some Android versions also require Location services to be enabled for peer discovery.
5. Compare and verify the peer fingerprint before accepting the first connection.
6. Wi-Fi Direct is a local peer-to-peer radio link; it does not provide town-to-town reach by itself.

## Send text and voice notes
1. Type in the message field and tap **SEND / QUEUE MESSAGE** to send text.
2. To record audio, tap **RECORD VOICE NOTE (MAX 20 SEC)**, speak, then tap **STOP & SEND VOICE NOTE**.
3. Voice notes are recorded as AAC, encrypted before storage/transmission, and limited to 20 seconds and 256 KB.
4. Tap **PLAY LAST VOICE NOTE** to play the most recent voice note saved in this conversation.
5. Text and voice notes can be queued locally while disconnected and sent when a secure direct link is active.
6. Keep both phones and VANTA open while sending and receiving.

## Security and privacy
- Message contents are encrypted between the two paired VANTA identities using an ECDH-derived AES-GCM session key.
- Local conversation history and the local private identity key are encrypted using an Android Keystore-protected key.
- First-time peer fingerprints must be checked out-of-band. Do not approve an unknown or unexpected identity.
- Pairing in Android Bluetooth settings is required.
- The app does not use mobile data, Internet endpoints, cloud services, or a central server. Android requires the INTERNET manifest permission for local TCP sockets used by Wi-Fi Direct; VANTA uses that socket only on the local Wi-Fi Direct peer link.
- The app does not request notification-listener, accessibility, or device-administrator permissions.

## Troubleshooting
| Problem | What to do |
|---|---|
| No paired devices listed | Pair the phones in Android Settings → Bluetooth, then reopen VANTA. |
| Receiver is not found | Tap **MAKE THIS PHONE DISCOVERABLE** on the receiving phone and keep VANTA open. |
| Connection fails | Check Bluetooth is on for both phones, confirm pairing, move closer, and retry. |
| Permission prompt appears | Allow the requested Nearby devices, Bluetooth, microphone, or Wi-Fi Direct discovery permissions. |
| Peer fingerprint changed | Reject the connection until you have independently verified the peer identity. |
| Message remains queued | Reconnect to the same peer, accept only its verified identity, and keep both app screens open. |

## Current release limitations
- Direct one-to-one Bluetooth and Wi-Fi Direct chat, plus encrypted voice notes.
- No multi-hop store-and-forward relay, group chat, photo/file attachments, or typing/read receipts.
- Background listening and automatic reconnection are not implemented; keep VANTA open while connecting and exchanging messages.
- Bluetooth availability and behavior depend on the Android device and OS version.

**Important:** CI success means the APK compiled. It does not replace real two-phone testing of Bluetooth behavior.

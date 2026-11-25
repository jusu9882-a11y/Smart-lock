# VANTA MESH — User Guide

**Powered by JUSU tech team**

## Before you start
- Install VANTA MESH on each participating Android phone.
- Bluetooth: pair phones in Android Settings → Bluetooth.
- Wi-Fi Direct: turn Wi-Fi on; no router, mobile data, or Internet hotspot is needed.
- Keep VANTA open while connecting and exchanging messages.

## Direct Bluetooth chat
1. On the receiving phone, open VANTA MESH and tap **MAKE THIS PHONE DISCOVERABLE**.
2. On the other phone, choose the receiving phone from the paired-device list.
3. Tap **CONNECT TO PAIRED DEVICE**.
4. On first contact, compare the peer fingerprint using a trusted separate channel. Accept only if it matches.
5. Wait for **END-TO-END ENCRYPTED**, then send messages.

## Direct Wi-Fi Direct chat
1. Turn Wi-Fi on on both phones.
2. Tap **DISCOVER NEARBY WI-FI DIRECT PHONES** on both phones.
3. Select the other phone and tap **CONNECT OVER WI-FI DIRECT**.
4. Grant Nearby Wi-Fi devices permission on Android 13+ or Location permission on older Android versions. Some versions also require Location services enabled for discovery.
5. Verify the peer fingerprint on first contact.
6. Wi-Fi Direct is a local radio link; it does not by itself reach another town.

## Send through the offline mesh
1. Connect to and fingerprint-verify the intended recipient at least once. This saves the recipient's public identity as a trusted peer.
2. Type your message and tap **SEND VIA OFFLINE MESH**.
3. Choose the intended recipient fingerprint. VANTA encrypts the message for that recipient and signs it with your identity.
4. Connect to a nearby participating VANTA phone. The relay phone stores the encrypted packet without reading its contents.
5. When that relay phone later connects to another VANTA phone, it forwards queued packets. Repeat this process across participating phones until the recipient connects.
6. The recipient checks the sender fingerprint before accepting the message. A signed delivery receipt can travel back through the same relay chain.

**Important:** each hop requires two VANTA phones to physically come within Bluetooth or Wi-Fi Direct range. This can extend delivery across multiple hops, but it cannot guarantee town-to-town delivery without a continuous chain of participating phones. Keep VANTA open and manually connect each hop; background relay and automatic routing are not yet available.

Packets expire after 7 days and are limited to 8 relay hops. Relay metadata is visible to participating phones, but the message body is encrypted for the intended recipient.

## Voice notes
1. Tap **RECORD VOICE NOTE (MAX 20 SEC)** and speak.
2. Tap **STOP & SEND VOICE NOTE**.
3. Voice notes use AAC and are limited to 20 seconds / 256 KB.
4. Tap **PLAY LAST VOICE NOTE** to play the most recent voice note in the active direct conversation.
5. Voice notes are encrypted locally and in transit. The current voice-note send path is direct; mesh relay currently carries text messages.

## Privacy and permissions
- Message content and relay packets are encrypted at rest with an Android Keystore-protected key.
- Direct sessions use ECDH-derived AES-GCM encryption. Mesh envelopes use recipient encryption plus an origin signature.
- Verify every new sender fingerprint out-of-band before accepting their messages.
- VANTA does not use mobile data, Internet endpoints, cloud services, FCM, or a central server.
- Android requires the INTERNET manifest permission for the local TCP socket used by Wi-Fi Direct. VANTA uses it only for local peer-to-peer connections.
- Permissions: Bluetooth/Nearby Devices, Wi-Fi discovery permission, microphone only for recording, and notifications for local alerts.

## Current limitations
- Multi-hop relaying is manual: users must keep VANTA open and connect each hop.
- No background listening, automatic reconnect, automatic routing, group chat, or file attachments.
- Wi-Fi Direct and Bluetooth range depend on device hardware and environment.
- CI success confirms compilation, not real-world delivery. Test with at least three physical phones for mesh relay.

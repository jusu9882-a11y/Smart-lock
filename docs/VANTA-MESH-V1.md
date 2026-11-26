# VANTA MESH — offline chat

## Available transports

### Bluetooth
- Pair the two phones in Android Bluetooth settings.
- On the receiving phone, open VANTA MESH and tap **Make discoverable**.
- On the sending phone, select the paired device and tap **Connect to paired device**.
- Both phones must run a compatible VANTA build.

### Wi-Fi Direct
- Turn on Wi-Fi on both phones and grant Nearby Wi-Fi permission when requested.
- On the hosting phone, tap **Host Wi-Fi**.
- On the other phone, tap **Find Wi-Fi Peers**, select the host, then tap **Connect Wi-Fi Peer**.
- Wait for the connection status before sending.

Wi-Fi Direct uses a local peer-to-peer group. Android requires the INTERNET permission to open TCP sockets, but the app connects only to the negotiated local group-owner address; it does not contact a public host or server. Wi-Fi Direct does not reach other towns without network infrastructure.

## Messaging and attachments

- Text messages are Base64-encoded and exchanged over the active local socket.
- Voice recording is started and stopped with touch controls. The completed recording can be previewed before sending.
- The Android document picker can select general files. The current per-file limit is 15 MB.
- Received files are stored under the app-private `filesDir/mesh_received` directory.
- Message text history is encrypted at rest using AES-GCM with an Android Keystore key and retains up to 500 entries.

## Notifications

Incoming messages and files can produce Android notifications while the chat activity/session remains alive in the background. This is not an always-on background service. If Android kills the process or the chat session is closed, VANTA cannot receive new messages until the user reconnects.

## Current limitations

- The current line-framed Base64 file protocol has a 15 MB cap and no streaming, progress, resume, or integrity verification.
- Received attachments do not yet have an in-app export/open workflow.
- Attachment and voice payloads are not part of the persistent text history.
- No multi-hop relay, store-and-forward queue, group chat, or long-distance offline mode.
- Camera fist/open-hand recognition is not implemented.
- Bluetooth and Wi-Fi Direct require two-device physical testing across supported Android versions.

## Required physical test checklist

- [ ] Pair two Android phones and exchange text in both directions over Bluetooth.
- [ ] Transfer a voice note and a general file in both directions over Bluetooth.
- [ ] Disable mobile data and Wi-Fi internet; verify Bluetooth still works.
- [ ] Discover and connect the two phones over Wi-Fi Direct.
- [ ] Exchange text, voice, and files over Wi-Fi Direct.
- [ ] Verify reconnect behavior after either phone disconnects.
- [ ] Verify notification permission, foreground suppression, and background notification behavior.
- [ ] Restart the app and verify encrypted text history remains available.
- [ ] Test Android 8 through current supported Android releases and multiple screen sizes.

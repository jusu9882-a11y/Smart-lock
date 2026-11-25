# VANTA MESH v1 — direct offline chat

## What this milestone does
- Uses Android secure Bluetooth RFCOMM sockets for a direct, one-to-one connection.
- Works without mobile data, Wi-Fi internet, an account, or Google Play services.
- Uses Android Bluetooth bonding for peer authentication and link encryption.
- Does not add INTERNET permission or send data to a server.

## Pair and connect two phones
1. On both phones, turn Bluetooth on in Android Settings.
2. Pair the two phones using Android Bluetooth settings.
3. Open VANTA on the receiving phone and tap **VANTA MESH / OFFLINE CHAT**.
4. Tap **MAKE THIS PHONE DISCOVERABLE** and accept Android's discoverability prompt.
5. Leave the receiving phone on the MESH screen while the listener waits.
6. On the sending phone, open MESH, select the paired receiving phone, and tap **CONNECT TO PAIRED DEVICE**.
7. Once the status says CONNECTED on both ends, exchange text messages.

## Current limits
- One direct connection at a time; no group chat or multi-hop forwarding.
- Both phones must remain in range and the MESH activity must remain alive.
- Messages and transcript are memory-only; closing the screen clears the transcript.
- Pairing is managed by Android Settings, not by an in-app QR/fingerprint flow.
- Secure RFCOMM relies on Android's bonded Bluetooth link security. A separate app-layer E2E protocol and verified identity fingerprints are still required before treating this as a high-assurance private messenger.
- The app does not currently provide durable offline queues, read receipts, or background delivery.

## Manual verification checklist
- [ ] Two Android phones pair successfully in Android Settings.
- [ ] Receiver grants Bluetooth permissions and enables discoverability.
- [ ] Receiver listener accepts a connection from the sender.
- [ ] Sender and receiver both display CONNECTED.
- [ ] Unicode and punctuation messages arrive intact in both directions.
- [ ] Closing either screen closes the session and the other side reports disconnection.
- [ ] With mobile data and Wi-Fi internet disabled, chat still works over Bluetooth.
- [ ] Bluetooth permission denial produces a clear status instead of a crash.

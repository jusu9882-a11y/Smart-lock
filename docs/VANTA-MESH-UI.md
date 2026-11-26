# VANTA MESH interface

The VANTA MESH chat screen uses a native Android view layout so it can run without an internet connection or a web runtime.

## Current interface work

- Dark VANTA palette with cyan primary actions and high-contrast text.
- Rounded connection/status panel, paired-device selector, and connect/discover controls.
- Conversation panel with left-aligned peer bubbles, right-aligned local bubbles, and centered system notices.
- Message list scrolls to the newest entry when a message arrives or is sent.
- Composer, voice recording, file selection, preview/discard, send, and optional AirWave arming controls use consistent touch targets and visual styling.
- Outer screen scroll view keeps controls reachable on compact displays and landscape layouts.
- Touch controls remain available; AirWave is opt-in and requires an explicit arm action.

## Current limitations

- The conversation is still in-memory only; messages are not persisted across process restarts.
- Attachments are represented by message text in the transcript; rich image/document/audio cards and inline voice waveform playback are not implemented.
- The current transfer protocol is a Base64 line protocol over Bluetooth RFCOMM. Large files can consume substantial memory and transfer progress/resume is not yet available.
- Wi-Fi Direct transport and camera-based hand-pose detection are not implemented.
- Visual rendering and interaction still require testing on physical Android devices at different screen sizes and API levels.

## Connectivity truth

The app is designed for offline, direct device-to-device use. The interface must not imply that Bluetooth is connected until a socket is actually established. Wi-Fi Direct, when implemented, will remain a local-network transport and will not provide town-to-town reach without intervening network infrastructure.

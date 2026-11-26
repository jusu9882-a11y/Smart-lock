# VANTA MESH interface

- Native Android chat screen with dark VANTA styling and cyan primary actions.
- Left-aligned peer bubbles, right-aligned local bubbles, and centered system notices.
- Conversation area scrolls to the newest message.
- Screen scroll container keeps controls reachable on compact displays.
- Touch controls are always available.
- Help dialog explains Bluetooth, Wi-Fi Direct, voice, files, AirWave, history, and offline range limits.
- Splash screen displays “Powered by JUSU tech team”.
- The launcher opens the chat experience directly; legacy lock/vault/app-guard screens are no longer exposed through the app manifest.

## Outstanding UI/device work

- Rich file/image/document cards and inline voice-note waveform playback are not implemented.
- Received file cards provide audio playback for audio attachments and a Save to Device action using Android's system document picker.
- Notification display depends on runtime permission and an active chat process.
- Screen-reader, font scaling, landscape, and OEM layout testing still require physical devices.

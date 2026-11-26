# VANTA AirWave

AirWave is an optional gesture shortcut for an already prepared VANTA MESH draft or attachment. It is not a transport and does not replace Bluetooth or Wi-Fi Direct.

## Implemented

- User opt-in is saved locally and disabled by default.
- The user must explicitly arm AirWave after preparing a draft or attachment.
- The linear-acceleration detector looks for a deliberate back-and-forth phone movement.
- One trigger disarms the gesture and calls the existing send action.
- The ordinary touch Send button remains available.
- Touch-based voice recording remains available and is not dependent on camera access.

## Optional camera hand gestures

The app includes an opt-in CameraX + MediaPipe hand-landmark path. A stable closed-fist pose starts voice recording; a stable open-hand pose stops it. A lateral open-hand wave sends only when AirWave is armed. The model is downloaded and checksum-verified during the build, then packaged in the APK; gesture inference has no runtime network dependency. The camera preview is visible while active, frames are processed in memory, and frames are not saved.

The landmark pose classifier is heuristic and has unit coverage for synthetic fist/open-hand landmarks. Real-world reliability still requires physical testing under varied lighting, hand angles, and camera positions. Touch recording and sending remain available.

## Safety and limits

- AirWave is disabled by default.
- It must be explicitly armed for the currently prepared item.
- It disarms after a trigger and when the chat activity pauses.
- It only works when the phone provides the required linear-acceleration sensor.
- The current gesture sends through whichever chat transport is connected.

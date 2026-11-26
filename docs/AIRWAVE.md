# VANTA AirWave

AirWave is an optional gesture shortcut for an already prepared VANTA MESH draft or attachment. It is not a transport and does not replace Bluetooth or Wi-Fi Direct.

## Implemented

- User opt-in is saved locally and disabled by default.
- The user must explicitly arm AirWave after preparing a draft or attachment.
- The linear-acceleration detector looks for a deliberate back-and-forth phone movement.
- One trigger disarms the gesture and calls the existing send action.
- The ordinary touch Send button remains available.
- Touch-based voice recording remains available and is not dependent on camera access.

## Not implemented

Phone motion sensors cannot reliably detect whether the user's fingers are curled into a fist. Camera-based fist/open-hand recognition is not implemented. Do not represent phone-wave detection as hand-pose recognition.

A future camera implementation must be optional, on-device/offline, clearly indicate camera use, avoid saving frames, and use a packaged hand-pose model rather than a remote service.

## Safety and limits

- AirWave is disabled by default.
- It must be explicitly armed for the currently prepared item.
- It disarms after a trigger and when the chat activity pauses.
- It only works when the phone provides the required linear-acceleration sensor.
- The current gesture sends through whichever chat transport is connected.

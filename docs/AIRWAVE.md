# VANTA AirWave — implementation contract

AirWave is an optional gesture shortcut inside an existing VANTA MESH conversation. It is not a new transport and does not replace Bluetooth or Wi-Fi Direct.

## User flow
1. The user records/selects a message, voice note, photo, video, or document.
2. The user explicitly arms AirWave for that pending item.
3. A deliberate back-and-forth phone wave triggers the send command.
4. The chat shows normal transfer progress and delivery state.
5. The gesture listener disarms after one trigger. A normal Send button remains available.

## Optional recording gesture
- A fist held in front of the phone starts voice recording.
- Opening the hand stops recording and leaves the recording in a preview state.
- The user can play, discard, or send the recording using ordinary controls.
- Gesture recording is opt-in; touch-to-record remains available.

## Sensor and camera reality
A phone's accelerometer/gyroscope cannot reliably tell whether fingers are curled into a fist. The first AirWave foundation therefore detects only a deliberate phone-wave motion using Android's linear-acceleration sensor. Actual fist/open-hand recognition needs camera-based hand-pose detection (with camera permission and an on-device model), or a compatible external wearable. If implemented with the camera, it must be opt-in, on-device/offline, show a clear camera-active state, and never save frames. Do not fake fist recognition using accelerometer data.

## Safety
- Never arm gesture sending automatically.
- Only send an item that the user has already selected and explicitly armed.
- Disarm after one successful gesture, on screen pause, disconnect, or cancellation.
- Require an explicit confirmation setting if the user enables it.
- Unknown devices are not trusted automatically.

## Transport
Bluetooth remains supported. Wi-Fi Direct is an additional local transport for faster/larger transfers; it does not provide town-to-town connectivity without network infrastructure. Neither transport requires Internet access.

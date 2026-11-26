# Third-party model notice

VANTA MESH uses the MediaPipe Tasks Vision Android library and Google's Hand Landmarker task asset for optional, on-device hand-pose estimation.

- Gradle artifact: `com.google.mediapipe:tasks-vision:0.10.35`
- Model asset: `hand_landmarker.task`
- Upstream model URL: https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task
- Pinned SHA-256: `fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1`
- The build task verifies the checksum before packaging the asset into the APK.
- The APK performs inference locally. No camera frames are uploaded or saved, and no runtime model download is performed.

The model is not committed as a binary in this repository. A source build downloads it during Gradle's `preBuild` task, so the first source build requires build-time network access unless the verified model file has already been placed at `app/src/main/assets/hand_landmarker.task`. This does not mean the installed app needs Internet access.

Review the upstream MediaPipe repository and model terms before redistributing modified model assets. The MediaPipe source repository is licensed under Apache 2.0; third-party asset terms should be preserved with any redistribution.

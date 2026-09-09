GalaxyRingTorch
===============
Minimal Android app: Samsung Galaxy Ring double-pinch -> flashlight toggle.

Intentionally NOT included:
- INTERNET permission / webhooks
- Location
- Usage access
- Shizuku / READ_LOGS
- Boot receiver
- Tasker
- Debug bridge/server

Build without installing Android Studio:
1. Create a new GitHub repository.
2. Upload the CONTENTS of this GalaxyRingTorch folder to the repository root.
3. Open the repository's Actions tab.
4. Select "Build RingTorch APK" and Run workflow.
   (A push to main/master also starts a build automatically.)
5. Open the completed green workflow run.
6. Under Artifacts, download "RingTorch-APK".
7. Unzip it and install RingTorch.apk on the phone.

The APK produced by this workflow is an Android debug-signed APK. In THIS minimal
project, debug builds do not contain SamsungOpenRing's Claude/debug bridge because
that code is not present at all.

Runtime permissions requested:
- Nearby devices / Bluetooth: connect to the already-paired Galaxy Ring
- Camera: control the rear camera torch
- Notifications (Android 13+): foreground monitoring notification

Stop monitoring from the app when not needed. The app sends the ring's
DISABLE_GESTURE command when its monitoring service is stopped.

# Galaxy Ring Actions 1.1
- Double pinch action selection: flashlight, media play/pause, phone finder alarm (10 seconds), diagnostic-only.
- Optional screen-off-only actions.
- Optional 250ms/500ms duplicate event suppression (NOT reliable typing detection).
- Received event/action counters for investigating false positives.
- No internet, location, accessibility, Shizuku, or webhook permission.

## Limits
- Not tested on a physical device or built in this environment. Build using included GitHub Actions.
- Media play/pause depends on the active media session/app.
- The phone finder is limited to BLE connection range, and audio depends on phone sound settings.
- Typing-related false positives cannot be reliably distinguished using only the ring's pinch event.
- Debug APK is built by Actions. Do not distribute it on Play Store; a properly signed release build and policy review are needed.

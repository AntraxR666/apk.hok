# HoK Draft Assistant 1.0 Personal - Android emulator checklist

This automated smoke test proves only standard Android startup behavior. It does
not emulate Huawei EMUI 9.1, the game's live interface, or the system dialogs
for overlay and MediaProjection permission.

## Automated command

With a booted Android device or AVD connected through adb, run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools\v55_emulator_smoke.ps1
```

The script installs `app-debug.apk`, starts `MainActivity`, saves a UI dump at
`build/qa/hok-v1.xml`, saves AndroidRuntime output filtered to the assistant
process, and fails if the activity is not resumed or that process reports a
fatal exception.

## Manual Android Studio checks

1. Launch the app and verify the start button is visible.
2. Grant overlay permission from the app's normal flow.
3. Start the assistant and approve the Android screen-capture dialog.
4. Expand the bubble; scroll to the final controls without moving the overlay.
5. Verify Auto/Manual and Auto/Ranked/Normal controls update state.
6. In Manual mode, assign and remove a hero in the in-overlay editor.

## Huawei-only final checks

Complete the real-device acceptance checklist for the JKM-LX3 before calling
the candidate final. In particular, validate EMUI background settings, capture
consent, ranked loading confirmation, normal-mode ally-only behavior, explicit
scoreboard scanning, and a full match with the overlay minimized.

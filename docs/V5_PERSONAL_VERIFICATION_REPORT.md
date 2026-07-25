# V5 Personal Verification Report

## Verified in the isolated development worktree

- All Python files compile with `py_compile`.
- GitHub Actions YAML parses successfully.
- Project, V4 compatibility and V5 personal validators pass.
- UI, overlay, scroll, layout-params, slot, pick-state, stage and calibration contracts pass.
- V5 build, background-vision, recognition, diagnostics, single-FGS and delivery contracts pass.
- Legacy PowerShell and delivery contracts remain valid.
- `git diff --check` reports no whitespace errors.
- Pure Kotlin smoke suites pass:
  - capture geometry;
  - vision diagnostics;
  - recognition confidence;
  - stage policy;
  - player-slot resolver;
  - player-pick override;
  - counter catalog;
  - V4 domain regression;
  - video-calibration regression.

## Verified design properties

- API 36 / AGP 8.10.1 / Gradle 8.11.1 / JDK 17 configuration.
- No `android.permission.INTERNET`.
- One foreground service only: `ScreenCaptureService` with `mediaProjection` type.
- Non-sticky services; capture cannot silently restart without new user consent.
- One MediaProjection `createVirtualDisplay()` call per session.
- Resize uses `VirtualDisplay.resize()` and `setSurface()`.
- Capture and OCR dimensions are bounded.
- ML Kit callbacks and post-processing use the vision HandlerThread executor.
- Busy frames are dropped and counted.
- Weak and ambiguous hero observations are rejected before temporal confirmation.
- Delivery launcher uses a real Windows PowerShell parser preflight, creates a backup, validates, pushes, waits for CI and verifies the downloaded APK archive.

## Not yet claimable without external execution

This container does not contain an Android SDK/JDK 17 environment capable of running the full Gradle Android build. Therefore these gates remain pending until the delivery launcher completes GitHub Actions:

- `testDebugUnitTest`;
- `lintDebug`;
- `assembleDebug`;
- APK version metadata and archive verification;
- artifact download and SHA-256.

The final real-device gate also remains pending until the checklist in `V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md` is completed on the target Huawei JKM-LX3. Automated evidence cannot honestly guarantee zero recognition errors across live drafts, skins, animations and future game updates.

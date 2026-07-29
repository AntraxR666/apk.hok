# HoK Draft Assistant 1.0 Personal - verification report

## Automated evidence

- Target profile: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM,
  2340 x 1080.
- Build metadata: `versionCode 14`, `1.0.0-personal-jkm-lx3-rc3`.
- Unit tests: 155 executed, 0 failures, 0 errors and 0 skipped.
- Lint: 0 errors and 41 non-blocking warnings.
- Static contracts: overlay, scroll isolation, 1.0 overlay controls, explicit
  scoreboard scan, item catalog, offline manifest, versioned delivery gate,
  hero aliases, and project validators.
- Android build: a clean `testDebugUnitTest`, `lintDebug` and `assembleDebug`
  completed successfully with Gradle 8.11.1 and JDK 17.
- Emulator smoke: an API 30 AVD was overridden to the JKM-LX3 production viewport
  of `1080 x 2340`, installed from a clean state and granted the overlay and
  MediaProjection flows. The capture surface changed from `540 x 1170` in
  portrait to `1170 x 540` in landscape without requesting a second projection
  consent. The selection status reported live frame processing.
- Overlay geometry: the expanded window measured `975 x 731` inside a
  `1080`-pixel landscape height (67.7 %, below the complete-window 72 % cap).
  An internal swipe kept the overlay origin and frame unchanged, confirming
  that scrolling did not drag the bubble.
- Runtime smoke: `OverlayService` and `ScreenCaptureService` remained active,
  only `ScreenCaptureService` was foreground, and logcat contained no fatal
  exception for the assistant. A repeated-toggle sample reported approximately
  17.5 MB Java heap, 76 MB native heap and 135.6 MB total PSS. These emulator
  measurements are evidence of bounded behavior, not a substitute for the
  physical Kirin 710 performance gate.
- APK inspection: `AndroidManifest.xml`, `classes.dex`, `hok_counters.json`,
  `hok_items.json`, and the ranked portrait seed are present; the merged
  manifest excludes `INTERNET` and `ACCESS_NETWORK_STATE`.
- Local debug APK: 49,495,226 bytes.
- Local debug APK SHA-256:
  `1529133B67CF34BD539D4443C6F696F1291D461FC31D94B28D982CDC027D8FF0`.

## Ranked-recording recognition regression

- Geometry authority: native JKM-LX3 landscape frame, `2340 x 1080`. The supplied
  `848 x 392` recording is test evidence only and never defines production ROIs.
- Portrait input: verified base-selection icons converted to compact, non-reversible
  V3 fingerprints. No source portrait raster is packaged in the APK.
- Each hero has a bounded set of 12 variants covering the scale, crop translation,
  brightness and contrast observed in the ranked recording.
- Matching combines spatial luminance, average/gradient hashes, edge orientation and
  color evidence, followed by an exact-slot 3-of-5 temporal consensus.
- Only exact-slot states confirmed as `DETECTED` or explicitly `MANUAL` enter
  the ranked composition and recommendation snapshot; low per-frame confidence
  cannot silently discard an already-confirmed 3-of-5 identity.
- Manual authority is position-preserving and recomputed from the immutable scan:
  correcting `A3` never moves it to `A1`, a manual identity replaces the automatic
  identity in that exact slot, and removing the correction restores the original
  automatic loading result.
- A hero already confirmed automatically or manually on the same side is removed
  from the one-slot suggestions. After a correction, the UI advances to the next
  unresolved slot instead of returning the user to a multi-step editor.
- Host regression window: seconds 45 through 158, sampled every second.
- Final consensus: 10 of 10 slots correct:
  - allies: Yao, Xiao Qiao, Zhang Fei, Kaizer and Luban No.7;
  - enemies: Arthur, Kongming, Diaochan, Cai Yan and Erin.
- The expected labels are stored in
  `tools/fixtures/ranked_video_jkm_expected.json`; the user-supplied recording remains
  outside the repository.
- Ambiguity rejection remains active. A top candidate must pass distance, margin and
  visual-confidence gates before temporal confirmation; otherwise the slot is routed
  to the one-slot correction UI.

## Safety and accuracy boundaries

- OCR and visual matching remain conservative. Unknown skin art or ambiguous
  evidence is left unresolved and routed to the manual editor.
- Loading-card, draft-side portrait, and scoreboard template domains remain
  separate; a manual loading-card correction can teach only that surface.
- The item catalog is a local dated snapshot. Object labels use verified source
  names until exact Spanish in-game labels are captured.

## Remaining physical-device gate

This candidate is not a final personal release until the checklist in
`docs/V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md` is completed on the actual
JKM-LX3. The mandatory paths are ranked selection, normal selection, loading
confirmation, scoreboard correction, background stability, and a complete
match with no overlay gesture conflict.

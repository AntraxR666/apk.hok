# HoK Draft Assistant 1.0 Personal - verification report

## Automated evidence

- Target profile: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM,
  2340 x 1080.
- Build metadata: `versionCode 13`, `1.0.0-personal-jkm-lx3-rc2`.
- Unit tests: 110 tests, 0 failures, 0 errors, 0 skipped.
- Static contracts: overlay, scroll isolation, 1.0 overlay controls, explicit
  scoreboard scan, item catalog, offline manifest, versioned delivery gate,
  hero aliases, and project validators.
- Android build: `testDebugUnitTest` and `assembleDebug` pass locally.
- Emulator smoke: API 30 AVD installed the debug APK, resumed `MainActivity`,
  produced a UI hierarchy dump, and reported no AndroidRuntime fatal exception
  for the assistant process.
- APK inspection: `AndroidManifest.xml`, `classes.dex`, `hok_counters.json`,
  and `hok_items.json` must be present; the merged manifest must exclude
  `INTERNET` and `ACCESS_NETWORK_STATE`.
- Debug APK SHA-256: `3E994B95D481A9DC3FBFEAFBF30A9694ECA02E29036019A6AA7847DAEBD11B81`.

## Ranked-recording recognition regression

- Geometry authority: native JKM-LX3 landscape frame, `2340 x 1080`. The supplied
  `848 x 392` recording is test evidence only and never defines production ROIs.
- Portrait input: verified base-selection icons converted to compact, non-reversible
  V3 fingerprints. No source portrait raster is packaged in the APK.
- Each hero has a bounded set of 12 variants covering the scale, crop translation,
  brightness and contrast observed in the ranked recording.
- Matching combines spatial luminance, average/gradient hashes, edge orientation and
  color evidence, followed by an exact-slot 3-of-5 temporal consensus.
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

The candidate is not a final personal release until the checklist in
`docs/V5_PERSONAL_REAL_DEVICE_ACCEPTANCE.md` is completed on the actual
JKM-LX3. The mandatory paths are ranked selection, normal selection, loading
confirmation, scoreboard correction, background stability, and a complete
match with no overlay gesture conflict.

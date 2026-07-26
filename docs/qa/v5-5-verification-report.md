# HoK Draft Assistant 1.0 Personal - verification report

## Automated evidence

- Target profile: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM,
  2340 x 1080.
- Build metadata: `versionCode 12`, `1.0.0-personal-jkm-lx3-rc1`.
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
- Debug APK SHA-256: `55FC224E76D714FAC434A8C53A46E2B69E5864AA1C671FA9FA7AD1B56636524B`.

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

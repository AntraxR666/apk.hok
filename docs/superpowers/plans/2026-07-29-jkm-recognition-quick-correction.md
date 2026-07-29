# JKM-LX3 Recognition V2 and Quick Correction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve ranked portrait recognition on Huawei JKM-LX3 and expose an immediate per-slot correction flow for uncertain or unidentified occupied slots.

**Architecture:** Keep the recognizer deterministic and offline. Introduce a backward-compatible V2 fingerprint with edge evidence, slot-level candidate results, bounded temporal consensus, and a compact quick-correction projection consumed by the existing overlay service.

**Tech Stack:** Kotlin/JVM unit tests, Android Views and `TYPE_APPLICATION_OVERLAY`, ML Kit OCR already bundled, JSON fingerprint assets, Gradle/JUnit 4, adb/emulator QA.

## Global Constraints

- Primary device: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM, `2340x1080` landscape.
- Internal capture remains proportional and bounded to `1170x540`.
- Runtime remains offline and must not request `INTERNET` or `ACCESS_NETWORK_STATE`.
- Loading/skin templates stay isolated from base draft portraits.
- Manual identities have confidence `1.0` and are never overwritten automatically.
- Ambiguous visual evidence remains unresolved.
- All interactive overlay targets are at least `48dp`.
- Auto analysis never expands a blocking panel without a deliberate user scan.

---

### Task 1: Add backward-compatible portrait fingerprint V2

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/PortraitFingerprint.kt`
- Test: `app/src/test/java/com/example/honorofkingsassistant/PortraitFingerprintV2Test.kt`

**Interfaces:**
- Produces: `PortraitFingerprint.fromArgb144(pixels: IntArray): PortraitFingerprint`
- Produces: `PortraitFingerprint.version: Int`
- Preserves: `PortraitFingerprint.decode(value: String): PortraitFingerprint?`

- [ ] **Step 1: Write failing serialization and discrimination tests**

```kotlin
@Test
fun v2RoundTripsAndV1StillDecodes() {
    val v2 = PortraitFingerprint.fromArgb144(edgeFixture())
    assertEquals(2, v2.version)
    assertEquals(v2, PortraitFingerprint.decode(v2.encode()))
    assertNotNull(PortraitFingerprint.decode("0000000000000001:1,1,1,1,1,1,1,1,1,1,1,1"))
}

@Test
fun edgeEvidenceSeparatesEqualColorLayouts() {
    val vertical = PortraitFingerprint.fromArgb144(verticalEdgeFixture())
    val horizontal = PortraitFingerprint.fromArgb144(horizontalEdgeFixture())
    assertTrue(vertical.distance(horizontal) > 0.20)
}
```

- [ ] **Step 2: Run the focused test and observe failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PortraitFingerprintV2Test" --no-daemon
```

Expected: compilation failure because `fromArgb144` and `version` do not exist.

- [ ] **Step 3: Implement the V2 format**

Use a 12x12 normalized sample and encode:

```text
v2:<average-hash-hex>:<gradient-hash-hex>:<32 edge bins>:<12 color bins>
```

Distance weights:

```kotlin
hashDistance * 0.35 +
gradientDistance * 0.30 +
edgeHistogramDistance * 0.20 +
colorDistance * 0.15
```

When either operand is V1, preserve the existing V1 distance calculation so
persisted user templates continue to work.

- [ ] **Step 4: Run V2 and existing confidence tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PortraitFingerprintV2Test" --tests "*RecognitionConfidenceTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/PortraitFingerprint.kt app/src/test/java/com/example/honorofkingsassistant/PortraitFingerprintV2Test.kt
git commit -m "feat(vision): add edge-aware portrait fingerprints"
```

### Task 2: Calibrate JKM-LX3 portrait interiors

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/JkmLx3SelectionProfile.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt`
- Test: `app/src/test/java/com/example/honorofkingsassistant/JkmLx3SelectionProfileTest.kt`

**Interfaces:**
- Produces: `JkmLx3SelectionProfile.slotBounds(physicalLeft: Boolean): List<NormalizedRect>`
- Produces: `JkmLx3SelectionProfile.portraitInteriors(physicalLeft: Boolean): List<NormalizedRect>`

- [ ] **Step 1: Write failing geometry tests**

```kotlin
@Test
fun profileScalesIdenticallyAcrossNativeCaptureAndVideo() {
    val region = JkmLx3SelectionProfile.portraitInteriors(true).first()
    val native = region.toPixelRect(2340, 1080)
    val capture = region.toPixelRect(1170, 540)
    val fixture = region.toPixelRect(848, 392)
    assertEquals(native.left / 2, capture.left, 1)
    assertEquals(region.left, fixture.left / 848.0, 0.002)
}

@Test
fun portraitInteriorExcludesTheFrameAndLockMarker() {
    val slot = JkmLx3SelectionProfile.slotBounds(true).first()
    val portrait = JkmLx3SelectionProfile.portraitInteriors(true).first()
    assertTrue(portrait.left > slot.left)
    assertTrue(portrait.right < slot.right)
    assertTrue(portrait.bottom < slot.bottom)
}
```

- [ ] **Step 2: Verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*JkmLx3SelectionProfileTest" --no-daemon
```

Expected: compilation failure because the profile is absent.

- [ ] **Step 3: Implement normalized native geometry**

Use the existing slot centers, represent them relative to `2340x1080`, and
define an inner portrait inset that excludes the visible frame and lower lock
diamond. Switch `HeroPortraitMatcher.fingerprints()` to the interior regions
and normalize crops to 12x12.

- [ ] **Step 4: Run geometry and video calibration tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*JkmLx3SelectionProfileTest" --tests "*RankedVideoCalibrationTest" --tests "*RankedFixtureRasterTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/JkmLx3SelectionProfile.kt app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt app/src/test/java/com/example/honorofkingsassistant/JkmLx3SelectionProfileTest.kt
git commit -m "fix(vision): isolate JKM draft portrait interiors"
```

### Task 3: Generate transformed V2 seed templates

**Files:**
- Create: `tools/generate_draft_portrait_seed.py`
- Modify: `app/src/main/assets/draft_portrait_seed_v1.json`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/PortraitTemplateStore.kt`
- Test: `app/src/test/java/com/example/honorofkingsassistant/PortraitTemplateSeedCatalogTest.kt`

**Interfaces:**
- Consumes: local source icons under `tmp/fandom_icons` only during generation.
- Produces: JSON schema `2`, with V2 fingerprints only; no raster image enters the APK.

- [ ] **Step 1: Extend the seed test to require variants**

```kotlin
@Test
fun productionSeedUsesV2VariantsWithoutEmbeddedImages() {
    val root = JSONObject(File("src/main/assets/draft_portrait_seed_v1.json").readText())
    assertEquals(2, root.getInt("schema_version"))
    val angela = root.getJSONObject("templates").getJSONArray("angela")
    assertTrue(angela.length() >= 5)
    repeat(angela.length()) { assertTrue(angela.getString(it).startsWith("v2:")) }
}
```

- [ ] **Step 2: Verify the test fails against schema 1**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*PortraitTemplateSeedCatalogTest" --no-daemon
```

Expected: FAIL because the asset contains V1 fingerprints and one template per hero.

- [ ] **Step 3: Implement a deterministic generator**

For each verified source icon, generate the nominal center crop and controlled
crop/brightness/contrast variants described in the design. Store unique V2
fingerprints. Write metadata containing generator version, source URL, and the
five identities still missing a verified source icon.

- [ ] **Step 4: Regenerate and verify**

Run:

```powershell
python tools/generate_draft_portrait_seed.py --source tmp/fandom_icons --output app/src/main/assets/draft_portrait_seed_v1.json
.\gradlew.bat testDebugUnitTest --tests "*PortraitTemplateSeedCatalogTest" --no-daemon
```

Expected: generator reports 111 heroes, no raster files are added under
`app/src/main`, and tests pass.

- [ ] **Step 5: Commit**

```powershell
git add tools/generate_draft_portrait_seed.py app/src/main/assets/draft_portrait_seed_v1.json app/src/main/kotlin/com/example/honorofkingsassistant/PortraitTemplateStore.kt app/src/test/java/com/example/honorofkingsassistant/PortraitTemplateSeedCatalogTest.kt
git commit -m "feat(vision): add transformed draft portrait seeds"
```

### Task 4: Publish ranked candidates and bounded slot consensus

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/SlotRecognitionModels.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/SlotRecognitionTracker.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/DraftModels.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt`
- Test: `app/src/test/java/com/example/honorofkingsassistant/SlotRecognitionTrackerTest.kt`

**Interfaces:**
- Produces: `HeroPortraitMatcher.rankSlots(...): List<SlotMatchEvidence>`
- Produces: `SlotRecognitionTracker.observe(board, evidence, manual): List<SlotRecognitionState>`
- Produces: `DraftVisionResult.slotRecognition: List<SlotRecognitionState>`

- [ ] **Step 1: Write tracker tests for all states**

Cover:

```text
empty -> WAITING
occupied first sample -> SCANNING
three coherent samples -> DETECTED
close top two -> UNCERTAIN with candidates
stable no-match -> NOT_DETECTED
manual assignment -> MANUAL
slot empty again -> clears history
```

- [ ] **Step 2: Run the focused test and verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SlotRecognitionTrackerTest" --no-daemon
```

Expected: compilation failure because slot recognition models are absent.

- [ ] **Step 3: Implement candidate ranking**

Return up to three candidates per occupied slot before final selection. Keep
absolute distance and best/second margin. Do not convert an uncertain result
to `HeroObservation`.

- [ ] **Step 4: Implement the 3-of-5 tracker**

Use a fixed-size deque per `ManualTeamSlot`. Require three coherent accepted
matches, clear on `EMPTY`, and project uncertain/no-match states after three
eligible observations.

- [ ] **Step 5: Integrate into `DraftVisionEngine`**

Populate `DraftVisionResult.slotRecognition` for ranked draft frames and
preserve the existing `observations` list for confirmed identities only.

- [ ] **Step 6: Run tracker, confidence and routing tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*SlotRecognitionTrackerTest" --tests "*RecognitionConfidenceTest" --tests "*RankedProductionRoutingTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/SlotRecognitionModels.kt app/src/main/kotlin/com/example/honorofkingsassistant/SlotRecognitionTracker.kt app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt app/src/main/kotlin/com/example/honorofkingsassistant/DraftModels.kt app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt app/src/test/java/com/example/honorofkingsassistant/SlotRecognitionTrackerTest.kt
git commit -m "feat(vision): track recognition state per draft slot"
```

### Task 5: Project per-slot state into the assistant session

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/QuickCorrectionPolicy.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt`
- Test: `app/src/test/java/com/example/honorofkingsassistant/QuickCorrectionPolicyTest.kt`

**Interfaces:**
- Produces: `AssistantUiState.slotRecognition`
- Produces: `AssistantUiState.quickCorrection`
- Produces: `QuickCorrectionPolicy.project(states, manual, explicitScan): QuickCorrectionState`

- [ ] **Step 1: Write failing projection tests**

Assert that waiting slots do not become pending, unresolved occupied slots are
ordered ally-first then enemy, manual assignments disappear from pending, and
an explicit scan requests the compact panel while continuous Auto does not.

- [ ] **Step 2: Verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*QuickCorrectionPolicyTest" --no-daemon
```

Expected: compilation failure because policy/state types are absent.

- [ ] **Step 3: Implement the pure policy**

`QuickCorrectionState` contains:

```kotlin
val occupiedCount: Int
val resolvedCount: Int
val pending: List<SlotCorrectionPrompt>
val shouldOpen: Boolean
```

Candidate prompts preserve up to three names and never include a hero already
manually fixed in another slot.

- [ ] **Step 4: Integrate into capture publication**

Persist the last per-slot recognition list, update it with manual assignments,
and set `shouldOpen` only after `ACTION_FORCE_SCAN` completes.

- [ ] **Step 5: Run policy and service-routing tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*QuickCorrectionPolicyTest" --tests "*RankedProductionRoutingTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/QuickCorrectionPolicy.kt app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt app/src/test/java/com/example/honorofkingsassistant/QuickCorrectionPolicyTest.kt
git commit -m "feat(session): expose contextual slot correction state"
```

### Task 6: Build the one-action overlay flow

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Create: `app/src/test/java/com/example/honorofkingsassistant/OverlayQuickCorrectionContractTest.kt`

**Interfaces:**
- Consumes: `AssistantUiState.quickCorrection`
- Produces: one-tap candidate assignment and next-pending navigation.

- [ ] **Step 1: Write the failing overlay contract test**

The source contract must verify:

```text
quick correction block is rendered before advanced controls
WAITING slots are absent
candidate buttons assign directly
Buscar otro opens the existing searchable picker
48dp minimum touch height
Auto cannot invoke showQuickCorrection automatically
explicit scan can invoke it
```

- [ ] **Step 2: Run the contract test and observe failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*OverlayQuickCorrectionContractTest" --no-daemon
```

Expected: FAIL because quick correction UI is absent.

- [ ] **Step 3: Simplify the main panel**

Move the primary scan action and result summary to the top. Add an
`Opciones avanzadas` toggle containing the current input/match/stage/slot/pick
controls. Keep verification/loading/scoreboard actions available in the
context where they apply.

- [ ] **Step 4: Implement correction cards**

Render one card per pending slot with textual status, candidate shortcuts and
`Buscar otro`/`Elegir héroe`. A candidate assignment advances to the next
pending slot. Resolving the last pending slot returns to the summary.

- [ ] **Step 5: Make touch targets safe**

Change button construction to enforce at least `48dp` height and keep the
panel at 72% of available height. Preserve header-only drag.

- [ ] **Step 6: Run overlay and policy tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*OverlayQuickCorrectionContractTest" --tests "*ManualTeamEditorPolicyTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt app/src/main/res/values/strings.xml app/src/test/java/com/example/honorofkingsassistant/OverlayQuickCorrectionContractTest.kt
git commit -m "feat(overlay): add contextual quick correction"
```

### Task 7: Add reproducible video regression validation

**Files:**
- Create: `tools/validate_ranked_portrait_video.py`
- Create: `tools/tests/test_ranked_portrait_video.py`
- Modify: `docs/qa/v5-5-verification-report.md`

**Interfaces:**
- Consumes: user-provided video path supplied as a command argument.
- Produces: JSON report with per-slot accepted/rejected candidates; does not copy the video.

- [ ] **Step 1: Write failing validator tests**

Use synthetic frames to verify native-to-capture scaling, border exclusion,
ambiguous candidate preservation and multi-frame consensus.

- [ ] **Step 2: Run and observe failure**

Run:

```powershell
python -m unittest tools.tests.test_ranked_portrait_video -v
```

Expected: import failure because validator is absent.

- [ ] **Step 3: Implement deterministic validation**

Read only requested timestamps, apply the same V2 signature and slot geometry,
and emit a compact JSON report. Exit nonzero for forced ambiguous matches,
geometry overflow or recognition of empty slots.

- [ ] **Step 4: Run against tests and the supplied recording**

Run:

```powershell
python -m unittest tools.tests.test_ranked_portrait_video -v
python tools/validate_ranked_portrait_video.py --video "C:\Users\Windows 11 Pro\Downloads\SELECCION HEROES COMPETITIVA.mp4" --seed app/src/main/assets/draft_portrait_seed_v1.json
```

Expected: synthetic tests pass; clear fixture identities remain accepted,
ambiguous slots remain pending or become stable only through multi-frame
evidence.

- [ ] **Step 5: Commit**

```powershell
git add tools/validate_ranked_portrait_video.py tools/tests/test_ranked_portrait_video.py docs/qa/v5-5-verification-report.md
git commit -m "test(vision): add ranked video regression harness"
```

### Task 8: Full Android and release-candidate verification

**Files:**
- Modify: `docs/qa/GUIA_USO_1_0_PERSONAL_JKM_LX3.md`
- Modify: `docs/qa/v5-5-emulator-checklist.md`
- Modify: `app/build.gradle`

**Interfaces:**
- Produces: one incremented RC APK only after all gates pass.

- [ ] **Step 1: Update user guidance**

Document the single normal flow:

```text
Selección -> Escanear selección -> revisar pendientes -> elegir -> listo
```

Describe loading and scoreboard actions separately.

- [ ] **Step 2: Run the full local gate**

Run:

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --no-daemon
python -m unittest tools.tests.test_ranked_portrait_video -v
```

Expected: all tests, lint and build pass.

- [ ] **Step 3: Verify APK identity and offline permissions**

Use the newest installed Android build tools `aapt.exe dump badging`. Confirm
the version increment and absence of `INTERNET` and `ACCESS_NETWORK_STATE`.

- [ ] **Step 4: Run emulator/ADB QA**

Use an Android 9 AVD when available. Verify install/launch, overlay permission
routing, touch targets, panel scroll, quick-correction rendering and logcat
crash absence. If no compatible AVD exists, record that physical overlay QA
remains the release gate instead of claiming it passed.

- [ ] **Step 5: Audit existing flows**

Review classified/normal selection, loading confirmation, in-game scoreboard,
manual precedence, item advice, overlay restoration, pause and process
recreation. Record any remaining blocker in the verification report.

- [ ] **Step 6: Increment candidate version and rebuild once**

Increment `versionCode` by one and set a single new
`1.0.0-personal-jkm-lx3-rcN` version. Re-run the complete gate after this edit.

- [ ] **Step 7: Commit**

```powershell
git add app/build.gradle docs/qa/GUIA_USO_1_0_PERSONAL_JKM_LX3.md docs/qa/v5-5-emulator-checklist.md
git commit -m "release: prepare JKM recognition V2 candidate"
```

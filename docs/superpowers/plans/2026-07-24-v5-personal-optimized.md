# HoK Draft Assistant V5 Huawei JKM-LX3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a stable, local-only V5 personal APK optimized for the user's Huawei JKM-LX3 con Android 9 / EMUI 9.1 with bounded capture cost, stricter recognition, diagnostics, and API 36 compatibility.

**Architecture:** Keep the existing service/overlay architecture, introduce pure geometry and diagnostics units, and make recognition stricter rather than replacing the working pipeline. Android-specific code consumes pure components so most behavior is unit-testable without a device.

**Tech Stack:** Kotlin, Android Views, MediaProjection, ImageReader, ML Kit Text Recognition, Gradle/AGP, JUnit 4, Python contract tests.

## Global Constraints

- No `android.permission.INTERNET`.
- Manual stage selection remains authoritative.
- Personal calibration supports 1170x540 and 848x392 landscape captures.
- `compileSdk 36`, `targetSdk 36`, `minSdk 23`.
- Capture surface maximum: 640,000 pixels and long edge 1170.
- OCR bitmap maximum long edge: 1170.
- Identity confirmation: 3 hits in 5 frames with minimum confidence 0.55.

---

### Task 1: Modernize build and CI

**Files:**
- Modify: `build.gradle`
- Modify: `app/build.gradle`
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `.github/workflows/android-ci.yml`
- Test: `tools/test_v5_build_contract.py`

**Interfaces:** Produces API 36 build configuration consumed by all later tasks.

- [ ] Write a failing contract asserting exact versions and no INTERNET permission.
- [ ] Run it and confirm failure on the V4 configuration.
- [ ] Update AGP, Gradle, SDK levels, V5 version metadata, and CI SDK packages.
- [ ] Run the contract and all existing validators.
- [ ] Commit `build: modernize V5 personal toolchain`.

### Task 2: Add bounded capture geometry

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/CaptureGeometry.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/CaptureGeometryTest.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt`

**Interfaces:**
- Produces: `CaptureGeometry.fit(sourceWidth: Int, sourceHeight: Int, maxLongEdge: Int, maxPixels: Int): CaptureSize`.
- Consumed by: `ScreenCaptureService` when creating/resizing ImageReader and VirtualDisplay.

- [ ] Write pure unit tests for 1170x540, 848x392, 2400x1080, and invalid dimensions.
- [ ] Run pure Kotlin test and confirm failure before implementation.
- [ ] Implement aspect-ratio-preserving bounded geometry.
- [ ] Integrate deterministic surface recreation and Android 14 resize callback.
- [ ] Run contracts and pure tests.
- [ ] Commit `feat: add bounded capture geometry`.

### Task 3: Harden vision and add diagnostics

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/VisionDiagnostics.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/OcrBitmapPreprocessor.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/VisionDiagnosticsTest.kt`
- Modify: `DraftVisionEngine.kt`
- Modify: `DraftModels.kt`
- Modify: `AssistantSessionBus.kt`

**Interfaces:**
- Produces: immutable `VisionDiagnostics` and bounded OCR bitmap preparation.
- Consumed by: `ScreenCaptureService` and overlay rendering.

- [ ] Write failing tests for rolling latency and dropped-frame accounting.
- [ ] Implement diagnostics without Android dependencies.
- [ ] Add OCR downscale with ownership/recycling contract.
- [ ] Publish diagnostics in every vision result.
- [ ] Run unit/static tests.
- [ ] Commit `feat: add vision diagnostics and bounded OCR input`.

### Task 4: Reject ambiguous portraits and strengthen temporal identity

**Files:**
- Modify: `HeroPortraitMatcher.kt`
- Modify: `TemporalDraftTracker.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/RecognitionConfidenceTest.kt`
- Create: `tools/test_v5_recognition_contract.py`

**Interfaces:** Existing public APIs remain source-compatible; defaults become personal precision settings.

- [ ] Write failing tests for ambiguous portrait rejection and low-confidence observation rejection.
- [ ] Implement best-vs-second-best distance margin and confidence floor.
- [ ] Set service tracker to 3 hits / 5 frames.
- [ ] Run tests and validators.
- [ ] Commit `feat: harden personal recognition confidence`.

### Task 5: Surface concise diagnostics and finalize delivery

**Files:**
- Modify: `OverlayService.kt`
- Modify: `strings.xml`
- Modify: `README.md`
- Modify: `tools/validate_v4.py`
- Create: `tools/validate_v5.py`
- Modify: `.github/workflows/android-ci.yml`
- Create: `PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.ps1`
- Create: `PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.bat`

**Interfaces:** Produces a single user-run installer/push script and CI artifact `HoK-Draft-Assistant-V5-Huawei-JKM-LX3`.

- [ ] Add a compact diagnostics line visible only while DRAFT is active.
- [ ] Move ML Kit callbacks to the vision HandlerThread and keep OverlayService non-foreground/non-sticky for API 36.
- [ ] Add V5 validators to CI and artifact verification.
- [ ] Add one-shot Windows script with preflight, parser-safe encoding, commit, push, run watch, and artifact download.
- [ ] Run all static and pure Kotlin tests.
- [ ] Create delivery ZIP and SHA-256.
- [ ] Commit `release: prepare V5 personal optimized candidate`.

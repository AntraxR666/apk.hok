# HoK Draft Assistant V4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline Android draft assistant that uses authorized screen capture and local OCR to detect hero names, track the draft, recommend top picks, and display strategy in a floating overlay.

**Architecture:** Keep recognition, state tracking, recommendation, and rendering in independent components. Android-specific capture and ML Kit adapters feed pure Kotlin domain logic, enabling deterministic unit testing.

**Tech Stack:** Kotlin, Android SDK 33, MediaProjection, ImageReader, ML Kit Text Recognition 16.0.1, AndroidX, JUnit 4.

## Global Constraints
- No INTERNET permission or remote service.
- No root, accessibility automation, memory reading, network interception, automated taps, or detection-evasion behavior.
- Screens are processed only in memory and never persisted.
- Manual selection is fallback only.
- Every successful delivery requires unit tests and an Android debug build.

---

### Task 1: Pure draft domain
**Files:** Create `DraftModels.kt`, `HeroNameMatcher.kt`, `TemporalDraftTracker.kt`, `DraftRecommendationEngine.kt`, `StrategyEngine.kt`; create matching unit tests.
- [ ] Write failing tests for normalization, fuzzy aliases, temporal confirmation, side assignment, multi-enemy recommendations, role filtering, and strategy output.
- [ ] Run tests and confirm failures are caused by missing classes.
- [ ] Implement the smallest pure Kotlin domain needed to pass.
- [ ] Run all unit tests.

### Task 2: Local OCR adapter
**Files:** Modify `app/build.gradle`; create `DraftVisionEngine.kt`, `DraftLayoutClassifier.kt`; add contract tests.
- [ ] Add bundled ML Kit Latin OCR dependency.
- [ ] Convert OCR blocks into positioned hero observations.
- [ ] Throttle concurrent processing and always close frames.
- [ ] Verify source contracts and compilation.

### Task 3: MediaProjection foreground capture
**Files:** Create `ScreenCaptureService.kt`, `AssistantSessionBus.kt`; modify manifest.
- [ ] Add media-projection foreground-service declaration and permissions.
- [ ] Create virtual display and ImageReader after foreground start.
- [ ] Process sampled frames and publish draft snapshots.
- [ ] Stop and release every projection/display/image resource deterministically.

### Task 4: Restored real-time overlay
**Files:** Rewrite `OverlayService.kt`; create overlay layout; update strings.
- [ ] Start a movable collapsed bubble without requiring a preselected hero.
- [ ] Expand to show detected teams, top three picks, plan, side-swap, rescan, and stop controls.
- [ ] Subscribe to local session updates.
- [ ] Preserve manual fallback from the overlay.

### Task 5: Main session flow
**Files:** Rewrite `MainActivity.kt` and `activity_main.xml`.
- [ ] Request notification, overlay, and capture consent in correct order.
- [ ] Start both foreground services with capture result data.
- [ ] Display clear active/inactive state and stop action.
- [ ] Retain manual catalog browser as a secondary section.

### Task 6: Data provenance and meta snapshot
**Files:** Extend catalog models/JSON; add `meta_snapshot.json` and validation.
- [ ] Add source type, patch label, snapshot date, confidence, aliases, and optional meta score.
- [ ] Reject missing hero references and duplicate aliases.
- [ ] Ensure unverified data is labeled rather than presented as official.

### Task 7: Verification and delivery
**Files:** Update README, validation tools, delivery scripts.
- [ ] Run pure Kotlin/unit tests.
- [ ] Run `assembleDebug`.
- [ ] Inspect APK for catalog, ML Kit classes, manifest service types, and no INTERNET permission.
- [ ] Package project, APK, build log, and verification report.

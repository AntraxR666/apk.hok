# V4 Manual Stage Control Implementation Plan

**Goal:** Add explicit Paused, Draft and In-game modes without removing automatic visual assistance inside the selected stage.

**Architecture:** A pure stage policy defines processing behavior. ScreenCaptureService owns stage state and projection surface attachment. OverlayService and MainActivity send explicit stage actions and render suggestions without automatic transitions.

**Tech Stack:** Kotlin, Android foreground services, MediaProjection, WindowManager overlay, ML Kit OCR, JUnit.

## Tasks

- [x] Add pure `AssistantStage` and `AssistantStagePolicy` with failing-then-passing smoke test.
- [x] Persist and publish selected/suggested stage.
- [x] Gate frame processing and detach the VirtualDisplay surface outside Draft.
- [x] Add overlay stage controls and confirmable transition suggestions.
- [x] Add MainActivity stage controls.
- [x] Add JUnit and static contract coverage.
- [x] Update installer, version metadata and documentation.
- [x] Run static, domain, XML, package and parser verification.
- [ ] Run Android Gradle build and device test on the user's Windows/Android environment.

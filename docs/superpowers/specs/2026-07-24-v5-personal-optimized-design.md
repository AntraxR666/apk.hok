# HoK Draft Assistant V5 Huawei JKM-LX3 — Design

## Objective

Deliver a personal build optimized for the user's Huawei JKM-LX3 running Android 9 / EMUI 9.1, while preserving the local-only architecture and preparing clean boundaries for a later universal/commercial edition.

## Non-negotiable behavior

- No `INTERNET` permission and no remote services.
- Manual stage remains authoritative: `PAUSED -> DRAFT -> IN_GAME`; automatic detection only suggests.
- The overlay remains available over Honor of Kings and preserves manual slot/pick correction.
- The final draft composition remains available during the match.
- Recognition never promotes a weak or ambiguous portrait match to a confirmed hero.
- Existing catalog: 116 heroes and 348 counter relations.

## Personal device profile

- Primary device: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM.
- Primary landscape calibration: 1170x540.
- Secondary recorded calibration: 848x392.
- Capture work surface is capped at 640,000 pixels and 1600 px on the long edge.
- OCR work bitmap is capped at 1170 px on the long edge; visual slot analysis keeps the capture bitmap.

## Architecture

### Capture

`ScreenCaptureService` is the only foreground service and owns the MediaProjection session. A pure `CaptureGeometry` component computes bounded dimensions. resize callbacks when available recreate the ImageReader and resize the VirtualDisplay without reusing the projection token. The overlay remains a normal same-process service and is non-sticky, preventing a second foreground-service type and stale restart. Resources are released deterministically.

### Vision

`DraftVisionEngine` separates visual analysis from OCR preprocessing. It drops new frames while busy, downsizes the OCR bitmap, records processing diagnostics, and always recycles temporary bitmaps. ML Kit success/failure/completion callbacks execute through the vision `HandlerThread` executor rather than the main thread. Portrait matching rejects ambiguous best/second-best results.

### Temporal confidence

Hero identities require 3 consistent hits inside a 5-frame window and a minimum observation confidence. Board state keeps its independent 3-frame stabilizer. Manual corrections remain confidence 1.0.

### Diagnostics

A lightweight `VisionDiagnostics` snapshot reports capture size, processed frames, dropped frames, last latency, and rolling average latency. The overlay displays one concise diagnostics line in DRAFT mode.

### Build and compatibility

- `compileSdk 36`, `targetSdk 36`, `minSdk 23`.
- AGP 8.10.1, Gradle 8.11.1, JDK 17.
- Kotlin plugin stays on the conservative 1.9.x line to avoid AGP 9 built-in-Kotlin migration risk.
- CI installs API 36 and Build Tools 35.0.0, then runs validators, unit tests, lint, and APK assembly.

## Acceptance gates

1. Static validators all pass.
2. Pure Kotlin regression tests pass.
3. GitHub CI completes `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
4. APK contains manifest, classes, counter catalog, and V5 version metadata.
5. Real-device gate: 30-minute session without crash/ANR, no stale overlay after stop, one foreground notification only, and manual controls remain responsive.
6. Recognition gate on supplied frames: no false confirmed player slot or lock state, and known late-draft fixtures remain stable.

## Deferred universal/commercial work

The commercial phase adds multi-device calibration packs, localization, release signing, Play distribution compliance, benchmark module, broader device matrix, and data-update tooling. It starts only after the personal build passes the real-device gate.

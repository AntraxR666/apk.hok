# HoK Draft Assistant V4 Design

## Goal
Restore the always-available overlay and add an on-device, user-authorized screen-analysis session that detects visible hero names during draft, tracks allied and enemy picks, recommends the best available picks, and presents a compact strategy without requiring the user to leave the game.

## Product behavior
- The main screen has one primary action: start the draft assistant.
- The app requests overlay permission, notification permission when required, and Android screen-capture consent.
- A foreground capture service processes screen frames locally.
- A movable overlay shows capture status, detected allied/enemy heroes, top three recommendations, confidence, and a short plan.
- Manual hero editing remains available from the overlay as a fallback, never as a prerequisite.
- The app does not use root, accessibility automation, memory reading, network interception, automatic taps, or concealment/evasion mechanisms.

## Recognition architecture
- `MediaProjection` captures a user-authorized screen session.
- `ImageReader` produces frames at a throttled cadence.
- ML Kit bundled Latin OCR reads visible text fully on-device.
- `HeroNameMatcher` normalizes OCR text and fuzzy-matches it against official/global hero names and aliases.
- `DraftLayoutClassifier` maps detections near the left and right screen edges to teams; the user can swap sides from the overlay.
- `TemporalDraftTracker` requires repeated observations before confirming a hero, reducing animation and OCR false positives.
- Low-confidence or ambiguous results are presented for manual correction.

## Recommendation architecture
- `DraftRecommendationEngine` aggregates counter coverage against all confirmed enemies.
- It filters heroes already picked or banned and supports an optional requested role.
- It scores candidate coverage, role need, ally role diversity, and current meta strength when verified meta data exists.
- Every recommendation includes explicit evidence and a confidence score. Missing evidence is shown as unknown rather than invented.
- `StrategyEngine` builds a short plan from documented counter reasons and composition-level role signals.

## Data integrity
- Existing 116-hero data remains available but is tagged with provenance metadata.
- Names and roles are reconciled against current global-server references.
- Counter and meta claims include source type, snapshot date, patch label, and confidence.
- Data updates are local assets and do not require a server.

## Security and privacy
- No `INTERNET` permission.
- Frames are processed in memory and immediately closed.
- No screenshots are saved.
- Services and receivers are non-exported.
- Foreground notification clearly indicates an active capture session.
- Release builds enable R8 and resource shrinking; no anti-detection or anti-analysis behavior is added.

## Verification
- Pure Kotlin tests cover matching, temporal tracking, team-side mapping, recommendation scoring, and strategy generation.
- Android build must pass `testDebugUnitTest` and `assembleDebug`.
- Static contract tests verify manifest service types, no internet permission, bundled OCR dependency, and required overlay controls.

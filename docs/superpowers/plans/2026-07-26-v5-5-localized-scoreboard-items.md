# HoK Draft Assistant 1.0 Personal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the first formal Huawei JKM-LX3-optimized 1.0 personal release that recognizes verified Spanish hero titles, distinguishes ranked and normal selection, offers a usable translucent overlay, recovers both teams and current items from an explicit scoreboard scan, and recommends adaptive purchases fully offline.

**Architecture:** Keep capture and ML Kit OCR in `ScreenCaptureService`, but move identity, match-mode, scoreboard, reconciliation, and item scoring into pure Kotlin components with deterministic tests. The overlay only publishes user intent and renders immutable `AssistantUiState`; analyzers consume normalized geometry so the same logic works at `2340 × 1080` and the reduced `1170 × 540` capture size.

**Tech Stack:** Kotlin/JVM, Android Views, ML Kit Text Recognition 16.0.1, Gson, JUnit 4, Python contract validators, Gradle 8.11.1, AGP 8.10.1, JDK 17, Android SDK 36, adb-driven emulator QA.

## Global Constraints

- Preserve `minSdk 23`, `compileSdk 36`, `targetSdk 36`, JDK 17, and Huawei JKM-LX3 / Android 9 / EMUI 9.1 compatibility.
- Keep the APK offline: no `INTERNET` or `ACCESS_NETWORK_STATE` permission in source or merged APK.
- Preserve the existing 116 hero IDs and 348 counter relations; aliases must resolve to an existing hero ID rather than create duplicate heroes.
- Use `0.55f` alpha for the collapsed bubble, `0.82f` for the expanded panel, and at most `72%` of available screen height.
- Dragging is allowed only from the header handle; scrolling and inner controls must never drag the window.
- Normal selection never invents enemy picks; enemies become available only from loading evidence or an explicit scoreboard scan.
- Scoreboard scanning is user-triggered through `Verificar equipos e ítems`; automatic detection may only show a suggestion.
- A manual correction has confidence `1.0` and cannot be overwritten automatically.
- Item recommendations must cite deterministic local evidence and remain useful when no enemy data is available.
- Every production behavior starts with a failing unit or contract test.

---

### Task 1: Versioned Hero Identity and Verified Spanish Aliases

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalog.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalogJson.kt`
- Modify: `app/src/main/assets/hok_counters.json`
- Create: `app/src/test/java/com/example/honorofkingsassistant/HeroIdentityTest.kt`
- Create: `tools/test_spanish_alias_catalog.py`

**Interfaces:**
- Consumes: existing `Hero.id`, `Hero.name`, and `Hero.aliases`.
- Produces: `HeroIdentityAliases(displayTitles, historicalNames, localizedAliases)`, `Hero.allRecognitionAliases()`, and exact alias lookup through `CounterCatalog.findHero(query)`.

- [ ] **Step 1: Write the failing identity tests**

```kotlin
@Test
fun spanishDisplayTitlesResolveToCanonicalHero() {
    val catalog = CounterCatalogJson.load(resourceText("hok_counters.json"))
    assertEquals("Angela", catalog.findHero("La Maga de Fuego")?.name)
    assertEquals("Bai Qi", catalog.findHero("El Arma Suprema")?.name)
    assertEquals("Dr. Bian", catalog.findHero("El Boticario")?.name)
    assertEquals("Shouyue", catalog.findHero("El Francotirador")?.name)
}

@Test
fun aliasesDoNotCreateDuplicateHeroes() {
    val catalog = CounterCatalogJson.load(resourceText("hok_counters.json"))
    assertEquals(116, catalog.heroes.size)
    assertSame(catalog.findHero("Angela"), catalog.findHero("La Maga de Fuego"))
}
```

- [ ] **Step 2: Run the focused test and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*HeroIdentityTest" --no-daemon`

Expected: FAIL because `La Maga de Fuego` and the other localized titles are not in the catalog.

- [ ] **Step 3: Add structured alias fields and compatibility flattening**

```kotlin
data class HeroIdentityAliases(
    val displayTitles: List<String> = emptyList(),
    val historicalNames: List<String> = emptyList(),
    val localizedAliases: Map<String, List<String>> = emptyMap()
) {
    fun flattened(): List<String> =
        (displayTitles + historicalNames + localizedAliases.values.flatten())
            .filter(String::isNotBlank)
            .distinct()
}

fun Hero.allRecognitionAliases(): List<String> =
    (aliases + identityAliases.flattened()).distinct()
```

Update candidate construction and `CounterCatalog` alias indexing to call `allRecognitionAliases()`.

- [ ] **Step 4: Add the twelve screenshot-verified mappings**

Add Spanish `display_titles` without changing hero IDs:

```text
Angela -> La Maga de Fuego
Bai Qi -> El Arma Suprema
Flowborn (Tank) -> Puño de la Paz
Flowborn (Mage) -> Corazón Arcano
Shouyue -> El Francotirador
Xuance -> La Hoz Justiciera
Augran -> El Sumo Sacerdote
Dr. Bian -> El Boticario
Mai Shiranui -> La Ninja de Fuego
Cai Yan -> La Alegre Canción
Fatih -> El Conquistador
Chano -> El Último Lobo
```

- [ ] **Step 5: Add the catalog contract**

`tools/test_spanish_alias_catalog.py` must parse the JSON, assert all twelve pairs, ensure normalized aliases are globally unique, and assert exactly 116 hero records.

- [ ] **Step 6: Run tests and validators**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*HeroIdentityTest" --no-daemon
python tools/test_spanish_alias_catalog.py
python tools/validate_v5.py
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalog.kt app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalogJson.kt app/src/main/assets/hok_counters.json app/src/test/java/com/example/honorofkingsassistant/HeroIdentityTest.kt tools/test_spanish_alias_catalog.py
git commit -m "feat(identity): recognize verified Spanish hero titles"
```

### Task 2: Exact-First OCR Matching and Unknown-Title Diagnostics

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/HeroNameMatcher.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/UnknownHeroTextStore.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/HeroNameMatcherTest.kt`

**Interfaces:**
- Consumes: `Hero.allRecognitionAliases()` from Task 1.
- Produces: `HeroNameMatcher.match(rawText): HeroNameMatch?`, `MatchKind.EXACT_ALIAS | EXACT_CANONICAL | FUZZY`, and `UnknownHeroTextStore.record(text, region, evidenceSource)`.

- [ ] **Step 1: Write failing matching tests**

```kotlin
@Test
fun exactLocalizedAliasWinsBeforeFuzzyCanonicalName() {
    val match = matcher.match("La Maga de Fuego")
    assertEquals("Angela", match?.hero?.name)
    assertEquals(MatchKind.EXACT_ALIAS, match?.kind)
    assertEquals(1.0, match?.score, 0.0)
}

@Test
fun accentsPunctuationAndWhitespaceAreNormalized() {
    assertEquals("Flowborn (Tank)", matcher.match("  PUÑO  DE LA PAZ ")?.hero?.name)
    assertEquals("Dr. Bian", matcher.match("El Boticario")?.hero?.name)
}

@Test
fun genericWordDoesNotResolveAngela() {
    assertNull(matcher.match("Maga"))
}
```

- [ ] **Step 2: Run tests and confirm the missing API**

Run: `.\gradlew.bat testDebugUnitTest --tests "*HeroNameMatcherTest" --no-daemon`

Expected: FAIL because `match` and `MatchKind` do not exist.

- [ ] **Step 3: Implement exact-first matching**

```kotlin
enum class MatchKind { EXACT_CANONICAL, EXACT_ALIAS, FUZZY }

data class HeroNameMatch(
    val hero: Hero,
    val score: Double,
    val matchedText: String,
    val kind: MatchKind
)
```

Build separate normalized exact maps. Only run fuzzy matching after both exact maps miss, require `minimumScore`, and reject single-token fragments shorter than five characters.

- [ ] **Step 4: Record unknown OCR strings without fabricating confidence**

ML Kit Latin `Text.Line` does not expose OCR confidence in this dependency.
Store only unresolved text with length `4..48` whose bounding box falls inside a
known hero-name/title region. Persist `evidenceSource = "mlkit_line_in_hero_roi"`
instead of inventing a numeric OCR score. Cap the app-private JSON log at 200
unique normalized entries.

- [ ] **Step 5: Run focused and regression tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*HeroNameMatcherTest" --no-daemon
.\gradlew.bat testDebugUnitTest --tests "*RecognitionConfidenceTest" --no-daemon
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/HeroNameMatcher.kt app/src/main/kotlin/com/example/honorofkingsassistant/UnknownHeroTextStore.kt app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt app/src/test/java/com/example/honorofkingsassistant/HeroNameMatcherTest.kt
git commit -m "feat(ocr): prefer exact aliases and log unknown titles"
```

### Task 3: Ranked/Normal Match Mode and Calibrated Normal Geometry

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/MatchMode.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/NormalSelectionGeometry.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/MatchModeResolver.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/NormalSelectionLayoutClassifier.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/AssistantPreferences.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/DraftFlowResolver.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt`
- Create: `app/src/test/resources/normal_mode/selection_early.png`
- Create: `app/src/test/resources/normal_mode/selection_populated.png`
- Create: `app/src/test/resources/normal_mode/loading.png`
- Create: `docs/normal_mode_calibration_2026-07-26.json`
- Create: `app/src/test/java/com/example/honorofkingsassistant/MatchModeResolverTest.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/NormalSelectionIsolationTest.kt`

**Interfaces:**
- Consumes: positioned OCR candidates before ranked side classification.
- Produces: `InputMode { AUTO_SCAN, MANUAL }`, `MatchMode { AUTO, RANKED_DRAFT, NORMAL_BLIND }`, `MatchModeState(preference, detected, effective)`, `NormalSelectionGeometry.forFrame(width, height)`, five normal ally rows, and `MatchModeResolver.observe(evidence): MatchModeState`.

- [ ] **Step 1: Add reproducible normal-mode fixtures**

From the user-provided recording `https://www.youtube.com/watch?v=t83Wka385_Q`,
extract only the game viewport at `00:45`, `01:00`, and `02:10`. Store the
three compressed PNG fixtures and a JSON manifest containing source URL,
timestamp, source viewport dimensions, crop rectangle, SHA-256, and measured
normalized envelopes. The fixtures are test evidence only and are not packaged
into the APK.

- [ ] **Step 2: Write failing geometry and resolver tests**

```kotlin
@Test
fun jkmAndCaptureFramesUseVerifiedNormalSelectionEnvelopes() {
    val geometry = NormalSelectionGeometry.forFrame(2340, 1080)
    assertEquals(PixelRect(1778, 43, 2270, 950), geometry.alliedColumn)
    assertEquals(PixelRect(1872, 842, 2293, 1058), geometry.confirmAction)
    assertEquals(5, geometry.allyRows.size)
    assertTrue(geometry.allyRows.zipWithNext().all { (a, b) -> a.bottom <= b.top })
    assertEquals(5, NormalSelectionGeometry.forFrame(1170, 540).allyRows.size)
}

@Test
fun normalRequiresThreeConsistentFrames() {
    val resolver = MatchModeResolver(requiredFrames = 3)
    repeat(2) { assertEquals(MatchMode.AUTO, resolver.observe(normalEvidence).effective) }
    assertEquals(MatchMode.NORMAL_BLIND, resolver.observe(normalEvidence).effective)
}

@Test
fun rankedBanEvidencePreventsNormalClassification() {
    val resolver = MatchModeResolver(requiredFrames = 3)
    repeat(3) { resolver.observe(normalEvidence.copy(rankedBanLayoutVisible = true)) }
    assertNotEquals(MatchMode.NORMAL_BLIND, resolver.current().effective)
}

@Test
fun manualPreferenceAlwaysWins() {
    val state = MatchModeState(
        preference = MatchMode.RANKED_DRAFT,
        detected = MatchMode.NORMAL_BLIND
    )
    assertEquals(MatchMode.RANKED_DRAFT, state.effective)
}
```

- [ ] **Step 3: Write failing observation-isolation tests**

```kotlin
@Test
fun normalCatalogAndCenterCandidatesAreDiscarded() {
    assertNull(classifier.classify(catalogCandidate))
    assertNull(classifier.classify(selectedHeroCandidate))
}

@Test
fun normalAllyRowThreeMapsOnlyToAllySlotThree() {
    val classified = classifier.classify(rowThreeCandidate)
    assertEquals(TeamSide.ALLY, classified?.side)
    assertEquals(3, classified?.slotIndex)
}

@Test
fun normalSelectionCanNeverProduceEnemyObservation() {
    assertTrue(allFixtureCandidates.mapNotNull(classifier::classify).none {
        it.side == TeamSide.ENEMY
    })
}
```

- [ ] **Step 4: Run and verify failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*MatchModeResolverTest" --no-daemon
.\gradlew.bat testDebugUnitTest --tests "*NormalSelectionIsolationTest" --no-daemon
```

Expected: FAIL because the match-mode types do not exist.

- [ ] **Step 5: Implement normalized geometry**

Use the calibrated envelopes:

```kotlin
val heroCatalog = normalizedRect(0.02, 0.05, 0.22, 0.86)
val selectedHero = normalizedRect(0.22, 0.05, 0.76, 0.92)
val alliedColumn = normalizedRect(0.76, 0.04, 0.97, 0.88)
val confirmAction = normalizedRect(0.80, 0.78, 0.98, 0.98)
```

Reuse the existing `NormalizedRect.toPixelRect` nearest-integer behavior and
`PixelRect` type, then clamp to frame bounds. Derive five non-overlapping
`allyRows` from measured fixture row centers rather than splitting the entire
column blindly.

- [ ] **Step 6: Implement preference/effective mode and conservative detection**

Require allied-column evidence, selected-hero evidence, no enemy-pick column, and no ranked-ban strip for three frames. Manual preference always overrides the suggestion.

- [ ] **Step 7: Route normal candidates before ranked tracking**

Preserve OCR center coordinates in an internal `PositionedHeroCandidate`.
For `RANKED_DRAFT`, keep the existing side/portrait/board path. For
`NORMAL_BLIND`, accept only candidates inside one of the five measured ally
rows, disable ranked portrait regions, publish no enemy observations, and
bypass ranked 10/10 completion logic. For unresolved `AUTO`, publish no hero
observations until three-frame evidence is decisive.

- [ ] **Step 8: Persist controls and service actions**

Add typed input-mode and match-mode keys to `AssistantPreferences`, fields to
`AssistantUiState`, and `ACTION_SET_INPUT_MODE` / `ACTION_SET_MATCH_MODE` to
`ScreenCaptureService`. Manual choices survive contradictory detection until
the session is reset.

- [ ] **Step 9: Run tests**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*MatchModeResolverTest" --no-daemon
.\gradlew.bat testDebugUnitTest --tests "*NormalSelectionIsolationTest" --no-daemon
.\gradlew.bat testDebugUnitTest --tests "*VideoCalibrationTest" --no-daemon
```

Expected: all PASS.

- [ ] **Step 10: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant app/src/test/java/com/example/honorofkingsassistant/MatchModeResolverTest.kt app/src/test/java/com/example/honorofkingsassistant/NormalSelectionIsolationTest.kt app/src/test/resources/normal_mode docs/normal_mode_calibration_2026-07-26.json
git commit -m "feat(match): add calibrated normal blind mode"
```

### Task 4: Overlay Transparency, Scroll, and Gesture Isolation

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `tools/test_overlay_contract.py`
- Modify: `tools/test_overlay_scroll_contract.py`
- Create: `tools/test_overlay_v55_contract.py`

**Interfaces:**
- Consumes: `InputMode`, `MatchMode`, and `AssistantUiState`.
- Produces: controls for input mode, match mode, stage, slot, pick state, `Escanear ahora`, and `Verificar equipos e ítems`.

- [ ] **Step 1: Extend failing overlay contracts**

Assert source contains:

```text
COLLAPSED_ALPHA = 0.55f
EXPANDED_ALPHA = 0.82f
MAX_PANEL_HEIGHT_RATIO = 0.72f
Verificar equipos e ítems
AUTO_SCAN
MANUAL
NORMAL_BLIND
RANKED_DRAFT
```

Also assert window dragging is installed on `dragHandle` and not on `ScrollView`.

- [ ] **Step 2: Run contracts and confirm failure**

Run: `python tools/test_overlay_v55_contract.py`

Expected: FAIL on missing alpha constants and controls.

- [ ] **Step 3: Implement the overlay shell**

Set bubble view alpha to `0.55f`; use a panel background whose alpha is `0.82f` while keeping text at full opacity. Compute:

```kotlin
val maxPanelHeight = (resources.displayMetrics.heightPixels * 0.72f).roundToInt()
scrollView.layoutParams = LinearLayout.LayoutParams(panelWidth, maxPanelHeight)
```

Attach movement touch handling only to a 40dp header handle. Let the scroll view and all child controls consume their own gestures.

- [ ] **Step 4: Add explicit mode controls and actions**

Publish immutable preference changes through service intents; do not mutate `AssistantSessionBus.state` directly from view listeners.

- [ ] **Step 5: Run contracts**

Run:

```powershell
python tools/test_overlay_contract.py
python tools/test_overlay_scroll_contract.py
python tools/test_overlay_v55_contract.py
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt app/src/main/res/values/strings.xml tools/test_overlay_contract.py tools/test_overlay_scroll_contract.py tools/test_overlay_v55_contract.py
git commit -m "feat(overlay): add translucent scroll-safe 1.0 controls"
```

### Task 5: Pure Scoreboard Geometry, Recognition Model, and Reconciliation

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardModels.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardGeometry.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardReconciler.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/ScoreboardGeometryTest.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/ScoreboardReconcilerTest.kt`

**Interfaces:**
- Produces: `ScoreboardRow`, `ScoreboardSnapshot`, `ScoreboardGeometry.forFrame(width, height)`, and `ScoreboardReconciler.reconcile(draft, scoreboard, manualOverrides)`.
- Later consumed by: `ScoreboardBitmapAnalyzer` and `ScreenCaptureService`.

- [ ] **Step 1: Write failing geometry tests**

```kotlin
@Test
fun screenshotGeometryCreatesFiveRowsPerSide() {
    val geometry = ScoreboardGeometry.forFrame(1560, 738)
    assertEquals(5, geometry.allies.size)
    assertEquals(5, geometry.enemies.size)
    assertTrue(geometry.allies.zipWithNext().all { (a, b) -> a.bounds.bottom <= b.bounds.top })
    assertTrue(geometry.enemies.all { it.bounds.left > 780 })
}
```

- [ ] **Step 2: Write failing reconciliation tests**

```kotlin
@Test
fun exactTitleAndPortraitCanCorrectWeakDraftIdentity() {
    val result = reconciler.reconcile(weakAngelaAsUnknown, strongAngelaScoreboard, emptyMap())
    assertEquals("Angela", result.snapshot.allies.first().heroName)
    assertTrue(result.appliedCorrections.single().reason.contains("retrato + título"))
}

@Test
fun manualIdentityIsNeverOverwritten() {
    val result = reconciler.reconcile(draft, conflictingScoreboard, mapOf(slot to manualHero))
    assertEquals(manualHero, result.snapshot.allies[slot.index].heroName)
}

@Test
fun strongPortraitTitleConflictRequiresConfirmation() {
    val result = reconciler.reconcile(draft, conflictingEvidence, emptyMap())
    assertTrue(result.pendingConflicts.isNotEmpty())
    assertTrue(result.appliedCorrections.isEmpty())
}
```

- [ ] **Step 3: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*Scoreboard*Test" --no-daemon`

Expected: FAIL because the scoreboard types do not exist.

- [ ] **Step 4: Implement normalized five-by-two geometry**

Use the supplied `1560 × 738` screenshot as the reference aspect ratio, but store every row, portrait, title, player-name, level, and six-item strip as normalized rectangles. Mark bottom-left row five as the initial R-95 hint only when OCR also reads normalized `r 95`.

- [ ] **Step 5: Implement confidence-gated reconciliation**

Apply corrections only for exact title plus portrait, or one strong signal when the preserved draft signal is weak. Return unresolved conflicts rather than mutating the draft silently.

- [ ] **Step 6: Run focused tests**

Run: `.\gradlew.bat testDebugUnitTest --tests "*Scoreboard*Test" --no-daemon`

Expected: all PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardModels.kt app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardGeometry.kt app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardReconciler.kt app/src/test/java/com/example/honorofkingsassistant/ScoreboardGeometryTest.kt app/src/test/java/com/example/honorofkingsassistant/ScoreboardReconcilerTest.kt
git commit -m "feat(scoreboard): model teams and safe draft corrections"
```

### Task 6: Scoreboard Bitmap Analyzer and One-Shot Capture Command

**Files:**
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardBitmapAnalyzer.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/ScoreboardScanCoordinatorTest.kt`
- Create: `tools/test_scoreboard_scan_contract.py`

**Interfaces:**
- Consumes: Task 5 geometry and reconciliation.
- Produces: `ScoreboardScanState { IDLE, ARMED, CAPTURING, REVIEW, FAILED }`, action `ACTION_SCAN_SCOREBOARD`, and `ScoreboardBitmapAnalyzer.analyze(bitmap, ocrBlocks, portraitTemplates)`.

- [ ] **Step 1: Write a failing coordinator test**

```kotlin
@Test
fun explicitScanHidesOverlayForExactlyOneEligibleFrame() {
    coordinator.arm()
    assertTrue(coordinator.shouldHideOverlay())
    assertTrue(coordinator.claimFrame(eligibleFrame))
    assertFalse(coordinator.claimFrame(nextFrame))
    assertEquals(ScoreboardScanState.REVIEW, coordinator.state)
}
```

- [ ] **Step 2: Write the failing static contract**

Assert the action string exists, the overlay publishes it only from the explicit button, and ordinary frame callbacks cannot arm a scan.

- [ ] **Step 3: Run tests and confirm failure**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ScoreboardScanCoordinatorTest" --no-daemon
python tools/test_scoreboard_scan_contract.py
```

Expected: FAIL.

- [ ] **Step 4: Implement one-shot capture coordination**

On button press: arm scan, hide overlay, claim the next eligible frame, copy the bitmap, restore overlay in `finally`, analyze off main thread, publish review results. A ten-second timeout restores the overlay and publishes a Spanish failure message.

- [ ] **Step 5: Implement row analysis**

For each row, combine exact localized title OCR, portrait matcher result, normalized player name, level digits, and item fingerprints. Do not block the whole scan when one row is unknown.

- [ ] **Step 6: Run focused tests and contracts**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*Scoreboard*" --no-daemon
python tools/test_scoreboard_scan_contract.py
```

Expected: all PASS.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/ScoreboardBitmapAnalyzer.kt app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt app/src/test/java/com/example/honorofkingsassistant/ScoreboardScanCoordinatorTest.kt tools/test_scoreboard_scan_contract.py
git commit -m "feat(scoreboard): add explicit one-shot team scan"
```

### Task 7: Offline Item Catalog and Adaptive Recommendation Engine

**Files:**
- Create: `app/src/main/assets/hok_items.json`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ItemCatalog.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ItemRecommendationEngine.kt`
- Create: `app/src/main/kotlin/com/example/honorofkingsassistant/ItemModels.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/ItemRecommendationEngineTest.kt`
- Create: `tools/test_item_catalog.py`
- Modify: `app/src/main/assets/data_manifest.json`

**Interfaces:**
- Consumes: canonical hero IDs, role, allied/enemy composition, visible enemy purchases, player level, and current items.
- Produces: `ItemPlan(nextItems, alternatives, sellOrReplace, evidence, confidence)`.

- [ ] **Step 1: Write failing scoring tests**

```kotlin
@Test
fun heavyMagicDamageRaisesMagicDefensePriority() {
    val plan = engine.recommend(context.copy(enemyThreats = listOf(MAGIC_BURST, MAGIC_BURST)))
    assertTrue(plan.nextItems.first().tags.contains("magic_defense"))
    assertTrue(plan.evidence.any { it.contains("daño mágico") })
}

@Test
fun healingCompositionRaisesAntiHealPriority() {
    val plan = engine.recommend(context.copy(enemyThreats = listOf(SUSTAIN, HEALING)))
    assertTrue(plan.nextItems.take(2).any { it.tags.contains("anti_heal") })
}

@Test
fun normalBlindWithoutEnemiesReturnsSafeCoreBuild() {
    val plan = engine.recommend(context.copy(matchMode = MatchMode.NORMAL_BLIND, enemies = emptyList()))
    assertTrue(plan.nextItems.isNotEmpty())
    assertTrue(plan.evidence.any { it.contains("núcleo seguro") })
}
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*ItemRecommendationEngineTest" --no-daemon`

Expected: FAIL because item models do not exist.

- [ ] **Step 3: Build a versioned offline catalog**

Each record must contain:

```json
{
  "id": "stable_item_id",
  "name_es": "Nombre mostrado",
  "cost": 0,
  "tier": 1,
  "tags": ["physical_attack"],
  "builds_from": [],
  "source_url": "https://...",
  "patch_label": "HoK Plus 2.0 / Season 15",
  "snapshot_date": "2026-07-26"
}
```

Bundle current item definitions and base hero core builds, reject duplicate normalized names/IDs, and update `data_manifest.json` hashes.

- [ ] **Step 4: Implement deterministic scoring**

Score:

```text
base hero fit + role fit + threat response + synergy need
+ build-path continuity + current-item complement
- duplicate unique passive - obsolete component - unaffordable detour
```

Return at most three next purchases and two situational alternatives. Every positive or negative term must generate a Spanish evidence line.

- [ ] **Step 5: Run tests and catalog validation**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ItemRecommendationEngineTest" --no-daemon
python tools/test_item_catalog.py
```

Expected: all PASS and no catalog duplication.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/assets/hok_items.json app/src/main/assets/data_manifest.json app/src/main/kotlin/com/example/honorofkingsassistant/ItemCatalog.kt app/src/main/kotlin/com/example/honorofkingsassistant/ItemRecommendationEngine.kt app/src/main/kotlin/com/example/honorofkingsassistant/ItemModels.kt app/src/test/java/com/example/honorofkingsassistant/ItemRecommendationEngineTest.kt tools/test_item_catalog.py
git commit -m "feat(items): add offline adaptive purchase guidance"
```

### Task 8: Session Integration and Review UI

**Files:**
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt`
- Modify: `app/src/main/kotlin/com/example/honorofkingsassistant/StrategyEngine.kt`
- Create: `app/src/test/java/com/example/honorofkingsassistant/V55SessionIntegrationTest.kt`

**Interfaces:**
- Consumes: Tasks 1–7.
- Produces: one immutable `AssistantUiState` containing input/match mode, scan state, scoreboard review, corrections, and `ItemPlan`.

- [ ] **Step 1: Write failing state-transition tests**

```kotlin
@Test
fun acceptedScoreboardCorrectionRecomputesStrategyAndItems() {
    val next = reducer.reduce(state, AcceptScoreboardCorrection(slot, "Angela"))
    assertEquals("Angela", next.snapshot.allies[slot.index].heroName)
    assertNotEquals(state.itemPlan, next.itemPlan)
    assertNotEquals(state.strategy, next.strategy)
}

@Test
fun normalSelectionNeverAddsEnemyFromDraftOcr() {
    val next = reducer.reduce(normalState, VisionUpdate(enemyLookingObservation))
    assertTrue(next.snapshot.enemies.isEmpty())
}
```

- [ ] **Step 2: Run and confirm failure**

Run: `.\gradlew.bat testDebugUnitTest --tests "*V55SessionIntegrationTest" --no-daemon`

Expected: FAIL because the reducer/events are absent.

- [ ] **Step 3: Add a pure reducer**

Create typed `AssistantEvent` values and a reducer that applies preferences, vision updates, scan results, manual corrections, and item-plan recalculation. Services publish events; only the reducer constructs the next state.

- [ ] **Step 4: Render review and item guidance**

The overlay must show:

```text
Equipo detectado: 9/10
Conflictos por revisar: 1
Próxima compra: <item>
Por qué: <short evidence>
Alternativa: <item>
```

Keep the bubble minimized during normal gameplay and expose details only when expanded.

- [ ] **Step 5: Run integration and all unit tests**

Run: `.\gradlew.bat testDebugUnitTest --no-daemon`

Expected: all PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/example/honorofkingsassistant/AssistantSessionBus.kt app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt app/src/main/kotlin/com/example/honorofkingsassistant/StrategyEngine.kt app/src/test/java/com/example/honorofkingsassistant/V55SessionIntegrationTest.kt
git commit -m "feat(session): integrate 1.0 recovery and item guidance"
```

### Task 9: Android Emulator QA and Performance Evidence

**Files:**
- Create: `docs/qa/v5-5-emulator-checklist.md`
- Create: `tools/v55_emulator_smoke.ps1`
- Modify: `.github/workflows/android-ci.yml`

**Interfaces:**
- Consumes: assembled debug APK and adb target.
- Produces: repeatable install/launch/overlay/logcat QA evidence.

- [ ] **Step 1: Add a failing CI contract for Version 1.0 assets and tests**

Extend validation to require `hok_items.json`, Spanish alias validator, scoreboard scan contract, item catalog validator, and Version 1.0 metadata.

- [ ] **Step 2: Run contract and confirm failure**

Run: `python tools/test_v5_delivery_contract.py`

Expected: FAIL until workflow and metadata include Version 1.0 artifacts.

- [ ] **Step 3: Update CI and version metadata**

Set `versionCode 12` and `versionName "1.0.0-personal-jkm-lx3-rc1"`. CI runs unit tests, lint, assemble, offline manifest verification, JSON validators, and artifact upload.

- [ ] **Step 4: Build and install on an Android 9-compatible AVD**

Run:

```powershell
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am force-stop com.example.honorofkingsassistant
adb shell monkey -p com.example.honorofkingsassistant -c android.intent.category.LAUNCHER 1
adb shell uiautomator dump /sdcard/v55.xml
adb pull /sdcard/v55.xml build/qa/v55.xml
```

Expected: installation succeeds, launcher activity is visible in UI XML, and logcat contains no fatal exception.

- [ ] **Step 5: Exercise overlay controls with UI-tree-backed coordinates**

Use `uiautomator dump` before each tap, derive button bounds from exact visible text, grant overlay/capture flows manually where Android system confirmation requires it, and capture screenshots for collapsed alpha, expanded panel, scroll bottom, match mode, and scoreboard action.

- [ ] **Step 6: Capture performance evidence**

Run a 30-minute synthetic capture/overlay session, then collect:

```powershell
adb shell dumpsys meminfo com.example.honorofkingsassistant
adb shell dumpsys gfxinfo com.example.honorofkingsassistant
adb logcat -d *:E
```

Acceptance: no crash/ANR, proportional set size remains below 220 MB, no unbounded frame backlog, and overlay actions respond within 500 ms outside OCR execution.

- [ ] **Step 7: Commit**

```powershell
git add app/build.gradle .github/workflows/android-ci.yml docs/qa/v5-5-emulator-checklist.md tools/v55_emulator_smoke.ps1 tools/test_v5_delivery_contract.py
git commit -m "test: add Version 1.0 emulator and CI gates"
```

### Task 10: Final Verification and Candidate Handoff

**Files:**
- Modify: `README.md`
- Create: `docs/qa/v5-5-verification-report.md`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: a single traceable Version 1.0 personal candidate with build evidence and explicit physical-device gates.

- [ ] **Step 1: Run the complete verification suite**

Run:

```powershell
python tools/validate_project.py
python tools/validate_v4.py
python tools/validate_v5.py
python tools/test_spanish_alias_catalog.py
python tools/test_overlay_v55_contract.py
python tools/test_scoreboard_scan_contract.py
python tools/test_item_catalog.py
.\gradlew.bat clean testDebugUnitTest lintDebug assembleDebug --no-daemon
```

Expected: every command exits `0`.

- [ ] **Step 2: Inspect the APK**

Use Android build tools to assert `AndroidManifest.xml`, `classes.dex`, `assets/hok_counters.json`, and `assets/hok_items.json` exist; assert network permissions are absent; calculate APK SHA-256.

- [ ] **Step 3: Review the diff**

Run:

```powershell
git diff main...HEAD --check
git status --short
git log --oneline main..HEAD
```

Expected: no whitespace errors, no unrelated generated files, and a clean worktree.

- [ ] **Step 4: Document evidence and remaining physical-device gate**

Record exact test counts, lint result, APK path/hash, emulator version, memory/gfx evidence, verified Spanish aliases, and the remaining Huawei checks: overlay permission, MediaProjection consent, normal/ranked selection accuracy, scoreboard scan, and item usefulness in a complete match.

- [ ] **Step 5: Commit**

```powershell
git add README.md docs/qa/v5-5-verification-report.md
git commit -m "docs: record Version 1.0 candidate verification"
```

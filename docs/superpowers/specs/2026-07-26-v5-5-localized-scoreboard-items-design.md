# HoK Draft Assistant 1.0 — Localized Identity, Scoreboard Recovery, and Items

## Objective

Deliver the first formal personal 1.0 release for the Huawei JKM-LX3 that remains comfortable
over Honor of Kings, recognizes canonical hero names and the Spanish titles shown
by the user's client, supports ranked and normal matches, can recover the full
composition from the in-game scoreboard on demand, and recommends the next item
with an explanation.

Version 1.0 is the reliability and data-quality release. Predictive commercial features must not
be built on top of unresolved identities, an unstable overlay, or unvalidated
counter data.

## Global constraints

- Primary device: Huawei JKM-LX3, Android 9, EMUI 9.1, Kirin 710, 4 GB RAM,
  2340x1080 landscape.
- The assistant remains offline: no `INTERNET` or `ACCESS_NETWORK_STATE`
  permission, server, account, telemetry, or remote API at runtime.
- `ScreenCaptureService` remains the only foreground service.
- Manual state is authoritative. Automatic screen detection can suggest a state
  change but cannot force it.
- The overlay never sends input to Honor of Kings.
- Images are processed in memory and are not stored.
- Every catalog record includes a source, patch label, snapshot date, and
  confidence.

## Scope decomposition

Version 1.0 contains five independently testable subsystems:

1. Overlay usability and match/input controls.
2. Localized hero identity and gallery calibration.
3. Ranked/normal draft behavior.
4. On-demand in-game scoreboard recovery.
5. Explainable, adaptive item recommendations.

V6 is specified only as the next architectural direction. It is not mixed into
the Version 1.0 implementation.

## 1. Overlay usability

### Visual behavior

- Collapsed bubble opacity: `0.55`.
- Expanded panel background opacity: `0.82`.
- Internal controls use at least `0.75` opacity.
- Text remains at least `0.95` opacity for readability.
- The expanded panel occupies at most 72% of the usable screen height.
- All panel content is inside a vertically scrollable container.
- Only the blue `HOK · <MODE>` header acts as a drag handle.
- Scrolling inside the content cannot move the overlay.
- The entire overlay is clamped inside system insets and display bounds.
- Switching to `IN_GAME` collapses the panel but preserves the bubble.

### Controls

The expanded panel exposes:

```text
Stage:      Pausa | Selección | Partida
Input:      Auto | Manual
Match:      Auto | Clasificatoria | Normal
My slot:    Auto | 1 | 2 | 3 | 4 | 5
My pick:    Auto | Pendiente | Fijado
Actions:    Escanear ahora | Verificar equipos e ítems | Editar composición
```

User-selected values persist for the current session. Stage, input mode, match
mode, slot override, and pick override are stored separately so changing one
does not reset the others.

## 2. Localized hero identity

### Data model

Every playable identity resolves to one stable `heroId`.

```json
{
  "id": "angela",
  "canonical_name": "Angela",
  "display_titles": {
    "es-419": ["La Maga de Fuego"]
  },
  "historical_names": [],
  "search_aliases": {
    "es-419": ["Ángela", "Angela", "Maga de Fuego"],
    "en": ["Angela", "Scorchette"]
  },
  "ocr_aliases": {
    "es-419": ["La Maga de Fuego", "Maga de Fuego"]
  },
  "source": "user_client_gallery_capture",
  "patch_label": "Season 15 HOK Plus 2.0",
  "snapshot_date": "2026-07-26",
  "confidence": 1.0
}
```

`search_aliases` may include convenient manual-search terms. `ocr_aliases`
contains only text verified as appearing in the client and is the only alias
group permitted to confirm an OCR observation automatically.

### Confirmed Spanish titles from the supplied gallery capture

| Canonical identity | Spanish title |
|---|---|
| Angela | La Maga de Fuego |
| Bai Qi | El Arma Suprema |
| Flowborn (Tank) | Puño de la Paz |
| Flowborn (Mage) | Corazón Arcano |
| Shouyue | El Francotirador |
| Xuance | La Hoz Justiciera |
| Augran | El Sumo Sacerdote |
| Dr Bian | El Boticario |
| Mai Shiranui | La Ninja de Fuego |
| Cai Yan | La Alegre Canción |
| Fatih | El Conquistador |
| Chano | El Último Lobo |

### Matching pipeline

1. Normalize case, accents, apostrophes, punctuation, and repeated whitespace.
2. Prefer exact canonical names.
3. Prefer exact verified OCR aliases.
4. Match a canonical name and title found in the same gallery card as a pair.
5. Use fuzzy matching only for OCR correction and require a sufficient margin
   between the best and second-best candidates.
6. Require temporal confirmation before adding an automatic observation.
7. Never resolve a generic word such as `Maga`, `Arma`, or `Fuego` alone.

### Gallery calibration

The main activity includes `Calibrar nombres desde galería`.

When active, the user opens the game's hero gallery and manually advances
through its pages. For every visible card the assistant:

1. Detects the card rectangle.
2. Reads the canonical line and the Spanish title line.
3. Resolves the canonical line against the existing roster.
4. Proposes the title-to-hero association.
5. Saves it only after exact canonical resolution and stable repeated OCR, or
   after explicit user confirmation.

Learned aliases are stored in app-private JSON and merged over the bundled
catalog. Export and import use a signed data manifest so corrupt files are
rejected.

## 3. Ranked and normal matches

```kotlin
enum class InputMode { AUTO_SCAN, MANUAL }
enum class MatchMode { AUTO, RANKED_DRAFT, NORMAL_BLIND }
enum class AssistantStage { PAUSED, DRAFT, IN_GAME }
```

### Ranked draft

- Track allies, enemies, bans, empty slots, previews, and confirmed picks.
- Recommend Top 3 while the user's pick is pending.
- Score direct matchup coverage, allied synergy, missing role, frontline,
  physical/magic balance, control, and worst exposed matchup.
- After the user's pick is fixed, replace Top 3 with composition analysis.

### Normal blind

- Do not require or infer enemy picks during hero selection.
- Recommend by requested lane, missing role, allied synergy, damage balance,
  frontline, control, safety, and the user's preferred heroes.
- Discover the enemy team later from loading or scoreboard recovery.
- Use normalized regions rather than fixed pixels so the JKM-LX3
  `2340 × 1080` frame and reduced capture frame share one geometry model.

### Normal-mode visual calibration

The linked 24:01 normal-match recording was sampled at the smallest useful set
of transitions instead of being transcribed or processed frame by frame:

- `00:00`: normal `Equipo 5v5` lobby and `Buscar partida`.
- `00:45`: normal hero selection with hero catalog on the left, selected hero
  in the center, five allied pick rows on the right, timer at the upper right,
  and confirm action at the lower right.
- `01:00` and `01:50`: the same selection layout while allied picks become
  populated; no enemy picks are exposed.
- `02:10`: loading screen with five allied cards on the upper row and five
  enemy cards on the lower row.
- `23:00`: return to the game lobby after the match.

The selection detector uses conservative normalized envelopes:

```text
hero catalog:       x 0.02..0.22, y 0.05..0.86
selected hero:      x 0.22..0.76, y 0.05..0.92
allied pick column: x 0.76..0.97, y 0.04..0.88
confirm action:     x 0.80..0.98, y 0.78..0.98
```

These are classification envelopes, not OCR crop rectangles. Exact crop
rectangles are derived inside each detected envelope from portrait and text
edges. The detector must require the allied column plus the absence of a ranked
ban/enemy-pick layout across three eligible frames before selecting
`NORMAL_BLIND`. If evidence is incomplete, it remains in `AUTO` and asks for a
manual match-mode selection instead of guessing.

The loading layout may confirm provisional identities, but the on-demand
scoreboard remains the authoritative recovery surface because the reference
recording is compressed and some loading-card assets are missing.

### Automatic match mode

Automatic mode may suggest ranked or normal from visible layout evidence. A
manual selection always wins and remains active for the session.

## 4. On-demand scoreboard recovery

### User flow

The overlay action is named `Verificar equipos e ítems`.

1. The user opens the in-game scoreboard.
2. The user presses the overlay action.
3. The overlay becomes invisible for the next eligible frame.
4. The analyzer captures one scoreboard frame.
5. The overlay returns immediately after capture.
6. Ten normalized row regions are analyzed: five allies on the left and five
   enemies on the right.
7. Hero portrait, Spanish title, player name, level, and up to six item slots are
   extracted when visible.
8. Results are reconciled against the preserved draft.
9. Conflicts are shown as explicit correction choices and are not silently
   applied.

The application can display `Marcador detectado · toca Verificar` but cannot run
this recovery automatically.

### Reconciliation rules

- A manual correction has confidence `1.0` and is never overwritten
  automatically.
- A scoreboard row with both recognized portrait and verified Spanish title may
  replace a lower-confidence draft identity.
- A title-only recognition requires an exact verified OCR alias.
- A portrait-only recognition requires the existing ambiguity-margin rule.
- A conflict between strong portrait and strong title evidence is presented to
  the user.
- Team side is fixed by row geometry, not inferred from colors alone.
- The user's row is matched by normalized player name `R-95` when visible; the
  configured manual slot remains authoritative.

### Supplied scoreboard fixture

`confirmacion heroes seleccionados en la partida.jpeg` becomes a regression
fixture. Tests verify:

- five left and five right row regions;
- the visible `R-95` row is on the ally side;
- `El Boticario` resolves to Dr Bian;
- item slots are cropped independently from hero/title/player text;
- overlay exclusion leaves the scoreboard unobstructed.

## 5. Adaptive item recommendations

### Item data

The bundled item catalog contains:

```text
Stable item ID
English and Spanish display names
Category and build path
Cost
Stats
Passive tags
Mutually exclusive groups
Source, patch, snapshot date, confidence
```

Passive tags include:

```text
physical_damage, magic_damage, true_damage, armor, magic_resist,
anti_heal, shield_break, crowd_control_resist, sustain, penetration,
attack_speed, critical, cooldown, movement, jungle, roam
```

Each hero has one or more role-specific base builds. Base builds are a starting
point rather than an immutable six-item list.

### Recommendation input

- Player hero and role.
- Allied and enemy composition.
- Enemy damage profile and major control.
- Enemy healing, shielding, sustain, and frontline.
- Items already recognized on the player.
- Visible enemy items and which enemy currently represents the largest threat.
- Current match phase inferred from level and completed-item count.

### Output

The overlay shows at most three next purchases:

```text
1. <item> — recommended next
   <one-sentence reason tied to observed evidence>
2. <alternative> — if <specific condition>
Avoid: <item> — <specific reason>
```

The engine is deterministic and explainable. It never invents an item name and
never recommends an item absent from the versioned catalog.

### Initial decision rules

- Prioritize magic resistance against multiple or fed magic threats.
- Prioritize armor against multiple or fed physical threats.
- Prioritize anti-heal against substantial healing or lifesteal.
- Prioritize control resistance against heavy crowd control.
- Prioritize penetration when the enemy has completed relevant defenses.
- Preserve the hero's first core power spike unless immediate defense is
  necessary.
- Do not recommend an already completed unique item.
- Respect mutually exclusive support, jungle, and active-item groups.

## Performance and failure handling

- Draft scanning retains the JKM-LX3 adaptive 700–1600 ms cadence.
- Scoreboard verification is a one-shot operation and does not enable continuous
  item OCR.
- Only cropped scoreboard rows and item slots are processed.
- OCR work is dropped while ML Kit is busy; no unbounded queue is permitted.
- Any failed scan restores the overlay and leaves the previous composition
  unchanged.
- The result states `No se pudo verificar` with one concise corrective action
  instead of publishing partial low-confidence data.

## Testing strategy

### Pure unit tests

- Canonical and Spanish-title resolution.
- Accent and punctuation normalization.
- Ambiguous alias rejection.
- Gallery pair extraction and merge precedence.
- Ranked versus normal scoring behavior.
- Scoreboard reconciliation and manual-confidence precedence.
- Item-rule selection, exclusions, and explanations.

### Fixture tests

- Supplied gallery capture for all twelve visible title mappings.
- Supplied scoreboard capture for row geometry, Dr Bian title resolution,
  `R-95`, and item-slot crops.
- Existing draft screenshots and recorded-video frames remain regression inputs.

### Android emulator

- Android 9 AVD matching 2340x1080 landscape density where practical.
- Overlay expand/collapse, alpha, drag-handle isolation, and full vertical scroll.
- Manual stage/input/match/slot/pick controls.
- Scoreboard action always restores the overlay after success or failure.
- Process recreation preserves user-selected session controls.

The emulator validates app interaction and rendering. Honor of Kings recognition
accuracy is validated with injected fixture frames because the real game and
EMUI behavior cannot be faithfully reproduced by a generic AVD.

### CI and device gates

- `testDebugUnitTest`, `lintDebug`, and `assembleDebug` pass.
- APK remains without network permissions.
- Existing 31 tests remain green and new Version 1.0 tests pass.
- Real Huawei session: 30 minutes without crash/ANR, overlay remains scrollable,
  scoreboard scan restores the overlay, and no automatic correction replaces a
  manual identity.

## V6 direction: Adaptive Decision Engine

The universal/commercial line begins only after the personal 1.0 device gates pass.

### Included

- Versioned hero feature vectors for damage, range, mobility, control,
  frontline, engage/disengage, sustain, wave clear, objective control, scaling,
  and power spikes.
- Full allied synergy and multi-enemy matchup coverage.
- User mastery and preference profile stored locally.
- Response simulation: likely counter-responses to each recommended pick.
- Draft Advantage Index with evidence and uncertainty.
- Patch-package importer with schema/signature validation.
- Phase-specific strategy and adaptive item planning.

### Experimental until calibrated data exists

- Next-pick prediction is presented as ranked candidates, not certainty.
- Win probability is not shown as a percentage until evaluated against a
  sufficiently large, patch-matched dataset with calibration error reported.
- Rank-specific recommendations are enabled only when observations are actually
  segmented by rank.
- The app learns the user's preferences and outcomes, not unverifiable profiles
  of random opponents.

## Acceptance criteria

Version 1.0 personal is complete only when:

1. All twelve supplied Spanish titles resolve to the correct stable identity.
2. Gallery calibration can add a new verified alias without recompiling.
3. Ranked recommendations score visible enemy matchups; normal-blind
   recommendations ignore unseen enemies and score allied composition instead.
4. The overlay is readable, translucent, draggable only by its header, and fully
   scrollable at 2340x1080 landscape.
5. `Verificar equipos e ítems` performs one-shot capture, hides/restores the
   overlay, and never silently applies a conflict.
6. Item advice names an existing catalog item and explains the observed reason.
7. All unit, fixture, emulator, lint, build, offline-permission, and Huawei
   acceptance gates pass.

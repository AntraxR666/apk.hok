package com.example.honorofkingsassistant

import kotlin.math.roundToInt

data class RankedLoadingRosterGeometry(
    val allyCards: List<PixelRect>,
    val enemyCards: List<PixelRect>,
    val allyPortraits: List<PixelRect>,
    val enemyPortraits: List<PixelRect>
)

data class LoadingRosterCardEvidence(
    val side: TeamSide,
    val slotIndex: Int,
    val exactTitleHeroName: String? = null,
    val portraitHeroName: String? = null,
    val playerName: String? = null
) {
    init {
        require(side != TeamSide.UNKNOWN)
        require(slotIndex in 1..5)
    }
}

data class PreservedRosterIdentity(
    val side: TeamSide,
    val slotIndex: Int?,
    val heroName: String,
    val confidence: Double,
    val isManual: Boolean
) {
    init {
        require(side != TeamSide.UNKNOWN)
        require(slotIndex == null || slotIndex in 1..5)
    }
}

data class LoadingRosterAssignment(
    val side: TeamSide,
    val slotIndex: Int,
    val heroName: String,
    val confidence: Double,
    val preservedManualEvidence: Boolean
)

data class LoadingRosterConflict(
    val side: TeamSide,
    val slotIndex: Int,
    val observedHeroName: String,
    val preservedHeroName: String?,
    val reason: String
)

data class LoadingRosterReconciliationResult(
    val assignments: List<LoadingRosterAssignment>,
    val conflicts: List<LoadingRosterConflict>,
    val playerSlotIndex: Int?
)

class RankedLoadingRosterEvidenceExtractor(
    heroes: List<Hero>
) {
    private val exactHeroByTitle = buildMap {
        heroes.forEach { hero ->
            sequenceOf(hero.name, hero.id)
                .plus(hero.identityAliases.displayTitles.asSequence())
                .map(::normalizeHeroRecognitionText)
                .filter(String::isNotBlank)
                .forEach { title -> putIfAbsent(title, hero) }
        }
    }

    fun extract(
        lines: List<PositionedTextLine>,
        portraitMatches: List<SlotHeroMatch>,
        frameWidth: Int,
        frameHeight: Int,
        configuredPlayerName: String
    ): List<LoadingRosterCardEvidence> {
        val geometry = RankedLoadingRosterAnalyzer.geometry(frameWidth, frameHeight)
        val targetPlayer = PlayerIdentityNormalizer.canonical(configuredPlayerName)
        val portraitBySlot = portraitMatches.associateBy { it.side to it.slotIndex }

        fun cards(side: TeamSide, regions: List<PixelRect>) =
            regions.mapIndexed { index, region ->
                val cardLines = lines.filter { line ->
                    line.centerX >= region.left &&
                        line.centerX < region.right &&
                        line.centerY >= region.top &&
                        line.centerY < region.bottom
                }
                val exactTitle = cardLines.firstNotNullOfOrNull { line ->
                    exactHeroByTitle[normalizeHeroRecognitionText(line.text)]?.name
                }
                val playerName = cardLines.firstOrNull { line ->
                    targetPlayer.isNotBlank() &&
                        PlayerIdentityNormalizer.canonical(line.text) == targetPlayer
                }?.text
                LoadingRosterCardEvidence(
                    side = side,
                    slotIndex = index + 1,
                    exactTitleHeroName = exactTitle,
                    portraitHeroName = portraitBySlot[side to (index + 1)]?.heroName,
                    playerName = playerName
                )
            }

        return cards(TeamSide.ALLY, geometry.allyCards) +
            cards(TeamSide.ENEMY, geometry.enemyCards)
    }
}

/**
 * Reconciles the stable ranked loading screen without mutating preserved draft evidence.
 */
class RankedLoadingRosterAnalyzer {
    fun reconcile(
        cards: List<LoadingRosterCardEvidence>,
        preserved: List<PreservedRosterIdentity> = emptyList(),
        configuredPlayerName: String = "R-95",
        manualPlayerSlotIndex: Int? = null
    ): LoadingRosterReconciliationResult {
        require(manualPlayerSlotIndex == null || manualPlayerSlotIndex in 1..5)
        val preservedBySlot = buildMap<Pair<TeamSide, Int>, PreservedRosterIdentity> {
            preserved.sortedBy { it.isManual }.forEach { identity ->
                identity.slotIndex?.let { slot -> put(identity.side to slot, identity) }
            }
        }
        val manualByHero = preserved
            .filter { it.isManual }
            .associateBy { it.side to CounterCatalog.normalize(it.heroName) }
        val assignments = linkedMapOf<Pair<TeamSide, Int>, LoadingRosterAssignment>()
        val conflicts = mutableListOf<LoadingRosterConflict>()

        preserved.forEach { identity ->
            identity.slotIndex?.let { slot ->
                val key = identity.side to slot
                val current = assignments[key]
                if (current == null || identity.isManual) {
                    assignments[key] = LoadingRosterAssignment(
                        side = identity.side,
                        slotIndex = slot,
                        heroName = identity.heroName,
                        confidence = identity.confidence,
                        preservedManualEvidence = identity.isManual
                    )
                }
            }
        }

        cards.forEach { card ->
            val key = card.side to card.slotIndex
            val title = card.exactTitleHeroName?.trim()?.takeIf(String::isNotBlank)
            val portrait = card.portraitHeroName?.trim()?.takeIf(String::isNotBlank)
            val signalsAgree = title != null && portrait != null &&
                CounterCatalog.normalize(title) == CounterCatalog.normalize(portrait)
            if (title != null && portrait != null && !signalsAgree) {
                conflicts += LoadingRosterConflict(
                    side = card.side,
                    slotIndex = card.slotIndex,
                    observedHeroName = "$title / $portrait",
                    preservedHeroName = preservedBySlot[key]?.heroName,
                    reason = "La identidad exacta del título contradice el retrato"
                )
                return@forEach
            }
            val observedHero = when {
                signalsAgree -> title
                title != null -> title
                else -> portrait
            } ?: return@forEach
            val observedConfidence = when {
                signalsAgree -> 0.99
                title != null -> 0.93
                else -> 0.88
            }
            val existing = preservedBySlot[key]
            val manualForObserved = manualByHero[
                card.side to CounterCatalog.normalize(observedHero)
            ]
            if (existing?.isManual == true &&
                CounterCatalog.normalize(existing.heroName) !=
                CounterCatalog.normalize(observedHero)
            ) {
                conflicts += LoadingRosterConflict(
                    side = card.side,
                    slotIndex = card.slotIndex,
                    observedHeroName = observedHero,
                    preservedHeroName = existing.heroName,
                    reason = "La evidencia de carga contradice una identidad manual"
                )
                return@forEach
            }
            if (manualForObserved != null) {
                if (manualForObserved.slotIndex != null &&
                    manualForObserved.slotIndex != card.slotIndex
                ) {
                    conflicts += LoadingRosterConflict(
                        side = card.side,
                        slotIndex = card.slotIndex,
                        observedHeroName = observedHero,
                        preservedHeroName = manualForObserved.heroName,
                        reason = "La evidencia de carga duplica una identidad manual en otra posición"
                    )
                    return@forEach
                }
                assignments[key] = LoadingRosterAssignment(
                    side = card.side,
                    slotIndex = card.slotIndex,
                    heroName = manualForObserved.heroName,
                    confidence = manualForObserved.confidence,
                    preservedManualEvidence = true
                )
                return@forEach
            }
            if (existing == null || !existing.isManual || existing.confidence < observedConfidence) {
                assignments[key] = LoadingRosterAssignment(
                    side = card.side,
                    slotIndex = card.slotIndex,
                    heroName = observedHero,
                    confidence = observedConfidence,
                    preservedManualEvidence = existing?.isManual == true
                )
            }
        }

        val targetPlayer = PlayerIdentityNormalizer.canonical(configuredPlayerName)
        val detectedPlayerSlot = cards.firstOrNull { card ->
            card.side == TeamSide.ALLY &&
                targetPlayer.isNotBlank() &&
                PlayerIdentityNormalizer.canonical(card.playerName.orEmpty()) == targetPlayer
        }?.slotIndex

        return LoadingRosterReconciliationResult(
            assignments = assignments.values.sortedWith(
                compareBy<LoadingRosterAssignment> { it.side.ordinal }.thenBy { it.slotIndex }
            ),
            conflicts = conflicts.toList(),
            playerSlotIndex = manualPlayerSlotIndex ?: detectedPlayerSlot
        )
    }

    companion object {
        private val CARD_CENTERS_X = listOf(0.24, 0.37, 0.50, 0.63, 0.76)
        private const val CARD_HALF_WIDTH = 0.055
        private const val ALLY_TOP = 0.0
        private const val ALLY_BOTTOM = 0.49
        private const val ENEMY_TOP = 0.54
        private const val ENEMY_BOTTOM = 1.0
        private const val PORTRAIT_HORIZONTAL_INSET = 0.04
        private const val ALLY_PORTRAIT_TOP_INSET = 0.02
        private const val ALLY_PORTRAIT_BOTTOM_INSET = 0.305
        private const val ENEMY_PORTRAIT_TOP_INSET = 0.04
        private const val ENEMY_PORTRAIT_BOTTOM_INSET = 0.28

        fun geometry(frameWidth: Int, frameHeight: Int): RankedLoadingRosterGeometry {
            require(frameWidth > 0 && frameHeight > 0)
            val halfWidth = (CARD_HALF_WIDTH * frameWidth).roundToInt()
            fun row(top: Double, bottom: Double): List<PixelRect> =
                CARD_CENTERS_X.map { normalizedCenter ->
                    val center = (normalizedCenter * frameWidth).roundToInt()
                    PixelRect(
                        left = (center - halfWidth).coerceAtLeast(0),
                        top = (top * frameHeight).roundToInt().coerceIn(0, frameHeight - 1),
                        right = (center + halfWidth).coerceAtMost(frameWidth),
                        bottom = (bottom * frameHeight).roundToInt().coerceIn(1, frameHeight)
                    )
                }
            fun portraits(
                cards: List<PixelRect>,
                topInset: Double,
                bottomInset: Double
            ): List<PixelRect> = cards.map { card ->
                val horizontalInset =
                    (card.width * PORTRAIT_HORIZONTAL_INSET).roundToInt()
                PixelRect(
                    left = card.left + horizontalInset,
                    top = card.top + (card.height * topInset).roundToInt(),
                    right = card.right - horizontalInset,
                    bottom = card.bottom - (card.height * bottomInset).roundToInt()
                )
            }
            val allyCards = row(ALLY_TOP, ALLY_BOTTOM)
            val enemyCards = row(ENEMY_TOP, ENEMY_BOTTOM)
            return RankedLoadingRosterGeometry(
                allyCards = allyCards,
                enemyCards = enemyCards,
                allyPortraits = portraits(
                    allyCards,
                    ALLY_PORTRAIT_TOP_INSET,
                    ALLY_PORTRAIT_BOTTOM_INSET
                ),
                enemyPortraits = portraits(
                    enemyCards,
                    ENEMY_PORTRAIT_TOP_INSET,
                    ENEMY_PORTRAIT_BOTTOM_INSET
                )
            )
        }
    }
}

object RankedLoadingSessionStateRouter {
    fun route(
        state: AssistantUiState,
        result: DraftVisionResult,
        configuredPlayerName: String,
        preserved: List<PreservedRosterIdentity> = emptyList()
    ): AssistantUiState {
        if (result.matchMode.effective != MatchMode.RANKED_DRAFT ||
            result.subphase != DraftSubphase.LOADING
        ) {
            return state
        }
        val reconciliation = RankedLoadingRosterAnalyzer().reconcile(
            cards = result.loadingRosterEvidence,
            preserved = preserved,
            configuredPlayerName = configuredPlayerName,
            manualPlayerSlotIndex = state.manualPlayerSlotIndex
        )

        fun mergedSide(side: TeamSide, existing: List<ConfirmedHero>): List<ConfirmedHero> {
            val manual = preserved.asSequence()
                .filter { it.side == side && it.isManual }
                .map { identity ->
                    ConfirmedHero(identity.heroName, side, identity.confidence)
                }
                .toList()
            val automatic = reconciliation.assignments.asSequence()
                .filter { it.side == side }
                .map { assignment ->
                    ConfirmedHero(assignment.heroName, side, assignment.confidence)
                }
                .toList()
            return (manual + automatic + existing)
                .distinctBy { CounterCatalog.normalize(it.heroName) }
        }

        val snapshot = state.snapshot.copy(
            allies = mergedSide(TeamSide.ALLY, state.snapshot.allies),
            enemies = mergedSide(TeamSide.ENEMY, state.snapshot.enemies)
        )
        val playerSlot = reconciliation.playerSlotIndex?.let { slot ->
            PlayerSlotDetection(slot, TeamSide.ALLY, 1.0)
        } ?: state.playerSlot
        return state.copy(
            snapshot = snapshot,
            playerSlot = playerSlot,
            loadingRosterReconciliation = reconciliation
        )
    }
}

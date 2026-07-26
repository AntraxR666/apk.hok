package com.example.honorofkingsassistant

import kotlin.math.roundToInt

data class RankedLoadingRosterGeometry(
    val allyCards: List<PixelRect>,
    val enemyCards: List<PixelRect>
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
    val slotIndex: Int,
    val heroName: String,
    val confidence: Double,
    val isManual: Boolean
)

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
        val preservedBySlot = preserved.associateBy { it.side to it.slotIndex }
        val assignments = linkedMapOf<Pair<TeamSide, Int>, LoadingRosterAssignment>()
        val conflicts = mutableListOf<LoadingRosterConflict>()

        preserved.forEach { identity ->
            assignments[identity.side to identity.slotIndex] = LoadingRosterAssignment(
                side = identity.side,
                slotIndex = identity.slotIndex,
                heroName = identity.heroName,
                confidence = identity.confidence,
                preservedManualEvidence = identity.isManual
            )
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
            return RankedLoadingRosterGeometry(
                allyCards = row(ALLY_TOP, ALLY_BOTTOM),
                enemyCards = row(ENEMY_TOP, ENEMY_BOTTOM)
            )
        }
    }
}

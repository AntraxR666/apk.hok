package com.example.honorofkingsassistant

enum class CandidateSource {
    OCR,
    PORTRAIT
}

data class PositionedHeroCandidate(
    val heroName: String,
    val confidence: Double,
    val centerX: Int,
    val centerY: Int,
    val source: CandidateSource,
    val sideHint: TeamSide = TeamSide.UNKNOWN,
    val rankedSlotStatus: DraftSlotStatus? = null
)

data class SlottedHeroCandidate(
    val heroName: String,
    val confidence: Double,
    val side: TeamSide,
    val slotIndex: Int
)

object NormalPortraitCandidateFactory {
    fun create(
        matches: List<SlotHeroMatch>,
        geometry: NormalSelectionGeometry
    ): List<PositionedHeroCandidate> = matches.mapNotNull { match ->
        if (match.side != TeamSide.ALLY) return@mapNotNull null
        val region = geometry.allyPortraits.getOrNull(match.slotIndex - 1)
            ?: return@mapNotNull null
        PositionedHeroCandidate(
            heroName = match.heroName,
            confidence = match.confidence,
            centerX = (region.left + region.right) / 2,
            centerY = (region.top + region.bottom) / 2,
            source = CandidateSource.PORTRAIT,
            sideHint = TeamSide.ALLY
        )
    }
}

class NormalSelectionLayoutClassifier private constructor(
    private val geometry: NormalSelectionGeometry
) {
    fun classify(candidate: PositionedHeroCandidate): SlottedHeroCandidate? {
        if (candidate.source != CandidateSource.PORTRAIT ||
            candidate.sideHint != TeamSide.ALLY
        ) {
            return null
        }
        val rowIndex = geometry.allyPortraits.indexOfFirst { portrait ->
            candidate.centerX >= portrait.left &&
                candidate.centerX < portrait.right &&
                candidate.centerY >= portrait.top &&
                candidate.centerY < portrait.bottom
        }
        if (rowIndex < 0) return null
        return SlottedHeroCandidate(
            heroName = candidate.heroName,
            confidence = candidate.confidence,
            side = TeamSide.ALLY,
            slotIndex = rowIndex + 1
        )
    }

    companion object {
        fun forFrame(width: Int, height: Int): NormalSelectionLayoutClassifier =
            NormalSelectionLayoutClassifier(NormalSelectionGeometry.forFrame(width, height))
    }
}

object HeroCandidateRouter {
    @Suppress("UNUSED_PARAMETER")
    fun route(
        candidates: List<PositionedHeroCandidate>,
        matchMode: MatchModeState,
        frameWidth: Int,
        frameHeight: Int,
        enemyOnRight: Boolean
    ): List<HeroObservation> {
        val observations = when (matchMode.effective) {
            MatchMode.AUTO -> emptyList()
            MatchMode.NORMAL_BLIND -> {
                val classifier = NormalSelectionLayoutClassifier.forFrame(frameWidth, frameHeight)
                candidates.mapNotNull { candidate ->
                    classifier.classify(candidate)?.let { classified ->
                        HeroObservation(
                            heroName = classified.heroName,
                            side = classified.side,
                            confidence = classified.confidence
                        )
                    }
                }
            }
            MatchMode.RANKED_DRAFT -> {
                candidates.mapNotNull { candidate ->
                    if (candidate.source != CandidateSource.PORTRAIT) return@mapNotNull null
                    if (candidate.sideHint == TeamSide.UNKNOWN) return@mapNotNull null
                    if (candidate.rankedSlotStatus != DraftSlotStatus.PREVIEWING &&
                        candidate.rankedSlotStatus != DraftSlotStatus.CONFIRMED
                    ) {
                        return@mapNotNull null
                    }
                    HeroObservation(
                        candidate.heroName,
                        candidate.sideHint,
                        candidate.confidence
                    )
                }
            }
        }
        val best = linkedMapOf<Pair<String, TeamSide>, HeroObservation>()
        observations.forEach { observation ->
            val key = CounterCatalog.normalize(observation.heroName) to observation.side
            val current = best[key]
            if (current == null || observation.confidence > current.confidence) {
                best[key] = observation
            }
        }
        return best.values.toList()
    }
}

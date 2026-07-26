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
    val sideHint: TeamSide = TeamSide.UNKNOWN
)

data class SlottedHeroCandidate(
    val heroName: String,
    val confidence: Double,
    val side: TeamSide,
    val slotIndex: Int
)

class NormalSelectionLayoutClassifier private constructor(
    private val geometry: NormalSelectionGeometry
) {
    fun classify(candidate: PositionedHeroCandidate): SlottedHeroCandidate? {
        if (candidate.source != CandidateSource.OCR) return null
        val rowIndex = geometry.allyRows.indexOfFirst { row ->
            candidate.centerX >= row.left &&
                candidate.centerX < row.right &&
                candidate.centerY >= row.top &&
                candidate.centerY < row.bottom
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
                val classifier = DraftLayoutClassifier(enemyOnRight)
                candidates.map { candidate ->
                    val side = when (candidate.source) {
                        CandidateSource.OCR -> classifier.classify(candidate.centerX, frameWidth)
                        CandidateSource.PORTRAIT -> candidate.sideHint
                    }
                    HeroObservation(candidate.heroName, side, candidate.confidence)
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

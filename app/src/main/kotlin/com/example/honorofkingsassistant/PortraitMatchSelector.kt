package com.example.honorofkingsassistant

data class PortraitMatchCandidate(
    val heroName: String,
    val distance: Double,
    val visualConfidence: Double
)

object PortraitMatchSelector {
    const val MATCH_THRESHOLD = 0.21
    const val MIN_MATCH_CONFIDENCE = 0.24
    const val MIN_AMBIGUITY_MARGIN = 0.035
    private const val SUGGESTION_DISTANCE = 0.50
    private const val CONFIDENCE_DISTANCE = 0.35
    private const val STRONG_MARGIN = 0.12

    /**
     * Returns the best distinct heroes even when strict automatic acceptance rejects them.
     * These candidates are suggestions for an explicit user correction, never auto-picks.
     */
    fun rank(
        candidates: List<PortraitMatchCandidate>,
        limit: Int = 3
    ): List<PortraitHeroMatch> {
        require(limit >= 1)
        return rankedCandidates(candidates)
            .take(limit)
            .map { candidate ->
                PortraitHeroMatch(
                    heroName = candidate.heroName,
                    distance = candidate.distance,
                    confidence = (
                        (1.0 - candidate.distance / SUGGESTION_DISTANCE).coerceIn(0.0, 1.0) *
                            candidate.visualConfidence.coerceIn(0.0, 1.0)
                        ).coerceIn(0.0, 1.0)
                )
            }
    }

    fun select(
        candidates: List<PortraitMatchCandidate>,
        matchThreshold: Double = MATCH_THRESHOLD,
        minimumConfidence: Double = MIN_MATCH_CONFIDENCE,
        minimumMargin: Double = MIN_AMBIGUITY_MARGIN
    ): PortraitHeroMatch? {
        require(matchThreshold > 0.0)
        require(minimumConfidence in 0.0..1.0)
        require(minimumMargin >= 0.0)

        val ranked = rankedCandidates(candidates)

        val best = ranked.firstOrNull() ?: return null
        if (best.distance > matchThreshold) return null

        val second = ranked.getOrNull(1)
        if (second != null && second.distance - best.distance < minimumMargin) return null

        val distanceEvidence =
            (1.0 - best.distance / CONFIDENCE_DISTANCE).coerceIn(0.0, 1.0)
        val marginEvidence = if (second == null) {
            1.0
        } else {
            ((second.distance - best.distance) / STRONG_MARGIN).coerceIn(0.0, 1.0)
        }
        val confidence = (
            (distanceEvidence * 0.70 + marginEvidence * 0.30) *
                best.visualConfidence.coerceIn(0.0, 1.0)
        ).coerceIn(0.0, 1.0)
        if (confidence < minimumConfidence) return null

        return PortraitHeroMatch(
            heroName = best.heroName,
            distance = best.distance,
            confidence = confidence
        )
    }

    private fun rankedCandidates(
        candidates: List<PortraitMatchCandidate>
    ): List<PortraitMatchCandidate> = candidates
        .filter { it.heroName.isNotBlank() && it.distance >= 0.0 }
        .groupBy { CounterCatalog.normalize(it.heroName) }
        .values
        .mapNotNull { group -> group.minByOrNull { it.distance } }
        .sortedWith(
            compareBy<PortraitMatchCandidate> { it.distance }
                .thenBy { CounterCatalog.normalize(it.heroName) }
        )
}

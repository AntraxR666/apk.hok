package com.example.honorofkingsassistant

import java.util.ArrayDeque

class TemporalDraftTracker(
    private val requiredHits: Int = 2,
    private val historySize: Int = 4
) {
    private val history = ArrayDeque<List<HeroObservation>>()

    init {
        require(requiredHits >= 1)
        require(historySize >= requiredHits)
    }

    @Synchronized
    fun observe(frame: List<HeroObservation>): DraftSnapshot {
        history.addLast(frame.distinctBy { it.heroName to it.side })
        while (history.size > historySize) history.removeFirst()

        val confirmed = history.flatten()
            .groupBy { CounterCatalog.normalize(it.heroName) }
            .mapNotNull { (_, heroObservations) ->
                val bestSideGroup = heroObservations
                    .groupBy { it.side }
                    .values
                    .maxWithOrNull(
                        compareBy<List<HeroObservation>> { it.size }
                            .thenBy { group -> group.map { it.confidence }.average() }
                    ) ?: return@mapNotNull null
                if (bestSideGroup.size < requiredHits) return@mapNotNull null
                ConfirmedHero(
                    heroName = bestSideGroup.first().heroName,
                    side = bestSideGroup.first().side,
                    confidence = bestSideGroup.map { it.confidence }.average().coerceIn(0.0, 1.0)
                )
            }
            .sortedBy { it.heroName }

        return DraftSnapshot(
            allies = confirmed.filter { it.side == TeamSide.ALLY },
            enemies = confirmed.filter { it.side == TeamSide.ENEMY },
            unknown = confirmed.filter { it.side == TeamSide.UNKNOWN }
        )
    }

    @Synchronized
    fun reset() {
        history.clear()
    }
}

package com.example.honorofkingsassistant

/**
 * Builds the recommendation snapshot from exact loading-screen slots.
 *
 * A player correction owns its exact slot. The displaced automatic identity is removed, and
 * an automatic copy of the corrected hero in another slot on the same side is suppressed.
 * Recomputing from the immutable loading result means removing a correction restores the
 * original automatic reading.
 */
object LoadingRosterSnapshotPolicy {
    fun from(
        loadingRoster: LoadingRosterReconciliationResult,
        manualAssignments: ManualTeamAssignments
    ): DraftSnapshot {
        fun side(side: TeamSide): List<ConfirmedHero> {
            val manualClaims = (1..5)
                .mapNotNull { slotIndex ->
                    manualAssignments.heroAt(side, slotIndex)?.let { heroName ->
                        CounterCatalog.normalize(heroName)
                    }
                }
                .toSet()
            val automaticBySlot = loadingRoster.assignments
                .filter { it.side == side }
                .associateBy(LoadingRosterAssignment::slotIndex)

            return (1..5).mapNotNull { slotIndex ->
                val manualHero = manualAssignments.heroAt(side, slotIndex)
                if (manualHero != null) {
                    ConfirmedHero(manualHero, side, 1.0)
                } else {
                    automaticBySlot[slotIndex]
                        ?.takeUnless {
                            CounterCatalog.normalize(it.heroName) in manualClaims
                        }
                        ?.let {
                            ConfirmedHero(
                                heroName = it.heroName,
                                side = side,
                                confidence = it.confidence.coerceIn(0.0, 1.0)
                            )
                        }
                }
            }
        }

        return DraftSnapshot(
            allies = side(TeamSide.ALLY),
            enemies = side(TeamSide.ENEMY),
            unknown = emptyList()
        )
    }
}

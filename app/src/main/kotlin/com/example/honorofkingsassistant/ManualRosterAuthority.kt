package com.example.honorofkingsassistant

object ManualRosterAuthority {
    fun preservedIdentities(
        manualAllies: Set<String>,
        manualEnemies: Set<String>,
        previous: LoadingRosterReconciliationResult?
    ): List<PreservedRosterIdentity> {
        val previousByHero = previous
            ?.assignments
            .orEmpty()
            .associateBy { assignment ->
                assignment.side to CounterCatalog.normalize(assignment.heroName)
            }

        fun identities(side: TeamSide, heroNames: Set<String>) =
            heroNames.mapNotNull { heroName ->
                heroName.trim().takeIf(String::isNotBlank)?.let { trimmed ->
                    PreservedRosterIdentity(
                        side = side,
                        slotIndex = previousByHero[
                            side to CounterCatalog.normalize(trimmed)
                        ]?.slotIndex,
                        heroName = trimmed,
                        confidence = 1.0,
                        isManual = true
                    )
                }
            }

        return identities(TeamSide.ALLY, manualAllies) +
            identities(TeamSide.ENEMY, manualEnemies)
    }
}

object ManualDraftSnapshotMerger {
    fun merge(
        snapshot: DraftSnapshot,
        manualAllies: Set<String>,
        manualEnemies: Set<String>
    ): DraftSnapshot {
        fun mergedSide(
            side: TeamSide,
            manualHeroes: Set<String>,
            automaticHeroes: List<ConfirmedHero>
        ): List<ConfirmedHero> =
            (manualHeroes.map { ConfirmedHero(it, side, 1.0) } + automaticHeroes)
                .distinctBy { CounterCatalog.normalize(it.heroName) }

        return snapshot.copy(
            allies = mergedSide(TeamSide.ALLY, manualAllies, snapshot.allies),
            enemies = mergedSide(TeamSide.ENEMY, manualEnemies, snapshot.enemies)
        )
    }
}

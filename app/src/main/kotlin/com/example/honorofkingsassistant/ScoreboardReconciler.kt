package com.example.honorofkingsassistant

class ScoreboardReconciler(private val catalog: CounterCatalog) {
    fun reconcile(
        draft: DraftSnapshot,
        scoreboard: ScoreboardSnapshot,
        manualOverrides: ManualTeamAssignments = ManualTeamAssignments()
    ): ScoreboardReconciliationResult {
        val corrections = mutableListOf<ScoreboardCorrection>()
        val conflicts = mutableListOf<ScoreboardConflict>()

        fun reconcileSide(side: TeamSide, rows: List<ScoreboardRow>, current: List<ConfirmedHero>): List<ConfirmedHero> =
            rows.sortedBy { it.slotIndex }.map { row ->
                val existing = current.getOrNull(row.slotIndex - 1)
                val manual = manualOverrides.heroAt(side, row.slotIndex)
                if (manual != null) return@map ConfirmedHero(manual, side, 1.0)

                val title = row.titleHeroName?.let(catalog::findHero)?.name
                val portrait = row.portraitHeroName?.let(catalog::findHero)?.name
                if (title != null && portrait != null && normalized(title) != normalized(portrait)) {
                    conflicts += ScoreboardConflict(side, row.slotIndex, title, portrait, existing?.heroName,
                        "El título y el retrato del marcador no coinciden")
                    return@map existing ?: ConfirmedHero("Desconocido", side, 0.0)
                }

                val observed = title ?: portrait
                val confidence = when {
                    title != null && portrait != null -> 0.96
                    title != null -> 0.90
                    portrait != null -> 0.86
                    else -> 0.0
                }
                if (observed == null) return@map existing ?: ConfirmedHero("Desconocido", side, 0.0)
                if (existing != null && normalized(existing.heroName) != normalized(observed) && existing.confidence >= 0.85) {
                    conflicts += ScoreboardConflict(side, row.slotIndex, title, portrait, existing.heroName,
                        "El marcador contradice una identidad de draft ya estable")
                    return@map existing
                }
                if (existing == null || normalized(existing.heroName) != normalized(observed)) {
                    corrections += ScoreboardCorrection(
                        side, row.slotIndex, observed,
                        if (title != null && portrait != null) "retrato + título del marcador" else "evidencia fuerte del marcador",
                        confidence
                    )
                }
                ConfirmedHero(observed, side, maxOf(existing?.confidence ?: 0.0, confidence))
            }

        return ScoreboardReconciliationResult(
            snapshot = DraftSnapshot(
                allies = reconcileSide(TeamSide.ALLY, scoreboard.allies, draft.allies),
                enemies = reconcileSide(TeamSide.ENEMY, scoreboard.enemies, draft.enemies),
                unknown = draft.unknown
            ),
            appliedCorrections = corrections,
            pendingConflicts = conflicts
        )
    }

    private fun normalized(value: String): String = CounterCatalog.normalize(value)
}

package com.example.honorofkingsassistant

/**
 * Sole automatic identity boundary for ranked selection.
 *
 * Per-frame portrait matches must never reach recommendations directly. Only an exact slot
 * that completed the bounded temporal consensus may become a confirmed draft hero.
 */
object ConfirmedSlotSnapshotPolicy {
    fun from(
        states: List<SlotRecognitionState>,
        manualAssignments: ManualTeamAssignments
    ): DraftSnapshot = from(
        QuickCorrectionPolicy.applyManualAuthority(states, manualAssignments)
    )

    fun from(states: List<SlotRecognitionState>): DraftSnapshot {
        val confirmed = states
            .asSequence()
            .filter {
                (it.status == SlotRecognitionStatus.DETECTED ||
                    it.status == SlotRecognitionStatus.MANUAL) &&
                    !it.heroName.isNullOrBlank()
            }
            .filter { it.side == TeamSide.ALLY || it.side == TeamSide.ENEMY }
            .sortedWith(
                compareBy<SlotRecognitionState> { if (it.side == TeamSide.ALLY) 0 else 1 }
                    .thenBy(SlotRecognitionState::slotIndex)
            )
            .map {
                ConfirmedHero(
                    heroName = requireNotNull(it.heroName),
                    side = it.side,
                    confidence = it.confidence.coerceIn(0.0, 1.0)
                )
            }
            .toList()

        return DraftSnapshot(
            allies = confirmed.filter { it.side == TeamSide.ALLY },
            enemies = confirmed.filter { it.side == TeamSide.ENEMY },
            unknown = emptyList()
        )
    }
}

package com.example.honorofkingsassistant

data class QuickCorrectionRequest(
    val slot: ManualTeamSlot,
    val status: SlotRecognitionStatus,
    val candidates: List<SlotRecognitionCandidate>
)

object QuickCorrectionPolicy {
    fun applyManualAuthority(
        states: List<SlotRecognitionState>,
        manualAssignments: ManualTeamAssignments
    ): List<SlotRecognitionState> = states.map { state ->
        val manualHero = manualAssignments.heroAt(state.side, state.slotIndex)
        if (manualHero == null) {
            state
        } else {
            state.copy(
                status = SlotRecognitionStatus.MANUAL,
                heroName = manualHero,
                confidence = 1.0
            )
        }
    }

    /**
     * Automatic background scans never open the panel. Only a scan explicitly requested by
     * the player can surface a contextual correction, and only after the tracker has reached
     * a definitive uncertain/not-detected state.
     */
    fun correctionRequest(
        explicitScanRequested: Boolean,
        states: List<SlotRecognitionState>
    ): QuickCorrectionRequest? {
        if (!explicitScanRequested) return null
        return states
            .filter(::isActionable)
            .sortedWith(
                compareBy<SlotRecognitionState> { if (it.side == TeamSide.ALLY) 0 else 1 }
                    .thenBy(SlotRecognitionState::slotIndex)
            )
            .firstOrNull()
            ?.let { state ->
                QuickCorrectionRequest(
                    slot = ManualTeamSlot(state.side, state.slotIndex),
                    status = state.status,
                    candidates = state.candidates
                )
            }
    }

    fun hasActionableProblem(states: List<SlotRecognitionState>): Boolean =
        states.any(::isActionable)

    private fun isActionable(state: SlotRecognitionState): Boolean =
        state.status == SlotRecognitionStatus.UNCERTAIN ||
            state.status == SlotRecognitionStatus.NOT_DETECTED
}

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
    ): List<SlotRecognitionState> {
        val manualClaims = states.mapNotNull { state ->
            manualAssignments.heroAt(state.side, state.slotIndex)?.let { heroName ->
                state.side to CounterCatalog.normalize(heroName)
            }
        }.toSet()
        return states.map { state ->
            val manualHero = manualAssignments.heroAt(state.side, state.slotIndex)
            when {
                manualHero != null -> state.copy(
                    status = SlotRecognitionStatus.MANUAL,
                    heroName = manualHero,
                    confidence = 1.0
                )
                state.heroName != null &&
                    (state.side to CounterCatalog.normalize(state.heroName)) in manualClaims ->
                    state.copy(
                        status = SlotRecognitionStatus.NOT_DETECTED,
                        heroName = null,
                        confidence = 0.0
                    )
                else -> state
            }
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
        val claimedHeroes = states
            .filter {
                (it.status == SlotRecognitionStatus.MANUAL ||
                    it.status == SlotRecognitionStatus.DETECTED) &&
                    !it.heroName.isNullOrBlank()
            }
            .groupBy(SlotRecognitionState::side)
            .mapValues { (_, sideStates) ->
                sideStates.mapNotNull(SlotRecognitionState::heroName)
                    .map(CounterCatalog::normalize)
                    .toSet()
            }
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
                    candidates = state.candidates.filterNot {
                        CounterCatalog.normalize(it.heroName) in
                            claimedHeroes[state.side].orEmpty()
                    }
                )
            }
    }

    fun hasActionableProblem(states: List<SlotRecognitionState>): Boolean =
        states.any(::isActionable)

    fun afterManualAssignment(
        activeRequest: QuickCorrectionRequest?,
        assignedSlot: ManualTeamSlot,
        states: List<SlotRecognitionState>,
        manualAssignments: ManualTeamAssignments
    ): QuickCorrectionRequest? {
        if (activeRequest?.slot != assignedSlot) return activeRequest
        return correctionRequest(
            explicitScanRequested = true,
            states = applyManualAuthority(states, manualAssignments)
        )
    }

    private fun isActionable(state: SlotRecognitionState): Boolean =
        state.status == SlotRecognitionStatus.UNCERTAIN ||
            state.status == SlotRecognitionStatus.NOT_DETECTED
}

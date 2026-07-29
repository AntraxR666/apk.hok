package com.example.honorofkingsassistant

/**
 * Resolves the player's hero without treating an incomplete roster as a positional array.
 */
object PlayerHeroIdentityPolicy {
    fun resolve(
        allySlotIndex: Int?,
        manualAssignments: ManualTeamAssignments,
        slotRecognition: List<SlotRecognitionState>,
        loadingRoster: LoadingRosterReconciliationResult?,
        snapshot: DraftSnapshot
    ): String? {
        val slot = allySlotIndex?.takeIf { it in 1..5 } ?: return null

        manualAssignments.heroAt(TeamSide.ALLY, slot)?.let { return it }

        loadingRoster?.assignments?.firstOrNull {
            it.side == TeamSide.ALLY && it.slotIndex == slot
        }?.heroName?.let { return it }

        slotRecognition.firstOrNull {
            it.side == TeamSide.ALLY &&
                it.slotIndex == slot &&
                (it.status == SlotRecognitionStatus.DETECTED ||
                    it.status == SlotRecognitionStatus.MANUAL) &&
                !it.heroName.isNullOrBlank()
        }?.heroName?.let { return it }

        return snapshot.allies
            .takeIf { it.size == 5 }
            ?.getOrNull(slot - 1)
            ?.heroName
    }
}

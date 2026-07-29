package com.example.honorofkingsassistant

object RosterSlotIdentityPolicy {
    fun resolve(
        slot: ManualTeamSlot,
        loadingRoster: LoadingRosterReconciliationResult?,
        manualAssignments: ManualTeamAssignments,
        slotRecognition: List<SlotRecognitionState>,
        snapshot: DraftSnapshot
    ): String? {
        manualAssignments.heroAt(slot.side, slot.slotIndex)?.let { return it }

        loadingRoster?.assignments?.firstOrNull {
            it.side == slot.side && it.slotIndex == slot.slotIndex
        }?.heroName?.let { return it }

        slotRecognition.firstOrNull {
            it.side == slot.side &&
                it.slotIndex == slot.slotIndex &&
                (it.status == SlotRecognitionStatus.DETECTED ||
                    it.status == SlotRecognitionStatus.MANUAL)
        }?.heroName?.takeIf(String::isNotBlank)?.let { return it }

        val sideSnapshot = if (slot.side == TeamSide.ALLY) {
            snapshot.allies
        } else {
            snapshot.enemies
        }
        return sideSnapshot
            .takeIf { it.size == 5 }
            ?.getOrNull(slot.slotIndex - 1)
            ?.heroName
    }
}

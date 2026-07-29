package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RosterSlotIdentityPolicyTest {
    @Test
    fun `incomplete compact snapshot never shifts ally three into ally one`() {
        val recognition = listOf(
            state(TeamSide.ALLY, 3, SlotRecognitionStatus.DETECTED, "Zhang Fei")
        )
        val compactSnapshot = DraftSnapshot(
            allies = listOf(ConfirmedHero("Zhang Fei", TeamSide.ALLY, 0.82)),
            enemies = emptyList(),
            unknown = emptyList()
        )

        assertNull(
            RosterSlotIdentityPolicy.resolve(
                slot = ManualTeamSlot(TeamSide.ALLY, 1),
                loadingRoster = null,
                manualAssignments = ManualTeamAssignments(),
                slotRecognition = recognition,
                snapshot = compactSnapshot
            )
        )
        assertEquals(
            "Zhang Fei",
            RosterSlotIdentityPolicy.resolve(
                slot = ManualTeamSlot(TeamSide.ALLY, 3),
                loadingRoster = null,
                manualAssignments = ManualTeamAssignments(),
                slotRecognition = recognition,
                snapshot = compactSnapshot
            )
        )
    }

    @Test
    fun `manual identity overrides loading and automatic identity at exact slot`() {
        val slot = ManualTeamSlot(TeamSide.ALLY, 3)
        val loading = LoadingRosterReconciliationResult(
            assignments = listOf(
                LoadingRosterAssignment(TeamSide.ALLY, 3, "Lam", 0.80, false)
            ),
            conflicts = emptyList(),
            playerSlotIndex = null
        )

        assertEquals(
            "Angela",
            RosterSlotIdentityPolicy.resolve(
                slot = slot,
                loadingRoster = loading,
                manualAssignments = ManualTeamAssignments()
                    .assign(TeamSide.ALLY, 3, "Angela"),
                slotRecognition = listOf(
                    state(TeamSide.ALLY, 3, SlotRecognitionStatus.DETECTED, "Yao")
                ),
                snapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
            )
        )
    }

    private fun state(
        side: TeamSide,
        slotIndex: Int,
        status: SlotRecognitionStatus,
        heroName: String
    ) = SlotRecognitionState(
        side = side,
        slotIndex = slotIndex,
        status = status,
        heroName = heroName,
        confidence = 0.82,
        sampleCount = 3
    )
}

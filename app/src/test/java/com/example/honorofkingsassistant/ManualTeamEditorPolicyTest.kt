package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualTeamEditorPolicyTest {
    @Test
    fun normalSelectionShowsOnlyFiveAllySlots() {
        val slots = ManualTeamEditorPolicy.slots(MatchMode.NORMAL_BLIND)

        assertEquals(5, slots.size)
        assertTrue(slots.all { it.side == TeamSide.ALLY })
        assertEquals(1, slots.first().slotIndex)
        assertEquals(5, slots.last().slotIndex)
    }

    @Test
    fun rankedSelectionShowsFiveSlotsPerTeam() {
        val slots = ManualTeamEditorPolicy.slots(MatchMode.RANKED_DRAFT)

        assertEquals(10, slots.size)
        assertEquals(5, slots.count { it.side == TeamSide.ALLY })
        assertEquals(5, slots.count { it.side == TeamSide.ENEMY })
    }

    @Test
    fun autoModeDoesNotGuessEnemyEditorShape() {
        val slots = ManualTeamEditorPolicy.slots(MatchMode.AUTO)

        assertEquals(5, slots.size)
        assertFalse(slots.any { it.side == TeamSide.ENEMY })
    }

    @Test
    fun manualAssignmentReplacesOnlyRequestedSlotAtFullConfidence() {
        val state = ManualTeamAssignments()
            .assign(TeamSide.ALLY, 1, "Angela")
            .assign(TeamSide.ALLY, 1, "Dr Bian")
            .assign(TeamSide.ENEMY, 2, "Bai Qi")

        assertEquals("Dr Bian", state.heroAt(TeamSide.ALLY, 1))
        assertEquals("Bai Qi", state.heroAt(TeamSide.ENEMY, 2))
        assertEquals(1.0, state.confirmedHeroes(TeamSide.ALLY).single().confidence, 0.0)
    }

    @Test
    fun removeClearsOnlyRequestedSlot() {
        val state = ManualTeamAssignments()
            .assign(TeamSide.ALLY, 1, "Angela")
            .assign(TeamSide.ALLY, 2, "Bai Qi")
            .remove(TeamSide.ALLY, 1)

        assertEquals(null, state.heroAt(TeamSide.ALLY, 1))
        assertEquals("Bai Qi", state.heroAt(TeamSide.ALLY, 2))
    }
}

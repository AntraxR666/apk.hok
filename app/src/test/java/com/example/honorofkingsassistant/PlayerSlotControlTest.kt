package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSlotControlTest {
    @Test
    fun manualSlotAlwaysWins() {
        val resolver = PlayerSlotResolver(requiredHits = 4, maxMisses = 4)
        val result = resolver.resolve(
            PlayerSlotDetection(4, TeamSide.ALLY, 1.0),
            manualSlotIndex = 2
        )
        assertEquals(PlayerSlotDetection(2, TeamSide.ALLY, 1.0), result)
    }

    @Test
    fun automaticSlotRequiresFourStableAllyDetections() {
        val resolver = PlayerSlotResolver(requiredHits = 4, maxMisses = 4)
        assertNull(resolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), null))
        assertNull(resolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), null))
        assertNull(resolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), null))
        assertEquals(4, resolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), null)?.slotIndex)
    }

    @Test
    fun pendingOverrideKeepsRecommendationsVisible() {
        val automatic = DraftFlowState(
            DraftMoment.PLAYER_LOCKED,
            shouldRecommendPicks = false,
            shouldShowStrategy = true,
            message = "auto"
        )
        val result = PlayerPickStatePolicy.apply(automatic, PlayerPickOverride.PENDING)
        assertTrue(result.shouldRecommendPicks)
        assertFalse(PlayerPickStatePolicy.isLocked(true, PlayerPickOverride.PENDING))
    }
}

package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionConfidenceTest {
    @Test
    fun ambiguousPortraitIsRejected() {
        val result = PortraitMatchSelector.select(
            listOf(
                PortraitMatchCandidate("Lam", 0.10, 0.90),
                PortraitMatchCandidate("Luna", 0.115, 0.90)
            )
        )
        assertNull(result)
    }

    @Test
    fun clearPortraitWinnerIsAccepted() {
        val result = PortraitMatchSelector.select(
            listOf(
                PortraitMatchCandidate("Lam", 0.10, 0.90),
                PortraitMatchCandidate("Luna", 0.16, 0.90)
            )
        )
        assertEquals("Lam", result?.heroName)
        assertTrue(requireNotNull(result).confidence >= PortraitMatchSelector.MIN_MATCH_CONFIDENCE)
    }

    @Test
    fun temporalTrackerIgnoresLowConfidenceNoise() {
        val tracker = TemporalDraftTracker(
            requiredHits = 3,
            historySize = 5,
            minimumObservationConfidence = 0.55
        )
        repeat(3) {
            tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.40)))
        }
        assertTrue(tracker.observe(emptyList()).enemies.isEmpty())

        repeat(2) {
            assertTrue(
                tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.90))).enemies.isEmpty()
            )
        }
        val confirmed = tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.92)))
        assertEquals(listOf("Lam"), confirmed.enemies.map { it.heroName })
    }
}

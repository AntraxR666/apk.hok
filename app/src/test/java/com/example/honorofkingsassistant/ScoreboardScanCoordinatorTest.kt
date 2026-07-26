package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardScanCoordinatorTest {
    @Test fun explicitScanClaimsExactlyOneEligibleFrame() {
        val coordinator = ScoreboardScanCoordinator()
        coordinator.arm()
        assertTrue(coordinator.shouldCapture())
        assertFalse(coordinator.claimFrame(isEligible = false))
        assertTrue(coordinator.claimFrame(isEligible = true))
        assertFalse(coordinator.claimFrame(isEligible = true))
        coordinator.completeReview()
        assertEquals(ScoreboardScanState.REVIEW, coordinator.state)
    }
}

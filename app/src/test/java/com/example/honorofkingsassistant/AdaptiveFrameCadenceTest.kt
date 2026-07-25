package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveFrameCadenceTest {
    @Test
    fun usesFastBaseCadenceWhenVisionIsHealthy() {
        assertEquals(700L, AdaptiveFrameCadence.interval(700L, 0L))
        assertEquals(700L, AdaptiveFrameCadence.interval(700L, 300L))
    }

    @Test
    fun backsOffWhenKirinVisionLatencyIncreases() {
        assertEquals(1_200L, AdaptiveFrameCadence.interval(700L, 800L))
        assertEquals(1_600L, AdaptiveFrameCadence.interval(700L, 1_500L))
    }

    @Test
    fun preservesDisabledStageSentinel() {
        assertEquals(Long.MAX_VALUE, AdaptiveFrameCadence.interval(Long.MAX_VALUE, 500L))
    }
}

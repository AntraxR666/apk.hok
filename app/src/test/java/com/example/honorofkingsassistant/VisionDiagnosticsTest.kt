package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class VisionDiagnosticsTest {
    @Test
    fun tracksDropsAndSmoothedLatency() {
        val tracker = VisionDiagnosticsTracker()
        tracker.onDroppedFrame()
        val first = tracker.onProcessedFrame(100, 1600, 738, 1280, 590)
        val second = tracker.onProcessedFrame(200, 1170, 540, 1170, 540)

        assertEquals(1L, first.droppedFrames)
        assertEquals(2L, second.processedFrames)
        assertEquals(125L, second.averageLatencyMs)
        assertEquals("1170x540", second.captureLabel)
        assertEquals("1170x540", second.ocrLabel)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidSmoothingFactor() {
        VisionDiagnosticsTracker(0.0)
    }
}

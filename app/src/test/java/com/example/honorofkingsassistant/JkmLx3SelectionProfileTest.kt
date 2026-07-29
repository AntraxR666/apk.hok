package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JkmLx3SelectionProfileTest {
    @Test
    fun normalizedGeometryScalesFromNativeHuaweiToCaptureAndFixture() {
        val region = JkmLx3SelectionProfile.portraitInteriors(physicalLeft = true).first()
        val native = region.toPixelRect(2340, 1080)
        val capture = region.toPixelRect(1170, 540)
        val fixture = region.toPixelRect(848, 392)

        assertTrue(kotlin.math.abs(native.left / 2 - capture.left) <= 1)
        assertTrue(kotlin.math.abs(native.top / 2 - capture.top) <= 1)
        assertEquals(region.left, fixture.left / 848.0, 0.002)
        assertEquals(region.top, fixture.top / 392.0, 0.003)
    }

    @Test
    fun portraitInteriorExcludesFrameAndLowerLockMarker() {
        val slot = JkmLx3SelectionProfile.slotBounds(physicalLeft = true).first()
        val portrait = JkmLx3SelectionProfile.portraitInteriors(physicalLeft = true).first()

        assertTrue(portrait.left > slot.left)
        assertTrue(portrait.top > slot.top)
        assertTrue(portrait.right < slot.right)
        assertTrue(portrait.bottom < slot.bottom)
        assertTrue(slot.bottom - portrait.bottom >= 0.015)
    }

    @Test
    fun bothSidesExposeExactlyFiveValidPortraitInteriors() {
        listOf(true, false).forEach { physicalLeft ->
            val regions = JkmLx3SelectionProfile.portraitInteriors(physicalLeft)
            assertEquals(5, regions.size)
            regions.forEach { region ->
                val rect = region.toPixelRect(1170, 540)
                assertTrue(rect.width >= 40)
                assertTrue(rect.height >= 40)
            }
        }
    }
}

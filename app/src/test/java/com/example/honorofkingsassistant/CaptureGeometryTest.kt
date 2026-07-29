package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGeometryTest {
    @Test
    fun halvesHuaweiNativeLandscapeWithoutDistortion() {
        assertEquals(CaptureSize(1170, 540), CaptureGeometry.fit(2340, 1080))
    }

    @Test
    fun swapsCaptureOrientationWhenHuaweiRotatesFromPortraitToLandscape() {
        assertEquals(CaptureSize(540, 1170), CaptureGeometry.fit(1080, 2340))
        assertEquals(CaptureSize(1170, 540), CaptureGeometry.fit(2340, 1080))
    }

    @Test
    fun doesNotUpscaleInputsSmallerThanTheHuaweiCaptureProfile() {
        assertEquals(CaptureSize(848, 392), CaptureGeometry.fit(848, 392))
    }

    @Test
    fun respectsHuaweiPixelBudget() {
        val result = CaptureGeometry.fit(2400, 1080)
        assertTrue(result.longEdge <= PersonalCaptureProfile.MAX_LONG_EDGE)
        assertTrue(result.pixelCount <= PersonalCaptureProfile.MAX_PIXELS)
        assertTrue(kotlin.math.abs(result.aspectRatio - 2400.0 / 1080.0) < 0.01)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInvalidDimensions() {
        CaptureGeometry.fit(0, 1080)
    }
}

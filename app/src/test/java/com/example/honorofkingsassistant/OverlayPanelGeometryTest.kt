package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayPanelGeometryTest {
    @Test
    fun `scroll height keeps complete overlay within seventy two percent`() {
        val availableHeightPx = 1017
        val density = 2.625f

        val scrollHeight = OverlayPanelGeometry.maxScrollHeight(
            availableHeightPx = availableHeightPx,
            density = density
        )

        val chromeHeightPx = 157
        val totalHeightLimitPx = 732
        assertEquals(575, scrollHeight)
        assertTrue(scrollHeight + chromeHeightPx <= totalHeightLimitPx)
    }
}

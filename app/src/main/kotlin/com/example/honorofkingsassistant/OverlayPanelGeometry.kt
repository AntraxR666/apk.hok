package com.example.honorofkingsassistant

import kotlin.math.roundToInt

object OverlayPanelGeometry {
    fun maxScrollHeight(
        availableHeightPx: Int,
        density: Float,
        totalHeightRatio: Float = 0.72f,
        chromeHeightDp: Int = 60
    ): Int {
        require(availableHeightPx > 0)
        require(density > 0f)
        require(totalHeightRatio in 0f..1f)
        require(chromeHeightDp >= 0)

        val totalHeightLimitPx = (availableHeightPx * totalHeightRatio).roundToInt()
        val chromeHeightPx = (chromeHeightDp * density).toInt()
        return (totalHeightLimitPx - chromeHeightPx).coerceAtLeast(1)
    }
}

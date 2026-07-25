package com.example.honorofkingsassistant

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class CaptureSize(
    val width: Int,
    val height: Int
) {
    init {
        require(width > 0)
        require(height > 0)
    }

    val pixelCount: Int get() = width * height
    val longEdge: Int get() = max(width, height)
    val aspectRatio: Double get() = width.toDouble() / height.toDouble()
}

object PersonalCaptureProfile {
    const val MAX_LONG_EDGE = PersonalDeviceProfile.CAPTURE_MAX_LONG_EDGE
    const val MAX_PIXELS = PersonalDeviceProfile.CAPTURE_MAX_PIXELS
    const val OCR_MAX_LONG_EDGE = PersonalDeviceProfile.OCR_MAX_LONG_EDGE
}

object CaptureGeometry {
    fun fit(
        sourceWidth: Int,
        sourceHeight: Int,
        maxLongEdge: Int = PersonalCaptureProfile.MAX_LONG_EDGE,
        maxPixels: Int = PersonalCaptureProfile.MAX_PIXELS
    ): CaptureSize {
        require(sourceWidth > 0) { "sourceWidth must be positive" }
        require(sourceHeight > 0) { "sourceHeight must be positive" }
        require(maxLongEdge > 0) { "maxLongEdge must be positive" }
        require(maxPixels > 0) { "maxPixels must be positive" }

        val sourceLongEdge = max(sourceWidth, sourceHeight)
        val sourcePixels = sourceWidth.toDouble() * sourceHeight.toDouble()
        val longEdgeScale = maxLongEdge.toDouble() / sourceLongEdge.toDouble()
        val pixelScale = sqrt(maxPixels.toDouble() / sourcePixels)
        val scale = min(1.0, min(longEdgeScale, pixelScale))

        val width = floor(sourceWidth * scale).toInt().coerceAtLeast(1)
        val height = floor(sourceHeight * scale).toInt().coerceAtLeast(1)
        return CaptureSize(width, height)
    }
}

package com.example.honorofkingsassistant

import android.graphics.Bitmap

data class PreparedOcrBitmap(
    val bitmap: Bitmap,
    private val ownsBitmap: Boolean
) {
    fun release() {
        if (ownsBitmap && !bitmap.isRecycled) bitmap.recycle()
    }
}

object OcrBitmapPreprocessor {
    fun prepare(
        source: Bitmap,
        maxLongEdge: Int = PersonalCaptureProfile.OCR_MAX_LONG_EDGE
    ): PreparedOcrBitmap {
        require(maxLongEdge > 0)
        val target = CaptureGeometry.fit(
            sourceWidth = source.width,
            sourceHeight = source.height,
            maxLongEdge = maxLongEdge,
            maxPixels = Int.MAX_VALUE
        )
        if (target.width == source.width && target.height == source.height) {
            return PreparedOcrBitmap(source, ownsBitmap = false)
        }
        return PreparedOcrBitmap(
            Bitmap.createScaledBitmap(source, target.width, target.height, true),
            ownsBitmap = true
        )
    }
}

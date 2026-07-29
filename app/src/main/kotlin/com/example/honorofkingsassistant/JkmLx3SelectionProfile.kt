package com.example.honorofkingsassistant

/**
 * Native Huawei JKM-LX3 ranked-selection geometry.
 *
 * Values originate in the 2340x1080 landscape coordinate space and are exposed as normalized
 * rectangles so the internal 1170x540 capture and lower-resolution regression fixtures retain
 * the same regions.
 */
object JkmLx3SelectionProfile {
    private const val NATIVE_WIDTH = 2340.0
    private const val NATIVE_HEIGHT = 1080.0

    private val slotTops = listOf(117, 291, 465, 639, 813)
    private const val SLOT_HEIGHT = 129
    private const val LEFT_SLOT_LEFT = 147
    private const val LEFT_SLOT_RIGHT = 283
    private const val RIGHT_SLOT_LEFT = 2078
    private const val RIGHT_SLOT_RIGHT = 2212

    private const val INTERIOR_HORIZONTAL_INSET = 12
    private const val INTERIOR_TOP_INSET = 8
    private const val INTERIOR_BOTTOM_INSET = 18

    fun slotBounds(physicalLeft: Boolean): List<NormalizedRect> {
        val left = if (physicalLeft) LEFT_SLOT_LEFT else RIGHT_SLOT_LEFT
        val right = if (physicalLeft) LEFT_SLOT_RIGHT else RIGHT_SLOT_RIGHT
        return slotTops.map { top ->
            nativeRect(left, top, right, top + SLOT_HEIGHT)
        }
    }

    fun portraitInteriors(physicalLeft: Boolean): List<NormalizedRect> {
        val left = if (physicalLeft) LEFT_SLOT_LEFT else RIGHT_SLOT_LEFT
        val right = if (physicalLeft) LEFT_SLOT_RIGHT else RIGHT_SLOT_RIGHT
        return slotTops.map { top ->
            nativeRect(
                left = left + INTERIOR_HORIZONTAL_INSET,
                top = top + INTERIOR_TOP_INSET,
                right = right - INTERIOR_HORIZONTAL_INSET,
                bottom = top + SLOT_HEIGHT - INTERIOR_BOTTOM_INSET
            )
        }
    }

    private fun nativeRect(left: Int, top: Int, right: Int, bottom: Int) = NormalizedRect(
        left = left / NATIVE_WIDTH,
        top = top / NATIVE_HEIGHT,
        right = right / NATIVE_WIDTH,
        bottom = bottom / NATIVE_HEIGHT
    )
}

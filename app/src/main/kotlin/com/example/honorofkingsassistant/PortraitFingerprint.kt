package com.example.honorofkingsassistant

import kotlin.math.max

/**
 * Compact, non-reversible visual signature. It stores no portrait image and is suitable for
 * local template learning from a user-confirmed draft slot.
 */
data class PortraitFingerprint(
    val averageHash: Long,
    val colorSignature: IntArray
) {
    init {
        require(colorSignature.size == COLOR_SIGNATURE_SIZE)
    }

    fun distance(other: PortraitFingerprint): Double {
        val hashDistance = java.lang.Long.bitCount(averageHash xor other.averageHash) / 64.0
        var colorDistance = 0.0
        for (index in colorSignature.indices) {
            colorDistance += kotlin.math.abs(colorSignature[index] - other.colorSignature[index]) / 255.0
        }
        colorDistance /= colorSignature.size
        return (hashDistance * 0.72 + colorDistance * 0.28).coerceIn(0.0, 1.0)
    }

    fun encode(): String = buildString {
        append(java.lang.Long.toUnsignedString(averageHash, 16).padStart(16, '0'))
        append(':')
        append(colorSignature.joinToString(","))
    }

    companion object {
        const val COLOR_SIGNATURE_SIZE = 12

        fun decode(value: String): PortraitFingerprint? = runCatching {
            val parts = value.split(':', limit = 2)
            if (parts.size != 2) return null
            val hash = java.lang.Long.parseUnsignedLong(parts[0], 16)
            val colors = parts[1].split(',').map(String::toInt).toIntArray()
            if (colors.size != COLOR_SIGNATURE_SIZE) return null
            PortraitFingerprint(hash, colors)
        }.getOrNull()

        /**
         * Creates an average hash plus a coarse RGB signature from a normalized 8x8 sample.
         */
        fun fromArgb64(pixels: IntArray): PortraitFingerprint {
            require(pixels.size == 64)
            val luminance = IntArray(64)
            var total = 0L
            pixels.forEachIndexed { index, color ->
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val value = (red * 299 + green * 587 + blue * 114) / 1000
                luminance[index] = value
                total += value
            }
            val average = (total / max(1, pixels.size)).toInt()
            var hash = 0L
            luminance.forEachIndexed { index, value ->
                if (value >= average) hash = hash or (1L shl index)
            }

            val signature = IntArray(COLOR_SIGNATURE_SIZE)
            val quadrants = arrayOf(
                intArrayOf(0, 0, 4, 4), intArrayOf(4, 0, 8, 4),
                intArrayOf(0, 4, 4, 8), intArrayOf(4, 4, 8, 8)
            )
            quadrants.forEachIndexed { quadrantIndex, q ->
                var redTotal = 0
                var greenTotal = 0
                var blueTotal = 0
                var count = 0
                for (y in q[1] until q[3]) {
                    for (x in q[0] until q[2]) {
                        val color = pixels[y * 8 + x]
                        redTotal += color ushr 16 and 0xff
                        greenTotal += color ushr 8 and 0xff
                        blueTotal += color and 0xff
                        count++
                    }
                }
                signature[quadrantIndex * 3] = redTotal / count
                signature[quadrantIndex * 3 + 1] = greenTotal / count
                signature[quadrantIndex * 3 + 2] = blueTotal / count
            }
            return PortraitFingerprint(hash, signature)
        }
    }
}

data class SlotPortraitFingerprint(
    val side: TeamSide,
    val slotIndex: Int,
    val fingerprint: PortraitFingerprint,
    val visualConfidence: Double
)

data class PortraitHeroMatch(
    val heroName: String,
    val confidence: Double,
    val distance: Double
)

package com.example.honorofkingsassistant

import kotlin.math.max
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Compact, non-reversible visual signature. It stores no portrait image and is suitable for
 * local template learning from a user-confirmed draft slot.
 */
data class PortraitFingerprint(
    val averageHash: Long,
    val colorSignature: IntArray,
    val version: Int = 1,
    val gradientHash: Long = 0L,
    val edgeSignature: IntArray = IntArray(EDGE_SIGNATURE_SIZE)
) {
    init {
        require(colorSignature.size == COLOR_SIGNATURE_SIZE)
        require(version in 1..2)
        require(edgeSignature.size == EDGE_SIGNATURE_SIZE)
    }

    fun distance(other: PortraitFingerprint): Double {
        val hashDistance = java.lang.Long.bitCount(averageHash xor other.averageHash) / 64.0
        val colorDistance = signatureDistance(colorSignature, other.colorSignature)
        if (version == 1 || other.version == 1) {
            return (hashDistance * 0.72 + colorDistance * 0.28).coerceIn(0.0, 1.0)
        }
        val gradientDistance =
            java.lang.Long.bitCount(gradientHash xor other.gradientHash) / 64.0
        val edgeDistance = signatureDistance(edgeSignature, other.edgeSignature)
        return (
            hashDistance * 0.35 +
                gradientDistance * 0.30 +
                edgeDistance * 0.20 +
                colorDistance * 0.15
            ).coerceIn(0.0, 1.0)
    }

    fun encode(): String =
        if (version == 1) {
            buildString {
                append(java.lang.Long.toUnsignedString(averageHash, 16).padStart(16, '0'))
                append(':')
                append(colorSignature.joinToString(","))
            }
        } else {
            buildString {
                append("v2:")
                append(java.lang.Long.toUnsignedString(averageHash, 16).padStart(16, '0'))
                append(':')
                append(java.lang.Long.toUnsignedString(gradientHash, 16).padStart(16, '0'))
                append(':')
                append(edgeSignature.joinToString(","))
                append(':')
                append(colorSignature.joinToString(","))
            }
        }

    private fun signatureDistance(first: IntArray, second: IntArray): Double {
        var total = 0.0
        for (index in first.indices) {
            total += kotlin.math.abs(first[index] - second[index]) / 255.0
        }
        return total / first.size
    }

    companion object {
        const val COLOR_SIGNATURE_SIZE = 12
        const val EDGE_SIGNATURE_SIZE = 32

        fun decode(value: String): PortraitFingerprint? = runCatching {
            if (value.startsWith("v2:")) {
                val parts = value.split(':', limit = 5)
                if (parts.size != 5) return null
                val hash = java.lang.Long.parseUnsignedLong(parts[1], 16)
                val gradient = java.lang.Long.parseUnsignedLong(parts[2], 16)
                val edges = parts[3].split(',').map(String::toInt).toIntArray()
                val colors = parts[4].split(',').map(String::toInt).toIntArray()
                if (edges.size != EDGE_SIGNATURE_SIZE || colors.size != COLOR_SIGNATURE_SIZE) {
                    return null
                }
                return PortraitFingerprint(
                    averageHash = hash,
                    colorSignature = colors,
                    version = 2,
                    gradientHash = gradient,
                    edgeSignature = edges
                )
            }
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

        /**
         * Creates a V2 signature from a normalized 12x12 portrait sample.
         */
        fun fromArgb144(pixels: IntArray): PortraitFingerprint {
            require(pixels.size == 144)
            val luminance = IntArray(144)
            pixels.forEachIndexed { index, color ->
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                luminance[index] = (red * 299 + green * 587 + blue * 114) / 1000
            }

            val sampledLuminance = IntArray(64) { index ->
                val x = index % 8
                val y = index / 8
                luminance[sampleCoordinate(y, 8) * 12 + sampleCoordinate(x, 8)]
            }
            val average = sampledLuminance.average()
            var averageHash = 0L
            sampledLuminance.forEachIndexed { index, value ->
                if (value >= average) averageHash = averageHash or (1L shl index)
            }

            var gradientHash = 0L
            for (y in 0 until 8) {
                val sourceY = sampleCoordinate(y, 8)
                for (x in 0 until 8) {
                    val leftX = sampleCoordinate(x, 9)
                    val rightX = sampleCoordinate(x + 1, 9)
                    if (luminance[sourceY * 12 + rightX] >= luminance[sourceY * 12 + leftX]) {
                        gradientHash = gradientHash or (1L shl (y * 8 + x))
                    }
                }
            }

            val edgeTotals = Array(4) { DoubleArray(8) }
            for (y in 1 until 11) {
                for (x in 1 until 11) {
                    val horizontal = (
                        luminance[y * 12 + x + 1] - luminance[y * 12 + x - 1]
                        ).toDouble()
                    val vertical = (
                        luminance[(y + 1) * 12 + x] - luminance[(y - 1) * 12 + x]
                        ).toDouble()
                    val magnitude = sqrt(horizontal * horizontal + vertical * vertical)
                    if (magnitude == 0.0) continue
                    val angle = (atan2(vertical, horizontal) + 2.0 * PI) % (2.0 * PI)
                    val bin = ((angle / (2.0 * PI)) * 8.0).toInt().coerceIn(0, 7)
                    val quadrant = (if (y >= 6) 2 else 0) + if (x >= 6) 1 else 0
                    edgeTotals[quadrant][bin] += magnitude
                }
            }
            val edgeSignature = IntArray(EDGE_SIGNATURE_SIZE)
            edgeTotals.forEachIndexed { quadrant, bins ->
                val total = bins.sum().coerceAtLeast(1.0)
                bins.forEachIndexed { bin, magnitude ->
                    edgeSignature[quadrant * 8 + bin] =
                        ((magnitude / total) * 255.0).toInt().coerceIn(0, 255)
                }
            }

            val colorSignature = IntArray(COLOR_SIGNATURE_SIZE)
            val quadrants = arrayOf(
                intArrayOf(0, 0, 6, 6), intArrayOf(6, 0, 12, 6),
                intArrayOf(0, 6, 6, 12), intArrayOf(6, 6, 12, 12)
            )
            quadrants.forEachIndexed { quadrantIndex, bounds ->
                var redTotal = 0
                var greenTotal = 0
                var blueTotal = 0
                var count = 0
                for (y in bounds[1] until bounds[3]) {
                    for (x in bounds[0] until bounds[2]) {
                        val color = pixels[y * 12 + x]
                        redTotal += color ushr 16 and 0xff
                        greenTotal += color ushr 8 and 0xff
                        blueTotal += color and 0xff
                        count++
                    }
                }
                colorSignature[quadrantIndex * 3] = redTotal / count
                colorSignature[quadrantIndex * 3 + 1] = greenTotal / count
                colorSignature[quadrantIndex * 3 + 2] = blueTotal / count
            }

            return PortraitFingerprint(
                averageHash = averageHash,
                colorSignature = colorSignature,
                version = 2,
                gradientHash = gradientHash,
                edgeSignature = edgeSignature
            )
        }

        private fun sampleCoordinate(index: Int, sampleCount: Int): Int =
            ((index * 11.0) / (sampleCount - 1).coerceAtLeast(1)).toInt().coerceIn(0, 11)
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

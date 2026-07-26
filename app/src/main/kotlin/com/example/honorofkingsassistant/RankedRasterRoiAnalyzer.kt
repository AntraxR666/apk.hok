package com.example.honorofkingsassistant

import android.graphics.Bitmap
import kotlin.math.abs

interface ArgbRaster {
    val width: Int
    val height: Int

    fun argb(x: Int, y: Int): Int
}

class BitmapArgbRaster(
    private val bitmap: Bitmap
) : ArgbRaster {
    override val width: Int get() = bitmap.width
    override val height: Int get() = bitmap.height

    override fun argb(x: Int, y: Int): Int = bitmap.getPixel(x, y)
}

data class RankedSideRasterEvidence(
    val physicalLeft: Boolean,
    val side: TeamSide,
    val slotIndex: Int,
    val portraitRegion: PixelRect,
    val signals: DraftSlotSignals,
    val status: DraftSlotStatus,
    val confidence: Double
)

data class RankedLoadingCardRasterEvidence(
    val side: TeamSide,
    val slotIndex: Int,
    val region: PixelRect,
    val portraitRegion: PixelRect,
    val visualStats: SlotVisualStats,
    val fingerprint: PortraitFingerprint,
    val visualConfidence: Double
)

/**
 * Pure ranked ROI analysis shared by Android capture and fixture-backed JVM tests.
 */
object RankedRasterRoiAnalyzer {
    fun sideSlots(
        raster: ArgbRaster,
        enemyOnRight: Boolean
    ): List<RankedSideRasterEvidence> =
        physicalSlots(raster, physicalLeft = true, enemyOnRight = enemyOnRight) +
            physicalSlots(raster, physicalLeft = false, enemyOnRight = enemyOnRight)

    fun physicalSlots(
        raster: ArgbRaster,
        physicalLeft: Boolean,
        enemyOnRight: Boolean
    ): List<RankedSideRasterEvidence> {
        require(raster.width > 0 && raster.height > 0)
        val side = when {
            physicalLeft && enemyOnRight -> TeamSide.ALLY
            physicalLeft -> TeamSide.ENEMY
            enemyOnRight -> TeamSide.ENEMY
            else -> TeamSide.ALLY
        }
        val portraits = HoKGlobalLandscapeProfile.portraitRegions(physicalLeft)
        val markers = HoKGlobalLandscapeProfile.confirmationMarkerRegions(physicalLeft)
        val highlights = HoKGlobalLandscapeProfile.previewHighlightRegions(physicalLeft)
        return portraits.indices.map { index ->
            val signals = DraftSlotSignals(
                portrait = visualStats(raster, portraits[index]),
                confirmationMarkerScore = markerScore(
                    raster,
                    markers[index],
                    physicalLeft
                ),
                previewHighlightScore = previewHighlightScore(raster, highlights[index])
            )
            val (status, confidence) = DraftSlotClassifier.classify(signals, physicalLeft)
            RankedSideRasterEvidence(
                physicalLeft = physicalLeft,
                side = side,
                slotIndex = index + 1,
                portraitRegion = portraits[index].toPixelRect(raster.width, raster.height),
                signals = signals,
                status = status,
                confidence = confidence
            )
        }
    }

    fun loadingCards(raster: ArgbRaster): List<RankedLoadingCardRasterEvidence> {
        require(raster.width > 0 && raster.height > 0)
        val geometry = RankedLoadingRosterAnalyzer.geometry(raster.width, raster.height)
        fun row(
            side: TeamSide,
            regions: List<PixelRect>,
            portraitRegions: List<PixelRect>
        ) =
            regions.mapIndexed { index, region ->
                val portraitRegion = portraitRegions[index]
                val pixels = sampleArgb64(raster, portraitRegion)
                RankedLoadingCardRasterEvidence(
                    side = side,
                    slotIndex = index + 1,
                    region = region,
                    portraitRegion = portraitRegion,
                    visualStats = visualStats(raster, portraitRegion),
                    fingerprint = PortraitFingerprint.fromArgb64(pixels),
                    visualConfidence = visualConfidence(pixels)
                )
            }
        return row(TeamSide.ALLY, geometry.allyCards, geometry.allyPortraits) +
            row(TeamSide.ENEMY, geometry.enemyCards, geometry.enemyPortraits)
    }

    fun visualStats(raster: ArgbRaster, region: NormalizedRect): SlotVisualStats =
        visualStats(raster, region.toPixelRect(raster.width, raster.height))

    fun visualStats(raster: ArgbRaster, rect: PixelRect): SlotVisualStats {
        val stepX = (rect.width / 32).coerceAtLeast(1)
        val stepY = (rect.height / 32).coerceAtLeast(1)
        var saturationTotal = 0.0
        var luminanceTotal = 0.0
        var bright = 0
        var samples = 0
        var edgeTotal = 0.0
        var edgeSamples = 0
        var previousRow = DoubleArray(((rect.width - 1) / stepX) + 1)
        var rowIndex = 0

        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            var columnIndex = 0
            var previousLum: Double? = null
            while (x < rect.right) {
                val color = raster.argb(x, y)
                val red = (color ushr 16 and 0xff) / 255.0
                val green = (color ushr 8 and 0xff) / 255.0
                val blue = (color and 0xff) / 255.0
                val max = maxOf(red, green, blue)
                val min = minOf(red, green, blue)
                val saturation = if (max <= 0.0) 0.0 else (max - min) / max
                val luminance = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                saturationTotal += saturation
                luminanceTotal += luminance
                if (luminance > 0.65) bright++
                samples++
                previousLum?.let {
                    edgeTotal += abs(luminance - it)
                    edgeSamples++
                }
                if (rowIndex > 0 && columnIndex < previousRow.size) {
                    edgeTotal += abs(luminance - previousRow[columnIndex])
                    edgeSamples++
                }
                if (columnIndex < previousRow.size) previousRow[columnIndex] = luminance
                previousLum = luminance
                columnIndex++
                x += stepX
            }
            rowIndex++
            y += stepY
        }
        val safeSamples = samples.coerceAtLeast(1)
        return SlotVisualStats(
            meanSaturation = saturationTotal / safeSamples,
            meanLuminance = luminanceTotal / safeSamples,
            edgeDensity = edgeTotal / edgeSamples.coerceAtLeast(1),
            brightPixelRatio = bright.toDouble() / safeSamples
        )
    }

    private fun markerScore(
        raster: ArgbRaster,
        region: NormalizedRect,
        physicalLeft: Boolean
    ): Double {
        val rect = region.toPixelRect(raster.width, raster.height)
        val stepX = (rect.width / 28).coerceAtLeast(1)
        val stepY = (rect.height / 28).coerceAtLeast(1)
        var hits = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = raster.argb(x, y)
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val max = maxOf(red, green, blue).coerceAtLeast(1)
                val min = minOf(red, green, blue)
                val saturation = (max - min).toDouble() / max
                val isMarkerPixel = if (physicalLeft) {
                    blue >= 85 && blue > red * 1.12 &&
                        blue > green * 1.02 && saturation >= 0.20
                } else {
                    red >= 85 && red > green * 1.10 &&
                        red > blue * 1.02 && saturation >= 0.20
                }
                if (isMarkerPixel) hits++
                samples++
                x += stepX
            }
            y += stepY
        }
        return hits.toDouble() / samples.coerceAtLeast(1)
    }

    private fun previewHighlightScore(
        raster: ArgbRaster,
        region: NormalizedRect
    ): Double {
        val rect = region.toPixelRect(raster.width, raster.height)
        val stepX = (rect.width / 32).coerceAtLeast(1)
        val stepY = (rect.height / 32).coerceAtLeast(1)
        var hits = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = raster.argb(x, y)
                val red = color ushr 16 and 0xff
                val green = color ushr 8 and 0xff
                val blue = color and 0xff
                val isGold = red >= 120 && green >= 85 &&
                    red > blue * 1.35 && green > blue * 1.15 &&
                    abs(red - green) < 110
                if (isGold) hits++
                samples++
                x += stepX
            }
            y += stepY
        }
        return hits.toDouble() / samples.coerceAtLeast(1)
    }

    private fun sampleArgb64(raster: ArgbRaster, rect: PixelRect): IntArray =
        IntArray(64) { index ->
            val column = index % 8
            val row = index / 8
            val x = (rect.left + ((column + 0.5) * rect.width / 8.0).toInt())
                .coerceIn(rect.left, rect.right - 1)
            val y = (rect.top + ((row + 0.5) * rect.height / 8.0).toInt())
                .coerceIn(rect.top, rect.bottom - 1)
            raster.argb(x, y)
        }

    private fun visualConfidence(pixels: IntArray): Double {
        var luminanceTotal = 0.0
        var saturationTotal = 0.0
        pixels.forEach { color ->
            val red = (color ushr 16 and 0xff) / 255.0
            val green = (color ushr 8 and 0xff) / 255.0
            val blue = (color and 0xff) / 255.0
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            luminanceTotal += 0.2126 * red + 0.7152 * green + 0.0722 * blue
            saturationTotal += if (max == 0.0) 0.0 else (max - min) / max
        }
        val luminance = luminanceTotal / pixels.size
        val saturation = saturationTotal / pixels.size
        return (0.40 + saturation * 0.45 + abs(luminance - 0.5) * 0.10)
            .coerceIn(0.0, 1.0)
    }
}

package com.example.honorofkingsassistant

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

class DraftBitmapAnalyzer(
    private var enemyOnRight: Boolean = true
) {
    fun setEnemyOnRight(value: Boolean) {
        enemyOnRight = value
    }

    fun analyze(bitmap: Bitmap, recognizedText: String): DraftBoardState {
        val leftPhysical = analyzePhysicalSlots(bitmap, physicalLeft = true)
        val rightPhysical = analyzePhysicalSlots(bitmap, physicalLeft = false)
        val mode = detectMode(bitmap, recognizedText, leftPhysical, rightPhysical)

        val allyPhysical = if (enemyOnRight) leftPhysical else rightPhysical
        val enemyPhysical = if (enemyOnRight) rightPhysical else leftPhysical
        val allySlots = allyPhysical.mapIndexed { index, classified ->
            DraftSlotState(TeamSide.ALLY, index + 1, classified.first, classified.second)
        }
        val enemySlots = enemyPhysical.mapIndexed { index, classified ->
            DraftSlotState(TeamSide.ENEMY, index + 1, classified.first, classified.second)
        }

        val leftConfirmed = leftPhysical.count { it.first == DraftSlotStatus.CONFIRMED }
        val rightConfirmed = rightPhysical.count { it.first == DraftSlotStatus.CONFIRMED }
        val phase = DraftPhaseEstimator.estimate(leftConfirmed, rightConfirmed)
        val previewPhysical = when {
            leftPhysical.any { it.first == DraftSlotStatus.PREVIEWING } &&
                !rightPhysical.any { it.first == DraftSlotStatus.PREVIEWING } ->
                DraftPhaseEstimator.PhysicalDraftSide.LEFT
            rightPhysical.any { it.first == DraftSlotStatus.PREVIEWING } &&
                !leftPhysical.any { it.first == DraftSlotStatus.PREVIEWING } ->
                DraftPhaseEstimator.PhysicalDraftSide.RIGHT
            else -> phase.activePhysicalSide
        }
        val activeSide = when (previewPhysical) {
            DraftPhaseEstimator.PhysicalDraftSide.LEFT -> if (enemyOnRight) TeamSide.ALLY else TeamSide.ENEMY
            DraftPhaseEstimator.PhysicalDraftSide.RIGHT -> if (enemyOnRight) TeamSide.ENEMY else TeamSide.ALLY
            else -> TeamSide.UNKNOWN
        }

        return DraftBoardState(
            mode = mode,
            allySlots = allySlots,
            enemySlots = enemySlots,
            activeSide = activeSide,
            pickSequenceStep = phase.step.takeIf { mode == ScreenMode.DRAFT }
        )
    }

    private fun analyzePhysicalSlots(
        bitmap: Bitmap,
        physicalLeft: Boolean
    ): List<Pair<DraftSlotStatus, Double>> {
        val portraits = HoKGlobalLandscapeProfile.portraitRegions(physicalLeft)
        val markers = HoKGlobalLandscapeProfile.confirmationMarkerRegions(physicalLeft)
        val highlights = HoKGlobalLandscapeProfile.previewHighlightRegions(physicalLeft)
        return portraits.indices.map { index ->
            val signals = DraftSlotSignals(
                portrait = stats(bitmap, portraits[index]),
                confirmationMarkerScore = markerScore(bitmap, markers[index], physicalLeft),
                previewHighlightScore = previewHighlightScore(bitmap, highlights[index])
            )
            DraftSlotClassifier.classify(signals, physicalLeft)
        }
    }

    private fun detectMode(
        bitmap: Bitmap,
        recognizedText: String,
        leftSlots: List<Pair<DraftSlotStatus, Double>>,
        rightSlots: List<Pair<DraftSlotStatus, Double>>
    ): ScreenMode {
        val normalizedText = CounterCatalog.normalize(recognizedText)

        val inGameText = normalizedText.contains("fps") ||
            normalizedText.contains("retirada") ||
            normalizedText.contains("recuperar") ||
            normalizedText.contains("control de masas")
        if (inGameText) return ScreenMode.IN_GAME

        // The supplied global-client captures have a stable bright-blue title bar in draft.
        val draftHeaderVisual = blueDominanceRatio(bitmap, HoKGlobalLandscapeProfile.titleRegion) >= 0.55
        if (draftHeaderVisual) return ScreenMode.DRAFT

        val minimap = stats(bitmap, HoKGlobalLandscapeProfile.minimapRegion)
        val abilities = stats(bitmap, HoKGlobalLandscapeProfile.abilityRegion)
        val inGameVisual = minimap.edgeDensity >= 0.014 && abilities.edgeDensity >= 0.012
        if (inGameVisual) return ScreenMode.IN_GAME

        val draftText = listOf(
            "elegir heroes",
            "elegir heroe",
            "jugador 1",
            "jugador 2",
            "jugador 3",
            "jugador 4",
            "jugador 5",
            "cambiar"
        ).any { normalizedText.contains(it) }
        val leftOccupied = leftSlots.count { it.first != DraftSlotStatus.EMPTY }
        val rightOccupied = rightSlots.count { it.first != DraftSlotStatus.EMPTY }
        val draftVisual = leftOccupied >= 3 && rightOccupied >= 3
        return if (draftText || draftVisual) ScreenMode.DRAFT else ScreenMode.UNKNOWN
    }

    private fun markerScore(
        bitmap: Bitmap,
        region: NormalizedRect,
        physicalLeft: Boolean
    ): Double {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
        val stepX = (rect.width / 28).coerceAtLeast(1)
        val stepY = (rect.height / 28).coerceAtLeast(1)
        var hits = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val max = maxOf(red, green, blue).coerceAtLeast(1)
                val min = minOf(red, green, blue)
                val saturation = (max - min).toDouble() / max
                val isMarkerPixel = if (physicalLeft) {
                    blue >= 85 && blue > red * 1.12 && blue > green * 1.02 && saturation >= 0.20
                } else {
                    red >= 85 && red > green * 1.10 && red > blue * 1.02 && saturation >= 0.20
                }
                if (isMarkerPixel) hits++
                samples++
                x += stepX
            }
            y += stepY
        }
        return hits.toDouble() / samples.coerceAtLeast(1)
    }

    private fun previewHighlightScore(bitmap: Bitmap, region: NormalizedRect): Double {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
        val stepX = (rect.width / 32).coerceAtLeast(1)
        val stepY = (rect.height / 32).coerceAtLeast(1)
        var hits = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                val isGold = red >= 120 && green >= 85 &&
                    red > blue * 1.35 && green > blue * 1.15 &&
                    kotlin.math.abs(red - green) < 110
                if (isGold) hits++
                samples++
                x += stepX
            }
            y += stepY
        }
        return hits.toDouble() / samples.coerceAtLeast(1)
    }

    private fun blueDominanceRatio(bitmap: Bitmap, region: NormalizedRect): Double {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
        val stepX = (rect.width / 48).coerceAtLeast(1)
        val stepY = (rect.height / 16).coerceAtLeast(1)
        var blueDominant = 0
        var samples = 0
        var y = rect.top
        while (y < rect.bottom) {
            var x = rect.left
            while (x < rect.right) {
                val color = bitmap.getPixel(x, y)
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                if (blue > 110 && blue > red * 1.15 && blue > green * 1.05) blueDominant++
                samples++
                x += stepX
            }
            y += stepY
        }
        return blueDominant.toDouble() / samples.coerceAtLeast(1)
    }

    private fun stats(bitmap: Bitmap, region: NormalizedRect): SlotVisualStats {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
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
                val color = bitmap.getPixel(x, y)
                val red = Color.red(color) / 255.0
                val green = Color.green(color) / 255.0
                val blue = Color.blue(color) / 255.0
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
}

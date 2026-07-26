package com.example.honorofkingsassistant

import android.graphics.Bitmap
import android.graphics.Color

class DraftBitmapAnalyzer(
    private var enemyOnRight: Boolean = true
) {
    fun setEnemyOnRight(value: Boolean) {
        enemyOnRight = value
    }

    fun analyze(bitmap: Bitmap, recognizedText: String): DraftBoardState {
        val raster = BitmapArgbRaster(bitmap)
        val leftPhysical = RankedRasterRoiAnalyzer
            .physicalSlots(raster, physicalLeft = true, enemyOnRight = enemyOnRight)
            .map { it.status to it.confidence }
        val rightPhysical = RankedRasterRoiAnalyzer
            .physicalSlots(raster, physicalLeft = false, enemyOnRight = enemyOnRight)
            .map { it.status to it.confidence }
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
        return RankedRasterRoiAnalyzer.visualStats(BitmapArgbRaster(bitmap), region)
    }
}

package com.example.honorofkingsassistant

import kotlin.math.roundToInt

data class NormalizedRect(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double
) {
    init {
        require(left in 0.0..1.0 && right in 0.0..1.0)
        require(top in 0.0..1.0 && bottom in 0.0..1.0)
        require(right > left && bottom > top)
    }

    fun toPixelRect(width: Int, height: Int): PixelRect {
        require(width > 0 && height > 0)
        return PixelRect(
            left = (left * width).roundToInt().coerceIn(0, width - 1),
            top = (top * height).roundToInt().coerceIn(0, height - 1),
            right = (right * width).roundToInt().coerceIn(1, width),
            bottom = (bottom * height).roundToInt().coerceIn(1, height)
        )
    }
}

data class PixelRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

enum class ScreenMode {
    DRAFT,
    IN_GAME,
    UNKNOWN
}

enum class DraftSlotStatus {
    EMPTY,
    PREVIEWING,
    CONFIRMED
}

data class SlotVisualStats(
    val meanSaturation: Double,
    val meanLuminance: Double,
    val edgeDensity: Double,
    val brightPixelRatio: Double
)

data class DraftSlotSignals(
    val portrait: SlotVisualStats,
    /** Ratio of strongly blue/red pixels inside the lock-diamond region. */
    val confirmationMarkerScore: Double,
    /** Ratio of gold pixels around the active player's portrait frame. */
    val previewHighlightScore: Double = 0.0
)

data class DraftSlotState(
    val side: TeamSide,
    val index: Int,
    val status: DraftSlotStatus,
    val confidence: Double
)

data class DraftBoardState(
    val mode: ScreenMode,
    val allySlots: List<DraftSlotState>,
    val enemySlots: List<DraftSlotState>,
    val activeSide: TeamSide = TeamSide.UNKNOWN,
    val pickSequenceStep: Int? = null
) {
    val allyConfirmedCount: Int get() = allySlots.count { it.status == DraftSlotStatus.CONFIRMED }
    val enemyConfirmedCount: Int get() = enemySlots.count { it.status == DraftSlotStatus.CONFIRMED }
    val totalConfirmedCount: Int get() = allyConfirmedCount + enemyConfirmedCount
    val previewingCount: Int get() = (allySlots + enemySlots).count { it.status == DraftSlotStatus.PREVIEWING }
    val isComplete: Boolean get() = allyConfirmedCount == 5 && enemyConfirmedCount == 5

    companion object {
        fun empty(mode: ScreenMode = ScreenMode.UNKNOWN): DraftBoardState = DraftBoardState(
            mode = mode,
            allySlots = (1..5).map { DraftSlotState(TeamSide.ALLY, it, DraftSlotStatus.EMPTY, 0.0) },
            enemySlots = (1..5).map { DraftSlotState(TeamSide.ENEMY, it, DraftSlotStatus.EMPTY, 0.0) }
        )
    }
}

/**
 * Calibrated against the user's 1600x738 Honor of Kings Global captures.
 * Coordinates are normalized so the same UI layout scales across landscape resolutions.
 */
object HoKGlobalLandscapeProfile {
    val titleRegion = NormalizedRect(0.39, 0.005, 0.61, 0.075)
    val leftPlayerColumn = NormalizedRect(0.052, 0.095, 0.225, 0.900)
    val rightPlayerColumn = NormalizedRect(0.775, 0.095, 0.952, 0.900)
    val centerSelectionRegion = NormalizedRect(0.255, 0.10, 0.745, 0.895)
    val minimapRegion = NormalizedRect(0.0, 0.0, 0.215, 0.36)
    val abilityRegion = NormalizedRect(0.63, 0.48, 1.0, 1.0)

    private val slotTopFractions = listOf(0.108, 0.269, 0.431, 0.592, 0.753)
    private const val slotHeight = 0.119

    fun portraitRegions(physicalLeft: Boolean): List<NormalizedRect> {
        val left = if (physicalLeft) 0.063 else 0.888
        val right = if (physicalLeft) 0.119 else 0.944
        return slotTopFractions.map { top -> NormalizedRect(left, top, right, top + slotHeight) }
    }

    fun confirmationMarkerRegions(physicalLeft: Boolean): List<NormalizedRect> {
        // Recalibrated against the complete 848x392 draft recording. The old left ROI
        // sampled the blue background instead of the lock diamond, producing false locks.
        // The lock diamond is centered under each portrait. The full 848x392 recording
        // placed its center near x=0.089 on the left and x=0.916 on the right. Tight ROIs
        // avoid sampling the blue/red side-panel background while still scaling to 1600x738.
        val left = if (physicalLeft) 0.072 else 0.902
        val right = if (physicalLeft) 0.110 else 0.940
        return slotTopFractions.map { top ->
            NormalizedRect(left, top + 0.085, right, (top + 0.145).coerceAtMost(0.995))
        }
    }

    fun previewHighlightRegions(physicalLeft: Boolean): List<NormalizedRect> {
        val left = if (physicalLeft) 0.043 else 0.855
        val right = if (physicalLeft) 0.151 else 0.958
        return slotTopFractions.map { top ->
            NormalizedRect(left, (top - 0.018).coerceAtLeast(0.0), right, (top + 0.150).coerceAtMost(0.995))
        }
    }

    fun playerNameRegions(physicalLeft: Boolean): List<NormalizedRect> {
        val left = if (physicalLeft) 0.105 else 0.665
        val right = if (physicalLeft) 0.345 else 0.895
        return slotTopFractions.map { top ->
            NormalizedRect(left, (top - 0.020).coerceAtLeast(0.0), right, top + 0.070)
        }
    }

    fun slotIndexForPlayerName(
        centerX: Int,
        centerY: Int,
        frameWidth: Int,
        frameHeight: Int,
        allyPhysicalLeft: Boolean
    ): Int? {
        if (frameWidth <= 0 || frameHeight <= 0) return null
        val x = centerX.toDouble() / frameWidth
        val y = centerY.toDouble() / frameHeight
        return playerNameRegions(allyPhysicalLeft)
            .indexOfFirst { x in it.left..it.right && y in it.top..it.bottom }
            .takeIf { it >= 0 }
            ?.plus(1)
    }

    @Deprecated("Use slotIndexForPlayerName for row-bounded OCR mapping")
    fun slotIndexForY(centerY: Int, frameHeight: Int): Int? {
        if (frameHeight <= 0) return null
        val ratio = centerY.toDouble() / frameHeight
        return slotTopFractions
            .mapIndexed { index, top -> index + 1 to kotlin.math.abs(ratio - (top + slotHeight / 2.0)) }
            .minByOrNull { it.second }
            ?.takeIf { it.second <= 0.050 }
            ?.first
    }
}

object DraftSlotClassifier {
    fun classify(signals: DraftSlotSignals, physicalLeft: Boolean): Pair<DraftSlotStatus, Double> {
        val markerThreshold = if (physicalLeft) 0.075 else 0.070
        val marker = signals.confirmationMarkerScore

        if (marker >= markerThreshold) {
            val confidence = (0.84 + (marker - markerThreshold) * 1.8).coerceIn(0.84, 0.99)
            return DraftSlotStatus.CONFIRMED to confidence
        }

        // Profile avatars and empty placeholders have edges and sometimes color, so portrait
        // statistics alone must never mark a player as selected. Only the gold active-frame
        // signal is accepted as a preview. This directly addresses the false "pick fixed" bug.
        val preview = signals.previewHighlightScore
        if (preview >= 0.120) {
            val confidence = (0.76 + (preview - 0.120) * 0.9).coerceIn(0.76, 0.96)
            return DraftSlotStatus.PREVIEWING to confidence
        }

        return DraftSlotStatus.EMPTY to 0.92
    }
}

/**
 * Ranked/no-ban pick order observed by the user and consistent with the official 1-2-2-2-2-1
 * selection pattern. Either physical side may receive first pick.
 */
object DraftPhaseEstimator {
    private val leftStarts = listOf(
        1 to 0,
        1 to 2,
        3 to 2,
        3 to 4,
        5 to 4,
        5 to 5
    )
    private val rightStarts = leftStarts.map { (left, right) -> right to left }

    data class Estimate(
        val activePhysicalSide: PhysicalDraftSide,
        val step: Int,
        val exact: Boolean
    )

    enum class PhysicalDraftSide { LEFT, RIGHT, COMPLETE, UNKNOWN }

    fun estimate(leftConfirmed: Int, rightConfirmed: Int): Estimate {
        val counts = leftConfirmed.coerceIn(0, 5) to rightConfirmed.coerceIn(0, 5)
        if (counts == 5 to 5) return Estimate(PhysicalDraftSide.COMPLETE, 6, true)

        fun evaluate(sequence: List<Pair<Int, Int>>, startsLeft: Boolean): Estimate? {
            val exactIndex = sequence.indexOf(counts)
            if (exactIndex < 0) return null
            val next = when (exactIndex) {
                0 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
                1 -> if (startsLeft) PhysicalDraftSide.LEFT else PhysicalDraftSide.RIGHT
                2 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
                3 -> if (startsLeft) PhysicalDraftSide.LEFT else PhysicalDraftSide.RIGHT
                4 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
                else -> PhysicalDraftSide.COMPLETE
            }
            return Estimate(next, exactIndex + 1, true)
        }

        evaluate(leftStarts, true)?.let { return it }
        evaluate(rightStarts, false)?.let { return it }

        val candidates = buildList {
            leftStarts.forEachIndexed { index, pair -> add(Triple(pair, index, true)) }
            rightStarts.forEachIndexed { index, pair -> add(Triple(pair, index, false)) }
        }
        val nearest = candidates.minByOrNull { (pair, _, _) ->
            kotlin.math.abs(pair.first - counts.first) + kotlin.math.abs(pair.second - counts.second)
        } ?: return Estimate(PhysicalDraftSide.UNKNOWN, 0, false)
        val (_, index, startsLeft) = nearest
        val active = when (index) {
            0 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
            1 -> if (startsLeft) PhysicalDraftSide.LEFT else PhysicalDraftSide.RIGHT
            2 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
            3 -> if (startsLeft) PhysicalDraftSide.LEFT else PhysicalDraftSide.RIGHT
            4 -> if (startsLeft) PhysicalDraftSide.RIGHT else PhysicalDraftSide.LEFT
            else -> PhysicalDraftSide.UNKNOWN
        }
        return Estimate(active, index + 1, false)
    }
}

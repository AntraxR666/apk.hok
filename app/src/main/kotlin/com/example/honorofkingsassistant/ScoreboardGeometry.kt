package com.example.honorofkingsassistant

data class ScoreboardRowGeometry(
    val bounds: PixelRect,
    val portrait: PixelRect,
    val heroTitle: PixelRect,
    val playerName: PixelRect,
    val level: PixelRect,
    val items: List<PixelRect>
)

data class ScoreboardGeometry(
    val allies: List<ScoreboardRowGeometry>,
    val enemies: List<ScoreboardRowGeometry>
) {
    companion object {
        /**
         * Calibrated from the supplied JKM-LX3 in-game scoreboard capture.
         * All values are normalized so the reduced capture frame uses the same model.
         */
        fun forFrame(width: Int, height: Int): ScoreboardGeometry {
            require(width > 0 && height > 0)
            fun rect(left: Double, top: Double, right: Double, bottom: Double) =
                NormalizedRect(left, top, right, bottom).toPixelRect(width, height)
            fun rows(left: Boolean): List<ScoreboardRowGeometry> = (0 until 5).map { index ->
                val top = 0.188 + index * 0.112
                val bottom = top + 0.095
                val rowLeft = if (left) 0.098 else 0.503
                val rowRight = if (left) 0.492 else 0.899
                val portraitLeft = rowLeft
                val portraitRight = rowLeft + 0.050
                val textLeft = rowLeft + 0.052
                val itemLeft = rowLeft + 0.144
                ScoreboardRowGeometry(
                    bounds = rect(rowLeft, top, rowRight, bottom),
                    portrait = rect(portraitLeft, top + 0.004, portraitRight, bottom - 0.004),
                    heroTitle = rect(textLeft, top + 0.008, rowLeft + 0.137, top + 0.044),
                    playerName = rect(textLeft, top + 0.044, rowLeft + 0.137, bottom - 0.006),
                    level = rect(portraitLeft - 0.016, bottom - 0.030, portraitLeft, bottom),
                    items = (0 until 6).map { item ->
                        val x = itemLeft + item * 0.036
                        rect(x, top + 0.020, x + 0.031, bottom - 0.016)
                    }
                )
            }
            return ScoreboardGeometry(allies = rows(left = true), enemies = rows(left = false))
        }
    }
}

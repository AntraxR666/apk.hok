package com.example.honorofkingsassistant

class DraftLayoutClassifier(
    private val enemyOnRight: Boolean,
    private val edgeFraction: Double = 0.36
) {
    init {
        require(edgeFraction in 0.15..0.49) { "edgeFraction fuera de rango" }
    }

    fun classify(centerX: Int, frameWidth: Int): TeamSide {
        if (frameWidth <= 0) return TeamSide.UNKNOWN
        val ratio = centerX.toDouble() / frameWidth.toDouble()
        val physicalSide = when {
            ratio <= edgeFraction -> PhysicalSide.LEFT
            ratio >= 1.0 - edgeFraction -> PhysicalSide.RIGHT
            else -> return TeamSide.UNKNOWN
        }
        return when (physicalSide) {
            PhysicalSide.LEFT -> if (enemyOnRight) TeamSide.ALLY else TeamSide.ENEMY
            PhysicalSide.RIGHT -> if (enemyOnRight) TeamSide.ENEMY else TeamSide.ALLY
        }
    }

    private enum class PhysicalSide { LEFT, RIGHT }
}

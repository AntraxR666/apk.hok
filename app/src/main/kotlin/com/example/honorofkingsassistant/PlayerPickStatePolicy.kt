package com.example.honorofkingsassistant

enum class PlayerPickOverride {
    AUTO,
    PENDING,
    LOCKED
}

/**
 * Lets the user correct a false visual pick state without losing automatic mode.
 */
object PlayerPickStatePolicy {
    fun apply(flow: DraftFlowState, override: PlayerPickOverride): DraftFlowState {
        if (flow.moment == DraftMoment.IN_GAME) return flow
        return when (override) {
        PlayerPickOverride.AUTO -> flow
        PlayerPickOverride.PENDING -> flow.copy(
            moment = DraftMoment.PLAYER_SELECTING,
            shouldRecommendPicks = true,
            shouldShowStrategy = true,
            message = "Marcado manualmente como pendiente; recomendaciones activas"
        )
        PlayerPickOverride.LOCKED -> flow.copy(
            moment = DraftMoment.PLAYER_LOCKED,
            shouldRecommendPicks = false,
            shouldShowStrategy = true,
            message = "Marcado manualmente como fijado; mostrando estrategia"
        )
        }
    }

    fun isLocked(automaticLocked: Boolean, override: PlayerPickOverride): Boolean = when (override) {
        PlayerPickOverride.AUTO -> automaticLocked
        PlayerPickOverride.PENDING -> false
        PlayerPickOverride.LOCKED -> true
    }
}

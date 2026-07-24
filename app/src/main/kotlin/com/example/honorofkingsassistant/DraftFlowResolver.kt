package com.example.honorofkingsassistant

enum class DraftMoment {
    SEARCHING,
    TRACKING,
    BEFORE_PLAYER_PICK,
    PLAYER_SELECTING,
    PLAYER_LOCKED,
    FINAL_ENEMY_PICK,
    COMPLETE,
    IN_GAME
}

data class DraftFlowState(
    val moment: DraftMoment,
    val shouldRecommendPicks: Boolean,
    val shouldShowStrategy: Boolean,
    val message: String
)

/**
 * Converts low-level visual slot state into the product behavior the overlay should expose.
 * The resolver does not assume that the user's physical side receives first pick.
 */
object DraftFlowResolver {
    fun resolve(board: DraftBoardState, playerSlot: PlayerSlotDetection?): DraftFlowState {
        if (board.mode == ScreenMode.IN_GAME) {
            return DraftFlowState(
                moment = DraftMoment.IN_GAME,
                shouldRecommendPicks = false,
                shouldShowStrategy = true,
                message = "Partida detectada; mostrando el plan final"
            )
        }
        if (board.mode != ScreenMode.DRAFT) {
            return DraftFlowState(
                moment = DraftMoment.SEARCHING,
                shouldRecommendPicks = false,
                shouldShowStrategy = false,
                message = "Buscando la pantalla de selección"
            )
        }
        if (board.isComplete) {
            return DraftFlowState(
                moment = DraftMoment.COMPLETE,
                shouldRecommendPicks = false,
                shouldShowStrategy = true,
                message = "Draft completo 10/10; plan final actualizado"
            )
        }

        val playerState = playerSlot?.let { detection ->
            when (detection.side) {
                TeamSide.ALLY -> board.allySlots.getOrNull(detection.slotIndex - 1)
                TeamSide.ENEMY -> board.enemySlots.getOrNull(detection.slotIndex - 1)
                TeamSide.UNKNOWN -> null
            }
        }
        val playerSide = playerSlot?.side ?: TeamSide.ALLY

        if (board.totalConfirmedCount == 9 && board.activeSide != playerSide) {
            return DraftFlowState(
                moment = DraftMoment.FINAL_ENEMY_PICK,
                shouldRecommendPicks = false,
                shouldShowStrategy = true,
                message = "Tu selección está fijada; esperando el último pick enemigo"
            )
        }

        when (playerState?.status) {
            DraftSlotStatus.CONFIRMED -> return DraftFlowState(
                moment = DraftMoment.PLAYER_LOCKED,
                shouldRecommendPicks = false,
                shouldShowStrategy = true,
                message = "Tu héroe está fijado; analizando amenazas y estrategia"
            )
            DraftSlotStatus.PREVIEWING -> return DraftFlowState(
                moment = DraftMoment.PLAYER_SELECTING,
                shouldRecommendPicks = true,
                shouldShowStrategy = true,
                message = "Estás seleccionando; top 3 recalculado en tiempo real"
            )
            DraftSlotStatus.EMPTY -> {
                val selecting = board.activeSide == playerSide
                return DraftFlowState(
                    moment = if (selecting) DraftMoment.PLAYER_SELECTING else DraftMoment.BEFORE_PLAYER_PICK,
                    shouldRecommendPicks = true,
                    shouldShowStrategy = true,
                    message = if (selecting) {
                        "Es tu turno; elige entre las mejores recomendaciones"
                    } else {
                        "Esperando tu turno; recomendación provisional actualizada"
                    }
                )
            }
            null -> Unit
        }

        return DraftFlowState(
            moment = DraftMoment.TRACKING,
            shouldRecommendPicks = board.activeSide == TeamSide.ALLY,
            shouldShowStrategy = board.totalConfirmedCount > 0,
            message = "Draft ${board.totalConfirmedCount}/10; seguimiento automático activo"
        )
    }
}

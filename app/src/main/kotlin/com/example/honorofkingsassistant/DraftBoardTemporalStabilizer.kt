package com.example.honorofkingsassistant

/**
 * Stabilizes slot lock state across animation frames. A confirmed pick never falls back to a
 * preview during the same draft, while one-frame false positives must repeat before they lock.
 */
class DraftBoardTemporalStabilizer(
    private val requiredConfirmationFrames: Int = 2
) {
    private data class Memory(var confirmationHits: Int = 0, var locked: Boolean = false)

    private val ally = Array(5) { Memory() }
    private val enemy = Array(5) { Memory() }
    private var lastMode = ScreenMode.UNKNOWN

    init {
        require(requiredConfirmationFrames >= 1)
    }

    @Synchronized
    fun stabilize(raw: DraftBoardState): DraftBoardState {
        if (raw.mode == ScreenMode.DRAFT && lastMode == ScreenMode.IN_GAME) reset()
        lastMode = raw.mode

        val allies = stabilizeSide(raw.allySlots, ally)
        val enemies = stabilizeSide(raw.enemySlots, enemy)
        val activeSide = when {
            allies.any { it.status == DraftSlotStatus.PREVIEWING } &&
                !enemies.any { it.status == DraftSlotStatus.PREVIEWING } -> TeamSide.ALLY
            enemies.any { it.status == DraftSlotStatus.PREVIEWING } &&
                !allies.any { it.status == DraftSlotStatus.PREVIEWING } -> TeamSide.ENEMY
            raw.activeSide != TeamSide.UNKNOWN -> raw.activeSide
            else -> TeamSide.UNKNOWN
        }

        return raw.copy(
            allySlots = allies,
            enemySlots = enemies,
            activeSide = if (raw.mode == ScreenMode.DRAFT) activeSide else TeamSide.UNKNOWN,
            pickSequenceStep = raw.pickSequenceStep
        )
    }

    private fun stabilizeSide(
        rawSlots: List<DraftSlotState>,
        memory: Array<Memory>
    ): List<DraftSlotState> = rawSlots.mapIndexed { index, slot ->
        val state = memory[index]
        when (slot.status) {
            DraftSlotStatus.CONFIRMED -> {
                state.confirmationHits++
                if (state.confirmationHits >= requiredConfirmationFrames) state.locked = true
            }
            DraftSlotStatus.PREVIEWING -> {
                if (!state.locked) state.confirmationHits = 0
            }
            DraftSlotStatus.EMPTY -> {
                if (!state.locked) state.confirmationHits = 0
            }
        }
        when {
            state.locked -> slot.copy(status = DraftSlotStatus.CONFIRMED, confidence = maxOf(slot.confidence, 0.93))
            slot.status == DraftSlotStatus.CONFIRMED -> slot.copy(status = DraftSlotStatus.PREVIEWING, confidence = 0.76)
            else -> slot
        }
    }

    @Synchronized
    fun reset() {
        (ally + enemy).forEach {
            it.confirmationHits = 0
            it.locked = false
        }
        lastMode = ScreenMode.UNKNOWN
    }
}

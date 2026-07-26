package com.example.honorofkingsassistant

data class DraftSessionResetState(
    val matchMode: MatchModeState,
    val snapshot: DraftSnapshot,
    val board: DraftBoardState,
    val playerSlot: PlayerSlotDetection?
)

class DraftSessionCoordinator {
    private val lock = Any()
    private var generation = 0L

    fun captureGeneration(): Long = synchronized(lock) { generation }

    fun <T> advanceGeneration(action: () -> T): T = synchronized(lock) {
        generation++
        action()
    }

    fun runIfCurrent(expectedGeneration: Long, action: () -> Unit): Boolean =
        synchronized(lock) {
            if (expectedGeneration != generation) {
                false
            } else {
                action()
                true
            }
        }

    fun beginDraftSession(
        resetMatchMode: () -> MatchModeState,
        tracker: TemporalDraftTracker,
        boardStabilizer: DraftBoardTemporalStabilizer,
        playerSlotResolver: PlayerSlotResolver,
        manualPlayerSlotIndex: Int?
    ): DraftSessionResetState = advanceGeneration {
        val resetMode = resetMatchMode()
        tracker.reset()
        boardStabilizer.reset()
        playerSlotResolver.reset()
        DraftSessionResetState(
            matchMode = resetMode,
            snapshot = DraftSnapshot(emptyList(), emptyList(), emptyList()),
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            playerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
        )
    }
}

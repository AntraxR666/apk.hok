package com.example.honorofkingsassistant

data class DraftSessionResetState(
    val matchMode: MatchModeState,
    val snapshot: DraftSnapshot,
    val board: DraftBoardState,
    val playerSlot: PlayerSlotDetection?
)

data class DraftFrameGeneration internal constructor(
    internal val value: Long
)

internal data class PreparedDraftFrame<T : Any>(
    val generation: DraftFrameGeneration,
    val value: T
)

internal fun <T : Any> prepareFrameAtCurrentGeneration(
    coordinator: DraftSessionCoordinator,
    preAnalysis: () -> T?
): PreparedDraftFrame<T>? {
    val generation = coordinator.beginFrame()
    val value = preAnalysis() ?: return null
    return PreparedDraftFrame(generation, value)
}

class DraftSessionCoordinator {
    private val lock = Any()
    private var generation = 0L

    fun captureGeneration(): Long = synchronized(lock) { generation }

    fun beginFrame(): DraftFrameGeneration =
        synchronized(lock) { DraftFrameGeneration(generation) }

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

    fun runIfCurrent(
        expectedGeneration: DraftFrameGeneration,
        action: () -> Unit
    ): Boolean = runIfCurrent(expectedGeneration.value, action)

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

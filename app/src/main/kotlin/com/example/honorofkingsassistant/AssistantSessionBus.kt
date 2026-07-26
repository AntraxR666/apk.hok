package com.example.honorofkingsassistant

import java.util.concurrent.CopyOnWriteArraySet

data class AssistantUiState(
    val active: Boolean = false,
    val status: String = "Asistente detenido",
    val selectedStage: AssistantStage = AssistantStage.PAUSED,
    val suggestedStage: AssistantStage? = null,
    val inputMode: InputMode = InputMode.AUTO_SCAN,
    val matchMode: MatchModeState = MatchModeState(),
    val snapshot: DraftSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList()),
    val recommendations: List<DraftPickRecommendation> = emptyList(),
    val strategy: StrategyPlan? = null,
    val enemyOnRight: Boolean = true,
    val board: DraftBoardState = DraftBoardState.empty(),
    val screenMode: ScreenMode = ScreenMode.UNKNOWN,
    val subphase: DraftSubphase = DraftSubphase.UNKNOWN,
    val playerSlot: PlayerSlotDetection? = null,
    val manualPlayerSlotIndex: Int? = null,
    val playerPickOverride: PlayerPickOverride = PlayerPickOverride.AUTO,
    val playerPickLocked: Boolean = false,
    val draftFlow: DraftFlowState = DraftFlowResolver.resolve(DraftBoardState.empty(), null),
    val learnedPortraitCount: Int = 0,
    val diagnostics: VisionDiagnostics = VisionDiagnostics(),
    val lastUpdatedAtMs: Long = System.currentTimeMillis()
)

object AssistantSessionBus {
    private val listeners = CopyOnWriteArraySet<(AssistantUiState) -> Unit>()

    @Volatile
    var state: AssistantUiState = AssistantUiState()
        private set

    fun publish(newState: AssistantUiState) {
        state = newState.copy(lastUpdatedAtMs = System.currentTimeMillis())
        listeners.forEach { listener -> runCatching { listener(state) } }
    }

    fun subscribe(listener: (AssistantUiState) -> Unit): () -> Unit {
        listeners += listener
        listener(state)
        return { listeners -= listener }
    }
}

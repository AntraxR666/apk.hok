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
    val itemPlan: ItemPlan? = null,
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
    val recognition: RecognitionCalibrationState = RecognitionCalibrationState.UNCALIBRATED,
    val loadingRosterReconciliation: LoadingRosterReconciliationResult? = null,
    val manualAssignments: ManualTeamAssignments = ManualTeamAssignments(),
    val slotRecognition: List<SlotRecognitionState> = emptyList(),
    val loadingConfirmationReview: Boolean = false,
    val diagnostics: VisionDiagnostics = VisionDiagnostics(),
    val lastUpdatedAtMs: Long = System.currentTimeMillis()
)

object RecognitionUiStatePolicy {
    fun apply(
        state: AssistantUiState,
        baseStatus: String,
        calibration: RecognitionCalibrationState
    ): AssistantUiState {
        val recognitionStatus = when (calibration.readiness) {
            RecognitionReadiness.UNCALIBRATED ->
                "Reconocimiento visual sin plantillas; usa corrección manual para confirmar el retrato"
            RecognitionReadiness.READY -> {
                val templateLabel =
                    if (calibration.storedTemplateCount == 1) "plantilla" else "plantillas"
                val heroLabel =
                    if (calibration.coveredHeroCount == 1) "héroe cubierto" else "héroes cubiertos"
                "${calibration.storedTemplateCount} $templateLabel base/aprendidas · " +
                    "${calibration.coveredHeroCount} $heroLabel"
            }
        }
        return state.copy(
            status = "$baseStatus · $recognitionStatus",
            recognition = calibration
        )
    }
}

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

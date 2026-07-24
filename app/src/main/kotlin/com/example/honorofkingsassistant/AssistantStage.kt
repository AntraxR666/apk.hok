package com.example.honorofkingsassistant

enum class AssistantStage {
    PAUSED,
    DRAFT,
    IN_GAME
}

enum class DetectedScene {
    DRAFT,
    IN_GAME,
    UNKNOWN
}

data class AssistantStagePolicyResult(
    val shouldProcessFrames: Boolean,
    val shouldRunDraftVision: Boolean,
    val shouldShowStrategy: Boolean,
    val frameIntervalMs: Long
)

object AssistantStagePolicy {
    fun forStage(stage: AssistantStage): AssistantStagePolicyResult = when (stage) {
        AssistantStage.PAUSED -> AssistantStagePolicyResult(
            shouldProcessFrames = false,
            shouldRunDraftVision = false,
            shouldShowStrategy = false,
            frameIntervalMs = Long.MAX_VALUE
        )
        AssistantStage.DRAFT -> AssistantStagePolicyResult(
            shouldProcessFrames = true,
            shouldRunDraftVision = true,
            shouldShowStrategy = false,
            frameIntervalMs = 1_200L
        )
        AssistantStage.IN_GAME -> AssistantStagePolicyResult(
            shouldProcessFrames = false,
            shouldRunDraftVision = false,
            shouldShowStrategy = true,
            frameIntervalMs = Long.MAX_VALUE
        )
    }

    fun suggest(
        selectedStage: AssistantStage,
        detectedScene: DetectedScene
    ): AssistantStage? = when {
        selectedStage == AssistantStage.PAUSED -> null
        selectedStage == AssistantStage.DRAFT && detectedScene == DetectedScene.IN_GAME -> AssistantStage.IN_GAME
        selectedStage == AssistantStage.IN_GAME && detectedScene == DetectedScene.DRAFT -> AssistantStage.DRAFT
        else -> null
    }
}

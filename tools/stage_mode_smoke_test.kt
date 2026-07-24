package com.example.honorofkingsassistant

fun main() {
    check(!AssistantStagePolicy.forStage(AssistantStage.PAUSED).shouldProcessFrames)
    check(!AssistantStagePolicy.forStage(AssistantStage.PAUSED).shouldShowStrategy)

    val draftPolicy = AssistantStagePolicy.forStage(AssistantStage.DRAFT)
    check(draftPolicy.shouldProcessFrames)
    check(draftPolicy.shouldRunDraftVision)
    check(!draftPolicy.shouldShowStrategy)

    val inGamePolicy = AssistantStagePolicy.forStage(AssistantStage.IN_GAME)
    check(!inGamePolicy.shouldProcessFrames)
    check(!inGamePolicy.shouldRunDraftVision)
    check(inGamePolicy.shouldShowStrategy)

    check(
        AssistantStagePolicy.suggest(
            selectedStage = AssistantStage.DRAFT,
            detectedScene = DetectedScene.IN_GAME
        ) == AssistantStage.IN_GAME
    )

    check(
        AssistantStagePolicy.suggest(
            selectedStage = AssistantStage.IN_GAME,
            detectedScene = DetectedScene.DRAFT
        ) == AssistantStage.DRAFT
    )

    check(
        AssistantStagePolicy.suggest(
            selectedStage = AssistantStage.PAUSED,
            detectedScene = DetectedScene.DRAFT
        ) == null
    )

    println("STAGE_MODE_SMOKE_OK")
}

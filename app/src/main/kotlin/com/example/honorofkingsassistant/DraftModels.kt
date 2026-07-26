package com.example.honorofkingsassistant

enum class TeamSide {
    ALLY,
    ENEMY,
    UNKNOWN
}

enum class MatchKind {
    EXACT_CANONICAL,
    EXACT_ALIAS,
    FUZZY
}

data class HeroNameMatch(
    val hero: Hero,
    val score: Double,
    val matchedText: String,
    val kind: MatchKind
)

data class HeroObservation(
    val heroName: String,
    val side: TeamSide,
    val confidence: Double
)

data class ConfirmedHero(
    val heroName: String,
    val side: TeamSide,
    val confidence: Double
)

data class DraftSnapshot(
    val allies: List<ConfirmedHero>,
    val enemies: List<ConfirmedHero>,
    val unknown: List<ConfirmedHero>,
    val updatedAtMs: Long = System.currentTimeMillis()
) {
    val allConfirmedNames: Set<String>
        get() = (allies + enemies + unknown).map { it.heroName }.toSet()
}

data class DraftPickRecommendation(
    val hero: Hero,
    val score: Double,
    val confidence: Double,
    val evidence: List<String>,
    val coveredEnemies: List<String>
)

data class StrategyPlan(
    val opening: String,
    val priorityTarget: String,
    val teamFight: String,
    val objectivePlan: String,
    val winCondition: String
) {
    fun asLines(): List<String> = listOf(
        "Inicio: $opening",
        "Prioridad: $priorityTarget",
        "Peleas: $teamFight",
        "Objetivos: $objectivePlan",
        "Condición de victoria: $winCondition"
    )
}

data class PlayerSlotDetection(
    val slotIndex: Int,
    val side: TeamSide,
    val confidence: Double
)

data class DraftVisionResult(
    val observations: List<HeroObservation>,
    val rawText: String,
    val board: DraftBoardState,
    val screenMode: ScreenMode,
    val subphase: DraftSubphase = DraftSubphase.UNKNOWN,
    val playerSlot: PlayerSlotDetection? = null,
    val slotFingerprints: List<SlotPortraitFingerprint> = emptyList(),
    val diagnostics: VisionDiagnostics = VisionDiagnostics()
)

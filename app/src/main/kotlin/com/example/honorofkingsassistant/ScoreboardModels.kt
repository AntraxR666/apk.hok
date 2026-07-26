package com.example.honorofkingsassistant

/** Evidence read from one visible row of the in-game scoreboard. */
data class ScoreboardRow(
    val side: TeamSide,
    val slotIndex: Int,
    val titleHeroName: String? = null,
    val portraitHeroName: String? = null,
    val playerName: String? = null,
    val level: Int? = null,
    val itemFingerprints: List<PortraitFingerprint> = emptyList()
) {
    init {
        require(side != TeamSide.UNKNOWN)
        require(slotIndex in 1..5)
        require(level == null || level in 1..20)
        require(itemFingerprints.size <= 6)
    }
}

data class ScoreboardSnapshot(val allies: List<ScoreboardRow>, val enemies: List<ScoreboardRow>) {
    init {
        require(allies.map { it.slotIndex }.sorted() == (1..5).toList())
        require(enemies.map { it.slotIndex }.sorted() == (1..5).toList())
        require(allies.all { it.side == TeamSide.ALLY })
        require(enemies.all { it.side == TeamSide.ENEMY })
    }

    val rows: List<ScoreboardRow> get() = allies + enemies
}

data class ScoreboardCorrection(
    val side: TeamSide,
    val slotIndex: Int,
    val heroName: String,
    val reason: String,
    val confidence: Double
)

data class ScoreboardConflict(
    val side: TeamSide,
    val slotIndex: Int,
    val observedTitleHero: String?,
    val observedPortraitHero: String?,
    val preservedHero: String?,
    val reason: String
)

data class ScoreboardReconciliationResult(
    val snapshot: DraftSnapshot,
    val appliedCorrections: List<ScoreboardCorrection>,
    val pendingConflicts: List<ScoreboardConflict>
)

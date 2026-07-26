package com.example.honorofkingsassistant

enum class InputMode {
    AUTO_SCAN,
    MANUAL
}

enum class MatchMode {
    AUTO,
    RANKED_DRAFT,
    NORMAL_BLIND
}

data class MatchModeState(
    val preference: MatchMode = MatchMode.AUTO,
    val detected: MatchMode = MatchMode.AUTO
) {
    val effective: MatchMode
        get() = if (preference == MatchMode.AUTO) detected else preference
}

data class NormalSelectionEvidence(
    val landscapeCompatible: Boolean,
    val heroCatalogVisible: Boolean,
    val selectedHeroVisible: Boolean,
    val allyRowEvidenceCount: Int,
    val enemyPickColumnVisible: Boolean,
    val rankedBanLayoutVisible: Boolean
)

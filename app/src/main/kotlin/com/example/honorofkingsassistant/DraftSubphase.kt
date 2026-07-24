package com.example.honorofkingsassistant

enum class DraftSubphase {
    BAN,
    PICK,
    ADJUSTMENTS,
    LOADING,
    IN_GAME,
    UNKNOWN
}

/**
 * Classifies the visible pre-game subphase from OCR text plus the already-established screen mode.
 * The labels are taken from the user's Spanish global-client recording:
 * "Fase de veto", "Elegir héroes" and "Últimos ajustes".
 */
object DraftSubphaseDetector {
    fun detect(
        recognizedText: String,
        screenMode: ScreenMode,
        confirmedPickCount: Int
    ): DraftSubphase {
        if (screenMode == ScreenMode.IN_GAME) return DraftSubphase.IN_GAME

        val text = CounterCatalog.normalize(recognizedText)
        return when {
            text.contains("fase de veto") || text.contains("veto") -> DraftSubphase.BAN
            text.contains("ultimos ajustes") || text.contains("ajustes") -> DraftSubphase.ADJUSTMENTS
            text.contains("elegir heroes") || text.contains("elegir heroe") -> DraftSubphase.PICK
            screenMode == ScreenMode.DRAFT && confirmedPickCount >= 10 -> DraftSubphase.ADJUSTMENTS
            screenMode == ScreenMode.DRAFT -> DraftSubphase.PICK
            text.split(Regex("\\s+")).any { it == "vs" } -> DraftSubphase.LOADING
            else -> DraftSubphase.UNKNOWN
        }
    }
}

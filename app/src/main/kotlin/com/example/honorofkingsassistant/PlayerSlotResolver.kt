package com.example.honorofkingsassistant

/**
 * Resolves the user's ally slot without trusting a single OCR frame.
 * Manual selection always wins. Automatic selection requires repeated,
 * high-confidence detections from the ally column and expires after misses.
 */
class PlayerSlotResolver(
    private val requiredHits: Int = 3,
    private val maxMisses: Int = 2,
    private val minimumConfidence: Double = 0.95
) {
    init {
        require(requiredHits >= 1)
        require(maxMisses >= 1)
        require(minimumConfidence in 0.0..1.0)
    }

    private var candidate: PlayerSlotDetection? = null
    private var hitCount = 0
    private var missCount = 0
    private var confirmed: PlayerSlotDetection? = null

    fun resolve(
        automaticDetection: PlayerSlotDetection?,
        manualSlotIndex: Int?
    ): PlayerSlotDetection? {
        if (manualSlotIndex != null) {
            require(manualSlotIndex in 1..5)
            resetAutomatic()
            return PlayerSlotDetection(manualSlotIndex, TeamSide.ALLY, 1.0)
        }

        val valid = automaticDetection?.takeIf {
            it.side == TeamSide.ALLY &&
                it.slotIndex in 1..5 &&
                it.confidence >= minimumConfidence
        }

        if (valid == null) {
            missCount++
            if (missCount >= maxMisses) {
                resetAutomatic()
            }
            return confirmed
        }

        missCount = 0
        if (candidate?.slotIndex == valid.slotIndex) {
            hitCount++
        } else {
            candidate = valid
            hitCount = 1
            confirmed = null
        }

        if (hitCount >= requiredHits) {
            confirmed = valid.copy(confidence = 1.0)
        }
        return confirmed
    }

    fun reset() {
        resetAutomatic()
    }

    private fun resetAutomatic() {
        candidate = null
        hitCount = 0
        missCount = 0
        confirmed = null
    }
}

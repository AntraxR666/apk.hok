package com.example.honorofkingsassistant

enum class ScoreboardScanState { IDLE, ARMED, CAPTURING, REVIEW, FAILED }

/** One explicit scoreboard scan can claim exactly one captured frame. */
class ScoreboardScanCoordinator {
    var state: ScoreboardScanState = ScoreboardScanState.IDLE
        private set

    @Synchronized fun arm() {
        state = ScoreboardScanState.ARMED
    }

    @Synchronized fun shouldCapture(): Boolean = state == ScoreboardScanState.ARMED

    @Synchronized fun claimFrame(isEligible: Boolean): Boolean {
        if (state != ScoreboardScanState.ARMED || !isEligible) return false
        state = ScoreboardScanState.CAPTURING
        return true
    }

    @Synchronized fun completeReview() {
        check(state == ScoreboardScanState.CAPTURING)
        state = ScoreboardScanState.REVIEW
    }

    @Synchronized fun fail() {
        state = ScoreboardScanState.FAILED
    }

    @Synchronized fun reset() {
        state = ScoreboardScanState.IDLE
    }
}

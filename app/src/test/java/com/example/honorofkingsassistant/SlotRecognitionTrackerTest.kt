package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotRecognitionTrackerTest {
    @Test
    fun requiresThreeMatchingFramesBeforePublishingDetectedHero() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ALLY, 1)
        val evidence = evidence(
            acceptedHeroName = "Angela",
            candidates = listOf(candidate("Angela", 0.10, 0.82))
        )

        assertEquals(
            SlotRecognitionStatus.SCANNING,
            tracker.observe(listOf(evidence), board).slot(TeamSide.ALLY, 1).status
        )
        assertEquals(
            SlotRecognitionStatus.SCANNING,
            tracker.observe(listOf(evidence), board).slot(TeamSide.ALLY, 1).status
        )
        val stable = tracker.observe(listOf(evidence), board).slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.DETECTED, stable.status)
        assertEquals("Angela", stable.heroName)
        assertTrue(stable.confidence >= 0.80)
        assertEquals("Angela", stable.candidates.first().heroName)
    }

    @Test
    fun ambiguousFrameStaysUnresolvedAndExposesCandidatesForOneTapCorrection() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ENEMY, 4)
        val evidence = SlotRecognitionEvidence(
            side = TeamSide.ENEMY,
            slotIndex = 4,
            visualConfidence = 0.80,
            candidates = listOf(
                candidate("Lam", 0.17, 0.62),
                candidate("Li Bai", 0.18, 0.60)
            ),
            acceptedHeroName = null
        )

        val state = tracker.observe(listOf(evidence), board).slot(TeamSide.ENEMY, 4)

        assertEquals(SlotRecognitionStatus.UNCERTAIN, state.status)
        assertNull(state.heroName)
        assertEquals(listOf("Lam", "Li Bai"), state.candidates.map { it.heroName })
    }

    @Test
    fun occupiedSlotWithoutUsablePortraitBecomesNotDetectedAfterThreeScans() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ALLY, 2)

        assertEquals(
            SlotRecognitionStatus.SCANNING,
            tracker.observe(emptyList(), board).slot(TeamSide.ALLY, 2).status
        )
        tracker.observe(emptyList(), board)
        val state = tracker.observe(emptyList(), board).slot(TeamSide.ALLY, 2)

        assertEquals(SlotRecognitionStatus.NOT_DETECTED, state.status)
        assertTrue(state.candidates.isEmpty())
    }

    @Test
    fun emptyDraftSlotsRemainWaitingAndDoNotAccumulateFailures() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)

        repeat(5) { tracker.observe(emptyList(), DraftBoardState.empty(ScreenMode.DRAFT)) }
        val state = tracker.current().slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.WAITING, state.status)
        assertEquals(0, state.sampleCount)
    }

    private fun evidence(
        acceptedHeroName: String?,
        candidates: List<SlotRecognitionCandidate>
    ) = SlotRecognitionEvidence(
        side = TeamSide.ALLY,
        slotIndex = 1,
        visualConfidence = 0.85,
        candidates = candidates,
        acceptedHeroName = acceptedHeroName
    )

    private fun candidate(
        heroName: String,
        distance: Double,
        confidence: Double
    ) = SlotRecognitionCandidate(heroName, distance, confidence)

    private fun boardWithOccupied(side: TeamSide, slotIndex: Int): DraftBoardState =
        DraftBoardState(
            mode = ScreenMode.DRAFT,
            allySlots = (1..5).map { index ->
                DraftSlotState(
                    TeamSide.ALLY,
                    index,
                    if (side == TeamSide.ALLY && index == slotIndex) {
                        DraftSlotStatus.CONFIRMED
                    } else {
                        DraftSlotStatus.EMPTY
                    },
                    0.95
                )
            },
            enemySlots = (1..5).map { index ->
                DraftSlotState(
                    TeamSide.ENEMY,
                    index,
                    if (side == TeamSide.ENEMY && index == slotIndex) {
                        DraftSlotStatus.CONFIRMED
                    } else {
                        DraftSlotStatus.EMPTY
                    },
                    0.95
                )
            }
        )

    private fun List<SlotRecognitionState>.slot(
        side: TeamSide,
        index: Int
    ): SlotRecognitionState = single { it.side == side && it.slotIndex == index }
}

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
    fun threePersistentAmbiguousFramesBecomeUncertainAndExposeOneTapCandidates() {
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

        assertEquals(
            SlotRecognitionStatus.SCANNING,
            tracker.observe(listOf(evidence), board).slot(TeamSide.ENEMY, 4).status
        )
        assertEquals(
            SlotRecognitionStatus.SCANNING,
            tracker.observe(listOf(evidence), board).slot(TeamSide.ENEMY, 4).status
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

    @Test
    fun clearPortraitChangeRequiresThreeNewHitsWithoutOneFrameFlicker() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ALLY, 1)
        val angela = evidence(
            acceptedHeroName = "Angela",
            candidates = listOf(candidate("Angela", 0.10, 0.82))
        )
        val lam = evidence(
            acceptedHeroName = "Lam",
            candidates = listOf(candidate("Lam", 0.09, 0.84))
        )

        repeat(3) { tracker.observe(listOf(angela), board) }
        assertEquals(
            "Angela",
            tracker.current().slot(TeamSide.ALLY, 1).heroName
        )

        val firstLam = tracker.observe(listOf(lam), board).slot(TeamSide.ALLY, 1)
        assertEquals(SlotRecognitionStatus.DETECTED, firstLam.status)
        assertEquals("Angela", firstLam.heroName)
        val secondLam = tracker.observe(listOf(lam), board).slot(TeamSide.ALLY, 1)
        assertEquals(SlotRecognitionStatus.DETECTED, secondLam.status)
        assertEquals("Angela", secondLam.heroName)
        val stableLam = tracker.observe(listOf(lam), board).slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.DETECTED, stableLam.status)
        assertEquals("Lam", stableLam.heroName)
        assertTrue(stableLam.sampleCount >= 3)
    }

    @Test
    fun persistentAmbiguityAgesOutPreviouslyDetectedIdentityWithoutOneFrameFlicker() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ALLY, 1)
        val angela = evidence(
            acceptedHeroName = "Angela",
            candidates = listOf(candidate("Angela", 0.10, 0.82))
        )
        repeat(3) { tracker.observe(listOf(angela), board) }

        val ambiguous = evidence(
            acceptedHeroName = null,
            candidates = listOf(
                candidate("Angela", 0.16, 0.60),
                candidate("Lam", 0.17, 0.59)
            )
        )
        repeat(2) {
            val retained = tracker.observe(listOf(ambiguous), board).slot(TeamSide.ALLY, 1)
            assertEquals(SlotRecognitionStatus.DETECTED, retained.status)
            assertEquals("Angela", retained.heroName)
        }
        val state = tracker.observe(listOf(ambiguous), board).slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.UNCERTAIN, state.status)
        assertNull(state.heroName)
    }

    @Test
    fun missingEvidenceAgesOutDetectedIdentityOnlyAfterThreeOccupiedFrames() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val board = boardWithOccupied(TeamSide.ALLY, 1)
        val angela = evidence(
            acceptedHeroName = "Angela",
            candidates = listOf(candidate("Angela", 0.10, 0.82))
        )
        repeat(3) { tracker.observe(listOf(angela), board) }

        repeat(2) {
            val retained = tracker.observe(emptyList(), board).slot(TeamSide.ALLY, 1)
            assertEquals(SlotRecognitionStatus.DETECTED, retained.status)
            assertEquals("Angela", retained.heroName)
        }
        val expired = tracker.observe(emptyList(), board).slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.NOT_DETECTED, expired.status)
        assertNull(expired.heroName)
    }

    @Test
    fun emptySlotRevokesHistoryAndReoccupationStartsFresh() {
        val tracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        val occupied = boardWithOccupied(TeamSide.ALLY, 1)
        val angela = evidence(
            acceptedHeroName = "Angela",
            candidates = listOf(candidate("Angela", 0.10, 0.82))
        )
        repeat(3) { tracker.observe(listOf(angela), occupied) }

        val empty = tracker.observe(
            emptyList(),
            DraftBoardState.empty(ScreenMode.DRAFT)
        ).slot(TeamSide.ALLY, 1)
        val fresh = tracker.observe(listOf(angela), occupied).slot(TeamSide.ALLY, 1)

        assertEquals(SlotRecognitionStatus.WAITING, empty.status)
        assertEquals(SlotRecognitionStatus.SCANNING, fresh.status)
        assertNull(fresh.heroName)
        assertEquals(1, fresh.sampleCount)
    }

    @Test
    fun rankedSnapshotUsesOnlySlotConfirmedIdentitiesInSlotOrder() {
        val snapshot = ConfirmedSlotSnapshotPolicy.from(
            listOf(
                recognitionState(TeamSide.ALLY, 3, SlotRecognitionStatus.DETECTED, "Zhang Fei", 0.48),
                recognitionState(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Yao", 0.53),
                recognitionState(TeamSide.ALLY, 2, SlotRecognitionStatus.SCANNING, null, 0.0),
                recognitionState(TeamSide.ENEMY, 1, SlotRecognitionStatus.UNCERTAIN, null, 0.0),
                recognitionState(TeamSide.ENEMY, 2, SlotRecognitionStatus.MANUAL, "Kongming", 1.0)
            )
        )

        assertEquals(listOf("Yao", "Zhang Fei"), snapshot.allies.map { it.heroName })
        assertEquals(listOf("Kongming"), snapshot.enemies.map { it.heroName })
        assertTrue(snapshot.unknown.isEmpty())
    }

    @Test
    fun rankedSnapshotKeepsLowNumericConfidenceAfterExactSlotThreeOfFiveConfirmation() {
        val snapshot = ConfirmedSlotSnapshotPolicy.from(
            listOf(
                recognitionState(
                    TeamSide.ENEMY,
                    5,
                    SlotRecognitionStatus.DETECTED,
                    "Erin",
                    0.249
                )
            )
        )

        assertEquals("Erin", snapshot.enemies.single().heroName)
        assertEquals(0.249, snapshot.enemies.single().confidence, 0.0001)
    }

    @Test
    fun rankedSnapshotManualCorrectionReplacesAutomaticHeroInTheSameSlot() {
        val states = listOf(
            recognitionState(
                TeamSide.ALLY,
                1,
                SlotRecognitionStatus.DETECTED,
                "Lam",
                0.84
            )
        )
        val manual = ManualTeamAssignments().assign(TeamSide.ALLY, 1, "Angela")

        val snapshot = ConfirmedSlotSnapshotPolicy.from(states, manual)

        assertEquals(listOf("Angela"), snapshot.allies.map { it.heroName })
        assertTrue(snapshot.enemies.isEmpty())
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

    private fun recognitionState(
        side: TeamSide,
        slotIndex: Int,
        status: SlotRecognitionStatus,
        heroName: String?,
        confidence: Double
    ) = SlotRecognitionState(
        side = side,
        slotIndex = slotIndex,
        status = status,
        heroName = heroName,
        confidence = confidence,
        sampleCount = 3
    )

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

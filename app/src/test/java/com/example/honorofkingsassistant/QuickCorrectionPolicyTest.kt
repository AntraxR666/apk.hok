package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCorrectionPolicyTest {
    @Test
    fun manualAssignmentAlwaysOverridesAutomaticRecognitionPresentation() {
        val states = listOf(
            state(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Lam")
        )
        val manual = ManualTeamAssignments().assign(TeamSide.ALLY, 1, "Angela")

        val result = QuickCorrectionPolicy.applyManualAuthority(states, manual).single()

        assertEquals(SlotRecognitionStatus.MANUAL, result.status)
        assertEquals("Angela", result.heroName)
        assertEquals(1.0, result.confidence, 0.0)
    }

    @Test
    fun explicitScanTargetsFirstUnresolvedOccupiedSlotButContinuousScanDoesNotOpenUi() {
        val states = listOf(
            state(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Angela"),
            state(TeamSide.ALLY, 2, SlotRecognitionStatus.UNCERTAIN),
            state(TeamSide.ENEMY, 1, SlotRecognitionStatus.NOT_DETECTED)
        )

        assertNull(QuickCorrectionPolicy.correctionRequest(false, states))
        assertEquals(
            ManualTeamSlot(TeamSide.ALLY, 2),
            QuickCorrectionPolicy.correctionRequest(true, states)?.slot
        )
    }

    @Test
    fun scanningAndWaitingSlotsDoNotInterruptThePlayer() {
        val states = listOf(
            state(TeamSide.ALLY, 1, SlotRecognitionStatus.SCANNING),
            state(TeamSide.ALLY, 2, SlotRecognitionStatus.WAITING)
        )

        assertNull(QuickCorrectionPolicy.correctionRequest(true, states))
        assertFalse(QuickCorrectionPolicy.hasActionableProblem(states))
    }

    @Test
    fun requestCarriesTheVisualCandidatesForOneTapSelection() {
        val candidates = listOf(
            SlotRecognitionCandidate("Lam", 0.14, 0.66),
            SlotRecognitionCandidate("Li Bai", 0.16, 0.61)
        )
        val uncertain = state(
            TeamSide.ENEMY,
            3,
            SlotRecognitionStatus.UNCERTAIN,
            candidates = candidates
        )

        val request = requireNotNull(
            QuickCorrectionPolicy.correctionRequest(true, listOf(uncertain))
        )

        assertEquals(candidates, request.candidates)
        assertTrue(QuickCorrectionPolicy.hasActionableProblem(listOf(uncertain)))
    }

    @Test
    fun resolvingActivePromptAdvancesToNextUnresolvedSlot() {
        val states = listOf(
            state(TeamSide.ALLY, 2, SlotRecognitionStatus.UNCERTAIN),
            state(TeamSide.ENEMY, 1, SlotRecognitionStatus.NOT_DETECTED)
        )
        val active = requireNotNull(QuickCorrectionPolicy.correctionRequest(true, states))
        val manual = ManualTeamAssignments().assign(TeamSide.ALLY, 2, "Angela")

        val next = QuickCorrectionPolicy.afterManualAssignment(
            activeRequest = active,
            assignedSlot = active.slot,
            states = states,
            manualAssignments = manual
        )

        assertEquals(ManualTeamSlot(TeamSide.ENEMY, 1), next?.slot)
    }

    @Test
    fun resolvingLastPromptEndsCorrectionFlow() {
        val states = listOf(
            state(TeamSide.ALLY, 2, SlotRecognitionStatus.UNCERTAIN)
        )
        val active = requireNotNull(QuickCorrectionPolicy.correctionRequest(true, states))
        val manual = ManualTeamAssignments().assign(TeamSide.ALLY, 2, "Angela")

        assertNull(
            QuickCorrectionPolicy.afterManualAssignment(
                activeRequest = active,
                assignedSlot = active.slot,
                states = states,
                manualAssignments = manual
            )
        )
    }

    @Test
    fun unrelatedManualEditDoesNotConsumeOrReplaceActivePrompt() {
        val states = listOf(
            state(TeamSide.ALLY, 2, SlotRecognitionStatus.UNCERTAIN),
            state(TeamSide.ENEMY, 1, SlotRecognitionStatus.NOT_DETECTED)
        )
        val active = requireNotNull(QuickCorrectionPolicy.correctionRequest(true, states))
        val manual = ManualTeamAssignments().assign(TeamSide.ENEMY, 1, "Lam")

        assertEquals(
            active,
            QuickCorrectionPolicy.afterManualAssignment(
                activeRequest = active,
                assignedSlot = ManualTeamSlot(TeamSide.ENEMY, 1),
                states = states,
                manualAssignments = manual
            )
        )
    }

    @Test
    fun candidateAlreadyAssignedToAnotherSlotOnSameTeamIsNotOfferedAgain() {
        val states = QuickCorrectionPolicy.applyManualAuthority(
            states = listOf(
                state(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Lam"),
                state(
                    TeamSide.ALLY,
                    2,
                    SlotRecognitionStatus.UNCERTAIN,
                    candidates = listOf(
                        SlotRecognitionCandidate("Angela", 0.12, 0.72),
                        SlotRecognitionCandidate("Li Bai", 0.15, 0.65)
                    )
                )
            ),
            manualAssignments = ManualTeamAssignments()
                .assign(TeamSide.ALLY, 1, "Ángela")
        )

        val request = requireNotNull(
            QuickCorrectionPolicy.correctionRequest(true, states)
        )

        assertEquals(listOf("Li Bai"), request.candidates.map { it.heroName })
    }

    @Test
    fun candidateAlreadyDetectedInAnotherSlotOnSameTeamIsNotOfferedAgain() {
        val states = listOf(
            state(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Angela"),
            state(
                TeamSide.ALLY,
                2,
                SlotRecognitionStatus.UNCERTAIN,
                candidates = listOf(
                    SlotRecognitionCandidate("Angela", 0.12, 0.72),
                    SlotRecognitionCandidate("Li Bai", 0.15, 0.65)
                )
            )
        )

        val request = requireNotNull(
            QuickCorrectionPolicy.correctionRequest(true, states)
        )

        assertEquals(listOf("Li Bai"), request.candidates.map { it.heroName })
    }

    @Test
    fun manualHeroClaimAtOneSlotRevokesAutomaticDuplicateAtAnotherSlot() {
        val presented = QuickCorrectionPolicy.applyManualAuthority(
            states = listOf(
                state(TeamSide.ALLY, 1, SlotRecognitionStatus.DETECTED, "Angela"),
                state(TeamSide.ALLY, 2, SlotRecognitionStatus.UNCERTAIN)
            ),
            manualAssignments = ManualTeamAssignments()
                .assign(TeamSide.ALLY, 2, "Angela")
        )

        val allyOne = presented.first { it.side == TeamSide.ALLY && it.slotIndex == 1 }
        val allyTwo = presented.first { it.side == TeamSide.ALLY && it.slotIndex == 2 }
        assertEquals(SlotRecognitionStatus.NOT_DETECTED, allyOne.status)
        assertNull(allyOne.heroName)
        assertEquals(SlotRecognitionStatus.MANUAL, allyTwo.status)
        assertEquals("Angela", allyTwo.heroName)
        assertEquals(
            listOf("Angela"),
            ConfirmedSlotSnapshotPolicy.from(presented).allies.map { it.heroName }
        )
    }

    private fun state(
        side: TeamSide,
        slot: Int,
        status: SlotRecognitionStatus,
        hero: String? = null,
        candidates: List<SlotRecognitionCandidate> = emptyList()
    ) = SlotRecognitionState(
        side = side,
        slotIndex = slot,
        status = status,
        heroName = hero,
        confidence = if (hero == null) 0.0 else 0.85,
        candidates = candidates,
        sampleCount = 3
    )
}

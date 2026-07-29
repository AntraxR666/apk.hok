package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerHeroIdentityPolicyTest {
    @Test
    fun exactSlotRecognitionResolvesPlayerHeroEvenWhenSnapshotIsIncomplete() {
        val hero = PlayerHeroIdentityPolicy.resolve(
            allySlotIndex = 4,
            manualAssignments = ManualTeamAssignments(),
            slotRecognition = listOf(
                SlotRecognitionState(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    status = SlotRecognitionStatus.DETECTED,
                    heroName = "Kaizer",
                    confidence = 0.46,
                    sampleCount = 3
                )
            ),
            loadingRoster = null,
            snapshot = DraftSnapshot(
                allies = listOf(ConfirmedHero("Kaizer", TeamSide.ALLY, 0.46)),
                enemies = emptyList(),
                unknown = emptyList()
            )
        )

        assertEquals("Kaizer", hero)
    }

    @Test
    fun manualExactSlotHasAuthorityOverAutomaticAndLoadingEvidence() {
        val hero = PlayerHeroIdentityPolicy.resolve(
            allySlotIndex = 4,
            manualAssignments = ManualTeamAssignments()
                .assign(TeamSide.ALLY, 4, "Angela"),
            slotRecognition = listOf(
                SlotRecognitionState(
                    TeamSide.ALLY,
                    4,
                    SlotRecognitionStatus.DETECTED,
                    "Kaizer",
                    0.80
                )
            ),
            loadingRoster = LoadingRosterReconciliationResult(
                assignments = listOf(
                    LoadingRosterAssignment(
                        TeamSide.ALLY,
                        4,
                        "Lam",
                        0.90,
                        false
                    )
                ),
                conflicts = emptyList(),
                playerSlotIndex = 4
            ),
            snapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
        )

        assertEquals("Angela", hero)
    }

    @Test
    fun loadingExactSlotReplacesStaleSelectionIdentityForItemAdvice() {
        val hero = PlayerHeroIdentityPolicy.resolve(
            allySlotIndex = 4,
            manualAssignments = ManualTeamAssignments(),
            slotRecognition = listOf(
                SlotRecognitionState(
                    TeamSide.ALLY,
                    4,
                    SlotRecognitionStatus.DETECTED,
                    "Kaizer",
                    0.80
                )
            ),
            loadingRoster = LoadingRosterReconciliationResult(
                assignments = listOf(
                    LoadingRosterAssignment(
                        TeamSide.ALLY,
                        4,
                        "Lam",
                        0.90,
                        false
                    )
                ),
                conflicts = emptyList(),
                playerSlotIndex = 4
            ),
            snapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
        )

        assertEquals("Lam", hero)
    }

    @Test
    fun incompleteSnapshotWithoutSlotEvidenceDoesNotGuessPlayerHero() {
        val hero = PlayerHeroIdentityPolicy.resolve(
            allySlotIndex = 4,
            manualAssignments = ManualTeamAssignments(),
            slotRecognition = emptyList(),
            loadingRoster = null,
            snapshot = DraftSnapshot(
                allies = listOf(ConfirmedHero("Kaizer", TeamSide.ALLY, 0.80)),
                enemies = emptyList(),
                unknown = emptyList()
            )
        )

        assertNull(hero)
    }
}

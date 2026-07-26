package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalModeRoutingTest {
    @Test
    fun fixtureShapedTextProducesPositiveNormalEvidenceWithoutEnemyColumn() {
        val evidence = NormalSelectionEvidenceDetector.detect(
            lines = listOf(
                PositionedTextLine("Mulan", 70, 44),
                PositionedTextLine("La Maga de Fuego", 260, 15),
                PositionedTextLine("Angela", 545, 22),
                PositionedTextLine("Lam", 545, 74),
                PositionedTextLine("Liang", 545, 131)
            ),
            frameWidth = 640,
            frameHeight = 288,
            enemyOnRight = true
        )

        assertTrue(evidence.landscapeCompatible)
        assertTrue(evidence.heroCatalogVisible)
        assertTrue(evidence.selectedHeroVisible)
        assertEquals(3, evidence.allyRowEvidenceCount)
        assertFalse(evidence.enemyPickColumnVisible)
        assertFalse(evidence.rankedBanLayoutVisible)
    }

    @Test
    fun rankedBanTextIsDecisiveRankedEvidence() {
        val evidence = NormalSelectionEvidenceDetector.detect(
            lines = listOf(
                PositionedTextLine("Fase de veto", 320, 16),
                PositionedTextLine("Jugador 1", 570, 40)
            ),
            frameWidth = 640,
            frameHeight = 288,
            enemyOnRight = true
        )

        assertTrue(evidence.rankedBanLayoutVisible)
    }

    @Test
    fun normalFlowNeverUsesFinalEnemyOrTenOfTenCompletion() {
        val board = DraftBoardState(
            mode = ScreenMode.DRAFT,
            allySlots = (1..5).map {
                DraftSlotState(TeamSide.ALLY, it, DraftSlotStatus.CONFIRMED, 0.95)
            },
            enemySlots = (1..5).map {
                DraftSlotState(TeamSide.ENEMY, it, DraftSlotStatus.CONFIRMED, 0.95)
            },
            activeSide = TeamSide.ENEMY
        )

        val flow = DraftFlowResolver.resolve(
            board = board,
            playerSlot = null,
            matchMode = MatchMode.NORMAL_BLIND
        )

        assertNotEquals(DraftMoment.FINAL_ENEMY_PICK, flow.moment)
        assertNotEquals(DraftMoment.COMPLETE, flow.moment)
    }

    @Test
    fun normalPendingSelectionGetsProvisionalGuidanceWithoutEnemies() {
        val emptyBoard = DraftBoardState.empty(ScreenMode.DRAFT)

        val normalFlow = DraftFlowResolver.resolve(
            board = emptyBoard,
            playerSlot = null,
            matchMode = MatchMode.NORMAL_BLIND
        )
        val rankedFlow = DraftFlowResolver.resolve(
            board = emptyBoard,
            playerSlot = null,
            matchMode = MatchMode.RANKED_DRAFT
        )

        assertTrue(normalFlow.shouldRecommendPicks)
        assertTrue(normalFlow.shouldShowStrategy)
        assertFalse(rankedFlow.shouldRecommendPicks)
    }

    @Test
    fun normalRouterUsesPortraitIdentityAndRejectsHeroLikeUsernameOcr() {
        val observations = HeroCandidateRouter.route(
            candidates = listOf(
                PositionedHeroCandidate(
                    heroName = "Angela",
                    confidence = 0.96,
                    centerX = 512,
                    centerY = 131,
                    source = CandidateSource.PORTRAIT,
                    sideHint = TeamSide.ALLY
                ),
                PositionedHeroCandidate(
                    heroName = "Wukong",
                    confidence = 1.0,
                    centerX = 550,
                    centerY = 187,
                    source = CandidateSource.OCR
                )
            ),
            matchMode = MatchModeState(detected = MatchMode.NORMAL_BLIND),
            frameWidth = 640,
            frameHeight = 288,
            enemyOnRight = true
        )

        assertEquals(listOf(HeroObservation("Angela", TeamSide.ALLY, 0.96)), observations)
    }

    @Test
    fun uiStateKeepsInputAndPreferenceSeparateFromEffectiveMode() {
        val state = AssistantUiState(
            inputMode = InputMode.MANUAL,
            matchMode = MatchModeState(
                preference = MatchMode.RANKED_DRAFT,
                detected = MatchMode.NORMAL_BLIND
            )
        )

        assertEquals(InputMode.MANUAL, state.inputMode)
        assertEquals(MatchMode.RANKED_DRAFT, state.matchMode.preference)
        assertEquals(MatchMode.NORMAL_BLIND, state.matchMode.detected)
        assertEquals(MatchMode.RANKED_DRAFT, state.matchMode.effective)
    }

    @Test
    fun staleVisionGenerationCannotOverrideManualModeOrRepopulateResetState() {
        val coordinator = DraftSessionCoordinator()
        val staleGeneration = coordinator.captureGeneration()
        var mode = MatchModeState()
        var snapshot = DraftSnapshot(
            allies = listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.98)),
            enemies = emptyList(),
            unknown = emptyList()
        )

        coordinator.advanceGeneration {
            mode = MatchModeState(preference = MatchMode.NORMAL_BLIND)
            snapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
        }
        val staleApplied = coordinator.runIfCurrent(staleGeneration) {
            mode = MatchModeState(detected = MatchMode.RANKED_DRAFT)
            snapshot = DraftSnapshot(
                allies = emptyList(),
                enemies = listOf(ConfirmedHero("Lam", TeamSide.ENEMY, 0.99)),
                unknown = emptyList()
            )
        }

        assertFalse(staleApplied)
        assertEquals(MatchMode.NORMAL_BLIND, mode.effective)
        assertTrue(snapshot.allConfirmedNames.isEmpty())
    }

    @Test
    fun frameTaggedBeforeConversionIsRejectedWhenGenerationAdvancesDuringConversion() {
        val coordinator = DraftSessionCoordinator()
        val frameGeneration = coordinator.beginFrame()
        var resultApplied = false

        coordinator.advanceGeneration {
            // Simulates a manual/session reset while the raw frame is converting.
        }
        val acceptedAfterConversion = coordinator.runIfCurrent(frameGeneration) {
            resultApplied = true
        }

        assertFalse(acceptedAfterConversion)
        assertFalse(resultApplied)
    }
}

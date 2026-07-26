package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedProductionRoutingTest {
    @Test
    fun automaticSessionStateSurfacesUncalibratedRecognitionAndRecoveryPath() {
        val state = RecognitionUiStatePolicy.apply(
            state = AssistantUiState(
                active = true,
                inputMode = InputMode.AUTO_SCAN
            ),
            baseStatus = "SELECCIÓN · Escaneando selección clasificatoria",
            calibration = RecognitionCalibrationState(
                readiness = RecognitionReadiness.UNCALIBRATED,
                storedTemplateCount = 0,
                coveredHeroCount = 0
            )
        )

        assertEquals(RecognitionReadiness.UNCALIBRATED, state.recognition.readiness)
        assertEquals(0, state.recognition.storedTemplateCount)
        assertEquals(0, state.recognition.coveredHeroCount)
        assertTrue(state.status.contains("reconocimiento visual sin calibrar", ignoreCase = true))
        assertTrue(state.status.contains("galería", ignoreCase = true))
        assertTrue(state.status.contains("manual", ignoreCase = true))
    }

    @Test
    fun calibratedSessionStateReportsActualCoverageWithoutFullCatalogClaim() {
        val state = RecognitionUiStatePolicy.apply(
            state = AssistantUiState(active = true, inputMode = InputMode.AUTO_SCAN),
            baseStatus = "SELECCIÓN · Escaneando selección clasificatoria",
            calibration = RecognitionCalibrationState(
                readiness = RecognitionReadiness.READY,
                storedTemplateCount = 2,
                coveredHeroCount = 1
            )
        )

        assertEquals(2, state.recognition.storedTemplateCount)
        assertEquals(1, state.recognition.coveredHeroCount)
        assertTrue(state.status.contains("2 plantillas", ignoreCase = true))
        assertTrue(state.status.contains("1 héroe cubierto", ignoreCase = true))
        assertFalse(state.status.contains("catálogo completo", ignoreCase = true))
    }

    @Test
    fun loadingEvidenceExtractorPairsExactOcrAndPortraitsWithAllTenCards() {
        val evidence = RankedLoadingRosterEvidenceExtractor(listOf(angela(), lam())).extract(
            lines = listOf(
                PositionedTextLine("La Maga de Fuego", 204, 120),
                PositionedTextLine("R95", 534, 150),
                PositionedTextLine("texto no resoluble", 314, 330)
            ),
            portraitMatches = listOf(
                SlotHeroMatch("Angela", TeamSide.ALLY, 1, 0.96),
                SlotHeroMatch("Lam", TeamSide.ALLY, 4, 0.95)
            ),
            frameWidth = 848,
            frameHeight = 392,
            configuredPlayerName = "R-95"
        )

        assertEquals(10, evidence.size)
        assertEquals("Angela", evidence.first {
            it.side == TeamSide.ALLY && it.slotIndex == 1
        }.exactTitleHeroName)
        assertEquals("Angela", evidence.first {
            it.side == TeamSide.ALLY && it.slotIndex == 1
        }.portraitHeroName)
        assertEquals("R95", evidence.first {
            it.side == TeamSide.ALLY && it.slotIndex == 4
        }.playerName)
        assertEquals("Lam", evidence.first {
            it.side == TeamSide.ALLY && it.slotIndex == 4
        }.portraitHeroName)
        assertTrue(evidence.first {
            it.side == TeamSide.ENEMY && it.slotIndex == 2
        }.exactTitleHeroName == null)
    }

    @Test
    fun rankedLoadingVisionResultReachesSessionStateDespiteAnotherCardConflict() {
        val result = DraftVisionResult(
            observations = emptyList(),
            rawText = "VS R95",
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            screenMode = ScreenMode.UNKNOWN,
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            subphase = DraftSubphase.LOADING,
            loadingRosterEvidence = listOf(
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 1,
                    exactTitleHeroName = "Angela",
                    portraitHeroName = "Lam"
                ),
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    exactTitleHeroName = "Lam",
                    portraitHeroName = "Lam",
                    playerName = "R95"
                )
            )
        )

        val state = RankedLoadingSessionStateRouter.route(
            state = AssistantUiState(
                active = true,
                matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT)
            ),
            result = result,
            configuredPlayerName = "R-95"
        )

        val reconciliation = requireNotNull(state.loadingRosterReconciliation)
        assertEquals(listOf(4), reconciliation.assignments.map { it.slotIndex })
        assertEquals(listOf(1), reconciliation.conflicts.map { it.slotIndex })
        assertEquals(4, state.playerSlot?.slotIndex)
        assertEquals(listOf("Lam"), state.snapshot.allies.map { it.heroName })
    }

    private fun angela() = Hero(
        id = "angela",
        name = "Angela",
        role = "Mid",
        counters = emptyList(),
        identityAliases = HeroIdentityAliases(displayTitles = listOf("La Maga de Fuego"))
    )

    private fun lam() = Hero(
        id = "lam",
        name = "Lam",
        role = "Jungle",
        counters = emptyList(),
        identityAliases = HeroIdentityAliases(displayTitles = listOf("El Último Lobo"))
    )
}

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

    @Test
    fun actualManualRosterSetsBecomeConfidenceOnePreservedIdentities() {
        val preserved = ManualRosterAuthority.preservedIdentities(
            manualAllies = setOf("Angela"),
            manualEnemies = setOf("Lam"),
            previous = LoadingRosterReconciliationResult(
                assignments = listOf(
                    LoadingRosterAssignment(
                        side = TeamSide.ALLY,
                        slotIndex = 4,
                        heroName = "Angela",
                        confidence = 0.88,
                        preservedManualEvidence = false
                    )
                ),
                conflicts = emptyList(),
                playerSlotIndex = null
            )
        )

        assertEquals(2, preserved.size)
        assertTrue(preserved.all { it.isManual })
        assertTrue(preserved.all { it.confidence == 1.0 })
        assertEquals(
            4,
            preserved.single { it.side == TeamSide.ALLY }.slotIndex
        )
        assertEquals(
            null,
            preserved.single { it.side == TeamSide.ENEMY }.slotIndex
        )
    }

    @Test
    fun loadingRouterPreservesManualHeroAuthorityOverAutomaticEvidenceAndSnapshot() {
        val result = DraftVisionResult(
            observations = emptyList(),
            rawText = "Angela",
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            screenMode = ScreenMode.UNKNOWN,
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            subphase = DraftSubphase.LOADING,
            loadingRosterEvidence = listOf(
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    portraitHeroName = "Angela"
                )
            )
        )
        val preserved = ManualRosterAuthority.preservedIdentities(
            manualAllies = setOf("Angela"),
            manualEnemies = emptySet(),
            previous = null
        )

        val state = RankedLoadingSessionStateRouter.route(
            state = AssistantUiState(
                snapshot = DraftSnapshot(
                    allies = listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.62)),
                    enemies = emptyList(),
                    unknown = emptyList()
                )
            ),
            result = result,
            configuredPlayerName = "R-95",
            preserved = preserved
        )

        val assignment = requireNotNull(state.loadingRosterReconciliation)
            .assignments
            .single()
        assertEquals(4, assignment.slotIndex)
        assertEquals("Angela", assignment.heroName)
        assertEquals(1.0, assignment.confidence, 0.0)
        assertTrue(assignment.preservedManualEvidence)
        assertEquals(1.0, state.snapshot.allies.single().confidence, 0.0)
    }

    @Test
    fun manualDraftMergeWinsHeroDeduplicationAgainstAutomaticSnapshot() {
        val merged = ManualDraftSnapshotMerger.merge(
            snapshot = DraftSnapshot(
                allies = listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.62)),
                enemies = listOf(ConfirmedHero("Lam", TeamSide.ENEMY, 0.88)),
                unknown = emptyList()
            ),
            manualAllies = setOf("Angela"),
            manualEnemies = setOf("Lam")
        )

        assertEquals(1.0, merged.allies.single().confidence, 0.0)
        assertEquals(1.0, merged.enemies.single().confidence, 0.0)
    }

    @Test
    fun confirmedLoadingUsesLoadingCardRoisAndTemplatesForRankedAndNormalModes() {
        assertEquals(
            PortraitRecognitionFramePlan(
                fingerprintLayout = PortraitFingerprintLayout.LOADING_CARDS,
                templateDomain = PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            ),
            PortraitRecognitionFramePolicy.forFrame(
                matchMode = MatchMode.RANKED_DRAFT,
                subphase = DraftSubphase.LOADING
            )
        )
        assertEquals(
            PortraitRecognitionFramePlan(
                fingerprintLayout = PortraitFingerprintLayout.RANKED_SIDE_PORTRAITS,
                templateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
            ),
            PortraitRecognitionFramePolicy.forFrame(
                matchMode = MatchMode.RANKED_DRAFT,
                subphase = DraftSubphase.PICK
            )
        )
        assertEquals(
            PortraitRecognitionFramePlan(
                fingerprintLayout = PortraitFingerprintLayout.LOADING_CARDS,
                templateDomain = PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            ),
            PortraitRecognitionFramePolicy.forFrame(
                matchMode = MatchMode.NORMAL_BLIND,
                subphase = DraftSubphase.LOADING
            )
        )
        assertEquals(
            PortraitRecognitionFramePlan(
                fingerprintLayout = PortraitFingerprintLayout.NORMAL_ALLY_PORTRAITS,
                templateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
            ),
            PortraitRecognitionFramePolicy.forFrame(
                matchMode = MatchMode.NORMAL_BLIND,
                subphase = DraftSubphase.PICK
            )
        )
    }

    @Test
    fun normalLoadingReconcilesConfirmedHeroesFromBothCardRows() {
        val result = DraftVisionResult(
            observations = emptyList(),
            rawText = "VS",
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            screenMode = ScreenMode.UNKNOWN,
            matchMode = MatchModeState(detected = MatchMode.NORMAL_BLIND),
            subphase = DraftSubphase.LOADING,
            loadingRosterEvidence = listOf(
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 1,
                    portraitHeroName = "Angela"
                ),
                LoadingRosterCardEvidence(
                    side = TeamSide.ENEMY,
                    slotIndex = 1,
                    portraitHeroName = "Lam"
                )
            )
        )

        val state = RankedLoadingSessionStateRouter.route(
            state = AssistantUiState(),
            result = result,
            configuredPlayerName = "R-95"
        )

        assertEquals(listOf("Angela"), state.snapshot.allies.map { it.heroName })
        assertEquals(listOf("Lam"), state.snapshot.enemies.map { it.heroName })
        assertEquals(2, state.loadingRosterReconciliation?.assignments?.size)
    }

    @Test
    fun unresolvedLoadingIdentityKeepsPriorAndManualRosterWithoutInventingAssignment() {
        val result = DraftVisionResult(
            observations = emptyList(),
            rawText = "R95",
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            screenMode = ScreenMode.UNKNOWN,
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            subphase = DraftSubphase.LOADING,
            loadingRosterEvidence = listOf(
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    playerName = "R95"
                )
            )
        )

        val state = RankedLoadingSessionStateRouter.route(
            state = AssistantUiState(
                snapshot = DraftSnapshot(
                    allies = listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.93)),
                    enemies = emptyList(),
                    unknown = emptyList()
                )
            ),
            result = result,
            configuredPlayerName = "R-95",
            preserved = ManualRosterAuthority.preservedIdentities(
                manualAllies = setOf("Lam"),
                manualEnemies = emptySet(),
                previous = null
            )
        )

        val reconciliation = requireNotNull(state.loadingRosterReconciliation)
        assertTrue(reconciliation.assignments.isEmpty())
        assertEquals(4, reconciliation.playerSlotIndex)
        assertEquals(
            listOf("Lam", "Angela"),
            state.snapshot.allies.map { it.heroName }
        )
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

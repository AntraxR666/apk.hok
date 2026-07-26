package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchModeResolverTest {
    private val normalEvidence = NormalSelectionEvidence(
        landscapeCompatible = true,
        heroCatalogVisible = true,
        selectedHeroVisible = true,
        allyRowEvidenceCount = 3,
        enemyPickColumnVisible = false,
        rankedBanLayoutVisible = false
    )

    @Test
    fun jkmAndCaptureFramesUseVerifiedNormalSelectionEnvelopes() {
        val geometry = NormalSelectionGeometry.forFrame(2340, 1080)

        assertEquals(PixelRect(1778, 43, 2270, 950), geometry.alliedColumn)
        assertEquals(PixelRect(1872, 842, 2293, 1058), geometry.confirmAction)
        assertEquals(5, geometry.allyRows.size)
        assertEquals(5, geometry.allyPortraits.size)
        assertTrue(geometry.allyRows.zipWithNext().all { (a, b) -> a.bottom <= b.top })

        val captureGeometry = NormalSelectionGeometry.forFrame(1170, 540)
        assertEquals(5, captureGeometry.allyRows.size)
        assertEquals(5, captureGeometry.allyPortraits.size)
        assertTrue(captureGeometry.allRects().all { rect ->
            rect.left >= 0 && rect.top >= 0 &&
                rect.right <= 1170 && rect.bottom <= 540 &&
                rect.width > 0 && rect.height > 0
        })
    }

    @Test(expected = IllegalArgumentException::class)
    fun portraitFramesAreRejectedBeforeNormalGeometryIsUsed() {
        NormalSelectionGeometry.forFrame(1080, 2340)
    }

    @Test
    fun normalRequiresThreeConsistentFrames() {
        val resolver = MatchModeResolver(requiredFrames = 3)

        repeat(2) {
            assertEquals(MatchMode.AUTO, resolver.observe(normalEvidence).effective)
        }

        assertEquals(MatchMode.NORMAL_BLIND, resolver.observe(normalEvidence).effective)
    }

    @Test
    fun rankedBanEvidencePreventsNormalClassification() {
        val resolver = MatchModeResolver(requiredFrames = 3)

        repeat(2) { resolver.observe(normalEvidence) }
        resolver.observe(normalEvidence.copy(rankedBanLayoutVisible = true))
        repeat(2) { resolver.observe(normalEvidence) }

        assertNotEquals(MatchMode.NORMAL_BLIND, resolver.current().effective)
    }

    @Test
    fun absenceOfRankedSignalsAloneNeverProducesNormalMode() {
        val resolver = MatchModeResolver(requiredFrames = 3)
        val emptyEvidence = normalEvidence.copy(
            heroCatalogVisible = false,
            selectedHeroVisible = false,
            allyRowEvidenceCount = 0
        )

        repeat(5) { resolver.observe(emptyEvidence) }

        assertEquals(MatchMode.AUTO, resolver.current().effective)
    }

    @Test
    fun rankedDetectionReturnsToSilentAutoWhileNormalEvidenceAccumulates() {
        val resolver = MatchModeResolver(requiredFrames = 3)
        val rankedEvidence = normalEvidence.copy(
            heroCatalogVisible = false,
            selectedHeroVisible = false,
            allyRowEvidenceCount = 0,
            enemyPickColumnVisible = true
        )
        assertEquals(MatchMode.RANKED_DRAFT, resolver.observe(rankedEvidence).effective)

        repeat(2) {
            assertEquals(MatchMode.AUTO, resolver.observe(normalEvidence).effective)
        }

        assertEquals(MatchMode.NORMAL_BLIND, resolver.observe(normalEvidence).effective)
    }

    @Test
    fun manualPreferenceAlwaysWinsContradictoryDetection() {
        val normalPreferred = MatchModeResolver(
            requiredFrames = 3,
            initialPreference = MatchMode.NORMAL_BLIND
        )
        repeat(3) {
            normalPreferred.observe(normalEvidence.copy(rankedBanLayoutVisible = true))
        }
        assertEquals(MatchMode.NORMAL_BLIND, normalPreferred.current().effective)

        val rankedPreferred = MatchModeResolver(
            requiredFrames = 3,
            initialPreference = MatchMode.RANKED_DRAFT
        )
        repeat(3) { rankedPreferred.observe(normalEvidence) }
        assertEquals(MatchMode.RANKED_DRAFT, rankedPreferred.current().effective)
    }

    @Test
    fun persistedControlValuesUseTypedSafeDefaults() {
        assertEquals(
            InputMode.MANUAL,
            AssistantPreferences.parseInputMode(InputMode.MANUAL.name)
        )
        assertEquals(
            MatchMode.NORMAL_BLIND,
            AssistantPreferences.parseMatchMode(MatchMode.NORMAL_BLIND.name)
        )
        assertEquals(InputMode.AUTO_SCAN, AssistantPreferences.parseInputMode("broken"))
        assertEquals(MatchMode.AUTO, AssistantPreferences.parseMatchMode("broken"))
    }

    @Test
    fun successiveDraftSessionsResetModeHeroBoardAndPlayerSlotState() {
        val modeResolver = MatchModeResolver(requiredFrames = 1)
        val tracker = TemporalDraftTracker(requiredHits = 1, historySize = 2)
        val boardStabilizer = DraftBoardTemporalStabilizer(requiredConfirmationFrames = 1)
        val playerSlotResolver = PlayerSlotResolver(requiredHits = 1, maxMisses = 2)
        val coordinator = DraftSessionCoordinator()

        coordinator.beginDraftSession(
            resetMatchMode = {
                modeResolver.reset()
                modeResolver.current()
            },
            tracker = tracker,
            boardStabilizer = boardStabilizer,
            playerSlotResolver = playerSlotResolver,
            manualPlayerSlotIndex = null
        )
        modeResolver.observe(normalEvidence)
        tracker.observe(listOf(HeroObservation("Angela", TeamSide.ALLY, 0.98)))
        boardStabilizer.stabilize(
            DraftBoardState.empty(ScreenMode.DRAFT).copy(
                allySlots = (1..5).map { index ->
                    DraftSlotState(
                        TeamSide.ALLY,
                        index,
                        DraftSlotStatus.CONFIRMED,
                        0.98
                    )
                }
            )
        )
        playerSlotResolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), null)

        val secondSession = coordinator.beginDraftSession(
            resetMatchMode = {
                modeResolver.reset()
                modeResolver.current()
            },
            tracker = tracker,
            boardStabilizer = boardStabilizer,
            playerSlotResolver = playerSlotResolver,
            manualPlayerSlotIndex = null
        )

        assertEquals(MatchMode.AUTO, secondSession.matchMode.detected)
        assertEquals(MatchMode.AUTO, secondSession.matchMode.effective)
        assertTrue(secondSession.snapshot.allConfirmedNames.isEmpty())
        assertEquals(0, secondSession.board.totalConfirmedCount)
        assertEquals(null, secondSession.playerSlot)
        assertTrue(tracker.observe(emptyList()).allConfirmedNames.isEmpty())
        assertEquals(
            0,
            boardStabilizer.stabilize(DraftBoardState.empty(ScreenMode.DRAFT))
                .totalConfirmedCount
        )
        assertEquals(null, playerSlotResolver.resolve(null, null))
    }

    private fun NormalSelectionGeometry.allRects(): List<PixelRect> =
        listOf(heroCatalog, selectedHero, alliedColumn, confirmAction) + allyRows + allyPortraits
}

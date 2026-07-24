package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoCalibrationTest {
    @Test
    fun detectsVideoSubphases() {
        assertEquals(DraftSubphase.BAN, DraftSubphaseDetector.detect("Fase de veto", ScreenMode.DRAFT, 0))
        assertEquals(DraftSubphase.PICK, DraftSubphaseDetector.detect("Elegir héroes", ScreenMode.DRAFT, 3))
        assertEquals(
            DraftSubphase.ADJUSTMENTS,
            DraftSubphaseDetector.detect("Últimos ajustes 00:07", ScreenMode.DRAFT, 10)
        )
        assertEquals(DraftSubphase.LOADING, DraftSubphaseDetector.detect("VS", ScreenMode.UNKNOWN, 10))
        assertEquals(DraftSubphase.IN_GAME, DraftSubphaseDetector.detect("FPS 30", ScreenMode.IN_GAME, 10))
    }

    @Test
    fun mapsPlayerNameOnlyInsideTheCorrectAllyRow() {
        assertEquals(4, HoKGlobalLandscapeProfile.slotIndexForPlayerName(112, 239, 848, 392, true))
        assertEquals(2, HoKGlobalLandscapeProfile.slotIndexForPlayerName(245, 185, 1600, 738, true))
        assertNull(HoKGlobalLandscapeProfile.slotIndexForPlayerName(500, 239, 848, 392, true))
    }

    @Test
    fun frame120FixtureDoesNotConfuseProfileAvatarWithLockedHero() {
        val leftMarkers = listOf(0.327, 0.193, 0.173, 0.000, 0.185)
        val leftPreview = listOf(0.000, 0.017, 0.015, 0.281, 0.055)
        val rightMarkers = listOf(0.439, 0.162, 0.253, 0.199, 0.000)
        val rightPreview = listOf(0.075, 0.000, 0.000, 0.000, 0.004)

        val leftStatuses = leftMarkers.indices.map { index ->
            DraftSlotClassifier.classify(
                DraftSlotSignals(
                    portrait = SlotVisualStats(0.40, 0.40, 0.06, 0.10),
                    confirmationMarkerScore = leftMarkers[index],
                    previewHighlightScore = leftPreview[index]
                ),
                physicalLeft = true
            ).first
        }
        val rightStatuses = rightMarkers.indices.map { index ->
            DraftSlotClassifier.classify(
                DraftSlotSignals(
                    portrait = SlotVisualStats(0.40, 0.40, 0.06, 0.10),
                    confirmationMarkerScore = rightMarkers[index],
                    previewHighlightScore = rightPreview[index]
                ),
                physicalLeft = false
            ).first
        }

        assertEquals(
            listOf(
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.PREVIEWING,
                DraftSlotStatus.CONFIRMED
            ),
            leftStatuses
        )
        assertEquals(
            listOf(
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.CONFIRMED,
                DraftSlotStatus.EMPTY
            ),
            rightStatuses
        )
    }
}

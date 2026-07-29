package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotRecognitionUiTextTest {
    @Test
    fun compactLabelsMakeEveryRecognitionStateUnderstandableWithoutInstructions() {
        assertEquals("A1 · vacío", SlotRecognitionUiText.compact(state(SlotRecognitionStatus.WAITING)))
        assertEquals("A1 …", SlotRecognitionUiText.compact(state(SlotRecognitionStatus.SCANNING)))
        assertEquals(
            "A1 ✓ Angela",
            SlotRecognitionUiText.compact(state(SlotRecognitionStatus.DETECTED, "Angela"))
        )
        assertEquals("A1 ? revisar", SlotRecognitionUiText.compact(state(SlotRecognitionStatus.UNCERTAIN)))
        assertEquals(
            "A1 ! no detectado",
            SlotRecognitionUiText.compact(state(SlotRecognitionStatus.NOT_DETECTED))
        )
        assertEquals(
            "A1 ✎ Maga de Fuego",
            SlotRecognitionUiText.compact(state(SlotRecognitionStatus.MANUAL, "Maga de Fuego"))
        )
    }

    @Test
    fun correctionHeadingNamesTheExactTeamAndPosition() {
        val request = QuickCorrectionRequest(
            slot = ManualTeamSlot(TeamSide.ENEMY, 4),
            status = SlotRecognitionStatus.UNCERTAIN,
            candidates = emptyList()
        )

        assertTrue(SlotRecognitionUiText.correctionTitle(request).contains("Enemigo 4"))
        assertTrue(SlotRecognitionUiText.correctionTitle(request).contains("dudoso"))
    }

    private fun state(
        status: SlotRecognitionStatus,
        heroName: String? = null
    ) = SlotRecognitionState(
        side = TeamSide.ALLY,
        slotIndex = 1,
        status = status,
        heroName = heroName
    )
}

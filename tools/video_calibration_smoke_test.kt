import com.example.honorofkingsassistant.*

private fun checkThat(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    checkThat(
        DraftSubphaseDetector.detect("Fase de veto", ScreenMode.DRAFT, 0) == DraftSubphase.BAN,
        "ban subphase failed"
    )
    checkThat(
        DraftSubphaseDetector.detect("Elegir héroes", ScreenMode.DRAFT, 3) == DraftSubphase.PICK,
        "pick subphase failed"
    )
    checkThat(
        DraftSubphaseDetector.detect("Últimos ajustes 00:07", ScreenMode.DRAFT, 10) == DraftSubphase.ADJUSTMENTS,
        "adjustments subphase failed"
    )
    checkThat(
        DraftSubphaseDetector.detect("VS", ScreenMode.UNKNOWN, 10) == DraftSubphase.LOADING,
        "loading subphase failed"
    )
    checkThat(
        DraftSubphaseDetector.detect("FPS 30", ScreenMode.IN_GAME, 10) == DraftSubphase.IN_GAME,
        "in-game subphase failed"
    )

    // Full recording: R-95 appears in ally row 4 at 848x392.
    checkThat(
        HoKGlobalLandscapeProfile.slotIndexForPlayerName(112, 239, 848, 392, true) == 4,
        "video row-4 player mapping failed"
    )
    // Earlier 1600x738 capture: R-95 appears in ally row 2.
    checkThat(
        HoKGlobalLandscapeProfile.slotIndexForPlayerName(245, 185, 1600, 738, true) == 2,
        "screenshot row-2 player mapping failed"
    )
    checkThat(
        HoKGlobalLandscapeProfile.slotIndexForPlayerName(500, 239, 848, 392, true) == null,
        "center OCR must not map to an ally slot"
    )


    // Pixel-derived fixture from video t=120s (848x392): four allied locks,
    // R-95 previewing in row 4, four enemy locks and enemy row 5 empty.
    val frame120LeftMarkers = listOf(0.327, 0.193, 0.173, 0.000, 0.185)
    val frame120LeftPreview = listOf(0.000, 0.017, 0.015, 0.281, 0.055)
    val frame120RightMarkers = listOf(0.439, 0.162, 0.253, 0.199, 0.000)
    val frame120RightPreview = listOf(0.075, 0.000, 0.000, 0.000, 0.004)
    val leftStatuses = frame120LeftMarkers.indices.map { index ->
        DraftSlotClassifier.classify(
            DraftSlotSignals(
                portrait = SlotVisualStats(0.40, 0.40, 0.06, 0.10),
                confirmationMarkerScore = frame120LeftMarkers[index],
                previewHighlightScore = frame120LeftPreview[index]
            ),
            physicalLeft = true
        ).first
    }
    val rightStatuses = frame120RightMarkers.indices.map { index ->
        DraftSlotClassifier.classify(
            DraftSlotSignals(
                portrait = SlotVisualStats(0.40, 0.40, 0.06, 0.10),
                confirmationMarkerScore = frame120RightMarkers[index],
                previewHighlightScore = frame120RightPreview[index]
            ),
            physicalLeft = false
        ).first
    }
    checkThat(
        leftStatuses == listOf(
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.PREVIEWING,
            DraftSlotStatus.CONFIRMED
        ),
        "video frame-120 left status regression: $leftStatuses"
    )
    checkThat(
        rightStatuses == listOf(
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.CONFIRMED,
            DraftSlotStatus.EMPTY
        ),
        "video frame-120 right status regression: $rightStatuses"
    )

    val placeholder = DraftSlotClassifier.classify(
        DraftSlotSignals(
            portrait = SlotVisualStats(0.50, 0.30, 0.08, 0.10),
            confirmationMarkerScore = 0.01,
            previewHighlightScore = 0.02
        ),
        physicalLeft = true
    )
    checkThat(placeholder.first == DraftSlotStatus.EMPTY, "profile placeholder became selected: $placeholder")

    val preview = DraftSlotClassifier.classify(
        DraftSlotSignals(
            portrait = SlotVisualStats(0.30, 0.45, 0.05, 0.05),
            confirmationMarkerScore = 0.02,
            previewHighlightScore = 0.27
        ),
        physicalLeft = true
    )
    checkThat(preview.first == DraftSlotStatus.PREVIEWING, "gold active frame not detected: $preview")

    val confirmed = DraftSlotClassifier.classify(
        DraftSlotSignals(
            portrait = SlotVisualStats(0.40, 0.40, 0.06, 0.15),
            confirmationMarkerScore = 0.20,
            previewHighlightScore = 0.01
        ),
        physicalLeft = false
    )
    checkThat(confirmed.first == DraftSlotStatus.CONFIRMED, "lock diamond not detected: $confirmed")

    println("VIDEO_CALIBRATION_SMOKE_OK")
}

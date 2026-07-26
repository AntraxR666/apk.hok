package com.example.honorofkingsassistant

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun normalModeReferenceFramesMatchRecordedProvenance() {
        val manifest = JSONObject(
            repositoryFile("docs/normal_mode_calibration_2026-07-26.json").readText()
        )
        val source = manifest.getJSONObject("source")
        assertEquals(
            "https://www.youtube.com/watch?v=t83Wka385_Q",
            source.getString("url")
        )
        assertEquals(640, source.getJSONObject("source_viewport").getInt("width"))
        assertEquals(288, source.getJSONObject("source_viewport").getInt("height"))
        assertEquals(640, source.getJSONObject("crop_rectangle").getInt("right"))
        assertEquals(288, source.getJSONObject("crop_rectangle").getInt("bottom"))
        assertFalse(source.getBoolean("full_video_downloaded"))
        assertFalse(source.getBoolean("content_in_apk"))

        val fixtures = manifest.getJSONArray("fixtures")
        repeat(fixtures.length()) { index ->
            val entry = fixtures.getJSONObject(index)
            val filename = entry.getString("filename")
            val bytes = requireNotNull(
                javaClass.classLoader?.getResourceAsStream("normal_mode/$filename")
            ).use { it.readBytes() }
            assertTrue(bytes.copyOfRange(0, 8).contentEquals(PNG_SIGNATURE))
            assertEquals(640, bytes.pngDimension(offset = 16))
            assertEquals(288, bytes.pngDimension(offset = 20))
            assertEquals(entry.getString("sha256"), bytes.sha256())
        }

        val envelopes = manifest.getJSONObject("normalized_envelopes")
        assertTrue(envelopes.getJSONArray("ally_rows").length() == 5)
        assertTrue(envelopes.getJSONArray("ally_portraits").length() == 5)
    }

    private fun repositoryFile(relativePath: String): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(5) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate
            directory = directory.parentFile ?: directory
        }
        error("Repository file not found: $relativePath")
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }

    private fun ByteArray.pngDimension(offset: Int): Int =
        ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).int

    companion object {
        private val PNG_SIGNATURE = byteArrayOf(
            0x89.toByte(),
            0x50,
            0x4e,
            0x47,
            0x0d,
            0x0a,
            0x1a,
            0x0a
        )
    }
}

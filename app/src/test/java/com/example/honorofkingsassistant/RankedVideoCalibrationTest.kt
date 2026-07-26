package com.example.honorofkingsassistant

import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedVideoCalibrationTest {
    @Test
    fun rankedFixtureManifestMatchesAllSixRecordedFrames() {
        val manifest = JSONObject(
            repositoryFile("docs/ranked_mode_calibration_2026-07-26.json").readText()
        )
        val source = manifest.getJSONObject("source")
        assertEquals(848, source.getInt("width"))
        assertEquals(392, source.getInt("height"))
        assertEquals("Huawei JKM-LX3 2340x1080 landscape", source.getString("device_profile"))

        val expectedHashes = linkedMapOf(
            "ban_active_0008.png" to
                "66514503484B6A847FB75B7488B016875A67D66C8A4949AE41311C6799D598C8",
            "ban_summary_0030.png" to
                "E917509EC7E82170A816C7312DD0743B4D36E66C23F5E7308B7FFE85EA08FA81",
            "pick_partial_0104.png" to
                "02EA84FF9E90F6625472B4B410914343113B08A065F2452374B6F731C436DD3E",
            "r95_pick_turn_0148.png" to
                "53E869657894AE032337965FE9E467EEEBC9F8BA7014540C8C46B8003C8A2355",
            "final_adjustments_0224.png" to
                "DB59FBD563FAB7E2471926CE2358AA79D74812068255F4CD502F45D3BDE6FAE0",
            "loading_roster_0245.png" to
                "4A94DFB818EA76C82B2D4B47262814F6C76526B2B707422E7492B3A46B9E6A74"
        )
        val fixtures = manifest.getJSONObject("fixtures")

        expectedHashes.forEach { (filename, expectedHash) ->
            assertEquals(expectedHash, fixtures.getString(filename))
            val bytes = requireNotNull(
                javaClass.classLoader?.getResourceAsStream("ranked_mode/$filename")
            ).use { it.readBytes() }
            assertEquals(848, bytes.pngDimension(offset = 16))
            assertEquals(392, bytes.pngDimension(offset = 20))
            assertEquals(expectedHash, bytes.sha256())
        }
    }

    @Test
    fun exactSpanishHeaderInsideCalibratedTitleRegionDeterminesRankedPhase() {
        assertEquals(
            DraftSubphase.BAN,
            RankedPhaseDetector.detect(
                lines = listOf(PositionedTextLine("Fase de veto", 424, 18)),
                frameWidth = 848,
                frameHeight = 392
            )
        )
        assertEquals(
            DraftSubphase.PICK,
            RankedPhaseDetector.detect(
                lines = listOf(PositionedTextLine("Elegir héroes", 424, 18)),
                frameWidth = 848,
                frameHeight = 392
            )
        )
        assertEquals(
            DraftSubphase.ADJUSTMENTS,
            RankedPhaseDetector.detect(
                lines = listOf(PositionedTextLine("Últimos ajustes", 424, 18)),
                frameWidth = 848,
                frameHeight = 392
            )
        )
        assertEquals(
            DraftSubphase.UNKNOWN,
            RankedPhaseDetector.detect(
                lines = listOf(PositionedTextLine("Elegir héroes", 90, 238)),
                frameWidth = 848,
                frameHeight = 392
            )
        )
        assertEquals(
            DraftSubphase.LOADING,
            RankedPhaseDetector.detect(
                lines = listOf(PositionedTextLine("VS", 424, 200)),
                frameWidth = 848,
                frameHeight = 392
            )
        )
    }

    @Test
    fun rankedSideAndGalleryOcrNeverBecomeHeroIdentity() {
        val observations = HeroCandidateRouter.route(
            candidates = listOf(
                ocrCandidate("R-95", 92, 238),
                ocrCandidate("Línea de choque", 132, 248),
                ocrCandidate("Jugador 4", 730, 238),
                ocrCandidate("Angela", 424, 120)
            ),
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            frameWidth = 848,
            frameHeight = 392,
            enemyOnRight = true
        )

        assertTrue(observations.isEmpty())
    }

    @Test
    fun rankedPortraitRequiresPreviewOrLockEvidence() {
        val withoutState = portraitCandidate("Angela", DraftSlotStatus.EMPTY)
        val preview = portraitCandidate("Lam", DraftSlotStatus.PREVIEWING)
        val locked = portraitCandidate("Liang", DraftSlotStatus.CONFIRMED)

        val observations = HeroCandidateRouter.route(
            candidates = listOf(withoutState, preview, locked),
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            frameWidth = 848,
            frameHeight = 392,
            enemyOnRight = true
        )

        assertEquals(
            listOf(
                HeroObservation("Lam", TeamSide.ALLY, 0.94),
                HeroObservation("Liang", TeamSide.ALLY, 0.94)
            ),
            observations
        )
    }

    @Test
    fun visuallyRichProfileAvatarIsIneligibleWhenSlotIsEmpty() {
        val visuallyRich = SlotPortraitFingerprint(
            side = TeamSide.ALLY,
            slotIndex = 4,
            fingerprint = fingerprint(7),
            visualConfidence = 0.98
        )
        val board = DraftBoardState.empty(ScreenMode.DRAFT)

        assertTrue(RankedPortraitEligibility.eligible(listOf(visuallyRich), board).isEmpty())
    }

    @Test
    fun r95AliasFromRecordedPickTurnBindsAllySlotFour() {
        assertEquals(
            PlayerIdentityNormalizer.canonical("R95"),
            PlayerIdentityNormalizer.canonical("R-95")
        )
        assertEquals(
            4,
            HoKGlobalLandscapeProfile.slotIndexForPlayerName(
                centerX = 92,
                centerY = 238,
                frameWidth = 848,
                frameHeight = 392,
                allyPhysicalLeft = true
            )
        )
    }

    @Test
    fun loadingFrameExposesFiveTopAllyAndFiveBottomEnemyCards() {
        val geometry = RankedLoadingRosterAnalyzer.geometry(848, 392)

        assertEquals(5, geometry.allyCards.size)
        assertEquals(5, geometry.enemyCards.size)
        assertTrue(geometry.allyCards.all { it.bottom <= (0.49 * 392).toInt() + 1 })
        assertTrue(geometry.enemyCards.all { it.top >= (0.54 * 392).toInt() - 1 })
        assertEquals(listOf(204, 314, 424, 534, 644), geometry.allyCards.map {
            (it.left + it.right) / 2
        })
    }

    @Test
    fun loadingRosterBindsR95ToAllyCardFourAndKeepsManualConflictForReview() {
        val result = RankedLoadingRosterAnalyzer().reconcile(
            cards = listOf(
                LoadingRosterCardEvidence(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    exactTitleHeroName = "Angela",
                    portraitHeroName = "Angela",
                    playerName = "R95"
                )
            ),
            preserved = listOf(
                PreservedRosterIdentity(
                    side = TeamSide.ALLY,
                    slotIndex = 4,
                    heroName = "Lam",
                    confidence = 1.0,
                    isManual = true
                )
            ),
            configuredPlayerName = "R-95"
        )

        assertEquals(4, result.playerSlotIndex)
        assertEquals("Lam", result.assignments.single().heroName)
        assertEquals("Angela", result.conflicts.single().observedHeroName)
        assertEquals("Lam", result.conflicts.single().preservedHeroName)
    }

    private fun ocrCandidate(
        text: String,
        centerX: Int,
        centerY: Int
    ) = PositionedHeroCandidate(
        heroName = text,
        confidence = 1.0,
        centerX = centerX,
        centerY = centerY,
        source = CandidateSource.OCR
    )

    private fun portraitCandidate(
        heroName: String,
        status: DraftSlotStatus
    ) = PositionedHeroCandidate(
        heroName = heroName,
        confidence = 0.94,
        centerX = 92,
        centerY = 238,
        source = CandidateSource.PORTRAIT,
        sideHint = TeamSide.ALLY,
        rankedSlotStatus = status
    )

    private fun fingerprint(seed: Int) = PortraitFingerprint(
        averageHash = seed.toLong(),
        colorSignature = IntArray(PortraitFingerprint.COLOR_SIGNATURE_SIZE) { seed }
    )

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
            .joinToString("") { "%02X".format(it) }

    private fun ByteArray.pngDimension(offset: Int): Int =
        ByteBuffer.wrap(this, offset, Int.SIZE_BYTES).int
}

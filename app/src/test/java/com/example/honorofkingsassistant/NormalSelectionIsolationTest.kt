package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalSelectionIsolationTest {
    private val width = 640
    private val height = 288
    private val classifier = NormalSelectionLayoutClassifier.forFrame(width, height)

    @Test
    fun normalCatalogAndCenterCandidatesAreDiscarded() {
        val catalogCandidate = ocrCandidate("Angela", centerX = 80, centerY = 120)
        val selectedHeroCandidate = ocrCandidate("Angela", centerX = 320, centerY = 120)

        assertNull(classifier.classify(catalogCandidate))
        assertNull(classifier.classify(selectedHeroCandidate))
    }

    @Test
    fun fixtureShapedPortraitCandidatesMapToAllFiveAllySlots() {
        val centers = listOf(20, 75, 131, 187, 244)

        val classified = centers.mapIndexed { index, centerY ->
            classifier.classify(
                portraitCandidate("Hero ${index + 1}", centerX = 512, centerY = centerY)
            )
        }

        assertEquals((1..5).toList(), classified.map { it?.slotIndex })
        assertTrue(classified.all { it?.side == TeamSide.ALLY })
    }

    @Test
    fun matchedNormalPortraitsPreserveSlotCoordinatesBeforeRouting() {
        val geometry = NormalSelectionGeometry.forFrame(width, height)
        val candidates = NormalPortraitCandidateFactory.create(
            matches = (1..5).map { slot ->
                SlotHeroMatch(
                    heroName = "Hero $slot",
                    side = TeamSide.ALLY,
                    slotIndex = slot,
                    confidence = 0.90 + slot / 100.0
                )
            },
            geometry = geometry
        )

        assertEquals(listOf(20, 75, 131, 187, 244), candidates.map { it.centerY })
        assertTrue(candidates.all { it.source == CandidateSource.PORTRAIT })
        assertEquals(
            (1..5).toList(),
            candidates.map { classifier.classify(it)?.slotIndex }
        )
    }

    @Test
    fun usernameOcrInNormalAllyRowCannotBecomeHeroIdentity() {
        val classified = classifier.classify(
            ocrCandidate("Angela", centerX = 550, centerY = 131)
        )

        assertNull(classified)
    }

    @Test
    fun normalSelectionCanNeverProduceEnemyObservation() {
        val allFixtureCandidates = listOf(
            ocrCandidate("Angela", 80, 120),
            ocrCandidate("Angela", 320, 120),
            portraitCandidate("Angela", 512, 20),
            portraitCandidate("Lam", 512, 75),
            portraitCandidate("Liang", 512, 131),
            portraitCandidate("Dun", 512, 187),
            portraitCandidate("Wukong", 512, 244),
            ocrCandidate("Hou Yi", 630, 131)
        )

        assertTrue(allFixtureCandidates.mapNotNull(classifier::classify).none {
            it.side == TeamSide.ENEMY
        })
    }

    @Test
    fun normalSelectionDiscardsRankedPortraitCandidates() {
        val portrait = PositionedHeroCandidate(
            heroName = "Angela",
            confidence = 0.95,
            centerX = 550,
            centerY = 131,
            source = CandidateSource.PORTRAIT,
            sideHint = TeamSide.ENEMY
        )

        assertNull(classifier.classify(portrait))
    }

    @Test
    fun autoPublishesNoObservationsUntilModeIsDecisive() {
        val observations = HeroCandidateRouter.route(
            candidates = listOf(ocrCandidate("Angela", 550, 131)),
            matchMode = MatchModeState(),
            frameWidth = width,
            frameHeight = height,
            enemyOnRight = true
        )

        assertTrue(observations.isEmpty())
    }

    @Test
    fun normalRouterPublishesOnlyMeasuredAllyRows() {
        val observations = HeroCandidateRouter.route(
            candidates = listOf(
                ocrCandidate("Angela", 80, 120),
                ocrCandidate("Lam", 320, 120),
                portraitCandidate("Liang", 512, 131)
            ),
            matchMode = MatchModeState(detected = MatchMode.NORMAL_BLIND),
            frameWidth = width,
            frameHeight = height,
            enemyOnRight = true
        )

        assertEquals(listOf(HeroObservation("Liang", TeamSide.ALLY, 0.94)), observations)
    }

    @Test
    fun rankedRouterRejectsOcrEvenWhenSideTextLooksLikeHeroIdentity() {
        val observations = HeroCandidateRouter.route(
            candidates = listOf(
                ocrCandidate("Angela", 80, 120),
                ocrCandidate("Lam", 560, 120)
            ),
            matchMode = MatchModeState(detected = MatchMode.RANKED_DRAFT),
            frameWidth = width,
            frameHeight = height,
            enemyOnRight = true
        )

        assertTrue(observations.isEmpty())
    }

    private fun ocrCandidate(
        heroName: String,
        centerX: Int,
        centerY: Int
    ): PositionedHeroCandidate = PositionedHeroCandidate(
        heroName = heroName,
        confidence = 0.94,
        centerX = centerX,
        centerY = centerY,
        source = CandidateSource.OCR
    )

    private fun portraitCandidate(
        heroName: String,
        centerX: Int,
        centerY: Int
    ): PositionedHeroCandidate = PositionedHeroCandidate(
        heroName = heroName,
        confidence = 0.94,
        centerX = centerX,
        centerY = centerY,
        source = CandidateSource.PORTRAIT,
        sideHint = TeamSide.ALLY
    )
}

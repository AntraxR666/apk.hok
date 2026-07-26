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
    fun normalAllyRowThreeMapsOnlyToAllySlotThree() {
        val classified = classifier.classify(
            ocrCandidate("Angela", centerX = 550, centerY = 131)
        )

        assertEquals(TeamSide.ALLY, classified?.side)
        assertEquals(3, classified?.slotIndex)
    }

    @Test
    fun normalSelectionCanNeverProduceEnemyObservation() {
        val allFixtureCandidates = listOf(
            ocrCandidate("Angela", 80, 120),
            ocrCandidate("Angela", 320, 120),
            ocrCandidate("Angela", 550, 22),
            ocrCandidate("Lam", 550, 74),
            ocrCandidate("Liang", 550, 131),
            ocrCandidate("Dun", 550, 187),
            ocrCandidate("Wukong", 550, 243),
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
                ocrCandidate("Liang", 550, 131)
            ),
            matchMode = MatchModeState(detected = MatchMode.NORMAL_BLIND),
            frameWidth = width,
            frameHeight = height,
            enemyOnRight = true
        )

        assertEquals(listOf(HeroObservation("Liang", TeamSide.ALLY, 0.94)), observations)
    }

    @Test
    fun rankedRouterKeepsExistingEnemyOnRightBehavior() {
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

        assertEquals(
            listOf(
                HeroObservation("Angela", TeamSide.ALLY, 0.94),
                HeroObservation("Lam", TeamSide.ENEMY, 0.94)
            ),
            observations
        )
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
}

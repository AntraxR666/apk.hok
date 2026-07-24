package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftAssistantTest {
    private val heroes = listOf(
        Hero(
            id = "lam",
            name = "Lam",
            role = "Jungler",
            counters = listOf(
                HeroCounter("Donghuang", "Supresión dirigida que corta su entrada."),
                HeroCounter("Liang", "Control dirigido que detiene su movilidad."),
                HeroCounter("Zhang Fei", "Escudos y desplazamiento protegen la retaguardia.")
            ),
            aliases = listOf("Lan"),
            metaScore = 82.0
        ),
        Hero("donghuang", "Donghuang", "Roamer/Support", emptyList(), metaScore = 78.0),
        Hero("liang", "Liang", "Mid Lane", emptyList(), metaScore = 86.0),
        Hero("zhang_fei", "Zhang Fei", "Roamer/Support", emptyList(), metaScore = 66.0),
        Hero("angela", "Angela", "Mid Lane", emptyList(), metaScore = 88.0),
        Hero("hou_yi", "Hou Yi", "Farm Lane", emptyList(), metaScore = 84.0)
    )

    @Test
    fun matcher_recognizesExactNameAliasAndOcrNoise() {
        val matcher = HeroNameMatcher(heroes)

        assertEquals("Lam", matcher.bestMatch("LAM")?.hero?.name)
        assertEquals("Lam", matcher.bestMatch("Lan")?.hero?.name)
        assertEquals("Donghuang", matcher.bestMatch("Donghuarig")?.hero?.name)
        assertTrue(requireNotNull(matcher.bestMatch("Donghuarig")).score >= 0.70)
    }

    @Test
    fun layoutClassifier_mapsScreenEdgesAndCanSwapEnemySide() {
        val rightEnemy = DraftLayoutClassifier(enemyOnRight = true)
        assertEquals(TeamSide.ALLY, rightEnemy.classify(centerX = 80, frameWidth = 1000))
        assertEquals(TeamSide.ENEMY, rightEnemy.classify(centerX = 920, frameWidth = 1000))
        assertEquals(TeamSide.UNKNOWN, rightEnemy.classify(centerX = 500, frameWidth = 1000))

        val leftEnemy = DraftLayoutClassifier(enemyOnRight = false)
        assertEquals(TeamSide.ENEMY, leftEnemy.classify(centerX = 80, frameWidth = 1000))
        assertEquals(TeamSide.ALLY, leftEnemy.classify(centerX = 920, frameWidth = 1000))
    }

    @Test
    fun temporalTracker_requiresRepeatedFramesAndSeparatesTeams() {
        val tracker = TemporalDraftTracker(requiredHits = 2, historySize = 3)

        val first = tracker.observe(
            listOf(
                HeroObservation("Lam", TeamSide.ENEMY, 0.91),
                HeroObservation("Angela", TeamSide.ALLY, 0.94)
            )
        )
        assertTrue(first.enemies.isEmpty())
        assertTrue(first.allies.isEmpty())

        val second = tracker.observe(
            listOf(
                HeroObservation("Lam", TeamSide.ENEMY, 0.93),
                HeroObservation("Angela", TeamSide.ALLY, 0.96)
            )
        )
        assertEquals(listOf("Lam"), second.enemies.map { it.heroName })
        assertEquals(listOf("Angela"), second.allies.map { it.heroName })
    }

    @Test
    fun temporalTracker_choosesTheMostConsistentSideForOneHero() {
        val tracker = TemporalDraftTracker(requiredHits = 2, historySize = 3)
        tracker.observe(listOf(HeroObservation("Lam", TeamSide.UNKNOWN, 0.75)))
        tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.93)))
        val snapshot = tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.95)))
        assertEquals(listOf("Lam"), snapshot.enemies.map { it.heroName })
        assertTrue(snapshot.unknown.isEmpty())
    }

    @Test
    fun recommendationEngine_prefersCounterThatFillsMissingFrontlineAndRespectsRole() {
        val catalog = CounterCatalog(heroes)
        val engine = DraftRecommendationEngine(catalog)
        val snapshot = DraftSnapshot(
            allies = listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.95)),
            enemies = listOf(ConfirmedHero("Lam", TeamSide.ENEMY, 0.96)),
            unknown = emptyList()
        )

        val allRoles = engine.recommend(snapshot, requestedRole = null, limit = 3)
        assertEquals("Donghuang", allRoles.first().hero.name)
        assertTrue(allRoles.first().evidence.any { it.contains("Lam") })
        assertTrue(allRoles.first().evidence.any { it.contains("primera línea") })

        val roamOnly = engine.recommend(snapshot, requestedRole = "Roamer/Support", limit = 3)
        assertFalse(roamOnly.isEmpty())
        assertTrue(roamOnly.all { it.hero.role == "Roamer/Support" })
        assertEquals("Donghuang", roamOnly.first().hero.name)
    }

    @Test
    fun strategyEngine_producesShortActionablePlanFromEvidence() {
        val recommendation = DraftPickRecommendation(
            hero = heroes.first { it.name == "Donghuang" },
            score = 88.0,
            confidence = 0.86,
            evidence = listOf("Contra Lam: supresión dirigida que corta su entrada."),
            coveredEnemies = listOf("Lam")
        )

        val plan = StrategyEngine().build(
            recommendation,
            DraftSnapshot(
                allies = emptyList(),
                enemies = listOf(ConfirmedHero("Lam", TeamSide.ENEMY, 0.95)),
                unknown = emptyList()
            )
        )

        assertTrue(plan.priorityTarget.contains("Lam"))
        assertTrue(plan.teamFight.isNotBlank())
        assertTrue(plan.winCondition.isNotBlank())
        assertTrue(plan.asLines().size <= 6)
    }
    @Test
    fun assistantStagePolicy_keepsManualControlAndOnlySuggestsTransitions() {
        val paused = AssistantStagePolicy.forStage(AssistantStage.PAUSED)
        assertFalse(paused.shouldProcessFrames)
        assertFalse(paused.shouldShowStrategy)

        val draft = AssistantStagePolicy.forStage(AssistantStage.DRAFT)
        assertTrue(draft.shouldRunDraftVision)
        assertFalse(draft.shouldShowStrategy)

        val inGame = AssistantStagePolicy.forStage(AssistantStage.IN_GAME)
        assertFalse(inGame.shouldProcessFrames)
        assertTrue(inGame.shouldShowStrategy)

        assertEquals(
            AssistantStage.IN_GAME,
            AssistantStagePolicy.suggest(AssistantStage.DRAFT, DetectedScene.IN_GAME)
        )
        assertEquals(
            AssistantStage.DRAFT,
            AssistantStagePolicy.suggest(AssistantStage.IN_GAME, DetectedScene.DRAFT)
        )
        assertEquals(
            null,
            AssistantStagePolicy.suggest(AssistantStage.PAUSED, DetectedScene.DRAFT)
        )
    }

}

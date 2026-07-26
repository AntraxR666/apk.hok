package com.example.honorofkingsassistant

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ItemRecommendationEngineTest {
    private val engine = ItemRecommendationEngine(ItemCatalog.parse(asset().readText()))
    private val mage = Hero("angela", "Angela", "Mid Lane", emptyList())

    @Test fun heavyMagicDamageRaisesMagicDefensePriority() {
        val plan = engine.recommend(ItemRecommendationContext(mage, MatchMode.RANKED_DRAFT, enemyThreats = listOf(EnemyThreat.MAGIC_BURST, EnemyThreat.MAGIC_BURST)))
        assertTrue("magic_defense" in plan.nextItems.first().tags)
        assertTrue(plan.evidence.any { it.contains("daño mágico") })
    }

    @Test fun healingCompositionRaisesAntiHealPriority() {
        val plan = engine.recommend(ItemRecommendationContext(mage, MatchMode.RANKED_DRAFT, enemyThreats = listOf(EnemyThreat.SUSTAIN, EnemyThreat.HEALING)))
        assertTrue(plan.nextItems.take(2).any { "anti_heal" in it.tags })
    }

    @Test fun normalBlindWithoutEnemiesReturnsSafeCoreBuild() {
        val plan = engine.recommend(ItemRecommendationContext(mage, MatchMode.NORMAL_BLIND))
        assertTrue(plan.nextItems.isNotEmpty())
        assertTrue(plan.evidence.any { it.contains("Núcleo seguro") })
    }

    private fun asset() = listOf(File("src/main/assets/hok_items.json"), File("app/src/main/assets/hok_items.json")).first { it.isFile }
}

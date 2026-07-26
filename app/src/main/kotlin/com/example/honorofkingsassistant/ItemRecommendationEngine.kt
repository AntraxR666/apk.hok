package com.example.honorofkingsassistant

class ItemRecommendationEngine(private val catalog: ItemCatalog) {
    fun recommend(context: ItemRecommendationContext): ItemPlan {
        val evidence = mutableListOf<String>()
        val score = linkedMapOf<String, Double>()
        fun add(tag: String, points: Double, reason: String) {
            catalog.withTag(tag).forEach { item -> score[item.id] = (score[item.id] ?: 0.0) + points }
            evidence += reason
        }
        val role = context.playerHero?.role.orEmpty()
        when {
            role.contains("Mid", ignoreCase = true) -> add("mage_core", 5.0, "Núcleo seguro para mago")
            role.contains("Farm", ignoreCase = true) -> add("marksman_core", 5.0, "Núcleo seguro para tirador")
            role.contains("Jung", ignoreCase = true) -> add("jungler_core", 5.0, "Núcleo seguro para jungla")
            else -> add("physical_defense", 1.5, "Núcleo seguro de resistencia")
        }
        val threats = context.enemyThreats
        if (threats.count { it == EnemyThreat.MAGIC_BURST } >= 1) add("magic_defense", 6.0, "Prioridad por daño mágico enemigo")
        if (threats.count { it == EnemyThreat.PHYSICAL_BURST } >= 2) add("physical_defense", 5.0, "Prioridad por daño físico enemigo")
        if (threats.any { it == EnemyThreat.HEALING || it == EnemyThreat.SUSTAIN }) add("anti_heal", 6.0, "Prioridad anti-curación contra sustain enemigo")
        if (threats.any { it == EnemyThreat.TANK }) add("anti_tank", 4.0, "Daño sostenido contra front line resistente")
        if (context.matchMode == MatchMode.NORMAL_BLIND && context.enemies.isEmpty()) evidence += "Núcleo seguro: aún no hay enemigos visibles"

        val available = score.mapNotNull { (id, value) -> catalog.find(id)?.let { it to value } }
            .filter { it.first.id !in context.currentItemIds }
            .filter { context.gold == null || it.first.cost <= context.gold || it.first.tier == 1 }
            .sortedWith(compareByDescending<Pair<HoKItem, Double>> { it.second }.thenBy { it.first.cost })
        val next = available.take(3).map { it.first }
        val alternatives = available.drop(3).take(2).map { it.first }
        return ItemPlan(next, alternatives, emptyList(), evidence.distinct(),
            if (context.enemies.isEmpty()) 0.55 else 0.80)
    }
}

package com.example.honorofkingsassistant

enum class EnemyThreat { MAGIC_BURST, PHYSICAL_BURST, HEALING, SUSTAIN, TANK, CROWD_CONTROL }

data class HoKItem(val id: String, val name: String, val cost: Int, val tier: Int, val tags: Set<String>)

data class ItemRecommendationContext(
    val playerHero: Hero?,
    val matchMode: MatchMode,
    val allies: List<ConfirmedHero> = emptyList(),
    val enemies: List<ConfirmedHero> = emptyList(),
    val enemyThreats: List<EnemyThreat> = emptyList(),
    val currentItemIds: Set<String> = emptySet(),
    val gold: Int? = null
)

data class ItemPlan(
    val nextItems: List<HoKItem>,
    val alternatives: List<HoKItem>,
    val sellOrReplace: List<HoKItem>,
    val evidence: List<String>,
    val confidence: Double
)

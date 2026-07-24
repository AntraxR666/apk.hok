package com.example.honorofkingsassistant

import java.text.Normalizer
import java.util.Locale

data class HeroCounter(
    val heroName: String,
    val reason: String,
    val source: String = "legacy_catalog",
    val confidence: Double = 0.65
)

data class Hero(
    val id: String,
    val name: String,
    val role: String,
    val counters: List<HeroCounter>,
    val aliases: List<String> = emptyList(),
    val metaScore: Double? = null,
    val source: String = "global_catalog",
    val patchLabel: String? = null,
    val snapshotDate: String? = null
)

data class HeroRecommendation(
    val name: String,
    val role: String,
    val score: Double,
    val reasons: List<String>
) {
    val justification: String
        get() = reasons.joinToString("; ")
}

class CounterCatalog(
    heroes: List<Hero>
) {
    val heroes: List<Hero> = heroes.toList()

    private val heroesByName: Map<String, Hero>
    private val heroesById: Map<String, Hero>
    private val heroesByAlias: Map<String, Hero>

    init {
        require(this.heroes.map { normalize(it.id) }.distinct().size == this.heroes.size) {
            "Los IDs de héroe deben ser únicos"
        }
        require(this.heroes.map { normalize(it.name) }.distinct().size == this.heroes.size) {
            "Los nombres de héroe deben ser únicos"
        }
        require(this.heroes.all { it.id.isNotBlank() && it.name.isNotBlank() && it.role.isNotBlank() }) {
            "Todos los héroes deben tener id, nombre y rol"
        }
        require(this.heroes.all { hero ->
            hero.counters.all { it.heroName.isNotBlank() && it.reason.isNotBlank() }
        }) {
            "Todos los counters deben incluir héroe y razón"
        }

        heroesByName = this.heroes.associateBy { normalize(it.name) }
        heroesById = this.heroes.associateBy { normalize(it.id) }
        heroesByAlias = buildMap {
            this@CounterCatalog.heroes.forEach { hero ->
                hero.aliases.forEach { alias ->
                    val key = normalize(alias)
                    require(key.isNotBlank()) { "Los alias no pueden estar vacíos" }
                    require(key !in this) { "Los alias de héroe deben ser únicos: $alias" }
                    put(key, hero)
                }
            }
        }
    }

    fun roles(): List<String> = heroes
        .map { it.role }
        .distinct()
        .sorted()

    fun findHero(query: String): Hero? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return null
        return heroesByName[normalizedQuery]
            ?: heroesById[normalizedQuery]
            ?: heroesByAlias[normalizedQuery]
    }

    fun heroesForRole(role: String?): List<Hero> {
        val normalizedRole = normalize(role.orEmpty())
        return heroes
            .asSequence()
            .filter {
                normalizedRole.isBlank() ||
                    normalizedRole == "todos" ||
                    normalize(it.role) == normalizedRole
            }
            .sortedBy { it.name }
            .toList()
    }

    fun countersFor(enemyNameOrId: String): List<HeroCounter> =
        findHero(enemyNameOrId)?.counters.orEmpty()

    fun recommendFor(enemyNamesOrIds: List<String>): List<HeroRecommendation> {
        data class Aggregate(
            val name: String,
            val role: String,
            val reasons: MutableList<String>,
            var hits: Int,
            val firstSeen: Int
        )

        val aggregates = linkedMapOf<String, Aggregate>()
        var sequence = 0

        enemyNamesOrIds
            .mapNotNull(::findHero)
            .forEach { enemy ->
                enemy.counters.forEach { counter ->
                    val key = normalize(counter.heroName)
                    val counterHero = heroesByName[key]
                    val aggregate = aggregates.getOrPut(key) {
                        Aggregate(
                            name = counter.heroName,
                            role = counterHero?.role.orEmpty(),
                            reasons = mutableListOf(),
                            hits = 0,
                            firstSeen = sequence++
                        )
                    }
                    aggregate.hits += 1
                    aggregate.reasons += "Contra ${enemy.name}: ${counter.reason}"
                }
            }

        return aggregates.values
            .sortedWith(compareByDescending<Aggregate> { it.hits }.thenBy { it.firstSeen })
            .map {
                HeroRecommendation(
                    name = it.name,
                    role = it.role,
                    score = it.hits * 10.0,
                    reasons = it.reasons.toList()
                )
            }
    }

    companion object {
        fun normalize(value: String): String {
            val withoutDiacritics = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")
            return withoutDiacritics
                .lowercase(Locale.ROOT)
                .replace("\\s+".toRegex(), " ")
        }
    }
}

package com.example.honorofkingsassistant

import org.json.JSONException
import org.json.JSONObject

object CounterCatalogJson {
    fun parse(json: String): CounterCatalog {
        try {
            val root = JSONObject(json)
            val heroesArray = root.getJSONArray("heroes")
            val heroes = buildList {
                for (index in 0 until heroesArray.length()) {
                    val heroObject = heroesArray.getJSONObject(index)
                    val countersArray = heroObject.getJSONArray("counters")
                    val counters = buildList {
                        for (counterIndex in 0 until countersArray.length()) {
                            val counterObject = countersArray.getJSONObject(counterIndex)
                            add(
                                HeroCounter(
                                    heroName = counterObject.getString("hero_name").trim(),
                                    reason = counterObject.getString("reason").trim(),
                                    source = counterObject.optString("source", "legacy_catalog").trim(),
                                    confidence = counterObject.optDouble("confidence", 0.65)
                                )
                            )
                        }
                    }
                    add(
                        Hero(
                            id = heroObject.getString("id").trim(),
                            name = heroObject.getString("name").trim(),
                            role = heroObject.getString("role").trim(),
                            counters = counters,
                            aliases = heroObject.optJSONArray("aliases")?.let { aliasesArray ->
                                buildList {
                                    for (aliasIndex in 0 until aliasesArray.length()) {
                                        add(aliasesArray.getString(aliasIndex).trim())
                                    }
                                }
                            }.orEmpty(),
                            metaScore = if (heroObject.has("meta_score")) heroObject.optDouble("meta_score") else null,
                            source = heroObject.optString("source", "global_catalog").trim(),
                            patchLabel = heroObject.optString("patch_label").trim().ifBlank { null },
                            snapshotDate = heroObject.optString("snapshot_date").trim().ifBlank { null }
                        )
                    )
                }
            }

            validateFullAsset(heroes)
            return CounterCatalog(heroes)
        } catch (error: JSONException) {
            throw IllegalArgumentException("El catálogo JSON no tiene el formato esperado", error)
        }
    }

    private fun validateFullAsset(heroes: List<Hero>) {
        require(heroes.isNotEmpty()) { "El catálogo no contiene héroes" }
        require(heroes.all { it.counters.size == 3 }) {
            "Cada héroe debe tener exactamente tres counters"
        }

        val heroNames = heroes.map { CounterCatalog.normalize(it.name) }.toSet()
        val missingReferences = heroes
            .flatMap { hero -> hero.counters.map { it.heroName } }
            .filter { CounterCatalog.normalize(it) !in heroNames }
            .distinct()

        require(missingReferences.isEmpty()) {
            "Counters con referencias inexistentes: ${missingReferences.joinToString()}"
        }
    }
}

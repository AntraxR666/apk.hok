package com.example.honorofkingsassistant

import android.content.Context
import android.util.Log
import java.io.IOException

class CounterEngine(
    context: Context,
    assetFileName: String = "hok_counters.json"
) {
    val catalog: CounterCatalog
    val loadError: String?

    init {
        var errorMessage: String? = null
        catalog = try {
            val json = context.assets.open(assetFileName)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            CounterCatalogJson.parse(json)
        } catch (error: IOException) {
            errorMessage = "No se pudo leer el catálogo de héroes"
            Log.e(TAG, errorMessage, error)
            CounterCatalog(emptyList())
        } catch (error: IllegalArgumentException) {
            errorMessage = error.message ?: "El catálogo de héroes no es válido"
            Log.e(TAG, errorMessage, error)
            CounterCatalog(emptyList())
        }
        loadError = errorMessage
    }

    fun allHeroes(): List<Hero> = catalog.heroesForRole(null)

    fun roles(): List<String> = catalog.roles()

    fun heroesForRole(role: String?): List<Hero> = catalog.heroesForRole(role)

    fun findHero(query: String): Hero? = catalog.findHero(query)

    fun getCounters(enemyNameOrId: String): List<HeroCounter> =
        catalog.countersFor(enemyNameOrId)

    fun getBestCounter(enemyNameOrId: String): HeroCounter? =
        getCounters(enemyNameOrId).firstOrNull()

    fun getHeroRecommendations(enemyHeroes: List<String>): List<HeroRecommendation> =
        catalog.recommendFor(enemyHeroes)

    companion object {
        private const val TAG = "CounterEngine"
    }
}

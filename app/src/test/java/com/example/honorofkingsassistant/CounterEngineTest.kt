package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CounterEngineTest {
    private val sampleCatalog = CounterCatalog(
        listOf(
            Hero(
                id = "annette",
                name = "Annette",
                role = "Roamer/Support",
                counters = listOf(
                    HeroCounter("Donghuang", "Supresión dirigida."),
                    HeroCounter("Da Qiao", "Silencio y retirada."),
                    HeroCounter("Nezha", "Reduce curación.")
                )
            ),
            Hero("donghuang", "Donghuang", "Roamer/Support", emptyList()),
            Hero("da_qiao", "Da Qiao", "Roamer/Support", emptyList()),
            Hero("nezha", "Nezha", "Clash Lane", emptyList())
        )
    )

    @Test
    fun findHero_isCaseAndWhitespaceInsensitive() {
        assertEquals("Annette", sampleCatalog.findHero("  aNNeTTe  ")?.name)
    }

    @Test
    fun findHero_acceptsId() {
        assertEquals("Da Qiao", sampleCatalog.findHero("da_qiao")?.name)
    }

    @Test
    fun countersFor_returnsOrderedCountersWithReasons() {
        val counters = sampleCatalog.countersFor("Annette")
        assertEquals(listOf("Donghuang", "Da Qiao", "Nezha"), counters.map { it.heroName })
        assertTrue(counters.all { it.reason.isNotBlank() })
    }

    @Test
    fun heroesForRole_filtersAndSorts() {
        val heroes = sampleCatalog.heroesForRole("Roamer/Support")
        assertEquals(listOf("Annette", "Da Qiao", "Donghuang"), heroes.map { it.name })
    }

    @Test
    fun recommendFor_aggregatesMultipleEnemies() {
        val catalog = CounterCatalog(
            listOf(
                Hero("a", "A", "Mid Lane", listOf(HeroCounter("C", "A reason"), HeroCounter("D", "D reason"))),
                Hero("b", "B", "Farm Lane", listOf(HeroCounter("C", "B reason"))),
                Hero("c", "C", "Jungler", emptyList()),
                Hero("d", "D", "Clash Lane", emptyList())
            )
        )
        val recommendations = catalog.recommendFor(listOf("A", "B"))
        assertEquals("C", recommendations.first().name)
        assertEquals(20.0, recommendations.first().score, 0.0)
        assertEquals(2, recommendations.first().reasons.size)
    }

    @Test
    fun fullAsset_contains116ValidHeroes() {
        val assetFile = listOf(
            File("src/main/assets/hok_counters.json"),
            File("app/src/main/assets/hok_counters.json")
        ).firstOrNull { it.isFile }

        assertNotNull("No se encontro hok_counters.json desde el directorio de pruebas", assetFile)
        val json = requireNotNull(assetFile).readText(Charsets.UTF_8)
        val catalog = CounterCatalogJson.parse(json)
        assertEquals(116, catalog.heroes.size)
        assertEquals(116, catalog.heroes.map { it.id }.toSet().size)
        assertEquals(116, catalog.heroes.map { it.name }.toSet().size)
        assertTrue(catalog.heroes.all { it.counters.size == 3 })
        assertNotNull(catalog.findHero("Annette"))
    }
}

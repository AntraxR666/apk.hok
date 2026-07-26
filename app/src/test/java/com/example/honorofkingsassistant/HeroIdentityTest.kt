package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.io.File

class HeroIdentityTest {
    @Test
    fun spanishDisplayTitlesResolveToCanonicalHeroes() {
        val catalog = catalog()

        val expectedNamesByTitle = mapOf(
            "La Maga de Fuego" to "Angela",
            "El Arma Suprema" to "Bai Qi",
            "Puño de la Paz" to "Flowborn (Tank)",
            "Corazón Arcano" to "Flowborn (Mage)",
            "El Francotirador" to "Shouyue",
            "La Hoz Justiciera" to "Xuance",
            "El Sumo Sacerdote" to "Augran",
            "El Boticario" to "Dr Bian",
            "La Ninja de Fuego" to "Mai Shiranui",
            "La Alegre Canción" to "Cai Yan",
            "El Conquistador" to "Fatih",
            "El Último Lobo" to "Chano"
        )

        expectedNamesByTitle.forEach { (title, expectedName) ->
            assertEquals(expectedName, catalog.findHero(title)?.name)
        }
    }

    @Test
    fun aliasesDoNotCreateDuplicateHeroes() {
        val catalog = catalog()

        assertEquals(116, catalog.heroes.size)
        assertSame(catalog.findHero("Angela"), catalog.findHero("La Maga de Fuego"))
        assertSame(catalog.findHero("Dr Bian"), catalog.findHero("Dr. Bian"))
    }

    private fun catalog(): CounterCatalog {
        val assetFile = listOf(
            File("src/main/assets/hok_counters.json"),
            File("app/src/main/assets/hok_counters.json")
        ).first { it.isFile }
        return CounterCatalogJson.parse(assetFile.readText(Charsets.UTF_8))
    }
}

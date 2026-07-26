package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class HeroNameMatcherTest {
    private val matcher = HeroNameMatcher(
        listOf(
            hero(
                id = "angela",
                name = "Angela",
                displayTitles = listOf("La Maga de Fuego")
            ),
            hero(id = "almost_fire_mage", name = "La Maga de Fuega"),
            hero(
                id = "flowborn_tank",
                name = "Flowborn (Tank)",
                displayTitles = listOf("Puño de la Paz")
            ),
            hero(
                id = "dr_bian",
                name = "Dr. Bian",
                displayTitles = listOf("El Boticario")
            )
        )
    )

    @Test
    fun exactLocalizedAliasWinsBeforeFuzzyCanonicalName() {
        val match = requireNotNull(matcher.match("La Maga de Fuego"))

        assertEquals("Angela", match.hero.name)
        assertEquals(MatchKind.EXACT_ALIAS, match.kind)
        assertEquals(1.0, match.score, 0.0)
    }

    @Test
    fun accentsPunctuationAndWhitespaceAreNormalized() {
        assertEquals("Flowborn (Tank)", matcher.match("  PUÑO  DE LA PAZ ")?.hero?.name)
        assertEquals("Dr. Bian", matcher.match("El Boticario")?.hero?.name)
        assertEquals(MatchKind.EXACT_CANONICAL, matcher.match("Dr Bian")?.kind)
    }

    @Test
    fun genericWordDoesNotResolveAngela() {
        assertNull(matcher.match("Maga"))
    }

    @Test
    fun fuzzyMatchingRunsOnlyAfterExactMapsMiss() {
        val match = matcher.match("La Maga de Fuegq")

        assertEquals("Angela", match?.hero?.name)
        assertEquals(MatchKind.FUZZY, match?.kind)
    }

    @Test
    fun unknownStoreKeepsOneNormalizedEntryWithoutOcrConfidence() {
        val log = newLogFile()
        var now = 10L
        val store = UnknownHeroTextStore(log, clock = { now++ })

        assertTrue(store.record("  Título Épico! ", "ally_slot_1", "mlkit_line_in_hero_roi"))
        assertFalse(store.record("titulo epico", "ally_slot_1", "mlkit_line_in_hero_roi"))

        val entries = store.entries()
        assertEquals(1, entries.size)
        assertEquals("titulo epico", entries.single().normalizedText)
        assertEquals(2, entries.single().occurrences)
        assertFalse(log.readText().contains("confidence", ignoreCase = true))
    }

    @Test
    fun unknownStoreRejectsUnsafeEvidenceAndLength() {
        val store = UnknownHeroTextStore(newLogFile())

        assertFalse(store.record("abc", "ally_slot_1", "mlkit_line_in_hero_roi"))
        assertFalse(store.record("a".repeat(49), "ally_slot_1", "mlkit_line_in_hero_roi"))
        assertFalse(store.record("Unknown Hero", "ally_slot_1", "mlkit_line"))
        assertFalse(store.record("Unknown Hero", "", "mlkit_line_in_hero_roi"))
        assertTrue(store.entries().isEmpty())
    }

    @Test
    fun unknownStoreCapsUniqueEntriesAndPersistsThem() {
        val log = newLogFile()
        var now = 100L
        val store = UnknownHeroTextStore(log, maxEntries = 2, clock = { now++ })

        assertTrue(store.record("First Hero", "ally_slot_1", "mlkit_line_in_hero_roi"))
        assertTrue(store.record("Second Hero", "ally_slot_2", "mlkit_line_in_hero_roi"))
        assertTrue(store.record("Third Hero", "enemy_slot_1", "mlkit_line_in_hero_roi"))

        val reloaded = UnknownHeroTextStore(log, maxEntries = 2).entries()
        assertEquals(listOf("second hero", "third hero"), reloaded.map { it.normalizedText })
    }

    private fun hero(
        id: String,
        name: String,
        displayTitles: List<String> = emptyList()
    ) = Hero(
        id = id,
        name = name,
        role = "Test",
        counters = emptyList(),
        identityAliases = HeroIdentityAliases(displayTitles = displayTitles)
    )

    private fun newLogFile(): File {
        val directory = Files.createTempDirectory("unknown-hero-text-store").toFile()
        return File(directory, "unknown_hero_text.json")
    }
}

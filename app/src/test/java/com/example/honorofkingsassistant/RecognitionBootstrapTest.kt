package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognitionBootstrapTest {
    @Test
    fun emptyPortraitTemplateStoreReportsUncalibrated() {
        val store = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())

        assertEquals(RecognitionReadiness.UNCALIBRATED, store.readiness())
    }

    @Test
    fun galleryObservationReturnsProposalWithoutTreatingCardAsPickedOrPersistingIt() {
        val store = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())
        val engine = GalleryCalibrationEngine(listOf(angela()))
        val portrait = GalleryPortraitEvidence(
            cardIndex = 1,
            bounds = PixelRect(100, 40, 160, 100),
            fingerprint = fingerprint(11)
        )

        val proposals = engine.observe(
            portraits = listOf(portrait),
            titleLines = listOf(PositionedTextLine("La Maga de Fuego", 130, 112))
        )

        assertEquals("Angela", proposals.single().heroName)
        assertFalse(proposals.single().persistenceEligible)
        assertTrue(store.templates().isEmpty())
        assertTrue(proposals.single().selectedHeroObservations.isEmpty())
    }

    @Test
    fun galleryRejectsUnpairedOrFuzzyTitleEvidence() {
        val engine = GalleryCalibrationEngine(listOf(angela()))
        val portrait = GalleryPortraitEvidence(
            cardIndex = 1,
            bounds = PixelRect(100, 40, 160, 100),
            fingerprint = fingerprint(12)
        )

        assertTrue(
            engine.observe(
                portraits = listOf(portrait),
                titleLines = listOf(PositionedTextLine("La Maga de Fueg", 130, 112))
            ).isEmpty()
        )
        assertTrue(
            engine.observe(
                portraits = listOf(portrait),
                titleLines = listOf(PositionedTextLine("La Maga de Fuego", 300, 112))
            ).isEmpty()
        )
    }

    @Test
    fun repeatedStableGalleryEvidenceOrExplicitConfirmationCanPersistVariants() {
        val store = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())
        val engine = GalleryCalibrationEngine(
            heroes = listOf(angela()),
            requiredStableObservations = 3
        )
        val firstVariant = GalleryPortraitEvidence(
            cardIndex = 1,
            bounds = PixelRect(100, 40, 160, 100),
            fingerprint = fingerprint(20)
        )
        val title = listOf(PositionedTextLine("La Maga de Fuego", 130, 112))

        val first = engine.observe(listOf(firstVariant), title).single()
        assertFalse(engine.persist(first, store, explicitlyConfirmed = false))
        engine.observe(listOf(firstVariant), title)
        val stable = engine.observe(listOf(firstVariant), title).single()
        assertTrue(stable.persistenceEligible)
        assertTrue(engine.persist(stable, store, explicitlyConfirmed = false))

        val secondVariant = engine.observe(
            portraits = listOf(firstVariant.copy(fingerprint = fingerprint(40))),
            titleLines = title
        ).single()
        assertTrue(engine.persist(secondVariant, store, explicitlyConfirmed = true))

        assertEquals(2, store.templates().getValue("angela").size)
        assertEquals(RecognitionReadiness.READY, store.readiness())
    }

    private fun angela() = Hero(
        id = "angela",
        name = "Angela",
        role = "Mid",
        counters = emptyList(),
        identityAliases = HeroIdentityAliases(displayTitles = listOf("La Maga de Fuego"))
    )

    private fun fingerprint(seed: Int) = PortraitFingerprint(
        averageHash = seed.toLong(),
        colorSignature = IntArray(PortraitFingerprint.COLOR_SIGNATURE_SIZE) { seed }
    )

    private class InMemoryPortraitTemplatePersistence : PortraitTemplatePersistence {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(value: String?) {
            this.value = value
        }
    }
}

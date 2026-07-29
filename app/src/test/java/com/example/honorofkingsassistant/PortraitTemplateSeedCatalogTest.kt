package com.example.honorofkingsassistant

import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitTemplateSeedCatalogTest {
    @Test
    fun seededTemplatesMakeAFirstInstallReadyWithoutPersistingImages() {
        val seed = PortraitTemplateSeedCatalog.parse(
            """
            {
              "domain":"DRAFT_PORTRAIT",
              "templates":{"angela":["0000000000000001:1,1,1,1,1,1,1,1,1,1,1,1"]}
            }
            """.trimIndent()
        )
        val persistence = InMemoryPortraitTemplatePersistence()
        val store = PortraitTemplateStore(persistence, seed)

        assertEquals(RecognitionReadiness.READY, store.readiness())
        assertEquals(1, store.calibrationState().storedTemplateCount)
        assertEquals(1, store.calibrationState().coveredHeroCount)
        assertEquals(1, store.templates().getValue("angela").size)
        assertTrue(store.learn("Angela", fingerprint(2)))
        assertEquals(2, store.templates().getValue("angela").size)

        store.clear()

        assertTrue(persistence.read() == null)
        assertEquals(1, store.templates().getValue("angela").size)
    }

    @Test
    fun productionSeedIsSelectionOnlyAndCoversTheVerifiedPublicSet() {
        val raw = File("src/main/assets/draft_portrait_seed_v1.json").readText()
        val seed = PortraitTemplateSeedCatalog.parse(raw)
        val root = JSONObject(raw)
        val angela = root.getJSONObject("templates").getJSONArray("angela")

        assertEquals(2, root.getInt("schema_version"))
        assertEquals(setOf(PortraitTemplateDomain.DRAFT_PORTRAIT), seed.keys)
        assertEquals(111, seed.getValue(PortraitTemplateDomain.DRAFT_PORTRAIT).size)
        assertTrue(seed.getValue(PortraitTemplateDomain.DRAFT_PORTRAIT).containsKey("angela"))
        assertTrue(angela.length() >= 5)
        repeat(angela.length()) { index ->
            assertTrue(angela.getString(index).startsWith("v2:"))
        }
    }

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

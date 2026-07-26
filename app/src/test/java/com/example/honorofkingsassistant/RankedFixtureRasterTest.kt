package com.example.honorofkingsassistant

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankedFixtureRasterTest {
    @Test
    fun realPickFrameProducesTenSideRegionsAndSeparatesLockFromPlaceholder() {
        val raster = fixtureRaster("pick_partial_0104.png")

        val slots = RankedRasterRoiAnalyzer.sideSlots(raster, enemyOnRight = true)

        assertEquals(848, raster.width)
        assertEquals(392, raster.height)
        assertEquals(10, slots.size)
        assertEquals((1..5).toList(), slots.filter { it.side == TeamSide.ALLY }.map { it.slotIndex })
        assertEquals((1..5).toList(), slots.filter { it.side == TeamSide.ENEMY }.map { it.slotIndex })

        val lockedAlly = slots.first { it.side == TeamSide.ALLY && it.slotIndex == 1 }
        val r95Placeholder = slots.first { it.side == TeamSide.ALLY && it.slotIndex == 4 }
        assertEquals(DraftSlotStatus.CONFIRMED, lockedAlly.status)
        assertEquals(DraftSlotStatus.EMPTY, r95Placeholder.status)
        assertTrue(
            lockedAlly.signals.confirmationMarkerScore >
                r95Placeholder.signals.confirmationMarkerScore
        )
    }

    @Test
    fun realR95PickTurnKeepsMeasuredRowFourGeometryWithoutConfirmingAvatar() {
        val raster = fixtureRaster("r95_pick_turn_0148.png")

        val r95Row = RankedRasterRoiAnalyzer
            .sideSlots(raster, enemyOnRight = true)
            .first { it.side == TeamSide.ALLY && it.slotIndex == 4 }
        val r95NameRegion = HoKGlobalLandscapeProfile
            .playerNameRegions(physicalLeft = true)[3]
            .toPixelRect(raster.width, raster.height)

        assertEquals(PixelRect(53, 232, 101, 279), r95Row.portraitRegion)
        assertEquals(PixelRect(89, 224, 293, 260), r95NameRegion)
        assertTrue(92 in r95NameRegion.left until r95NameRegion.right)
        assertTrue(238 in r95NameRegion.top until r95NameRegion.bottom)
        assertNotEquals(DraftSlotStatus.CONFIRMED, r95Row.status)
        assertTrue(r95Row.signals.portrait.edgeDensity > 0.01)
    }

    @Test
    fun realLoadingFrameProducesTwoRowsOfFiveDistinctCardCrops() {
        val raster = fixtureRaster("loading_roster_0245.png")

        val cards = RankedRasterRoiAnalyzer.loadingCards(raster)

        assertEquals(10, cards.size)
        assertEquals(5, cards.count { it.side == TeamSide.ALLY })
        assertEquals(5, cards.count { it.side == TeamSide.ENEMY })
        val allyR95Card = cards.first { it.side == TeamSide.ALLY && it.slotIndex == 4 }
        val enemyCardFour = cards.first { it.side == TeamSide.ENEMY && it.slotIndex == 4 }
        assertEquals(PixelRect(487, 0, 581, 192), allyR95Card.region)
        assertEquals(PixelRect(491, 4, 577, 133), allyR95Card.portraitRegion)
        assertEquals(PixelRect(491, 219, 577, 342), enemyCardFour.portraitRegion)
        assertTrue(534 in allyR95Card.region.left until allyR95Card.region.right)
        assertTrue(150 in allyR95Card.region.top until allyR95Card.region.bottom)
        assertFalse(150 in allyR95Card.portraitRegion.top until allyR95Card.portraitRegion.bottom)
        assertTrue(allyR95Card.visualStats.edgeDensity > 0.01)
        assertTrue(allyR95Card.fingerprint.distance(enemyCardFour.fingerprint) > 0.01)
    }

    @Test
    fun realLoadingPortraitMatchesSameRepresentationTemplateAndRejectsAmbiguity() {
        val card = RankedRasterRoiAnalyzer
            .loadingCards(fixtureRaster("loading_roster_0245.png"))
            .first { it.side == TeamSide.ALLY && it.slotIndex == 4 }
        val slot = SlotPortraitFingerprint(
            side = card.side,
            slotIndex = card.slotIndex,
            fingerprint = card.fingerprint,
            visualConfidence = card.visualConfidence
        )
        val draftTemplateStore = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())
        assertTrue(draftTemplateStore.learn("Angela", card.fingerprint))
        val draftTemplateMatcher = HeroPortraitMatcher(
            CounterCatalog(listOf(hero("angela", "Angela"))),
            draftTemplateStore
        )
        assertTrue(
            draftTemplateMatcher.matchSlots(
                listOf(slot),
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            ).isEmpty()
        )

        val singleTemplateStore = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())
        assertTrue(
            singleTemplateStore.learn(
                "Angela",
                card.fingerprint,
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            )
        )
        val singleTemplateMatcher = HeroPortraitMatcher(
            CounterCatalog(listOf(hero("angela", "Angela"))),
            singleTemplateStore
        )

        val match = singleTemplateMatcher.matchSlots(
            listOf(slot),
            PortraitTemplateDomain.LOADING_CARD_PORTRAIT
        ).single()

        assertEquals("Angela", match.heroName)
        assertEquals(4, match.slotIndex)

        val ambiguousStore = PortraitTemplateStore(InMemoryPortraitTemplatePersistence())
        assertTrue(
            ambiguousStore.learn(
                "Angela",
                card.fingerprint,
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            )
        )
        assertTrue(
            ambiguousStore.learn(
                "Lam",
                card.fingerprint,
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            )
        )
        val ambiguousMatcher = HeroPortraitMatcher(
            CounterCatalog(listOf(hero("angela", "Angela"), hero("lam", "Lam"))),
            ambiguousStore
        )

        assertTrue(
            ambiguousMatcher.matchSlots(
                listOf(slot),
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            ).isEmpty()
        )
    }

    private fun hero(id: String, name: String) = Hero(
        id = id,
        name = name,
        role = "Test",
        counters = emptyList()
    )

    private class InMemoryPortraitTemplatePersistence : PortraitTemplatePersistence {
        private var value: String? = null

        override fun read(): String? = value

        override fun write(value: String?) {
            this.value = value
        }
    }

    private fun fixtureRaster(filename: String): ArgbRaster {
        val bytes = requireNotNull(
            javaClass.classLoader?.getResourceAsStream("ranked_mode/$filename")
        ).use { it.readBytes() }
        return PngArgbRaster(bytes)
    }

    private class PngArgbRaster(bytes: ByteArray) : ArgbRaster {
        override val width = bytes.int32(16)
        override val height = bytes.int32(20)
        private val pixels = IntArray(width * height)

        init {
            require(bytes[24].toInt() == 8)
            require(bytes[25].toInt() == 2)
            require(bytes[28].toInt() == 0)
            val compressed = ByteArrayOutputStream()
            var chunkOffset = 8
            while (chunkOffset + 12 <= bytes.size) {
                val length = bytes.int32(chunkOffset)
                val type = bytes.copyOfRange(chunkOffset + 4, chunkOffset + 8)
                    .toString(Charsets.US_ASCII)
                if (type == "IDAT") {
                    compressed.write(bytes, chunkOffset + 8, length)
                }
                chunkOffset += length + 12
                if (type == "IEND") break
            }
            val inflated = InflaterInputStream(
                ByteArrayInputStream(compressed.toByteArray())
            ).use { it.readBytes() }
            val bytesPerPixel = 3
            val rowSize = width * bytesPerPixel
            var sourceOffset = 0
            var previous = IntArray(rowSize)
            repeat(height) { y ->
                val filter = inflated[sourceOffset++].toInt() and 0xff
                val current = IntArray(rowSize)
                for (index in 0 until rowSize) {
                    val raw = inflated[sourceOffset++].toInt() and 0xff
                    val left = if (index >= bytesPerPixel) current[index - bytesPerPixel] else 0
                    val above = previous[index]
                    val upperLeft = if (index >= bytesPerPixel) {
                        previous[index - bytesPerPixel]
                    } else {
                        0
                    }
                    val predictor = when (filter) {
                        0 -> 0
                        1 -> left
                        2 -> above
                        3 -> (left + above) / 2
                        4 -> paeth(left, above, upperLeft)
                        else -> error("Unsupported PNG filter: $filter")
                    }
                    current[index] = (raw + predictor) and 0xff
                }
                repeat(width) { x ->
                    val pixelOffset = x * bytesPerPixel
                    pixels[y * width + x] =
                        (0xff shl 24) or
                        (current[pixelOffset] shl 16) or
                        (current[pixelOffset + 1] shl 8) or
                        current[pixelOffset + 2]
                }
                previous = current
            }
        }

        override fun argb(x: Int, y: Int): Int = pixels[y * width + x]

        private fun paeth(left: Int, above: Int, upperLeft: Int): Int {
            val estimate = left + above - upperLeft
            val leftDistance = kotlin.math.abs(estimate - left)
            val aboveDistance = kotlin.math.abs(estimate - above)
            val upperLeftDistance = kotlin.math.abs(estimate - upperLeft)
            return when {
                leftDistance <= aboveDistance && leftDistance <= upperLeftDistance -> left
                aboveDistance <= upperLeftDistance -> above
                else -> upperLeft
            }
        }
    }

    companion object {
        private fun ByteArray.int32(offset: Int): Int =
            ((this[offset].toInt() and 0xff) shl 24) or
                ((this[offset + 1].toInt() and 0xff) shl 16) or
                ((this[offset + 2].toInt() and 0xff) shl 8) or
                (this[offset + 3].toInt() and 0xff)
    }
}

package com.example.honorofkingsassistant

import android.graphics.Bitmap

data class SlotHeroMatch(
    val heroName: String,
    val side: TeamSide,
    val slotIndex: Int,
    val confidence: Double
)

class HeroPortraitMatcher(
    private val catalog: CounterCatalog,
    private val store: PortraitTemplateStore
) {
    fun fingerprints(bitmap: Bitmap, enemyOnRight: Boolean): List<SlotPortraitFingerprint> {
        val output = mutableListOf<SlotPortraitFingerprint>()
        listOf(true, false).forEach { physicalLeft ->
            val side = when {
                physicalLeft && enemyOnRight -> TeamSide.ALLY
                physicalLeft -> TeamSide.ENEMY
                enemyOnRight -> TeamSide.ENEMY
                else -> TeamSide.ALLY
            }
            HoKGlobalLandscapeProfile.portraitRegions(physicalLeft).forEachIndexed { index, region ->
                val normalized = normalizedPixels(bitmap, region)
                val visualConfidence = visualConfidence(normalized)
                if (visualConfidence >= MIN_VISUAL_CONFIDENCE) {
                    output += SlotPortraitFingerprint(
                        side = side,
                        slotIndex = index + 1,
                        fingerprint = PortraitFingerprint.fromArgb64(normalized),
                        visualConfidence = visualConfidence
                    )
                }
            }
        }
        return output
    }

    fun normalFingerprints(bitmap: Bitmap): List<SlotPortraitFingerprint> =
        NormalSelectionGeometry.forFrame(bitmap.width, bitmap.height)
            .allyPortraits
            .mapIndexedNotNull { index, region ->
                val normalized = normalizedPixels(bitmap, region)
                val visualConfidence = visualConfidence(normalized)
                if (visualConfidence < MIN_VISUAL_CONFIDENCE) {
                    null
                } else {
                    SlotPortraitFingerprint(
                        side = TeamSide.ALLY,
                        slotIndex = index + 1,
                        fingerprint = PortraitFingerprint.fromArgb64(normalized),
                        visualConfidence = visualConfidence
                    )
                }
            }

    fun match(fingerprints: List<SlotPortraitFingerprint>): List<HeroObservation> =
        matchSlots(fingerprints).map { match ->
            HeroObservation(match.heroName, match.side, match.confidence)
        }

    fun matchSlots(fingerprints: List<SlotPortraitFingerprint>): List<SlotHeroMatch> {
        val templates = store.templates()
        if (templates.isEmpty()) return emptyList()
        return fingerprints.mapNotNull slotLoop@ { slot ->
            val candidates = templates.mapNotNull { (normalizedHero, heroTemplates) ->
                val hero = catalog.heroes.firstOrNull {
                    CounterCatalog.normalize(it.name) == normalizedHero
                } ?: return@mapNotNull null
                val distance = heroTemplates.minOfOrNull { it.distance(slot.fingerprint) }
                    ?: return@mapNotNull null
                PortraitMatchCandidate(
                    heroName = hero.name,
                    distance = distance,
                    visualConfidence = slot.visualConfidence
                )
            }
            val best = PortraitMatchSelector.select(candidates) ?: return@slotLoop null
            SlotHeroMatch(
                heroName = best.heroName,
                side = slot.side,
                slotIndex = slot.slotIndex,
                confidence = best.confidence
            )
        }
    }

    fun learn(heroName: String, slot: SlotPortraitFingerprint): Boolean =
        store.learn(heroName, slot.fingerprint)

    private fun normalizedPixels(bitmap: Bitmap, region: NormalizedRect): IntArray {
        val rect = region.toPixelRect(bitmap.width, bitmap.height)
        return normalizedPixels(bitmap, rect)
    }

    private fun normalizedPixels(bitmap: Bitmap, rect: PixelRect): IntArray {
        val crop = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width, rect.height)
        val scaled = Bitmap.createScaledBitmap(crop, 8, 8, true)
        val pixels = IntArray(64)
        scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
        if (scaled !== crop) scaled.recycle()
        crop.recycle()
        return pixels
    }

    private fun visualConfidence(pixels: IntArray): Double {
        var luminanceTotal = 0.0
        var saturationTotal = 0.0
        pixels.forEach { color ->
            val red = (color ushr 16 and 0xff) / 255.0
            val green = (color ushr 8 and 0xff) / 255.0
            val blue = (color and 0xff) / 255.0
            val max = maxOf(red, green, blue)
            val min = minOf(red, green, blue)
            luminanceTotal += 0.2126 * red + 0.7152 * green + 0.0722 * blue
            saturationTotal += if (max == 0.0) 0.0 else (max - min) / max
        }
        val luminance = luminanceTotal / pixels.size
        val saturation = saturationTotal / pixels.size
        return (0.40 + saturation * 0.45 + kotlin.math.abs(luminance - 0.5) * 0.10)
            .coerceIn(0.0, 1.0)
    }

    companion object {
        private const val MIN_VISUAL_CONFIDENCE = 0.45
    }
}

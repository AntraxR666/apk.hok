package com.example.honorofkingsassistant

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

class DraftVisionEngine(
    context: Context,
    heroes: List<Hero>,
    enemyOnRight: Boolean = true,
    private var playerName: String = "R-95"
) : AutoCloseable {
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private val catalog = CounterCatalog(heroes)
    private val matcher = HeroNameMatcher(heroes)
    private val portraitMatcher = HeroPortraitMatcher(catalog, PortraitTemplateStore(context))
    private val processing = AtomicBoolean(false)
    private val bitmapAnalyzer = DraftBitmapAnalyzer(enemyOnRight)

    @Volatile
    private var enemyOnRight = enemyOnRight

    @Volatile
    private var classifier = DraftLayoutClassifier(enemyOnRight)

    fun setEnemyOnRight(value: Boolean) {
        enemyOnRight = value
        classifier = DraftLayoutClassifier(value)
        bitmapAnalyzer.setEnemyOnRight(value)
    }

    fun setPlayerName(value: String) {
        playerName = value.trim()
    }

    fun learnPortrait(heroName: String, slot: SlotPortraitFingerprint): Boolean =
        portraitMatcher.learn(heroName, slot)

    fun process(
        bitmap: Bitmap,
        onResult: (DraftVisionResult) -> Unit,
        onError: (Throwable) -> Unit
    ): Boolean {
        if (!processing.compareAndSet(false, true)) return false
        val slotFingerprints = runCatching {
            portraitMatcher.fingerprints(bitmap, enemyOnRight)
        }.getOrElse { emptyList() }
        val portraitObservations = portraitMatcher.match(slotFingerprints)
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { text ->
                val observations = mergeObservations(
                    extractOcrObservations(text, bitmap.width),
                    portraitObservations
                )
                val board = bitmapAnalyzer.analyze(bitmap, text.text)
                val subphase = DraftSubphaseDetector.detect(
                    recognizedText = text.text,
                    screenMode = board.mode,
                    confirmedPickCount = board.totalConfirmedCount
                )
                onResult(
                    DraftVisionResult(
                        observations = observations,
                        rawText = text.text,
                        board = board,
                        screenMode = board.mode,
                        subphase = subphase,
                        playerSlot = detectPlayerSlot(text, bitmap.width, bitmap.height),
                        slotFingerprints = slotFingerprints
                    )
                )
            }
            .addOnFailureListener(onError)
            .addOnCompleteListener {
                processing.set(false)
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        return true
    }

    private fun mergeObservations(
        ocr: List<HeroObservation>,
        portrait: List<HeroObservation>
    ): List<HeroObservation> {
        val best = linkedMapOf<Pair<String, TeamSide>, HeroObservation>()
        (portrait + ocr).forEach { observation ->
            val key = CounterCatalog.normalize(observation.heroName) to observation.side
            val current = best[key]
            if (current == null || observation.confidence > current.confidence) best[key] = observation
        }
        return best.values.toList()
    }

    private fun extractOcrObservations(text: Text, frameWidth: Int): List<HeroObservation> {
        val bestByHeroAndSide = linkedMapOf<Pair<String, TeamSide>, HeroObservation>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val match = matcher.bestMatch(line.text) ?: continue
                val side = classifier.classify(box.centerX(), frameWidth)
                val observation = HeroObservation(
                    heroName = match.hero.name,
                    side = side,
                    confidence = match.score.coerceIn(0.0, 1.0)
                )
                val key = observation.heroName to observation.side
                val previous = bestByHeroAndSide[key]
                if (previous == null || observation.confidence > previous.confidence) {
                    bestByHeroAndSide[key] = observation
                }
            }
        }
        return bestByHeroAndSide.values.toList()
    }

    private fun detectPlayerSlot(text: Text, frameWidth: Int, frameHeight: Int): PlayerSlotDetection? {
        val target = canonicalPlayerName(playerName)
        if (target.isBlank()) return null
        val allyPhysicalLeft = enemyOnRight
        return text.textBlocks.asSequence()
            .flatMap { it.lines.asSequence() }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val normalizedLine = canonicalPlayerName(line.text)
                if (normalizedLine != target) return@mapNotNull null
                val side = classifier.classify(box.centerX(), frameWidth)
                if (side != TeamSide.ALLY) return@mapNotNull null
                val slot = HoKGlobalLandscapeProfile.slotIndexForPlayerName(
                    centerX = box.centerX(),
                    centerY = box.centerY(),
                    frameWidth = frameWidth,
                    frameHeight = frameHeight,
                    allyPhysicalLeft = allyPhysicalLeft
                ) ?: return@mapNotNull null
                PlayerSlotDetection(slot, TeamSide.ALLY, 1.0)
            }
            .firstOrNull()
    }

    private fun canonicalPlayerName(value: String): String = CounterCatalog.normalize(value)
        .replace("[^a-z0-9]".toRegex(), "")

    override fun close() {
        recognizer.close()
    }
}

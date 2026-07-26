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
    private var playerName: String = "R-95",
    initialMatchModePreference: MatchMode = MatchMode.AUTO,
    private val callbackExecutor: java.util.concurrent.Executor
) : AutoCloseable {
    private val recognizer: TextRecognizer = TextRecognition.getClient(
        TextRecognizerOptions.DEFAULT_OPTIONS
    )
    private val catalog = CounterCatalog(heroes)
    private val matcher = HeroNameMatcher(heroes)
    private val portraitMatcher = HeroPortraitMatcher(catalog, PortraitTemplateStore(context))
    private val processing = AtomicBoolean(false)
    private val diagnosticsTracker = VisionDiagnosticsTracker()
    private val bitmapAnalyzer = DraftBitmapAnalyzer(enemyOnRight)
    private val matchModeResolver = MatchModeResolver(
        initialPreference = initialMatchModePreference
    )

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

    fun setMatchModePreference(value: MatchMode): MatchModeState =
        matchModeResolver.setPreference(value)

    fun matchModeState(): MatchModeState = matchModeResolver.current()

    fun resetMatchModeDetection(): MatchModeState {
        matchModeResolver.reset()
        return matchModeResolver.current()
    }

    fun learnPortrait(heroName: String, slot: SlotPortraitFingerprint): Boolean =
        portraitMatcher.learn(heroName, slot)

    fun diagnostics(): VisionDiagnostics = diagnosticsTracker.snapshot()

    fun process(
        bitmap: Bitmap,
        onResult: (DraftVisionResult) -> Unit,
        onError: (Throwable) -> Unit,
        runIfCurrent: ((() -> Unit) -> Boolean) = { action ->
            action()
            true
        }
    ): Boolean {
        if (!processing.compareAndSet(false, true)) {
            diagnosticsTracker.onDroppedFrame()
            return false
        }

        val startedAtNanos = System.nanoTime()
        val prepared = runCatching { OcrBitmapPreprocessor.prepare(bitmap) }
            .getOrElse { error ->
                processing.set(false)
                if (!bitmap.isRecycled) bitmap.recycle()
                runIfCurrent { onError(error) }
                return true
            }
        val ocrBitmap = prepared.bitmap
        val image = InputImage.fromBitmap(ocrBitmap, 0)

        return runCatching {
            recognizer.process(image)
                .addOnSuccessListener(callbackExecutor) { text ->
                    runIfCurrent {
                        val latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                        val diagnostics = diagnosticsTracker.onProcessedFrame(
                            latencyMs = latencyMs,
                            captureWidth = bitmap.width,
                            captureHeight = bitmap.height,
                            ocrWidth = ocrBitmap.width,
                            ocrHeight = ocrBitmap.height
                        )
                        val textLines = extractTextLines(text)
                        val matchMode = matchModeResolver.observe(
                            NormalSelectionEvidenceDetector.detect(
                                lines = textLines,
                                frameWidth = ocrBitmap.width,
                                frameHeight = ocrBitmap.height,
                                enemyOnRight = enemyOnRight
                            )
                        )
                        val ocrCandidates = extractOcrCandidates(text)
                        val slotFingerprints = runCatching {
                            when (matchMode.effective) {
                                MatchMode.AUTO -> emptyList()
                                MatchMode.RANKED_DRAFT ->
                                    portraitMatcher.fingerprints(bitmap, enemyOnRight)
                                MatchMode.NORMAL_BLIND ->
                                    portraitMatcher.normalFingerprints(bitmap)
                            }
                        }.getOrElse { emptyList() }
                        val portraitCandidates = when (matchMode.effective) {
                            MatchMode.AUTO -> emptyList()
                            MatchMode.NORMAL_BLIND -> NormalPortraitCandidateFactory.create(
                                matches = portraitMatcher.matchSlots(slotFingerprints),
                                geometry = NormalSelectionGeometry.forFrame(
                                    ocrBitmap.width,
                                    ocrBitmap.height
                                )
                            )
                            MatchMode.RANKED_DRAFT -> portraitMatcher.match(slotFingerprints).map {
                                PositionedHeroCandidate(
                                    heroName = it.heroName,
                                    confidence = it.confidence,
                                    centerX = -1,
                                    centerY = -1,
                                    source = CandidateSource.PORTRAIT,
                                    sideHint = it.side
                                )
                            }
                        }
                        val observations = HeroCandidateRouter.route(
                            candidates = ocrCandidates + portraitCandidates,
                            matchMode = matchMode,
                            frameWidth = ocrBitmap.width,
                            frameHeight = ocrBitmap.height,
                            enemyOnRight = enemyOnRight
                        )
                        val modeVisuals = resolveModeVisuals(bitmap, text, matchMode)
                        onResult(
                            DraftVisionResult(
                                observations = observations,
                                rawText = text.text,
                                board = modeVisuals.board,
                                screenMode = modeVisuals.board.mode,
                                matchMode = matchMode,
                                subphase = modeVisuals.subphase,
                                playerSlot = if (
                                    matchMode.effective == MatchMode.RANKED_DRAFT
                                ) {
                                    detectPlayerSlot(
                                        text,
                                        ocrBitmap.width,
                                        ocrBitmap.height
                                    )
                                } else {
                                    null
                                },
                                slotFingerprints = slotFingerprints,
                                diagnostics = diagnostics
                            )
                        )
                    }
                }
                .addOnFailureListener(callbackExecutor) { error ->
                    runIfCurrent {
                        val latencyMs = (System.nanoTime() - startedAtNanos) / 1_000_000L
                        diagnosticsTracker.onProcessedFrame(
                            latencyMs = latencyMs,
                            captureWidth = bitmap.width,
                            captureHeight = bitmap.height,
                            ocrWidth = ocrBitmap.width,
                            ocrHeight = ocrBitmap.height
                        )
                        onError(error)
                    }
                }
                .addOnCompleteListener(callbackExecutor) {
                    prepared.release()
                    processing.set(false)
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
            true
        }.getOrElse { error ->
            prepared.release()
            processing.set(false)
            if (!bitmap.isRecycled) bitmap.recycle()
            runIfCurrent { onError(error) }
            true
        }
    }

    private fun extractOcrCandidates(text: Text): List<PositionedHeroCandidate> {
        val bestByHeroAndPosition = linkedMapOf<Triple<String, Int, Int>, PositionedHeroCandidate>()
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val box = line.boundingBox ?: continue
                val match = matcher.match(line.text) ?: continue
                val candidate = PositionedHeroCandidate(
                    heroName = match.hero.name,
                    confidence = match.score.coerceIn(0.0, 1.0),
                    centerX = box.centerX(),
                    centerY = box.centerY(),
                    source = CandidateSource.OCR
                )
                val key = Triple(
                    CounterCatalog.normalize(candidate.heroName),
                    candidate.centerX,
                    candidate.centerY
                )
                val previous = bestByHeroAndPosition[key]
                if (previous == null || candidate.confidence > previous.confidence) {
                    bestByHeroAndPosition[key] = candidate
                }
            }
        }
        return bestByHeroAndPosition.values.toList()
    }

    private fun extractTextLines(text: Text): List<PositionedTextLine> =
        text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                PositionedTextLine(
                    text = line.text,
                    centerX = box.centerX(),
                    centerY = box.centerY()
                )
            }
        }

    private data class ModeVisuals(
        val board: DraftBoardState,
        val subphase: DraftSubphase
    )

    private fun resolveModeVisuals(
        bitmap: Bitmap,
        text: Text,
        matchMode: MatchModeState
    ): ModeVisuals = when (matchMode.effective) {
        MatchMode.AUTO -> ModeVisuals(
            board = DraftBoardState.empty(ScreenMode.UNKNOWN),
            subphase = DraftSubphaseDetector.detect(text.text, ScreenMode.UNKNOWN, 0)
        )
        MatchMode.NORMAL_BLIND -> {
            val detected = DraftSubphaseDetector.detect(text.text, ScreenMode.UNKNOWN, 0)
            val subphase = if (detected == DraftSubphase.LOADING) {
                DraftSubphase.LOADING
            } else {
                DraftSubphase.PICK
            }
            ModeVisuals(
                board = DraftBoardState.empty(
                    if (subphase == DraftSubphase.LOADING) ScreenMode.UNKNOWN else ScreenMode.DRAFT
                ),
                subphase = subphase
            )
        }
        MatchMode.RANKED_DRAFT -> {
            val board = bitmapAnalyzer.analyze(bitmap, text.text)
            ModeVisuals(
                board = board,
                subphase = DraftSubphaseDetector.detect(
                    recognizedText = text.text,
                    screenMode = board.mode,
                    confirmedPickCount = board.totalConfirmedCount
                )
            )
        }
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

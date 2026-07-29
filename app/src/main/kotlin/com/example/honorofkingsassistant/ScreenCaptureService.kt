package com.example.honorofkingsassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class ScreenCaptureService : Service() {
    private lateinit var counterEngine: CounterEngine
    private lateinit var recommendationEngine: DraftRecommendationEngine
    private lateinit var strategyEngine: StrategyEngine
    private lateinit var tracker: TemporalDraftTracker
    private lateinit var slotRecognitionTracker: SlotRecognitionTracker
    private lateinit var visionEngine: DraftVisionEngine
    private lateinit var scoreboardAnalyzer: ScoreboardBitmapAnalyzer
    private lateinit var scoreboardReconciler: ScoreboardReconciler
    private lateinit var itemRecommendationEngine: ItemRecommendationEngine
    private lateinit var boardStabilizer: DraftBoardTemporalStabilizer
    private lateinit var playerSlotResolver: PlayerSlotResolver
    private val sessionCoordinator = DraftSessionCoordinator()

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var captureSize: CaptureSize? = null
    private var captureDensityDpi: Int = 0
    private var enemyOnRight = true
    private var selectedStage = AssistantStage.PAUSED
    private var suggestedStage: AssistantStage? = null
    private var inputMode = InputMode.AUTO_SCAN
    private var matchMode = MatchModeState()
    private var lastFrameAt = 0L
    private var forceNextFrame = false
    private var lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
    private var lastBoard = DraftBoardState.empty()
    private var lastScreenMode = ScreenMode.UNKNOWN
    private var lastSubphase = DraftSubphase.UNKNOWN
    private var lastPlayerSlot: PlayerSlotDetection? = null
    private var manualPlayerSlotIndex: Int? = null
    private var playerPickOverride: PlayerPickOverride = PlayerPickOverride.AUTO
    private var lastSlotFingerprints: List<SlotPortraitFingerprint> = emptyList()
    private var lastSlotRecognition: List<SlotRecognitionState> = emptyList()
    private var explicitDraftScanFramesRemaining = 0
    private var quickCorrectionRequest: QuickCorrectionRequest? = null
    private var learnedPortraitCount = 0
    private var lastRecognition = RecognitionCalibrationState.UNCALIBRATED
    private var lastLoadingRosterReconciliation: LoadingRosterReconciliationResult? = null
    private var manualAssignments = ManualTeamAssignments()
    private var pendingLoadingConfirmation = false
    private var loadingConfirmationDeadlineMs = 0L
    private var loadingConfirmationReview = false
    private val scoreboardScanCoordinator = ScoreboardScanCoordinator()
    private var scoreboardRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var lastVisionDiagnostics = VisionDiagnostics()
    private val manualAllies = linkedSetOf<String>()
    private val manualEnemies = linkedSetOf<String>()

    override fun onCreate() {
        super.onCreate()
        counterEngine = CounterEngine(this)
        recommendationEngine = DraftRecommendationEngine(counterEngine.catalog)
        strategyEngine = StrategyEngine()
        tracker = TemporalDraftTracker(
            requiredHits = 3,
            historySize = 5,
            minimumObservationConfidence = 0.55
        )
        slotRecognitionTracker = SlotRecognitionTracker(requiredHits = 3, historySize = 5)
        boardStabilizer = DraftBoardTemporalStabilizer(requiredConfirmationFrames = 3)
        playerSlotResolver = PlayerSlotResolver(requiredHits = 4, maxMisses = 4)
        manualPlayerSlotIndex = AssistantPreferences.getManualPlayerSlot(this)
        playerPickOverride = AssistantPreferences.getPlayerPickOverride(this)
        inputMode = AssistantPreferences.getInputMode(this)
        val matchModePreference = AssistantPreferences.getMatchMode(this)
        matchMode = MatchModeState(preference = matchModePreference)
        lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
        selectedStage = AssistantPreferences.getAssistantStage(this)
        workerThread = HandlerThread("HoKDraftCapture").also { it.start() }
        workerHandler = Handler(requireNotNull(workerThread).looper)
        val callbackExecutor = java.util.concurrent.Executor { command ->
            val handler = workerHandler
            if (handler == null || !handler.post(command)) command.run()
        }
        visionEngine = DraftVisionEngine(
            context = this,
            heroes = counterEngine.allHeroes(),
            enemyOnRight = enemyOnRight,
            playerName = AssistantPreferences.getPlayerName(this),
            initialMatchModePreference = matchModePreference,
            callbackExecutor = callbackExecutor
        )
        scoreboardAnalyzer = ScoreboardBitmapAnalyzer(counterEngine.catalog)
        scoreboardReconciler = ScoreboardReconciler(counterEngine.catalog)
        itemRecommendationEngine = ItemRecommendationEngine(ItemCatalog.load(this))
        matchMode = visionEngine.matchModeState()
        lastRecognition = visionEngine.recognitionCalibration()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SET_STAGE -> {
                val requested = intent.getStringExtra(EXTRA_ASSISTANT_STAGE)
                    ?.let { runCatching { AssistantStage.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                setStage(requested)
                return START_NOT_STICKY
            }
            ACTION_SET_INPUT_MODE -> {
                val requested = intent.getStringExtra(EXTRA_INPUT_MODE)
                    ?.let { runCatching { InputMode.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                sessionCoordinator.advanceGeneration {
                    inputMode = requested
                    AssistantPreferences.setInputMode(this, inputMode)
                    tracker.reset()
                    slotRecognitionTracker.reset()
                    boardStabilizer.reset()
                    playerSlotResolver.reset()
                    lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
                    lastBoard = DraftBoardState.empty(
                        if (matchMode.effective == MatchMode.AUTO) {
                            ScreenMode.UNKNOWN
                        } else {
                            ScreenMode.DRAFT
                        }
                    )
                    lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                    lastSlotFingerprints = emptyList()
                    lastSlotRecognition = slotRecognitionTracker.current()
                }
                publishCurrent(
                    if (inputMode == InputMode.MANUAL) {
                        "Entrada manual activa; el escaneo no añadirá héroes"
                    } else {
                        "Escaneo automático activo; esperando evidencia estable"
                    }
                )
                return START_NOT_STICKY
            }
            ACTION_SET_MATCH_MODE -> {
                val requested = intent.getStringExtra(EXTRA_MATCH_MODE)
                    ?.let { runCatching { MatchMode.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                sessionCoordinator.advanceGeneration {
                    AssistantPreferences.setMatchMode(this, requested)
                    matchMode = visionEngine.setMatchModePreference(requested)
                    tracker.reset()
                    slotRecognitionTracker.reset()
                    boardStabilizer.reset()
                    playerSlotResolver.reset()
                    lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
                    lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                    lastSlotFingerprints = emptyList()
                    lastSlotRecognition = slotRecognitionTracker.current()
                    lastBoard = DraftBoardState.empty(
                        if (matchMode.effective == MatchMode.AUTO) {
                            ScreenMode.UNKNOWN
                        } else {
                            ScreenMode.DRAFT
                        }
                    )
                    lastScreenMode = lastBoard.mode
                    lastSubphase = DraftSubphase.UNKNOWN
                }
                publishCurrent(
                    when (requested) {
                        MatchMode.AUTO -> "Modo de partida automático; esperando evidencia decisiva"
                        MatchMode.RANKED_DRAFT -> "Draft clasificatorio fijado manualmente"
                        MatchMode.NORMAL_BLIND -> "Selección normal fijada manualmente"
                    }
                )
                return START_NOT_STICKY
            }
            ACTION_SWAP_SIDES -> {
                sessionCoordinator.advanceGeneration {
                    enemyOnRight = !enemyOnRight
                    visionEngine.setEnemyOnRight(enemyOnRight)
                    tracker.reset()
                    slotRecognitionTracker.reset()
                    boardStabilizer.reset()
                    playerSlotResolver.reset()
                    lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                    lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
                    lastBoard = DraftBoardState.empty(ScreenMode.DRAFT)
                    lastScreenMode = ScreenMode.DRAFT
                    lastSubphase = DraftSubphase.UNKNOWN
                    suggestedStage = null
                    lastSlotRecognition = slotRecognitionTracker.current()
                }
                publishCurrent("Lados intercambiados; esperando confirmaciones nuevas")
                return START_NOT_STICKY
            }
            ACTION_FORCE_SCAN -> {
                if (selectedStage == AssistantStage.DRAFT) {
                    forceNextFrame = true
                    explicitDraftScanFramesRemaining = EXPLICIT_SCAN_FRAME_BUDGET
                    quickCorrectionRequest = null
                    publishCurrent("Escaneo solicitado; comprobando cada slot")
                } else {
                    publishCurrent("Activa el modo Selección para escanear el draft")
                }
                return START_NOT_STICKY
            }
            ACTION_SET_PLAYER_SLOT -> {
                val requested = intent.getIntExtra(EXTRA_PLAYER_SLOT_INDEX, 0)
                manualPlayerSlotIndex = requested.takeIf { it in 1..5 }
                AssistantPreferences.setManualPlayerSlot(this, manualPlayerSlotIndex)
                playerSlotResolver.reset()
                lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                publishCurrent(
                    manualPlayerSlotIndex?.let { "Tu slot quedó fijado manualmente en $it" }
                        ?: "Detección automática de tu slot activada; requiere cuatro lecturas coincidentes"
                )
                return START_NOT_STICKY
            }
            ACTION_SET_PLAYER_PICK_OVERRIDE -> {
                playerPickOverride = intent.getStringExtra(EXTRA_PLAYER_PICK_OVERRIDE)
                    ?.let { runCatching { PlayerPickOverride.valueOf(it) }.getOrNull() }
                    ?: PlayerPickOverride.AUTO
                AssistantPreferences.setPlayerPickOverride(this, playerPickOverride)
                publishCurrent(
                    when (playerPickOverride) {
                        PlayerPickOverride.AUTO -> "Estado de tu pick en automático"
                        PlayerPickOverride.PENDING -> "Tu pick quedó marcado manualmente como pendiente"
                        PlayerPickOverride.LOCKED -> "Tu pick quedó marcado manualmente como fijado"
                    }
                )
                return START_NOT_STICKY
            }
            ACTION_MANUAL_ADD -> {
                val heroName = intent.getStringExtra(EXTRA_HERO_NAME).orEmpty()
                val side = intent.getStringExtra(EXTRA_TEAM_SIDE)
                if (counterEngine.findHero(heroName) != null) {
                    val teamSide = if (side == TeamSide.ALLY.name) TeamSide.ALLY else TeamSide.ENEMY
                    val occupied = (1..5).firstOrNull {
                        manualAssignments.heroAt(teamSide, it) == null
                    } ?: 5
                    assignManualSlot(teamSide, occupied, heroName, teachLoading = false)
                    val learned = learnFromCurrentPreview(heroName, teamSide)
                    if (learned) {
                        learnedPortraitCount++
                        lastRecognition = visionEngine.recognitionCalibration()
                    }
                    publishCurrent(
                        if (learned) "Corrección guardada; el retrato quedó aprendido localmente"
                        else "Héroe añadido manualmente"
                    )
                }
                return START_NOT_STICKY
            }
            ACTION_MANUAL_ASSIGN_SLOT -> {
                val heroName = intent.getStringExtra(EXTRA_HERO_NAME).orEmpty()
                val side = intent.getStringExtra(EXTRA_TEAM_SIDE)
                    ?.let { runCatching { TeamSide.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                val slotIndex = intent.getIntExtra(EXTRA_MANUAL_SLOT_INDEX, 0)
                val teachLoading = intent.getBooleanExtra(EXTRA_TEACH_LOADING_TEMPLATE, false)
                if (slotIndex in 1..5 && counterEngine.findHero(heroName) != null) {
                    val learned = assignManualSlot(side, slotIndex, heroName, teachLoading)
                    if (quickCorrectionRequest?.slot == ManualTeamSlot(side, slotIndex)) {
                        quickCorrectionRequest = null
                    }
                    publishCurrent(
                        if (learned) {
                            if (teachLoading) {
                                "Corrección confirmada; se aprendió esta tarjeta de carga localmente"
                            } else {
                                "Corrección guardada; se aprendió este retrato de selección localmente"
                            }
                        } else {
                            "Slot manual actualizado"
                        }
                    )
                }
                return START_NOT_STICKY
            }
            ACTION_MANUAL_REMOVE_SLOT -> {
                val side = intent.getStringExtra(EXTRA_TEAM_SIDE)
                    ?.let { runCatching { TeamSide.valueOf(it) }.getOrNull() }
                    ?: return START_NOT_STICKY
                val slotIndex = intent.getIntExtra(EXTRA_MANUAL_SLOT_INDEX, 0)
                if (slotIndex in 1..5) {
                    manualAssignments = manualAssignments.remove(side, slotIndex)
                    syncLegacyManualSets()
                    quickCorrectionRequest = null
                    publishCurrent("Héroe manual quitado del slot")
                }
                return START_NOT_STICKY
            }
            ACTION_MANUAL_CLEAR -> {
                manualAssignments = manualAssignments.clear()
                manualAllies.clear()
                manualEnemies.clear()
                quickCorrectionRequest = null
                publishCurrent("Correcciones manuales eliminadas")
                return START_NOT_STICKY
            }
            ACTION_CONFIRM_LOADING_ROSTER -> {
                if (selectedStage != AssistantStage.DRAFT) {
                    restoreOverlayAfterOneShot()
                    publishCurrent("Activa Selección y espera la pantalla de dos filas antes de confirmar")
                } else {
                    pendingLoadingConfirmation = true
                    loadingConfirmationReview = false
                    loadingConfirmationDeadlineMs = SystemClock.elapsedRealtime() +
                        LOADING_CONFIRMATION_TIMEOUT_MS
                    forceNextFrame = true
                    publishCurrent("Confirmación armada; esperando la pantalla de carga de dos filas")
                }
                return START_NOT_STICKY
            }
            ACTION_SCAN_SCOREBOARD -> {
                scoreboardScanCoordinator.arm()
                forceNextFrame = true
                publishCurrent("Marcador armado; capturando el próximo fotograma visible")
                return START_NOT_STICKY
            }
        }

        if (mediaProjection != null) return START_NOT_STICKY

        startForeground(NOTIFICATION_ID, buildNotification("Asistente activo · modo pausado"))
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val resultData = getProjectionIntent(intent)
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            Log.e(TAG, "Falta el consentimiento de captura")
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startProjection(resultCode, resultData)
            selectedStage = AssistantStage.PAUSED
            suggestedStage = null
            playerPickOverride = PlayerPickOverride.AUTO
            AssistantPreferences.setPlayerPickOverride(this, playerPickOverride)
            AssistantPreferences.setAssistantStage(this, selectedStage)
            updateCaptureSurfaceForStage()
            startOverlayService()
            AssistantSessionBus.publish(
                RecognitionUiStatePolicy.apply(
                    state = AssistantUiState(
                        active = true,
                        selectedStage = selectedStage,
                        suggestedStage = null,
                        inputMode = inputMode,
                        matchMode = matchMode,
                        enemyOnRight = enemyOnRight,
                        recognition = lastRecognition
                    ),
                    baseStatus =
                        "Asistente listo para ${PersonalDeviceProfile.MODEL}. Elige Selección o Partida.",
                    calibration = lastRecognition
                )
            )
            START_NOT_STICKY
        } catch (error: Exception) {
            Log.e(TAG, "No se pudo iniciar MediaProjection", error)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun setStage(stage: AssistantStage) {
        val previousStage = selectedStage
        when {
            stage == AssistantStage.DRAFT && previousStage != AssistantStage.DRAFT ->
                resetForNewDraftSession()
            stage != previousStage -> sessionCoordinator.advanceGeneration { }
        }
        selectedStage = stage
        suggestedStage = null
        AssistantPreferences.setAssistantStage(this, stage)
        updateCaptureSurfaceForStage()
        when (stage) {
            AssistantStage.PAUSED -> publishCurrent(
                "Modo pausado: la captura sigue autorizada, pero no se procesan fotogramas"
            )
            AssistantStage.DRAFT -> {
                forceNextFrame = true
                lastScreenMode = ScreenMode.UNKNOWN
                lastSubphase = DraftSubphase.UNKNOWN
                publishCurrent("Modo Selección activado; buscando veto, picks y ajustes")
            }
            AssistantStage.IN_GAME -> {
                lastScreenMode = ScreenMode.IN_GAME
                lastSubphase = DraftSubphase.IN_GAME
                publishCurrent("Modo Partida activado; análisis visual pausado y plan disponible")
            }
        }
        updateNotification()
    }

    private fun resetForNewDraftSession() {
        val reset = sessionCoordinator.beginDraftSession(
            resetMatchMode = visionEngine::resetMatchModeDetection,
            tracker = tracker,
            boardStabilizer = boardStabilizer,
            playerSlotResolver = playerSlotResolver,
            manualPlayerSlotIndex = manualPlayerSlotIndex
        )
        matchMode = reset.matchMode
        lastVisionSnapshot = reset.snapshot
        lastBoard = reset.board
        lastPlayerSlot = reset.playerSlot
        lastScreenMode = ScreenMode.UNKNOWN
        lastSubphase = DraftSubphase.UNKNOWN
        lastSlotFingerprints = emptyList()
        slotRecognitionTracker.reset()
        lastSlotRecognition = slotRecognitionTracker.current()
        lastLoadingRosterReconciliation = null
        manualAssignments = ManualTeamAssignments()
        pendingLoadingConfirmation = false
        loadingConfirmationReview = false
        explicitDraftScanFramesRemaining = 0
        quickCorrectionRequest = null
        manualAllies.clear()
        manualEnemies.clear()
        playerPickOverride = PlayerPickOverride.AUTO
        AssistantPreferences.setPlayerPickOverride(this, playerPickOverride)
        lastFrameAt = 0L
    }

    @Suppress("DEPRECATION")
    private fun getProjectionIntent(intent: Intent?): Intent? = if (Build.VERSION.SDK_INT >= 33) {
        intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
    } else {
        intent?.getParcelableExtra(EXTRA_RESULT_DATA)
    }

    @android.annotation.SuppressLint("WrongConstant")
    private fun startProjection(resultCode: Int, resultData: Intent) {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val callbackHandler = Handler(mainLooper)
        val projection = requireNotNull(
            manager.getMediaProjection(resultCode, resultData)
        ) {
            "MediaProjectionManager returned null after valid capture consent"
        }
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }

            override fun onCapturedContentResize(width: Int, height: Int) {
                callbackHandler.post { resizeCaptureSurface(width, height) }
            }
        }, callbackHandler)
        mediaProjection = projection

        captureDensityDpi = resources.displayMetrics.densityDpi

        val (sourceWidth, sourceHeight) = initialCaptureSourceSize()
        val initialSize = CaptureGeometry.fit(sourceWidth, sourceHeight)
        val reader = createImageReader(initialSize)
        imageReader = reader
        captureSize = initialSize
        virtualDisplay = projection.createVirtualDisplay(
            "HoKDraftAssistant",
            initialSize.width,
            initialSize.height,
            captureDensityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            workerHandler
        )
    }

    private fun initialCaptureSourceSize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            return bounds.width().coerceAtLeast(1) to bounds.height().coerceAtLeast(1)
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }

    @android.annotation.SuppressLint("WrongConstant")
    private fun createImageReader(size: CaptureSize): ImageReader =
        ImageReader.newInstance(size.width, size.height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener(
                { reader -> handleImage(reader.acquireLatestImage()) },
                workerHandler
            )
        }

    private fun resizeCaptureSurface(sourceWidth: Int, sourceHeight: Int) {
        if (sourceWidth <= 0 || sourceHeight <= 0) return
        val display = virtualDisplay ?: return
        val nextSize = CaptureGeometry.fit(sourceWidth, sourceHeight)
        if (nextSize == captureSize) return

        val nextReader = createImageReader(nextSize)
        val nextSurface = if (selectedStage == AssistantStage.DRAFT) nextReader.surface else null
        runCatching {
            display.resize(nextSize.width, nextSize.height, captureDensityDpi)
            display.setSurface(nextSurface)
        }.onSuccess {
            val previousReader = imageReader
            imageReader = nextReader
            captureSize = nextSize
            previousReader?.close()
        }.onFailure { error ->
            nextReader.close()
            Log.w(TAG, "No se pudo redimensionar la captura a $nextSize", error)
        }
    }

    private fun handleImage(image: Image?) {
        val preparedFrame = prepareFrameAtCurrentGeneration(sessionCoordinator) {
            if (image == null) return@prepareFrameAtCurrentGeneration null
            val policy = AssistantStagePolicy.forStage(selectedStage)
            val scoreboardArmed = scoreboardScanCoordinator.shouldCapture()
            if (!policy.shouldProcessFrames && !scoreboardArmed) {
                image.close()
                return@prepareFrameAtCurrentGeneration null
            }

            val now = SystemClock.elapsedRealtime()
            val effectiveIntervalMs = AdaptiveFrameCadence.interval(
                baseIntervalMs = policy.frameIntervalMs,
                averageLatencyMs = visionEngine.diagnostics().averageLatencyMs
            )
            if (!forceNextFrame && !scoreboardArmed && now - lastFrameAt < effectiveIntervalMs) {
                image.close()
                return@prepareFrameAtCurrentGeneration null
            }
            forceNextFrame = false
            lastFrameAt = now

            try {
                imageToBitmap(image)
            } catch (error: Exception) {
                Log.w(TAG, "No se pudo convertir el frame", error)
                null
            } finally {
                image.close()
            }
        } ?: return
        val bitmap = preparedFrame.value

        if (scoreboardScanCoordinator.claimFrame(isEligible = true)) {
            analyzeScoreboard(bitmap, preparedFrame.generation)
            return
        }

        val accepted = visionEngine.process(
            bitmap = bitmap,
            onResult = { result ->
                lastVisionDiagnostics = result.diagnostics
                lastRecognition = result.recognition
                lastScreenMode = result.screenMode
                lastSubphase = result.subphase
                val previousEffectiveMatchMode = matchMode.effective
                matchMode = result.matchMode
                if (matchMode.effective != previousEffectiveMatchMode) {
                    tracker.reset()
                    slotRecognitionTracker.reset()
                    boardStabilizer.reset()
                    playerSlotResolver.reset()
                    lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
                    lastBoard = DraftBoardState.empty(result.screenMode)
                    lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                    lastSlotFingerprints = emptyList()
                    lastSlotRecognition = slotRecognitionTracker.current()
                    lastLoadingRosterReconciliation = null
                }
                suggestedStage = AssistantStagePolicy.suggest(
                    selectedStage = selectedStage,
                    detectedScene = result.screenMode.toDetectedScene()
                )

                if (result.screenMode == ScreenMode.DRAFT) {
                    if (inputMode == InputMode.AUTO_SCAN) {
                        when (matchMode.effective) {
                            MatchMode.RANKED_DRAFT -> {
                                lastBoard = boardStabilizer.stabilize(result.board)
                                lastPlayerSlot = playerSlotResolver.resolve(
                                    result.playerSlot,
                                    manualPlayerSlotIndex
                                )
                                lastSlotFingerprints = result.slotFingerprints
                                lastSlotRecognition = slotRecognitionTracker.observe(
                                    result.slotRecognitionEvidence,
                                    lastBoard
                                )
                                updateExplicitScanCorrection()
                            }
                            MatchMode.NORMAL_BLIND -> {
                                lastBoard = result.board
                                lastPlayerSlot = playerSlotResolver.resolve(
                                    null,
                                    manualPlayerSlotIndex
                                )
                                lastSlotFingerprints = emptyList()
                                slotRecognitionTracker.reset()
                                lastSlotRecognition = slotRecognitionTracker.current()
                            }
                            MatchMode.AUTO -> Unit
                        }
                        lastVisionSnapshot = tracker.observe(result.observations)
                    } else {
                        lastBoard = DraftBoardState.empty(ScreenMode.DRAFT)
                        lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                        lastSlotFingerprints = emptyList()
                        slotRecognitionTracker.reset()
                        lastSlotRecognition = slotRecognitionTracker.current()
                    }
                }
                if (matchMode.effective != MatchMode.AUTO &&
                    result.subphase == DraftSubphase.LOADING
                ) {
                    val preservedManual = ManualRosterAuthority.preservedIdentities(
                        manualAllies = manualAllies,
                        manualEnemies = manualEnemies,
                        previous = lastLoadingRosterReconciliation
                    )
                    val routed = RankedLoadingSessionStateRouter.route(
                        state = AssistantSessionBus.state.copy(
                            snapshot = lastVisionSnapshot,
                            playerSlot = lastPlayerSlot,
                            manualPlayerSlotIndex = manualPlayerSlotIndex
                        ),
                        result = result,
                        configuredPlayerName = AssistantPreferences.getPlayerName(this),
                        preserved = preservedManual
                    )
                    lastLoadingRosterReconciliation = routed.loadingRosterReconciliation
                    lastPlayerSlot = routed.playerSlot
                    lastVisionSnapshot = routed.snapshot
                }

                if (pendingLoadingConfirmation) {
                    when {
                        result.subphase == DraftSubphase.LOADING &&
                            lastLoadingRosterReconciliation != null -> {
                            pendingLoadingConfirmation = false
                            loadingConfirmationReview = true
                            restoreOverlayAfterOneShot(openManualEditor = true)
                        }
                        SystemClock.elapsedRealtime() >= loadingConfirmationDeadlineMs -> {
                            pendingLoadingConfirmation = false
                            loadingConfirmationReview = false
                            restoreOverlayAfterOneShot()
                        }
                    }
                }

                val stabilizedResult = result.copy(board = lastBoard)
                val status = when (result.subphase) {
                    DraftSubphase.BAN -> "Fase de veto detectada; preparando amenazas prioritarias"
                    DraftSubphase.PICK -> buildDraftStatus(stabilizedResult)
                    DraftSubphase.ADJUSTMENTS -> "Últimos ajustes detectados; composición cerrada y plan final preparado"
                    DraftSubphase.LOADING -> {
                        val reconciliation = lastLoadingRosterReconciliation
                        val resolved = reconciliation?.assignments?.size ?: 0
                        val conflicts = reconciliation?.conflicts?.size ?: 0
                        "Pantalla de carga detectada; $resolved/10 reconciliados" +
                            if (conflicts > 0) " · $conflicts conflictos para revisar" else ""
                    }
                    DraftSubphase.IN_GAME -> "Parece que comenzó la partida; confirma el cambio desde la burbuja"
                    DraftSubphase.UNKNOWN -> when (result.screenMode) {
                        ScreenMode.DRAFT -> buildDraftStatus(stabilizedResult)
                        ScreenMode.IN_GAME -> "Parece que comenzó la partida; confirma el cambio desde la burbuja"
                        ScreenMode.UNKNOWN -> "Buscando veto, selección o pantalla de carga"
                    }
                }
                publishCurrent(status)
            },
            onError = { error ->
                lastVisionDiagnostics = visionEngine.diagnostics()
                Log.w(TAG, "OCR falló en un frame", error)
                publishCurrent("OCR temporalmente sin resultado")
            },
            runIfCurrent = { action ->
                sessionCoordinator.runIfCurrent(preparedFrame.generation, action)
            }
        )
        if (!accepted) {
            lastVisionDiagnostics = visionEngine.diagnostics()
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun analyzeScoreboard(bitmap: Bitmap, generation: DraftFrameGeneration) {
        val input = InputImage.fromBitmap(bitmap, 0)
        scoreboardRecognizer.process(input)
            .addOnSuccessListener { text ->
                val handler = workerHandler
                val complete: () -> Unit = {
                    try {
                        val applied = sessionCoordinator.runIfCurrent(generation) {
                            val scoreboard = scoreboardAnalyzer.analyze(text, bitmap.width, bitmap.height)
                            val result = scoreboardReconciler.reconcile(
                                draft = lastVisionSnapshot,
                                scoreboard = scoreboard,
                                manualOverrides = manualAssignments
                            )
                            lastVisionSnapshot = result.snapshot
                            scoreboardScanCoordinator.completeReview()
                            restoreOverlayAfterOneShot(openManualEditor = true)
                            publishCurrent(
                                "Marcador verificado: ${result.appliedCorrections.size} correcciones" +
                                    if (result.pendingConflicts.isNotEmpty()) {
                                        " · ${result.pendingConflicts.size} conflictos para revisar"
                                    } else {
                                        " · revisa los slots si falta algún título"
                                    }
                            )
                        }
                        if (!applied) {
                            scoreboardScanCoordinator.fail()
                            restoreOverlayAfterOneShot()
                        }
                    } catch (error: Exception) {
                        scoreboardScanCoordinator.fail()
                        restoreOverlayAfterOneShot()
                        publishCurrent("No se pudo verificar el marcador; abre el panel e inténtalo otra vez")
                        Log.w(TAG, "Falló el análisis del marcador", error)
                    } finally {
                        if (!bitmap.isRecycled) bitmap.recycle()
                    }
                }
                if (handler == null || !handler.post { complete() }) complete()
            }
            .addOnFailureListener { error ->
                scoreboardScanCoordinator.fail()
                restoreOverlayAfterOneShot()
                if (!bitmap.isRecycled) bitmap.recycle()
                publishCurrent("OCR del marcador sin resultado; vuelve a abrirlo e inténtalo")
                Log.w(TAG, "OCR de marcador falló", error)
            }
    }

    private fun ScreenMode.toDetectedScene(): DetectedScene = when (this) {
        ScreenMode.DRAFT -> DetectedScene.DRAFT
        ScreenMode.IN_GAME -> DetectedScene.IN_GAME
        ScreenMode.UNKNOWN -> DetectedScene.UNKNOWN
    }

    private fun buildDraftStatus(result: DraftVisionResult): String {
        val board = result.board
        val identityCount = result.observations.size
        if (result.matchMode.effective == MatchMode.NORMAL_BLIND) {
            return "Selección normal · $identityCount aliados reconocidos · sin observaciones enemigas"
        }
        val progress = "Draft ${board.totalConfirmedCount}/10"
        val active = when (board.activeSide) {
            TeamSide.ALLY -> "turno aliado"
            TeamSide.ENEMY -> "turno enemigo"
            TeamSide.UNKNOWN -> "turno en transición"
        }
        val player = lastPlayerSlot
            ?.let { detection ->
                val source = if (manualPlayerSlotIndex != null) "manual" else "auto estable"
                " · ${AssistantPreferences.getPlayerName(this)} en slot ${detection.slotIndex} ($source)"
            }
            .orEmpty()
        val identity = if (identityCount == 0) {
            " · retratos detectados; faltan identidades"
        } else {
            " · $identityCount identidades"
        }
        return "$progress · $active$player$identity"
    }

    private fun learnFromCurrentPreview(heroName: String, side: TeamSide): Boolean {
        val preferredIndex = when (side) {
            TeamSide.ALLY -> lastBoard.allySlots.firstOrNull { it.status == DraftSlotStatus.PREVIEWING }?.index
            TeamSide.ENEMY -> lastBoard.enemySlots.firstOrNull { it.status == DraftSlotStatus.PREVIEWING }?.index
            TeamSide.UNKNOWN -> null
        } ?: return false
        val fingerprint = lastSlotFingerprints.firstOrNull {
            it.side == side && it.slotIndex == preferredIndex
        } ?: return false
        return visionEngine.learnPortrait(heroName, fingerprint)
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes.first()
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        if (paddedWidth == image.width) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun publishCurrent(status: String) {
        val merged = mergeManual(lastVisionSnapshot)
        val requestedRole = AssistantPreferences.getRequestedRole(this)
        val automaticFlow = DraftFlowResolver.resolve(
            lastBoard,
            lastPlayerSlot,
            matchMode.effective
        )
        val flow = PlayerPickStatePolicy.apply(automaticFlow, playerPickOverride)
        val allCandidates = recommendationEngine.recommend(
            merged,
            requestedRole = requestedRole,
            limit = 3
        )
        val recommendations = if (
            selectedStage == AssistantStage.DRAFT && flow.shouldRecommendPicks
        ) allCandidates else emptyList()
        val playerSlot = manualPlayerSlotIndex ?: lastPlayerSlot?.takeIf { it.side == TeamSide.ALLY }?.slotIndex
        val playerHero = playerSlot?.let { slot ->
            manualAssignments.heroAt(TeamSide.ALLY, slot)
                ?: merged.allies.getOrNull(slot - 1)?.heroName
        }?.let(counterEngine::findHero)
        val enemyThreats = merged.enemies.mapNotNull { enemy ->
            counterEngine.findHero(enemy.heroName)?.role?.let { role ->
                when {
                    role.contains("Mid", ignoreCase = true) -> EnemyThreat.MAGIC_BURST
                    role.contains("Farm", ignoreCase = true) -> EnemyThreat.PHYSICAL_BURST
                    role.contains("Clash", ignoreCase = true) -> EnemyThreat.TANK
                    role.contains("Roamer", ignoreCase = true) -> EnemyThreat.CROWD_CONTROL
                    else -> null
                }
            }
        }
        val itemPlan = playerHero?.let {
            itemRecommendationEngine.recommend(
                ItemRecommendationContext(
                    playerHero = it,
                    matchMode = matchMode.effective,
                    allies = merged.allies,
                    enemies = merged.enemies,
                    enemyThreats = enemyThreats
                )
            )
        }

        val strategyCandidate = recommendations.firstOrNull()
            ?: allCandidates.firstOrNull()
            ?: recommendationEngine.recommend(merged, requestedRole = requestedRole, limit = 1).firstOrNull()
        val stagePolicy = AssistantStagePolicy.forStage(selectedStage)
        val strategy = if (
            stagePolicy.shouldShowStrategy ||
            (selectedStage == AssistantStage.DRAFT && flow.shouldShowStrategy)
        ) {
            strategyCandidate?.let { strategyEngine.build(it, merged) }
        } else null

        val automaticPlayerPickLocked = lastPlayerSlot?.let { detection ->
            val slot = when (detection.side) {
                TeamSide.ALLY -> lastBoard.allySlots.getOrNull(detection.slotIndex - 1)
                TeamSide.ENEMY -> lastBoard.enemySlots.getOrNull(detection.slotIndex - 1)
                TeamSide.UNKNOWN -> null
            }
            slot?.status == DraftSlotStatus.CONFIRMED
        } ?: false
        val playerPickLocked = PlayerPickStatePolicy.isLocked(
            automaticPlayerPickLocked,
            playerPickOverride
        )
        val baseState = AssistantUiState(
            active = true,
            status = "",
            selectedStage = selectedStage,
            suggestedStage = suggestedStage,
            inputMode = inputMode,
            matchMode = matchMode,
            snapshot = merged,
            recommendations = recommendations,
            itemPlan = itemPlan,
            strategy = strategy,
            enemyOnRight = enemyOnRight,
            board = lastBoard,
            screenMode = lastScreenMode,
            subphase = lastSubphase,
            playerSlot = lastPlayerSlot,
            manualPlayerSlotIndex = manualPlayerSlotIndex,
            playerPickOverride = playerPickOverride,
            playerPickLocked = playerPickLocked,
            draftFlow = flow,
            learnedPortraitCount = learnedPortraitCount,
            recognition = lastRecognition,
            loadingRosterReconciliation = lastLoadingRosterReconciliation,
            manualAssignments = manualAssignments,
            slotRecognition = QuickCorrectionPolicy.applyManualAuthority(
                lastSlotRecognition,
                manualAssignments
            ),
            quickCorrectionRequest = quickCorrectionRequest,
            loadingConfirmationReview = loadingConfirmationReview,
            diagnostics = lastVisionDiagnostics
        )
        val baseStatus = buildString {
            append(stagePrefix(selectedStage))
            append(" · ")
            append(status)
            if (requestedRole != null) append(" · Rol: ").append(requestedRole)
            suggestedStage?.let {
                append(" · Sugerencia: cambiar a ")
                append(if (it == AssistantStage.IN_GAME) "Partida" else "Selección")
            }
        }
        AssistantSessionBus.publish(
            RecognitionUiStatePolicy.apply(
                state = baseState,
                baseStatus = baseStatus,
                calibration = lastRecognition
            )
        )
        startOverlayService()
    }

    private fun stagePrefix(stage: AssistantStage): String = when (stage) {
        AssistantStage.PAUSED -> "PAUSADO"
        AssistantStage.DRAFT -> "SELECCIÓN"
        AssistantStage.IN_GAME -> "PARTIDA"
    }

    private fun mergeManual(snapshot: DraftSnapshot): DraftSnapshot {
        return ManualDraftSnapshotMerger.merge(snapshot, manualAllies, manualEnemies)
    }

    private fun assignManualSlot(
        side: TeamSide,
        slotIndex: Int,
        heroName: String,
        teachLoading: Boolean
    ): Boolean {
        manualAssignments = manualAssignments.assign(side, slotIndex, heroName)
        if (quickCorrectionRequest?.slot == ManualTeamSlot(side, slotIndex)) {
            quickCorrectionRequest = null
        }
        syncLegacyManualSets()
        val templateDomain = when {
            teachLoading && loadingConfirmationReview &&
                lastSubphase == DraftSubphase.LOADING ->
                PortraitTemplateDomain.LOADING_CARD_PORTRAIT
            selectedStage == AssistantStage.DRAFT -> PortraitTemplateDomain.DRAFT_PORTRAIT
            else -> return false
        }
        val fingerprint = lastSlotFingerprints.firstOrNull {
            it.side == side && it.slotIndex == slotIndex
        } ?: return false
        val learned = visionEngine.learnPortrait(
            heroName,
            fingerprint,
            templateDomain
        )
        if (learned) {
            learnedPortraitCount++
            lastRecognition = visionEngine.recognitionCalibration()
        }
        return learned
    }

    private fun syncLegacyManualSets() {
        manualAllies.clear()
        manualAllies += manualAssignments.heroes(TeamSide.ALLY)
        manualEnemies.clear()
        manualEnemies += manualAssignments.heroes(TeamSide.ENEMY)
    }

    private fun updateExplicitScanCorrection() {
        if (explicitDraftScanFramesRemaining <= 0) return
        explicitDraftScanFramesRemaining--
        val presented = QuickCorrectionPolicy.applyManualAuthority(
            lastSlotRecognition,
            manualAssignments
        )
        val request = QuickCorrectionPolicy.correctionRequest(
            explicitScanRequested = true,
            states = presented
        )
        when {
            request != null -> {
                quickCorrectionRequest = request
                explicitDraftScanFramesRemaining = 0
            }
            presented.any {
                it.status == SlotRecognitionStatus.DETECTED ||
                    it.status == SlotRecognitionStatus.MANUAL
            } && presented.none { it.status == SlotRecognitionStatus.SCANNING } -> {
                quickCorrectionRequest = null
                explicitDraftScanFramesRemaining = 0
            }
            // An all-WAITING result commonly occurs while the board detector is still
            // stabilizing. Keep the explicit request alive so the remaining frames can
            // reach SCANNING and a real 3-of-5 decision.
            explicitDraftScanFramesRemaining == 0 -> quickCorrectionRequest = null
        }
    }

    private fun restoreOverlayAfterOneShot(openManualEditor: Boolean = false) {
        startService(
            Intent(this, OverlayService::class.java)
                .setAction(OverlayService.ACTION_RESTORE_AFTER_ONE_SHOT)
                .putExtra(OverlayService.EXTRA_OPEN_MANUAL_EDITOR, openManualEditor)
        )
    }

    private fun startOverlayService() {
        startService(
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_SHOW)
        )
    }

    private fun updateCaptureSurfaceForStage() {
        val display = virtualDisplay ?: return
        val targetSurface = if (selectedStage == AssistantStage.DRAFT) imageReader?.surface else null
        runCatching { display.setSurface(targetSurface) }
            .onFailure { Log.w(TAG, "No se pudo cambiar el estado de la captura", it) }
    }

    private fun updateNotification() {
        val text = when (selectedStage) {
            AssistantStage.PAUSED -> "Modo pausado · sin procesamiento visual"
            AssistantStage.DRAFT -> "Modo selección · analizando el draft"
            AssistantStage.IN_GAME -> "Modo partida · mostrando estrategia"
        }
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            10,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            11,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(0, getString(R.string.stop_assistant), stopIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        sessionCoordinator.advanceGeneration { }
        selectedStage = AssistantStage.PAUSED
        AssistantPreferences.setAssistantStage(this, selectedStage)
        AssistantSessionBus.publish(
            AssistantSessionBus.state.copy(
                active = false,
                status = "Asistente detenido",
                selectedStage = AssistantStage.PAUSED,
                suggestedStage = null
            )
        )
        visionEngine.close()
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        scoreboardRecognizer.close()
        imageReader = null
        captureSize = null
        captureDensityDpi = 0
        mediaProjection?.stop()
        mediaProjection = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
        stopService(Intent(this, OverlayService::class.java))
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.example.honorofkingsassistant.START_CAPTURE"
        const val ACTION_STOP = "com.example.honorofkingsassistant.STOP_CAPTURE"
        const val ACTION_SET_STAGE = "com.example.honorofkingsassistant.SET_STAGE"
        const val ACTION_SET_INPUT_MODE = "com.example.honorofkingsassistant.SET_INPUT_MODE"
        const val ACTION_SET_MATCH_MODE = "com.example.honorofkingsassistant.SET_MATCH_MODE"
        const val ACTION_SWAP_SIDES = "com.example.honorofkingsassistant.SWAP_SIDES"
        const val ACTION_FORCE_SCAN = "com.example.honorofkingsassistant.FORCE_SCAN"
        const val ACTION_MANUAL_ADD = "com.example.honorofkingsassistant.MANUAL_ADD"
        const val ACTION_MANUAL_ASSIGN_SLOT =
            "com.example.honorofkingsassistant.MANUAL_ASSIGN_SLOT"
        const val ACTION_MANUAL_REMOVE_SLOT =
            "com.example.honorofkingsassistant.MANUAL_REMOVE_SLOT"
        const val ACTION_CONFIRM_LOADING_ROSTER =
            "com.example.honorofkingsassistant.CONFIRM_LOADING_ROSTER"
        const val ACTION_SCAN_SCOREBOARD =
            "com.example.honorofkingsassistant.SCAN_SCOREBOARD"
        const val ACTION_SET_PLAYER_SLOT = "com.example.honorofkingsassistant.SET_PLAYER_SLOT"
        const val ACTION_SET_PLAYER_PICK_OVERRIDE = "com.example.honorofkingsassistant.SET_PLAYER_PICK_OVERRIDE"
        const val ACTION_MANUAL_CLEAR = "com.example.honorofkingsassistant.MANUAL_CLEAR"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_HERO_NAME = "extra_hero_name"
        const val EXTRA_TEAM_SIDE = "extra_team_side"
        const val EXTRA_MANUAL_SLOT_INDEX = "extra_manual_slot_index"
        const val EXTRA_TEACH_LOADING_TEMPLATE = "extra_teach_loading_template"
        const val EXTRA_ASSISTANT_STAGE = "extra_assistant_stage"
        const val EXTRA_INPUT_MODE = "extra_input_mode"
        const val EXTRA_MATCH_MODE = "extra_match_mode"
        const val EXTRA_PLAYER_SLOT_INDEX = "extra_player_slot_index"
        const val EXTRA_PLAYER_PICK_OVERRIDE = "extra_player_pick_override"

        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "draft_capture"
        private const val NOTIFICATION_ID = 2001
        private const val LOADING_CONFIRMATION_TIMEOUT_MS = 10_000L
        private const val EXPLICIT_SCAN_FRAME_BUDGET = 5
    }
}

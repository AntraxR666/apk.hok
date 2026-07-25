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

class ScreenCaptureService : Service() {
    private lateinit var counterEngine: CounterEngine
    private lateinit var recommendationEngine: DraftRecommendationEngine
    private lateinit var strategyEngine: StrategyEngine
    private lateinit var tracker: TemporalDraftTracker
    private lateinit var visionEngine: DraftVisionEngine
    private lateinit var boardStabilizer: DraftBoardTemporalStabilizer
    private lateinit var playerSlotResolver: PlayerSlotResolver

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
    private var learnedPortraitCount = 0
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
        boardStabilizer = DraftBoardTemporalStabilizer(requiredConfirmationFrames = 3)
        playerSlotResolver = PlayerSlotResolver(requiredHits = 4, maxMisses = 4)
        manualPlayerSlotIndex = AssistantPreferences.getManualPlayerSlot(this)
        playerPickOverride = AssistantPreferences.getPlayerPickOverride(this)
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
            callbackExecutor = callbackExecutor
        )
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
            ACTION_SWAP_SIDES -> {
                enemyOnRight = !enemyOnRight
                visionEngine.setEnemyOnRight(enemyOnRight)
                tracker.reset()
                boardStabilizer.reset()
                playerSlotResolver.reset()
                lastPlayerSlot = playerSlotResolver.resolve(null, manualPlayerSlotIndex)
                lastVisionSnapshot = DraftSnapshot(emptyList(), emptyList(), emptyList())
                lastBoard = DraftBoardState.empty(ScreenMode.DRAFT)
                lastScreenMode = ScreenMode.DRAFT
                lastSubphase = DraftSubphase.UNKNOWN
                suggestedStage = null
                publishCurrent("Lados intercambiados; esperando confirmaciones nuevas")
                return START_NOT_STICKY
            }
            ACTION_FORCE_SCAN -> {
                if (selectedStage == AssistantStage.DRAFT) {
                    forceNextFrame = true
                    publishCurrent("Escaneo solicitado")
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
                    if (teamSide == TeamSide.ALLY) manualAllies += heroName else manualEnemies += heroName
                    val learned = learnFromCurrentPreview(heroName, teamSide)
                    if (learned) learnedPortraitCount++
                    publishCurrent(
                        if (learned) "Corrección guardada; el retrato quedó aprendido localmente"
                        else "Héroe añadido manualmente"
                    )
                }
                return START_NOT_STICKY
            }
            ACTION_MANUAL_CLEAR -> {
                manualAllies.clear()
                manualEnemies.clear()
                publishCurrent("Correcciones manuales eliminadas")
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
                AssistantUiState(
                    active = true,
                    status = "Asistente listo para ${PersonalDeviceProfile.MODEL}. Elige Selección o Partida.",
                    selectedStage = selectedStage,
                    suggestedStage = null,
                    enemyOnRight = enemyOnRight
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
        if (image == null) return
        val policy = AssistantStagePolicy.forStage(selectedStage)
        if (!policy.shouldProcessFrames) {
            image.close()
            return
        }

        val now = SystemClock.elapsedRealtime()
        val effectiveIntervalMs = AdaptiveFrameCadence.interval(
            baseIntervalMs = policy.frameIntervalMs,
            averageLatencyMs = visionEngine.diagnostics().averageLatencyMs
        )
        if (!forceNextFrame && now - lastFrameAt < effectiveIntervalMs) {
            image.close()
            return
        }
        forceNextFrame = false
        lastFrameAt = now

        val bitmap = try {
            imageToBitmap(image)
        } catch (error: Exception) {
            Log.w(TAG, "No se pudo convertir el frame", error)
            null
        } finally {
            image.close()
        } ?: return

        val accepted = visionEngine.process(
            bitmap = bitmap,
            onResult = { result ->
                lastVisionDiagnostics = result.diagnostics
                lastScreenMode = result.screenMode
                lastSubphase = result.subphase
                suggestedStage = AssistantStagePolicy.suggest(
                    selectedStage = selectedStage,
                    detectedScene = result.screenMode.toDetectedScene()
                )

                if (result.screenMode == ScreenMode.DRAFT) {
                    lastBoard = boardStabilizer.stabilize(result.board)
                    lastPlayerSlot = playerSlotResolver.resolve(
                        result.playerSlot,
                        manualPlayerSlotIndex
                    )
                    lastSlotFingerprints = result.slotFingerprints
                    lastVisionSnapshot = tracker.observe(result.observations)
                }

                val stabilizedResult = result.copy(board = lastBoard)
                val status = when (result.subphase) {
                    DraftSubphase.BAN -> "Fase de veto detectada; preparando amenazas prioritarias"
                    DraftSubphase.PICK -> buildDraftStatus(stabilizedResult)
                    DraftSubphase.ADJUSTMENTS -> "Últimos ajustes detectados; composición cerrada y plan final preparado"
                    DraftSubphase.LOADING -> "Pantalla de carga detectada; esperando el inicio de la partida"
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
            }
        )
        if (!accepted) {
            lastVisionDiagnostics = visionEngine.diagnostics()
            if (!bitmap.isRecycled) bitmap.recycle()
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
        val automaticFlow = DraftFlowResolver.resolve(lastBoard, lastPlayerSlot)
        val flow = PlayerPickStatePolicy.apply(automaticFlow, playerPickOverride)
        val allCandidates = recommendationEngine.recommend(
            merged,
            requestedRole = requestedRole,
            limit = 3
        )
        val recommendations = if (
            selectedStage == AssistantStage.DRAFT && flow.shouldRecommendPicks
        ) allCandidates else emptyList()

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
        AssistantSessionBus.publish(
            AssistantUiState(
                active = true,
                status = buildString {
                    append(stagePrefix(selectedStage))
                    append(" · ")
                    append(status)
                    if (requestedRole != null) append(" · Rol: ").append(requestedRole)
                    suggestedStage?.let {
                        append(" · Sugerencia: cambiar a ")
                        append(if (it == AssistantStage.IN_GAME) "Partida" else "Selección")
                    }
                },
                selectedStage = selectedStage,
                suggestedStage = suggestedStage,
                snapshot = merged,
                recommendations = recommendations,
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
                diagnostics = lastVisionDiagnostics
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
        val allies = (snapshot.allies + manualAllies.map { ConfirmedHero(it, TeamSide.ALLY, 1.0) })
            .distinctBy { CounterCatalog.normalize(it.heroName) }
        val enemies = (snapshot.enemies + manualEnemies.map { ConfirmedHero(it, TeamSide.ENEMY, 1.0) })
            .distinctBy { CounterCatalog.normalize(it.heroName) }
        return snapshot.copy(allies = allies, enemies = enemies)
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
        const val ACTION_SWAP_SIDES = "com.example.honorofkingsassistant.SWAP_SIDES"
        const val ACTION_FORCE_SCAN = "com.example.honorofkingsassistant.FORCE_SCAN"
        const val ACTION_MANUAL_ADD = "com.example.honorofkingsassistant.MANUAL_ADD"
        const val ACTION_SET_PLAYER_SLOT = "com.example.honorofkingsassistant.SET_PLAYER_SLOT"
        const val ACTION_SET_PLAYER_PICK_OVERRIDE = "com.example.honorofkingsassistant.SET_PLAYER_PICK_OVERRIDE"
        const val ACTION_MANUAL_CLEAR = "com.example.honorofkingsassistant.MANUAL_CLEAR"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_HERO_NAME = "extra_hero_name"
        const val EXTRA_TEAM_SIDE = "extra_team_side"
        const val EXTRA_ASSISTANT_STAGE = "extra_assistant_stage"
        const val EXTRA_PLAYER_SLOT_INDEX = "extra_player_slot_index"
        const val EXTRA_PLAYER_PICK_OVERRIDE = "extra_player_pick_override"

        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "draft_capture"
        private const val NOTIFICATION_ID = 2001
    }
}

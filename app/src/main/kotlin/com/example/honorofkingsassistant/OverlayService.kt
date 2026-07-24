package com.example.honorofkingsassistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private var rootView: LinearLayout? = null
    private var panelView: ScrollView? = null
    private var bubbleView: TextView? = null
    private var statusView: TextView? = null
    private var teamsView: TextView? = null
    private var recommendationsView: TextView? = null
    private var strategyView: TextView? = null
    private var playerSlotLabelView: TextView? = null
    private var playerPickLabelView: TextView? = null
    private var suggestionButton: Button? = null
    private var rescanButton: Button? = null
    private val playerSlotButtons = linkedMapOf<Int, Button>()
    private val playerPickButtons = linkedMapOf<PlayerPickOverride, Button>()
    private var params: WindowManager.LayoutParams? = null
    private var unsubscribe: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopAssistant()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (rootView == null) createOverlay()
        if (unsubscribe == null) {
            unsubscribe = AssistantSessionBus.subscribe { state ->
                mainHandler.post { render(state) }
            }
        }
        return START_STICKY
    }

    @Suppress("DEPRECATION")
    private fun createOverlay() {
        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(36)
        }
        params = layoutParams

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(6))
            setBackgroundColor(Color.argb(238, 18, 22, 28))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = dp(10).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val bubble = TextView(this).apply {
            text = "HOK · PAUSA"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.rgb(46, 125, 250))
        }
        bubbleView = bubble
        val toggle = compactButton(getString(R.string.expand_overlay)) {
            val panel = panelView ?: return@compactButton
            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            it.text = if (panel.visibility == View.VISIBLE) {
                getString(R.string.collapse_overlay)
            } else {
                getString(R.string.expand_overlay)
            }
            root.post { clampOverlayPosition(root, layoutParams) }
        }
        val close = compactButton(getString(R.string.stop_short)) { stopAssistant() }
        header.addView(bubble)
        header.addView(toggle)
        header.addView(close)

        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        val scrollPanel = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                panelContent,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            this.layoutParams = LinearLayout.LayoutParams(
                maxOverlayPanelWidth(),
                maxOverlayPanelHeight()
            )
        }
        panelView = scrollPanel

        statusView = overlayText(13f, true)
        teamsView = overlayText(13f, false)
        recommendationsView = overlayText(13f, false)
        strategyView = overlayText(12f, false)

        panelContent.addView(statusView)
        panelContent.addView(sectionLabel(getString(R.string.manual_stage_title)))
        panelContent.addView(
            buttonRow(
                actionButton(getString(R.string.mode_draft_short)) {
                    sendStageAction(AssistantStage.DRAFT)
                },
                actionButton(getString(R.string.mode_game_short)) {
                    sendStageAction(AssistantStage.IN_GAME)
                },
                actionButton(getString(R.string.mode_pause_short)) {
                    sendStageAction(AssistantStage.PAUSED)
                }
            )
        )

        suggestionButton = actionButton(getString(R.string.confirm_stage_change)) {
            AssistantSessionBus.state.suggestedStage?.let { sendStageAction(it) }
        }.apply { visibility = View.GONE }
        panelContent.addView(suggestionButton)

        playerSlotLabelView = sectionLabel(getString(R.string.player_slot_auto))
        panelContent.addView(playerSlotLabelView)
        val firstSlotRow = buttonRow(
            playerSlotButton(0, getString(R.string.player_slot_auto_short)),
            playerSlotButton(1, "1"),
            playerSlotButton(2, "2")
        )
        val secondSlotRow = buttonRow(
            playerSlotButton(3, "3"),
            playerSlotButton(4, "4"),
            playerSlotButton(5, "5")
        )
        panelContent.addView(firstSlotRow)
        panelContent.addView(secondSlotRow)

        playerPickLabelView = sectionLabel(getString(R.string.player_pick_state_auto))
        panelContent.addView(playerPickLabelView)
        panelContent.addView(
            buttonRow(
                playerPickButton(PlayerPickOverride.AUTO, getString(R.string.player_pick_auto_short)),
                playerPickButton(PlayerPickOverride.PENDING, getString(R.string.player_pick_pending_short)),
                playerPickButton(PlayerPickOverride.LOCKED, getString(R.string.player_pick_locked_short))
            )
        )

        panelContent.addView(teamsView)
        panelContent.addView(recommendationsView)
        panelContent.addView(strategyView)

        rescanButton = actionButton(getString(R.string.rescan)) {
            sendCaptureAction(ScreenCaptureService.ACTION_FORCE_SCAN)
        }
        panelContent.addView(
            buttonRow(
                requireNotNull(rescanButton),
                actionButton(getString(R.string.swap_sides)) {
                    sendCaptureAction(ScreenCaptureService.ACTION_SWAP_SIDES)
                },
                actionButton(getString(R.string.manual_edit)) {
                    startActivity(
                        Intent(this@OverlayService, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    )
                }
            )
        )

        root.addView(header)
        root.addView(scrollPanel)
        attachDrag(bubble, root, layoutParams)
        windowManager.addView(root, layoutParams)
        rootView = root
        root.post { clampOverlayPosition(root, layoutParams) }
        render(AssistantSessionBus.state)
    }

    private fun sectionLabel(label: String): TextView = overlayText(12f, true).apply {
        text = label
        setPadding(dp(4), dp(8), dp(4), dp(3))
    }

    private fun overlayText(size: Float, bold: Boolean): TextView = TextView(this).apply {
        setTextColor(Color.WHITE)
        textSize = size
        setPadding(dp(4), dp(4), dp(4), dp(4))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun compactButton(label: String, action: (Button) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 9f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(dp(5), dp(2), dp(5), dp(2))
        setOnClickListener { action(this) }
    }

    private fun actionButton(label: String, action: () -> Unit): Button = compactButton(label) {
        action()
    }

    private fun playerSlotButton(slotIndex: Int, label: String): Button = actionButton(label) {
        sendPlayerSlotAction(slotIndex)
    }.also { playerSlotButtons[slotIndex] = it }

    private fun playerPickButton(override: PlayerPickOverride, label: String): Button = actionButton(label) {
        sendPlayerPickOverrideAction(override)
    }.also { playerPickButtons[override] = it }

    private fun buttonRow(vararg buttons: Button): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        buttons.forEach { button ->
            addView(
                button,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun attachDrag(
        dragHandle: View,
        overlayView: View,
        layoutParams: WindowManager.LayoutParams
    ) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    layoutParams.x = startX - (event.rawX - downX).toInt()
                    layoutParams.y = startY + (event.rawY - downY).toInt()
                    clampOverlayPosition(overlayView, layoutParams)
                    runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
                    true
                }
                else -> true
            }
        }
    }

    private fun clampOverlayPosition(view: View, layoutParams: WindowManager.LayoutParams) {
        val (availableWidth, availableHeight) = availableDisplaySize()
        val maxX = (availableWidth - view.width).coerceAtLeast(0)
        val maxY = (availableHeight - view.height).coerceAtLeast(0)
        layoutParams.x = layoutParams.x.coerceIn(0, maxX)
        layoutParams.y = layoutParams.y.coerceIn(0, maxY)
    }

    private fun maxOverlayPanelWidth(): Int {
        val (width, _) = availableDisplaySize()
        val screenBound = (width - dp(16)).coerceAtLeast(dp(180))
        return minOf(dp(360), (width * 0.52f).toInt(), screenBound)
    }

    private fun maxOverlayPanelHeight(): Int {
        val (_, height) = availableDisplaySize()
        val screenBound = (height - dp(72)).coerceAtLeast(dp(120))
        return minOf(dp(520), screenBound)
    }

    @Suppress("DEPRECATION")
    private fun availableDisplaySize(): Pair<Int, Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or
                    WindowInsets.Type.navigationBars() or
                    WindowInsets.Type.displayCutout()
            )
            val bounds = metrics.bounds
            return (
                bounds.width() - insets.left - insets.right
                ).coerceAtLeast(1) to (
                bounds.height() - insets.top - insets.bottom
                ).coerceAtLeast(1)
        }
        val metrics = resources.displayMetrics
        return metrics.widthPixels.coerceAtLeast(1) to metrics.heightPixels.coerceAtLeast(1)
    }

    private fun render(state: AssistantUiState) {
        bubbleView?.text = when (state.selectedStage) {
            AssistantStage.PAUSED -> "HOK · PAUSA"
            AssistantStage.DRAFT -> "HOK · DRAFT"
            AssistantStage.IN_GAME -> "HOK · PARTIDA"
        }
        statusView?.text = state.status
        rescanButton?.isEnabled = state.selectedStage == AssistantStage.DRAFT

        val suggested = state.suggestedStage
        suggestionButton?.apply {
            visibility = if (suggested == null) View.GONE else View.VISIBLE
            text = when (suggested) {
                AssistantStage.DRAFT -> getString(R.string.confirm_switch_to_draft)
                AssistantStage.IN_GAME -> getString(R.string.confirm_switch_to_game)
                AssistantStage.PAUSED, null -> getString(R.string.confirm_stage_change)
            }
        }

        val selectedSlot = state.manualPlayerSlotIndex ?: 0
        playerSlotLabelView?.text = if (state.manualPlayerSlotIndex == null) {
            state.playerSlot?.let {
                getString(R.string.player_slot_auto_detected, it.slotIndex)
            } ?: getString(R.string.player_slot_auto)
        } else {
            getString(R.string.player_slot_manual_selected, state.manualPlayerSlotIndex)
        }
        playerSlotButtons.forEach { (slot, button) ->
            val base = if (slot == 0) getString(R.string.player_slot_auto_short) else slot.toString()
            button.text = if (slot == selectedSlot) "✓ $base" else base
        }

        playerPickLabelView?.text = when (state.playerPickOverride) {
            PlayerPickOverride.AUTO -> getString(R.string.player_pick_state_auto)
            PlayerPickOverride.PENDING -> getString(R.string.player_pick_state_pending)
            PlayerPickOverride.LOCKED -> getString(R.string.player_pick_state_locked)
        }
        playerPickButtons.forEach { (override, button) ->
            val base = when (override) {
                PlayerPickOverride.AUTO -> getString(R.string.player_pick_auto_short)
                PlayerPickOverride.PENDING -> getString(R.string.player_pick_pending_short)
                PlayerPickOverride.LOCKED -> getString(R.string.player_pick_locked_short)
            }
            button.text = if (override == state.playerPickOverride) "✓ $base" else base
        }

        teamsView?.text = buildString {
            append("Modo manual: ")
            append(
                when (state.selectedStage) {
                    AssistantStage.PAUSED -> "Pausado"
                    AssistantStage.DRAFT -> "Selección de héroes"
                    AssistantStage.IN_GAME -> "Partida en curso"
                }
            )
            append("\nPantalla detectada: ")
            append(
                when (state.screenMode) {
                    ScreenMode.DRAFT -> "draft"
                    ScreenMode.IN_GAME -> "partida"
                    ScreenMode.UNKNOWN -> "sin confirmar"
                }
            )
            append(" · fase: ")
            append(
                when (state.subphase) {
                    DraftSubphase.BAN -> "veto"
                    DraftSubphase.PICK -> "selección"
                    DraftSubphase.ADJUSTMENTS -> "últimos ajustes"
                    DraftSubphase.LOADING -> "carga"
                    DraftSubphase.IN_GAME -> "partida"
                    DraftSubphase.UNKNOWN -> "sin confirmar"
                }
            )
            append("\nDraft: ")
            append(state.board.totalConfirmedCount)
            append("/10 confirmados")
            if (state.board.previewingCount > 0) {
                append(" · ")
                append(state.board.previewingCount)
                append(" preselección")
            }
            append("\nAliados: ")
            append(state.snapshot.allies.joinToString { it.heroName }.ifBlank { "—" })
            append("\nEnemigos: ")
            append(state.snapshot.enemies.joinToString { it.heroName }.ifBlank { "—" })
            if (state.selectedStage == AssistantStage.DRAFT) {
                append("\nTurno: ")
                append(
                    when (state.board.activeSide) {
                        TeamSide.ALLY -> "equipo aliado"
                        TeamSide.ENEMY -> "equipo enemigo"
                        TeamSide.UNKNOWN -> if (state.board.isComplete) "draft completo" else "transición"
                    }
                )
            }
            state.playerSlot?.let {
                append("\nTu slot: ")
                append(it.slotIndex)
                append(if (state.playerPickLocked) " · pick fijado" else " · pendiente")
                append(if (state.manualPlayerSlotIndex != null) " · manual" else " · auto estable")
            } ?: append("\nTu slot: sin confirmar; selecciona 1–5 si Auto falla")
            if (state.learnedPortraitCount > 0) {
                append("\nRetratos aprendidos: ")
                append(state.learnedPortraitCount)
            }
        }

        recommendationsView?.text = when (state.selectedStage) {
            AssistantStage.PAUSED -> "Selecciona DRAFT o PARTIDA para activar la función correspondiente."
            AssistantStage.IN_GAME -> "Modo partida: se conserva la composición y se muestra el plan; el OCR queda pausado."
            AssistantStage.DRAFT -> when {
                state.subphase == DraftSubphase.BAN ->
                    "Fase de veto detectada. Top 3 se activará al comenzar la selección."
                state.subphase == DraftSubphase.LOADING ->
                    "Pantalla de carga detectada. La composición se conserva para el plan de partida."
                state.subphase == DraftSubphase.ADJUSTMENTS || state.board.isComplete ->
                    "Draft completo. La estrategia final está preparada."
                state.playerSlot != null && state.playerPickLocked ->
                    "Tu héroe ya está fijado. El asistente mantiene el análisis de amenazas."
                state.draftFlow.moment == DraftMoment.FINAL_ENEMY_PICK ->
                    "Tu equipo está completo. Esperando el último pick enemigo para cerrar el plan."
                state.recommendations.isEmpty() ->
                    "Top picks: esperando identidades de héroes o una corrección rápida."
                else -> buildString {
                    append("Top picks\n")
                    state.recommendations.forEachIndexed { index, recommendation ->
                        append(index + 1)
                        append(". ")
                        append(recommendation.hero.name)
                        append(" · ")
                        append(recommendation.hero.role)
                        append(" · ")
                        append((recommendation.confidence * 100).toInt())
                        append("%\n")
                        append(recommendation.evidence.firstOrNull().orEmpty())
                        if (index != state.recommendations.lastIndex) append("\n")
                    }
                }
            }
        }

        strategyView?.text = when (state.selectedStage) {
            AssistantStage.PAUSED -> "Plan conservado; no se realizan análisis nuevos."
            else -> state.strategy?.asLines()?.joinToString("\n")
                ?: if (state.board.totalConfirmedCount > 0) {
                    "Plan: esperando identificar más héroes; puedes corregir la composición."
                } else {
                    "Plan: esperando composición"
                }
        }
    }

    private fun sendStageAction(stage: AssistantStage) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_STAGE)
                .putExtra(ScreenCaptureService.EXTRA_ASSISTANT_STAGE, stage.name)
        )
    }

    private fun sendPlayerSlotAction(slotIndex: Int) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_PLAYER_SLOT)
                .putExtra(ScreenCaptureService.EXTRA_PLAYER_SLOT_INDEX, slotIndex)
        )
    }

    private fun sendPlayerPickOverrideAction(override: PlayerPickOverride) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_PLAYER_PICK_OVERRIDE)
                .putExtra(ScreenCaptureService.EXTRA_PLAYER_PICK_OVERRIDE, override.name)
        )
    }

    private fun sendCaptureAction(action: String) {
        startService(Intent(this, ScreenCaptureService::class.java).setAction(action))
    }

    private fun stopAssistant() {
        startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(getString(R.string.overlay_notification_title))
            .setContentText(getString(R.string.overlay_notification_text_v4))
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.stop_assistant), stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.overlay_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        unsubscribe?.invoke()
        unsubscribe = null
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        panelView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.example.honorofkingsassistant.SHOW_OVERLAY"
        const val ACTION_STOP = "com.example.honorofkingsassistant.STOP_OVERLAY"
        private const val CHANNEL_ID = "draft_overlay"
        private const val NOTIFICATION_ID = 1001
    }
}

package com.example.honorofkingsassistant

import android.app.Service
import android.content.Intent
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var counterEngine: CounterEngine
    private var rootView: LinearLayout? = null
    private var panelView: ScrollView? = null
    private var mainPanelContent: LinearLayout? = null
    private var manualEditorContent: LinearLayout? = null
    private var toggleButton: Button? = null
    private var bubbleView: TextView? = null
    private var statusView: TextView? = null
    private var diagnosticsView: TextView? = null
    private var teamsView: TextView? = null
    private var recommendationsView: TextView? = null
    private var strategyView: TextView? = null
    private var playerSlotLabelView: TextView? = null
    private var playerPickLabelView: TextView? = null
    private var suggestionButton: Button? = null
    private var rescanButton: Button? = null
    private var loadingRosterButton: Button? = null
    private var scoreboardScanButton: Button? = null
    private val playerSlotButtons = linkedMapOf<Int, Button>()
    private val playerPickButtons = linkedMapOf<PlayerPickOverride, Button>()
    private val inputModeButtons = linkedMapOf<InputMode, Button>()
    private val matchModeButtons = linkedMapOf<MatchMode, Button>()
    private var params: WindowManager.LayoutParams? = null
    private var lastRenderedStage: AssistantStage? = null
    private var unsubscribe: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        counterEngine = CounterEngine(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAssistant()
                return START_NOT_STICKY
            }
            ACTION_RESTORE_AFTER_ONE_SHOT -> {
                rootView?.visibility = View.VISIBLE
                if (intent.getBooleanExtra(EXTRA_OPEN_MANUAL_EDITOR, false)) {
                    showManualEditor(forceTenSlots = true)
                }
            }
        }

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
        return START_NOT_STICKY
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
            background = panelBackground(COLLAPSED_ALPHA)
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
            gravity = Gravity.CENTER_VERTICAL
            minHeight = dp(DRAG_HANDLE_HEIGHT_DP)
            setPadding(dp(10), 0, dp(10), 0)
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
            root.background = panelBackground(
                if (panel.visibility == View.VISIBLE) EXPANDED_ALPHA else COLLAPSED_ALPHA
            )
            if (panel.visibility != View.VISIBLE) showMainPanel()
            root.post { clampOverlayPosition(root, layoutParams) }
        }
        toggleButton = toggle
        val close = compactButton(getString(R.string.stop_short)) { stopAssistant() }
        header.addView(bubble)
        header.addView(toggle)
        header.addView(close)

        val panelContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        mainPanelContent = panelContent
        val manualContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(4), dp(8), dp(4), dp(8))
        }
        manualEditorContent = manualContent
        val panelHost = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(panelContent)
            addView(manualContent)
        }
        val scrollPanel = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                panelHost,
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
        diagnosticsView = overlayText(11f, false).apply { visibility = View.GONE }
        teamsView = overlayText(13f, false)
        recommendationsView = overlayText(13f, false)
        strategyView = overlayText(12f, false)

        panelContent.addView(statusView)
        panelContent.addView(requireNotNull(diagnosticsView))
        panelContent.addView(sectionLabel(getString(R.string.input_mode_title)))
        panelContent.addView(
            buttonRow(
                inputModeButton(InputMode.AUTO_SCAN, getString(R.string.input_auto)),
                inputModeButton(InputMode.MANUAL, getString(R.string.input_manual))
            )
        )
        panelContent.addView(sectionLabel(getString(R.string.match_mode_title)))
        panelContent.addView(
            buttonRow(
                matchModeButton(MatchMode.AUTO, getString(R.string.match_auto)),
                matchModeButton(MatchMode.RANKED_DRAFT, getString(R.string.match_ranked)),
                matchModeButton(MatchMode.NORMAL_BLIND, getString(R.string.match_normal))
            )
        )
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

        // Keep the recovery actions above the slot and strategy details. During a live match
        // the user must be able to reach them with one expansion and a short tap sequence.
        panelContent.addView(sectionLabel(getString(R.string.verification_actions_title)))
        panelContent.addView(
            actionButton(getString(R.string.manual_editor_title)) {
                showManualEditor(forceTenSlots = false)
            }
        )
        loadingRosterButton = actionButton(getString(R.string.confirm_loading_roster)) {
                root.visibility = View.INVISIBLE
                sendCaptureAction(ScreenCaptureService.ACTION_CONFIRM_LOADING_ROSTER)
                mainHandler.postDelayed({
                    if (root.visibility != View.VISIBLE) root.visibility = View.VISIBLE
                }, ONE_SHOT_OVERLAY_TIMEOUT_MS)
            }
        panelContent.addView(requireNotNull(loadingRosterButton))
        scoreboardScanButton = actionButton(getString(R.string.scan_scoreboard_items)) {
            root.visibility = View.INVISIBLE
            sendCaptureAction(ScreenCaptureService.ACTION_SCAN_SCOREBOARD)
            mainHandler.postDelayed({
                if (root.visibility != View.VISIBLE) root.visibility = View.VISIBLE
            }, ONE_SHOT_OVERLAY_TIMEOUT_MS)
        }
        panelContent.addView(requireNotNull(scoreboardScanButton))

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
        panelContent.addView(buttonRow(requireNotNull(rescanButton)))
        panelContent.addView(
            buttonRow(
                actionButton(getString(R.string.swap_sides)) {
                    sendCaptureAction(ScreenCaptureService.ACTION_SWAP_SIDES)
                },
                actionButton(getString(R.string.edit_team)) {
                    showManualEditor(forceTenSlots = false)
                }
            )
        )

        root.addView(header)
        root.addView(scrollPanel)
        val dragHandle = bubble
        attachDrag(dragHandle, root, layoutParams)
        windowManager.addView(root, layoutParams)
        rootView = root
        root.post { clampOverlayPosition(root, layoutParams) }
        render(AssistantSessionBus.state)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updatePanelBoundsAndClamp()
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

    private fun inputModeButton(mode: InputMode, label: String): Button = actionButton(label) {
        sendInputModeAction(mode)
        if (mode == InputMode.MANUAL) showManualEditor(forceTenSlots = false)
    }.also { inputModeButtons[mode] = it }

    private fun matchModeButton(mode: MatchMode, label: String): Button = actionButton(label) {
        sendMatchModeAction(mode)
    }.also { matchModeButtons[mode] = it }

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
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = layoutParams.x
                    startY = layoutParams.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragging = true
                    }
                    if (dragging) {
                        layoutParams.x = startX - deltaX.toInt()
                        layoutParams.y = startY + deltaY.toInt()
                        clampOverlayPosition(overlayView, layoutParams)
                        runCatching { windowManager.updateViewLayout(overlayView, layoutParams) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) dragHandle.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    dragging = false
                    true
                }
                else -> false
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
        return (height * MAX_PANEL_HEIGHT_RATIO).roundToInt().coerceAtLeast(dp(120))
    }

    private fun updatePanelBoundsAndClamp() {
        val panel = panelView ?: return
        panel.layoutParams = LinearLayout.LayoutParams(
            maxOverlayPanelWidth(),
            maxOverlayPanelHeight()
        )
        val root = rootView ?: return
        val layoutParams = params ?: return
        root.post {
            clampOverlayPosition(root, layoutParams)
            runCatching { windowManager.updateViewLayout(root, layoutParams) }
        }
    }

    private fun panelBackground(alpha: Float): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(10).toFloat()
        setColor(Color.argb((alpha * 255).roundToInt(), 18, 22, 28))
    }

    private fun showMainPanel() {
        mainPanelContent?.visibility = View.VISIBLE
        manualEditorContent?.visibility = View.GONE
        setOverlayFocusable(false)
    }

    private fun showManualEditor(forceTenSlots: Boolean) {
        val editor = manualEditorContent ?: return
        setOverlayFocusable(false)
        val state = AssistantSessionBus.state
        val effectiveMode = if (forceTenSlots) {
            MatchMode.RANKED_DRAFT
        } else {
            state.matchMode.effective
        }
        mainPanelContent?.visibility = View.GONE
        editor.visibility = View.VISIBLE
        editor.removeAllViews()
        editor.addView(sectionLabel(getString(R.string.manual_editor_title)))
        editor.addView(
            actionButton(getString(R.string.back_to_summary)) {
                showMainPanel()
            }
        )
        ManualTeamEditorPolicy.slots(effectiveMode).forEach { slot ->
            val current = state.manualAssignments.heroAt(slot.side, slot.slotIndex)
                ?: rosterHeroAt(state, slot)
            val label = buildString {
                append(if (slot.side == TeamSide.ALLY) getString(R.string.ally_short) else getString(R.string.enemy_short))
                append(" ")
                append(slot.slotIndex)
                append(": ")
                append(current ?: getString(R.string.unassigned))
            }
            val row = buttonRow(
                actionButton(label) {
                    showHeroPicker(slot, forceTenSlots)
                }
            )
            if (current != null) {
                row.addView(
                    actionButton(getString(R.string.remove_hero)) {
                        sendManualRemove(slot)
                        mainHandler.postDelayed(
                            { showManualEditor(forceTenSlots) },
                            UI_REFRESH_DELAY_MS
                        )
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            editor.addView(row)
        }
        panelView?.scrollTo(0, 0)
    }

    private fun showHeroPicker(slot: ManualTeamSlot, teachLoading: Boolean) {
        val editor = manualEditorContent ?: return
        editor.removeAllViews()
        editor.addView(sectionLabel(getString(R.string.choose_hero)))
        editor.addView(
            actionButton(getString(R.string.back_to_team_editor)) {
                showManualEditor(forceTenSlots = teachLoading)
            }
        )
        val heroesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        var selectedRole: String? = null
        var query = ""
        fun renderHeroes() {
            heroesContainer.removeAllViews()
            val normalizedQuery = CounterCatalog.normalize(query)
            val matches = counterEngine.heroesForRole(selectedRole).filter { hero ->
                normalizedQuery.isBlank() || sequenceOf(hero.name, hero.id)
                    .plus(hero.allRecognitionAliases().asSequence())
                    .map(CounterCatalog::normalize)
                    .any { normalizedQuery in it }
            }
            if (matches.isEmpty()) {
                heroesContainer.addView(
                    overlayText(12f, false).apply {
                        text = getString(R.string.no_hero_matches)
                    }
                )
            }
            matches.forEach { hero ->
                val title = hero.identityAliases.displayTitles.firstOrNull()
                val label = if (title == null) hero.name else "${hero.name} · $title"
                heroesContainer.addView(
                    actionButton(label) {
                        sendManualAssignment(slot, hero.name, teachLoading)
                        showMainPanel()
                        panelView?.scrollTo(0, 0)
                    }
                )
            }
        }
        val searchInput = EditText(this).apply {
            hint = getString(R.string.search_heroes)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.LTGRAY)
            textSize = 13f
            isSingleLine = true
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) setOverlayFocusable(true)
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(text: Editable?) {
                    query = text?.toString().orEmpty()
                    renderHeroes()
                }
            })
        }
        editor.addView(searchInput)
        editor.addView(sectionLabel(getString(R.string.filter_role)))
        editor.addView(
            buttonRow(
                actionButton(getString(R.string.role_all)) {
                    selectedRole = null
                    renderHeroes()
                },
                actionButton(getString(R.string.role_clash)) {
                    selectedRole = "Clash Lane"
                    renderHeroes()
                },
                actionButton(getString(R.string.role_mid)) {
                    selectedRole = "Mid Lane"
                    renderHeroes()
                }
            )
        )
        editor.addView(
            buttonRow(
                actionButton(getString(R.string.role_farm)) {
                    selectedRole = "Farm Lane"
                    renderHeroes()
                },
                actionButton(getString(R.string.role_jungle)) {
                    selectedRole = "Jungler"
                    renderHeroes()
                },
                actionButton(getString(R.string.role_roam)) {
                    selectedRole = "Roamer/Support"
                    renderHeroes()
                }
            )
        )
        editor.addView(heroesContainer)
        renderHeroes()
        panelView?.scrollTo(0, 0)
    }

    private fun setOverlayFocusable(focusable: Boolean) {
        val root = rootView ?: return
        val layoutParams = params ?: return
        val updatedFlags = if (focusable) {
            layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        if (updatedFlags == layoutParams.flags) return
        layoutParams.flags = updatedFlags
        if (!focusable) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                ?.hideSoftInputFromWindow(root.windowToken, 0)
        }
        runCatching { windowManager.updateViewLayout(root, layoutParams) }
    }

    private fun rosterHeroAt(state: AssistantUiState, slot: ManualTeamSlot): String? {
        val loading = state.loadingRosterReconciliation?.assignments?.firstOrNull {
            it.side == slot.side && it.slotIndex == slot.slotIndex
        }?.heroName
        if (loading != null) return loading
        val heroes = if (slot.side == TeamSide.ALLY) state.snapshot.allies else state.snapshot.enemies
        return heroes.getOrNull(slot.slotIndex - 1)?.heroName
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
        if (state.selectedStage == AssistantStage.IN_GAME &&
            lastRenderedStage != AssistantStage.IN_GAME
        ) {
            panelView?.visibility = View.GONE
            rootView?.background = panelBackground(COLLAPSED_ALPHA)
            toggleButton?.text = getString(R.string.expand_overlay)
            showMainPanel()
        }
        lastRenderedStage = state.selectedStage
        bubbleView?.text = when (state.selectedStage) {
            AssistantStage.PAUSED -> "HOK · PAUSA"
            AssistantStage.DRAFT -> "HOK · DRAFT"
            AssistantStage.IN_GAME -> "HOK · PARTIDA"
        }
        statusView?.text = state.status
        val diagnostics = state.diagnostics
        diagnosticsView?.apply {
            val showDiagnostics = state.selectedStage == AssistantStage.DRAFT &&
                diagnostics.processedFrames > 0
            visibility = if (showDiagnostics) View.VISIBLE else View.GONE
            if (showDiagnostics) {
                text = getString(
                    R.string.vision_diagnostics,
                    diagnostics.averageLatencyMs,
                    diagnostics.captureLabel,
                    diagnostics.ocrLabel,
                    diagnostics.processedFrames,
                    diagnostics.droppedFrames
                )
            }
        }
        rescanButton?.isEnabled = state.selectedStage == AssistantStage.DRAFT
        loadingRosterButton?.isEnabled = state.selectedStage == AssistantStage.DRAFT
        loadingRosterButton?.alpha = if (state.selectedStage == AssistantStage.DRAFT) 1f else 0.55f
        scoreboardScanButton?.isEnabled = state.selectedStage == AssistantStage.IN_GAME
        scoreboardScanButton?.alpha = if (state.selectedStage == AssistantStage.IN_GAME) 1f else 0.55f
        inputModeButtons.forEach { (mode, button) ->
            val base = if (mode == InputMode.AUTO_SCAN) {
                getString(R.string.input_auto)
            } else {
                getString(R.string.input_manual)
            }
            button.text = if (mode == state.inputMode) "✓ $base" else base
        }
        matchModeButtons.forEach { (mode, button) ->
            val base = when (mode) {
                MatchMode.AUTO -> getString(R.string.match_auto)
                MatchMode.RANKED_DRAFT -> getString(R.string.match_ranked)
                MatchMode.NORMAL_BLIND -> getString(R.string.match_normal)
            }
            button.text = if (mode == state.matchMode.preference) "✓ $base" else base
        }

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

        val strategyText = when (state.selectedStage) {
            AssistantStage.PAUSED -> "Plan conservado; no se realizan análisis nuevos."
            else -> state.strategy?.asLines()?.joinToString("\n")
                ?: if (state.board.totalConfirmedCount > 0) {
                    "Plan: esperando identificar más héroes; puedes corregir la composición."
                } else {
                    "Plan: esperando composición"
                }
        }
        strategyView?.text = buildString {
            append(strategyText)
            state.itemPlan?.let { plan ->
                append("\n\nCompra siguiente\n")
                append(plan.nextItems.joinToString(" · ") { it.name })
                plan.evidence.firstOrNull()?.let { append("\n").append(it) }
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

    private fun sendInputModeAction(mode: InputMode) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_INPUT_MODE)
                .putExtra(ScreenCaptureService.EXTRA_INPUT_MODE, mode.name)
        )
    }

    private fun sendMatchModeAction(mode: MatchMode) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_MATCH_MODE)
                .putExtra(ScreenCaptureService.EXTRA_MATCH_MODE, mode.name)
        )
    }

    private fun sendManualAssignment(
        slot: ManualTeamSlot,
        heroName: String,
        teachLoading: Boolean
    ) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_MANUAL_ASSIGN_SLOT)
                .putExtra(ScreenCaptureService.EXTRA_TEAM_SIDE, slot.side.name)
                .putExtra(ScreenCaptureService.EXTRA_MANUAL_SLOT_INDEX, slot.slotIndex)
                .putExtra(ScreenCaptureService.EXTRA_HERO_NAME, heroName)
                .putExtra(ScreenCaptureService.EXTRA_TEACH_LOADING_TEMPLATE, teachLoading)
        )
    }

    private fun sendManualRemove(slot: ManualTeamSlot) {
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_MANUAL_REMOVE_SLOT)
                .putExtra(ScreenCaptureService.EXTRA_TEAM_SIDE, slot.side.name)
                .putExtra(ScreenCaptureService.EXTRA_MANUAL_SLOT_INDEX, slot.slotIndex)
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        unsubscribe?.invoke()
        unsubscribe = null
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        panelView = null
        diagnosticsView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_SHOW = "com.example.honorofkingsassistant.SHOW_OVERLAY"
        const val ACTION_STOP = "com.example.honorofkingsassistant.STOP_OVERLAY"
        const val ACTION_RESTORE_AFTER_ONE_SHOT =
            "com.example.honorofkingsassistant.RESTORE_OVERLAY_AFTER_ONE_SHOT"
        const val EXTRA_OPEN_MANUAL_EDITOR = "extra_open_manual_editor"
        const val COLLAPSED_ALPHA = 0.55f
        const val EXPANDED_ALPHA = 0.82f
        const val MAX_PANEL_HEIGHT_RATIO = 0.72f
        const val DRAG_HANDLE_HEIGHT_DP = 40
        private const val ONE_SHOT_OVERLAY_TIMEOUT_MS = 10_500L
        private const val UI_REFRESH_DELAY_MS = 120L
    }
}

package com.example.honorofkingsassistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var counterEngine: CounterEngine
    private lateinit var statusText: TextView
    private lateinit var startAssistantButton: Button
    private lateinit var stopAssistantButton: Button
    private lateinit var draftModeButton: Button
    private lateinit var gameModeButton: Button
    private lateinit var pauseModeButton: Button
    private lateinit var roleSpinner: Spinner
    private lateinit var playerSlotSpinner: Spinner
    private lateinit var playerPickSpinner: Spinner
    private lateinit var heroSearch: AutoCompleteTextView
    private lateinit var sideSpinner: Spinner
    private lateinit var manualAddButton: Button
    private lateinit var manualCountersButton: Button
    private lateinit var clearManualButton: Button
    private lateinit var resultsContainer: LinearLayout
    private var pendingAssistantStart = false
    private var unsubscribe: (() -> Unit)? = null

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) continueOverlayPermissionFlow() else {
            pendingAssistantStart = false
            statusText.text = getString(R.string.notification_permission_needed)
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) requestCaptureConsent() else {
            pendingAssistantStart = false
            statusText.text = getString(R.string.overlay_permission_needed)
        }
    }

    private val capturePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pendingAssistantStart = false
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) {
            statusText.text = getString(R.string.capture_permission_needed)
            return@registerForActivityResult
        }
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_START)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
        )
        statusText.text = getString(R.string.assistant_starting)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        startAssistantButton = findViewById(R.id.startAssistantButton)
        stopAssistantButton = findViewById(R.id.stopAssistantButton)
        draftModeButton = findViewById(R.id.draftModeButton)
        gameModeButton = findViewById(R.id.gameModeButton)
        pauseModeButton = findViewById(R.id.pauseModeButton)
        roleSpinner = findViewById(R.id.roleSpinner)
        playerSlotSpinner = findViewById(R.id.playerSlotSpinner)
        playerPickSpinner = findViewById(R.id.playerPickSpinner)
        heroSearch = findViewById(R.id.heroSearch)
        sideSpinner = findViewById(R.id.sideSpinner)
        manualAddButton = findViewById(R.id.manualAddButton)
        manualCountersButton = findViewById(R.id.manualCountersButton)
        clearManualButton = findViewById(R.id.clearManualButton)
        resultsContainer = findViewById(R.id.resultsContainer)

        counterEngine = CounterEngine(this)
        configureRoleSelection()
        configurePlayerSlotSelection()
        configurePlayerPickSelection()
        configureManualFallback()
        startAssistantButton.setOnClickListener { beginAssistantFlow() }
        stopAssistantButton.setOnClickListener { stopAssistant() }
        draftModeButton.setOnClickListener { setAssistantStage(AssistantStage.DRAFT) }
        gameModeButton.setOnClickListener { setAssistantStage(AssistantStage.IN_GAME) }
        pauseModeButton.setOnClickListener { setAssistantStage(AssistantStage.PAUSED) }
    }

    override fun onStart() {
        super.onStart()
        unsubscribe = AssistantSessionBus.subscribe { state ->
            runOnUiThread {
                statusText.text = state.status
                startAssistantButton.isEnabled = !state.active && !pendingAssistantStart
                stopAssistantButton.isEnabled = state.active
                draftModeButton.isEnabled = state.active
                gameModeButton.isEnabled = state.active
                pauseModeButton.isEnabled = state.active
                updateStageButtonLabels(state.selectedStage)
                val desiredSlotPosition = state.manualPlayerSlotIndex ?: 0
                if (playerSlotSpinner.selectedItemPosition != desiredSlotPosition) {
                    playerSlotSpinner.setSelection(desiredSlotPosition, false)
                }
                val desiredPickPosition = state.playerPickOverride.ordinal
                if (playerPickSpinner.selectedItemPosition != desiredPickPosition) {
                    playerPickSpinner.setSelection(desiredPickPosition, false)
                }
            }
        }
    }

    override fun onStop() {
        unsubscribe?.invoke()
        unsubscribe = null
        super.onStop()
    }


    private fun configureRoleSelection() {
        val roles = listOf(getString(R.string.role_auto)) + counterEngine.roles()
        roleSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            roles
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val saved = AssistantPreferences.getRequestedRole(this)
        roleSpinner.setSelection(roles.indexOf(saved).takeIf { it >= 0 } ?: 0)
        roleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                AssistantPreferences.setRequestedRole(
                    this@MainActivity,
                    roles.getOrNull(position)?.takeUnless { it == getString(R.string.role_auto) }
                )
                if (AssistantSessionBus.state.active) {
                    startService(
                        Intent(this@MainActivity, ScreenCaptureService::class.java)
                            .setAction(ScreenCaptureService.ACTION_FORCE_SCAN)
                    )
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }


    private fun configurePlayerSlotSelection() {
        val options = listOf(
            getString(R.string.player_slot_auto),
            getString(R.string.player_slot_number, 1),
            getString(R.string.player_slot_number, 2),
            getString(R.string.player_slot_number, 3),
            getString(R.string.player_slot_number, 4),
            getString(R.string.player_slot_number, 5)
        )
        playerSlotSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        playerSlotSpinner.setSelection(AssistantPreferences.getManualPlayerSlot(this) ?: 0, false)
        playerSlotSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val manualSlot = position.takeIf { it in 1..5 }
                AssistantPreferences.setManualPlayerSlot(this@MainActivity, manualSlot)
                if (AssistantSessionBus.state.active) {
                    startService(
                        Intent(this@MainActivity, ScreenCaptureService::class.java)
                            .setAction(ScreenCaptureService.ACTION_SET_PLAYER_SLOT)
                            .putExtra(ScreenCaptureService.EXTRA_PLAYER_SLOT_INDEX, manualSlot ?: 0)
                    )
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun configurePlayerPickSelection() {
        val options = listOf(
            getString(R.string.player_pick_state_auto),
            getString(R.string.player_pick_state_pending),
            getString(R.string.player_pick_state_locked)
        )
        playerPickSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        playerPickSpinner.setSelection(AssistantPreferences.getPlayerPickOverride(this).ordinal, false)
        playerPickSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long
            ) {
                val override = PlayerPickOverride.values().getOrElse(position) { PlayerPickOverride.AUTO }
                AssistantPreferences.setPlayerPickOverride(this@MainActivity, override)
                if (AssistantSessionBus.state.active) {
                    startService(
                        Intent(this@MainActivity, ScreenCaptureService::class.java)
                            .setAction(ScreenCaptureService.ACTION_SET_PLAYER_PICK_OVERRIDE)
                            .putExtra(ScreenCaptureService.EXTRA_PLAYER_PICK_OVERRIDE, override.name)
                    )
                }
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
    }

    private fun configureManualFallback() {
        val heroNames = counterEngine.allHeroes().map { it.name }
        heroSearch.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, heroNames)
        )
        heroSearch.setOnClickListener { heroSearch.showDropDown() }
        sideSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf(getString(R.string.enemy_team), getString(R.string.ally_team))
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        manualAddButton.setOnClickListener {
            val hero = counterEngine.findHero(heroSearch.text.toString())
            if (hero == null) {
                Toast.makeText(this, R.string.hero_not_found, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!AssistantSessionBus.state.active) {
                Toast.makeText(this, R.string.start_assistant_first, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val side = if (sideSpinner.selectedItemPosition == 1) TeamSide.ALLY else TeamSide.ENEMY
            startService(
                Intent(this, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_MANUAL_ADD)
                    .putExtra(ScreenCaptureService.EXTRA_HERO_NAME, hero.name)
                    .putExtra(ScreenCaptureService.EXTRA_TEAM_SIDE, side.name)
            )
            Toast.makeText(this, getString(R.string.manual_added, hero.name), Toast.LENGTH_SHORT).show()
        }

        clearManualButton.setOnClickListener {
            if (AssistantSessionBus.state.active) {
                startService(
                    Intent(this, ScreenCaptureService::class.java)
                        .setAction(ScreenCaptureService.ACTION_MANUAL_CLEAR)
                )
            }
        }

        manualCountersButton.setOnClickListener {
            val hero = counterEngine.findHero(heroSearch.text.toString())
            resultsContainer.removeAllViews()
            if (hero == null) {
                addResultText(getString(R.string.hero_not_found))
                return@setOnClickListener
            }
            counterEngine.getCounters(hero.name).forEachIndexed { index, counter ->
                addResultText(getString(R.string.counter_result_format, index + 1, counter.heroName, counter.reason))
            }
        }
    }

    private fun beginAssistantFlow() {
        pendingAssistantStart = true
        startAssistantButton.isEnabled = false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueOverlayPermissionFlow()
        }
    }

    private fun continueOverlayPermissionFlow() {
        if (!Settings.canDrawOverlays(this)) {
            overlayPermissionLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            requestCaptureConsent()
        }
    }

    private fun requestCaptureConsent() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermissionLauncher.launch(manager.createScreenCaptureIntent())
    }


    private fun setAssistantStage(stage: AssistantStage) {
        if (!AssistantSessionBus.state.active) {
            Toast.makeText(this, R.string.start_assistant_first, Toast.LENGTH_SHORT).show()
            return
        }
        startService(
            Intent(this, ScreenCaptureService::class.java)
                .setAction(ScreenCaptureService.ACTION_SET_STAGE)
                .putExtra(ScreenCaptureService.EXTRA_ASSISTANT_STAGE, stage.name)
        )
    }

    private fun updateStageButtonLabels(stage: AssistantStage) {
        draftModeButton.text = if (stage == AssistantStage.DRAFT) {
            getString(R.string.mode_draft_active)
        } else getString(R.string.mode_draft_short)
        gameModeButton.text = if (stage == AssistantStage.IN_GAME) {
            getString(R.string.mode_game_active)
        } else getString(R.string.mode_game_short)
        pauseModeButton.text = if (stage == AssistantStage.PAUSED) {
            getString(R.string.mode_pause_active)
        } else getString(R.string.mode_pause_short)
    }

    private fun stopAssistant() {
        startService(
            Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP)
        )
        startService(Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_STOP))
        statusText.text = getString(R.string.assistant_stopped)
    }

    private fun addResultText(text: String) {
        val padding = (12 * resources.displayMetrics.density).toInt()
        val view = TextView(this).apply {
            this.text = text
            textSize = 15f
            setPadding(padding, padding, padding, padding)
            setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
        }
        resultsContainer.addView(view)
    }
}

package com.example.honorofkingsassistant

import android.content.Context

object AssistantPreferences {
    private const val FILE_NAME = "draft_assistant_preferences"
    private const val KEY_REQUESTED_ROLE = "requested_role"
    private const val KEY_PLAYER_NAME = "player_name"
    private const val KEY_ASSISTANT_STAGE = "assistant_stage"
    private const val KEY_MANUAL_PLAYER_SLOT = "manual_player_slot"
    private const val KEY_PLAYER_PICK_OVERRIDE = "player_pick_override"
    private const val DEFAULT_PLAYER_NAME = "R-95"

    fun getRequestedRole(context: Context): String? = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getString(KEY_REQUESTED_ROLE, null)
        ?.takeIf { it.isNotBlank() }

    fun setRequestedRole(context: Context, role: String?) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_REQUESTED_ROLE, role.orEmpty())
            .apply()
    }

    fun getPlayerName(context: Context): String = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getString(KEY_PLAYER_NAME, DEFAULT_PLAYER_NAME)
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_PLAYER_NAME

    fun setPlayerName(context: Context, playerName: String) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYER_NAME, playerName.trim().ifBlank { DEFAULT_PLAYER_NAME })
            .apply()
    }

    fun getManualPlayerSlot(context: Context): Int? = context
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        .getInt(KEY_MANUAL_PLAYER_SLOT, 0)
        .takeIf { it in 1..5 }

    fun setManualPlayerSlot(context: Context, slotIndex: Int?) {
        require(slotIndex == null || slotIndex in 1..5)
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_MANUAL_PLAYER_SLOT, slotIndex ?: 0)
            .apply()
    }

    fun getPlayerPickOverride(context: Context): PlayerPickOverride {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_PLAYER_PICK_OVERRIDE, PlayerPickOverride.AUTO.name)
        return runCatching { PlayerPickOverride.valueOf(stored.orEmpty()) }
            .getOrDefault(PlayerPickOverride.AUTO)
    }

    fun setPlayerPickOverride(context: Context, override: PlayerPickOverride) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PLAYER_PICK_OVERRIDE, override.name)
            .apply()
    }

    fun getAssistantStage(context: Context): AssistantStage {
        val stored = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ASSISTANT_STAGE, AssistantStage.PAUSED.name)
        return runCatching { AssistantStage.valueOf(stored.orEmpty()) }
            .getOrDefault(AssistantStage.PAUSED)
    }

    fun setAssistantStage(context: Context, stage: AssistantStage) {
        context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ASSISTANT_STAGE, stage.name)
            .apply()
    }
}

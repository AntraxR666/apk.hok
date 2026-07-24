package com.example.honorofkingsassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores only compact visual fingerprints in app-private SharedPreferences. No screenshot or
 * portrait image is persisted. Multiple templates per hero allow different skins/art variants.
 */
class PortraitTemplateStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun learn(heroName: String, fingerprint: PortraitFingerprint): Boolean {
        val normalized = CounterCatalog.normalize(heroName)
        if (normalized.isBlank()) return false
        val current = loadAll().toMutableMap()
        val templates = current.getOrPut(normalized) { mutableListOf() }.toMutableList()
        if (templates.any { it.distance(fingerprint) <= DUPLICATE_DISTANCE }) return false
        templates += fingerprint
        current[normalized] = templates.takeLast(MAX_TEMPLATES_PER_HERO).toMutableList()
        saveAll(current)
        return true
    }

    @Synchronized
    fun templates(): Map<String, List<PortraitFingerprint>> = loadAll()

    @Synchronized
    fun clear() {
        preferences.edit().remove(KEY_TEMPLATES).apply()
    }

    private fun loadAll(): Map<String, MutableList<PortraitFingerprint>> {
        val raw = preferences.getString(KEY_TEMPLATES, null) ?: return linkedMapOf()
        return runCatching {
            val root = JSONObject(raw)
            linkedMapOf<String, MutableList<PortraitFingerprint>>().apply {
                root.keys().forEach { heroKey ->
                    val array = root.optJSONArray(heroKey) ?: JSONArray()
                    val values = mutableListOf<PortraitFingerprint>()
                    for (index in 0 until array.length()) {
                        PortraitFingerprint.decode(array.optString(index))?.let(values::add)
                    }
                    if (values.isNotEmpty()) put(heroKey, values)
                }
            }
        }.getOrElse { linkedMapOf() }
    }

    private fun saveAll(values: Map<String, List<PortraitFingerprint>>) {
        val root = JSONObject()
        values.forEach { (heroKey, fingerprints) ->
            root.put(heroKey, JSONArray().apply { fingerprints.forEach { put(it.encode()) } })
        }
        preferences.edit().putString(KEY_TEMPLATES, root.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "portrait_templates"
        private const val KEY_TEMPLATES = "templates_v1"
        private const val MAX_TEMPLATES_PER_HERO = 6
        private const val DUPLICATE_DISTANCE = 0.055
    }
}

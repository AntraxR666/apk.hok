package com.example.honorofkingsassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class RecognitionReadiness {
    UNCALIBRATED,
    READY
}

enum class PortraitTemplateDomain {
    DRAFT_PORTRAIT,
    LOADING_CARD_PORTRAIT
}

object PortraitTemplateDomainPolicy {
    fun forFrame(
        matchMode: MatchMode,
        subphase: DraftSubphase
    ): PortraitTemplateDomain =
        if (matchMode == MatchMode.RANKED_DRAFT && subphase == DraftSubphase.LOADING) {
            PortraitTemplateDomain.LOADING_CARD_PORTRAIT
        } else {
            PortraitTemplateDomain.DRAFT_PORTRAIT
        }
}

data class RecognitionCalibrationState(
    val readiness: RecognitionReadiness,
    val storedTemplateCount: Int,
    val coveredHeroCount: Int
) {
    init {
        require(storedTemplateCount >= 0)
        require(coveredHeroCount >= 0)
        require(coveredHeroCount <= storedTemplateCount)
    }

    companion object {
        val UNCALIBRATED = RecognitionCalibrationState(
            readiness = RecognitionReadiness.UNCALIBRATED,
            storedTemplateCount = 0,
            coveredHeroCount = 0
        )
    }
}

interface PortraitTemplatePersistence {
    fun read(): String?

    fun write(value: String?)
}

private class SharedPreferencesPortraitTemplatePersistence(
    context: Context
) : PortraitTemplatePersistence {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(KEY_TEMPLATES, null)

    override fun write(value: String?) {
        val editor = preferences.edit()
        if (value == null) {
            editor.remove(KEY_TEMPLATES)
        } else {
            editor.putString(KEY_TEMPLATES, value)
        }
        editor.apply()
    }

    private companion object {
        const val PREFS_NAME = "portrait_templates"
        const val KEY_TEMPLATES = "templates_v1"
    }
}

/**
 * Stores only compact visual fingerprints in app-private SharedPreferences. No screenshot or
 * portrait image is persisted. Multiple templates per hero allow different skins/art variants.
 */
class PortraitTemplateStore(
    private val persistence: PortraitTemplatePersistence
) {
    constructor(context: Context) : this(SharedPreferencesPortraitTemplatePersistence(context))

    @Synchronized
    fun learn(
        heroName: String,
        fingerprint: PortraitFingerprint,
        domain: PortraitTemplateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
    ): Boolean {
        val normalized = CounterCatalog.normalize(heroName)
        if (normalized.isBlank()) return false
        val current = loadAll().toMutableMap()
        val storageKey = storageKey(domain, normalized)
        val templates = current.getOrPut(storageKey) { mutableListOf() }.toMutableList()
        if (templates.any { it.distance(fingerprint) <= DUPLICATE_DISTANCE }) return false
        templates += fingerprint
        current[storageKey] = templates.takeLast(MAX_TEMPLATES_PER_HERO).toMutableList()
        saveAll(current)
        return true
    }

    @Synchronized
    fun templates(
        domain: PortraitTemplateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
    ): Map<String, List<PortraitFingerprint>> = buildMap {
        loadAll().forEach { (storageKey, fingerprints) ->
            heroKey(storageKey, domain)?.let { normalizedHero ->
                put(normalizedHero, fingerprints)
            }
        }
    }

    @Synchronized
    fun readiness(): RecognitionReadiness = calibrationState().readiness

    @Synchronized
    fun calibrationState(): RecognitionCalibrationState {
        val storedTemplates = loadAll()
        if (storedTemplates.isEmpty()) return RecognitionCalibrationState.UNCALIBRATED
        val coveredHeroes = storedTemplates.keys.mapNotNull { storageKey ->
            PortraitTemplateDomain.entries.firstNotNullOfOrNull { domain ->
                heroKey(storageKey, domain)
            }
        }.toSet()
        return RecognitionCalibrationState(
            readiness = RecognitionReadiness.READY,
            storedTemplateCount =
                storedTemplates.values.sumOf(List<PortraitFingerprint>::size),
            coveredHeroCount = coveredHeroes.size
        )
    }

    @Synchronized
    fun clear() {
        persistence.write(null)
    }

    private fun loadAll(): Map<String, MutableList<PortraitFingerprint>> {
        val raw = persistence.read() ?: return linkedMapOf()
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
        persistence.write(root.toString())
    }

    companion object {
        private const val MAX_TEMPLATES_PER_HERO = 6
        private const val DUPLICATE_DISTANCE = 0.055
        private const val DOMAIN_PREFIX = "__portrait_domain__:"

        private fun storageKey(
            domain: PortraitTemplateDomain,
            normalizedHero: String
        ): String = when (domain) {
            PortraitTemplateDomain.DRAFT_PORTRAIT -> normalizedHero
            PortraitTemplateDomain.LOADING_CARD_PORTRAIT ->
                "$DOMAIN_PREFIX${domain.name}:$normalizedHero"
        }

        private fun heroKey(
            storageKey: String,
            domain: PortraitTemplateDomain
        ): String? = when (domain) {
            PortraitTemplateDomain.DRAFT_PORTRAIT ->
                storageKey.takeUnless { it.startsWith(DOMAIN_PREFIX) }
            PortraitTemplateDomain.LOADING_CARD_PORTRAIT -> {
                val prefix = "$DOMAIN_PREFIX${domain.name}:"
                storageKey.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
            }
        }
    }
}

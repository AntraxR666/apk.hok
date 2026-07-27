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

enum class PortraitFingerprintLayout {
    NONE,
    RANKED_SIDE_PORTRAITS,
    NORMAL_ALLY_PORTRAITS,
    LOADING_CARDS
}

data class PortraitRecognitionFramePlan(
    val fingerprintLayout: PortraitFingerprintLayout,
    val templateDomain: PortraitTemplateDomain
)

object PortraitRecognitionFramePolicy {
    fun forFrame(
        matchMode: MatchMode,
        subphase: DraftSubphase
    ): PortraitRecognitionFramePlan = when {
        matchMode == MatchMode.AUTO -> PortraitRecognitionFramePlan(
            fingerprintLayout = PortraitFingerprintLayout.NONE,
            templateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
        )
        subphase == DraftSubphase.LOADING -> PortraitRecognitionFramePlan(
            fingerprintLayout = PortraitFingerprintLayout.LOADING_CARDS,
            templateDomain = PortraitTemplateDomain.LOADING_CARD_PORTRAIT
        )
        matchMode == MatchMode.RANKED_DRAFT -> PortraitRecognitionFramePlan(
            fingerprintLayout = PortraitFingerprintLayout.RANKED_SIDE_PORTRAITS,
            templateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
        )
        else -> PortraitRecognitionFramePlan(
            fingerprintLayout = PortraitFingerprintLayout.NORMAL_ALLY_PORTRAITS,
            templateDomain = PortraitTemplateDomain.DRAFT_PORTRAIT
        )
    }
}

object PortraitTemplateDomainPolicy {
    fun forFrame(
        matchMode: MatchMode,
        subphase: DraftSubphase
    ): PortraitTemplateDomain =
        PortraitRecognitionFramePolicy.forFrame(matchMode, subphase).templateDomain
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
 * Read-only portraits bundled with the app. They are a starting point for the base icons used
 * during hero selection; user-confirmed variants remain separate and are never overwritten.
 */
object PortraitTemplateSeedCatalog {
    private const val ASSET_NAME = "draft_portrait_seed_v1.json"

    fun load(context: Context): Map<PortraitTemplateDomain, Map<String, List<PortraitFingerprint>>> =
        runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { reader -> parse(reader.readText()) }
        }.getOrElse { emptyMap() }

    internal fun parse(raw: String): Map<PortraitTemplateDomain, Map<String, List<PortraitFingerprint>>> {
        val root = JSONObject(raw)
        val domain = PortraitTemplateDomain.valueOf(root.getString("domain"))
        val templates = root.getJSONObject("templates")
        val byHero = linkedMapOf<String, List<PortraitFingerprint>>()
        templates.keys().forEach { normalizedHero ->
            val values = templates.optJSONArray(normalizedHero) ?: JSONArray()
            val fingerprints = buildList {
                for (index in 0 until values.length()) {
                    PortraitFingerprint.decode(values.optString(index))?.let(::add)
                }
            }
            if (fingerprints.isNotEmpty()) byHero[normalizedHero] = fingerprints
        }
        return mapOf(domain to byHero)
    }
}

/**
 * Stores only compact visual fingerprints in app-private SharedPreferences. No screenshot or
 * portrait image is persisted. Multiple templates per hero allow different skins/art variants.
 */
class PortraitTemplateStore(
    private val persistence: PortraitTemplatePersistence,
    private val seededTemplates: Map<PortraitTemplateDomain, Map<String, List<PortraitFingerprint>>> = emptyMap()
) {
    constructor(context: Context) : this(
        persistence = SharedPreferencesPortraitTemplatePersistence(context),
        seededTemplates = PortraitTemplateSeedCatalog.load(context)
    )

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
    ): Map<String, List<PortraitFingerprint>> = linkedMapOf<String, MutableList<PortraitFingerprint>>().apply {
        seededTemplates[domain].orEmpty().forEach { (hero, fingerprints) ->
            put(hero, fingerprints.toMutableList())
        }
        loadAll().forEach { (storageKey, fingerprints) ->
            heroKey(storageKey, domain)?.let { normalizedHero ->
                val merged = getOrPut(normalizedHero) { mutableListOf() }
                fingerprints.forEach { fingerprint ->
                    if (merged.none { it.encode() == fingerprint.encode() }) merged += fingerprint
                }
            }
        }
    }.mapValues { (_, fingerprints) -> fingerprints.toList() }

    @Synchronized
    fun readiness(): RecognitionReadiness = calibrationState().readiness

    @Synchronized
    fun calibrationState(): RecognitionCalibrationState {
        val byDomain = PortraitTemplateDomain.entries.associateWith(::templates)
        val templateCount = byDomain.values.sumOf { templates ->
            templates.values.sumOf(List<PortraitFingerprint>::size)
        }
        if (templateCount == 0) return RecognitionCalibrationState.UNCALIBRATED
        val coveredHeroes = byDomain.values.flatMap { it.keys }.toSet()
        return RecognitionCalibrationState(
            readiness = RecognitionReadiness.READY,
            storedTemplateCount = templateCount,
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

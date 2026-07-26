package com.example.honorofkingsassistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class UnknownHeroTextEntry(
    val text: String,
    val normalizedText: String,
    val region: String,
    val evidenceSource: String,
    val firstSeenAtMs: Long,
    val lastSeenAtMs: Long,
    val occurrences: Int
)

class UnknownHeroTextStore(
    private val logFile: File,
    private val maxEntries: Int = MAX_ENTRIES,
    private val clock: () -> Long = System::currentTimeMillis
) {
    init {
        require(maxEntries in 1..MAX_ENTRIES)
    }

    constructor(context: Context) : this(File(context.filesDir, FILE_NAME))

    @Synchronized
    fun record(text: String, region: String, evidenceSource: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length !in MIN_TEXT_LENGTH..MAX_TEXT_LENGTH) return false
        if (region.isBlank() || evidenceSource != ML_KIT_HERO_ROI_EVIDENCE) return false
        val normalized = normalizeHeroRecognitionText(trimmed)
        if (normalized.isBlank()) return false

        val entries = loadEntries().associateByTo(linkedMapOf()) { it.normalizedText }
        val now = clock()
        val existing = entries[normalized]
        entries[normalized] = if (existing == null) {
            UnknownHeroTextEntry(
                text = trimmed,
                normalizedText = normalized,
                region = region,
                evidenceSource = evidenceSource,
                firstSeenAtMs = now,
                lastSeenAtMs = now,
                occurrences = 1
            )
        } else {
            existing.copy(
                text = trimmed,
                region = region,
                lastSeenAtMs = now,
                occurrences = existing.occurrences + 1
            )
        }
        saveEntries(entries.values.toList().takeLast(maxEntries))
        return existing == null
    }

    @Synchronized
    fun entries(): List<UnknownHeroTextEntry> = loadEntries()

    private fun loadEntries(): List<UnknownHeroTextEntry> {
        if (!logFile.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(logFile.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        UnknownHeroTextEntry(
                            text = item.getString("text"),
                            normalizedText = item.getString("normalized_text"),
                            region = item.getString("region"),
                            evidenceSource = item.getString("evidence_source"),
                            firstSeenAtMs = item.getLong("first_seen_at_ms"),
                            lastSeenAtMs = item.getLong("last_seen_at_ms"),
                            occurrences = item.getInt("occurrences")
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    private fun saveEntries(entries: Collection<UnknownHeroTextEntry>) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("text", entry.text)
                    .put("normalized_text", entry.normalizedText)
                    .put("region", entry.region)
                    .put("evidence_source", entry.evidenceSource)
                    .put("first_seen_at_ms", entry.firstSeenAtMs)
                    .put("last_seen_at_ms", entry.lastSeenAtMs)
                    .put("occurrences", entry.occurrences)
            )
        }
        logFile.parentFile?.mkdirs()
        logFile.writeText(array.toString(), Charsets.UTF_8)
    }

    companion object {
        const val ML_KIT_HERO_ROI_EVIDENCE = "mlkit_line_in_hero_roi"
        private const val FILE_NAME = "unknown_hero_text.json"
        private const val MIN_TEXT_LENGTH = 4
        private const val MAX_TEXT_LENGTH = 48
        private const val MAX_ENTRIES = 200
    }
}

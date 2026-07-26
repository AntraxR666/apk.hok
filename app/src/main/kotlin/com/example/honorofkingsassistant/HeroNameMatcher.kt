package com.example.honorofkingsassistant

import kotlin.math.max

class HeroNameMatcher(
    heroes: List<Hero>,
    private val minimumScore: Double = 0.70
) {
    private data class Candidate(val hero: Hero, val label: String, val normalized: String)

    private val canonicalCandidates = heroes.flatMap { hero ->
        listOf(hero.name, hero.id).toCandidates(hero)
    }
    private val aliasCandidates = heroes.flatMap { hero ->
        hero.allRecognitionAliases().toCandidates(hero)
    }
    private val candidates = canonicalCandidates + aliasCandidates
    private val exactCanonical = canonicalCandidates.toExactMap()
    private val exactAliases = aliasCandidates.toExactMap()

    fun match(rawText: String): HeroNameMatch? {
        val normalizedInput = normalizeHeroRecognitionText(rawText)
        if (normalizedInput.isBlank()) return null

        exactCanonical[normalizedInput]?.let { candidate ->
            return candidate.toMatch(MatchKind.EXACT_CANONICAL, 1.0)
        }
        exactAliases[normalizedInput]?.let { candidate ->
            return candidate.toMatch(MatchKind.EXACT_ALIAS, 1.0)
        }
        if (' ' !in normalizedInput && normalizedInput.length < MIN_FUZZY_FRAGMENT_LENGTH) {
            return null
        }

        val fragments = buildFragments(normalizedInput)
            .filter { ' ' in it || it.length >= MIN_FUZZY_FRAGMENT_LENGTH }
        if (fragments.isEmpty()) return null

        return candidates.asSequence()
            .map { candidate ->
                val score = fragments.maxOf { fragment -> similarity(fragment, candidate.normalized) }
                candidate.toMatch(MatchKind.FUZZY, score)
            }
            .filter { it.score >= minimumScore }
            .sortedWith(
                compareByDescending<HeroNameMatch> { it.score }
                    .thenByDescending { it.matchedText.length }
                    .thenBy { it.hero.name }
            )
            .firstOrNull()
    }

    fun bestMatch(rawText: String): HeroNameMatch? = match(rawText)

    fun findMatches(rawText: String): List<HeroNameMatch> {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
        return lines.mapNotNull(::bestMatch)
            .groupBy { it.hero.name }
            .map { (_, matches) -> matches.maxBy { it.score } }
            .sortedByDescending { it.score }
    }

    private fun buildFragments(input: String): List<String> {
        val words = input.split(' ').filter { it.isNotBlank() }
        val fragments = linkedSetOf(input)
        for (start in words.indices) {
            for (endExclusive in (start + 1)..minOf(words.size, start + 4)) {
                fragments += words.subList(start, endExclusive).joinToString(" ")
            }
        }
        return fragments.toList()
    }

    private fun List<String>.toCandidates(hero: Hero): List<Candidate> = asSequence()
        .filter { it.isNotBlank() }
        .distinct()
        .map { Candidate(hero, it, normalizeHeroRecognitionText(it)) }
        .filter { it.normalized.isNotBlank() }
        .toList()

    private fun List<Candidate>.toExactMap(): Map<String, Candidate> = buildMap {
        this@toExactMap.forEach { candidate -> putIfAbsent(candidate.normalized, candidate) }
    }

    private fun Candidate.toMatch(kind: MatchKind, score: Double) =
        HeroNameMatch(hero, score, label, kind)

    private fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.contains(right) || right.contains(left)) {
            val shorter = minOf(left.length, right.length).toDouble()
            val longer = max(left.length, right.length).toDouble()
            return 0.92 + (shorter / longer) * 0.08
        }
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / max(left.length, right.length).coerceAtLeast(1)
    }

    private fun levenshtein(left: String, right: String): Int {
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length
        var previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            val current = IntArray(right.length + 1)
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, substitution)
            }
            previous = current
        }
        return previous[right.length]
    }

    private companion object {
        const val MIN_FUZZY_FRAGMENT_LENGTH = 5
    }
}

internal fun normalizeHeroRecognitionText(value: String): String =
    CounterCatalog.normalize(value)
        .replace("[^a-z0-9]+".toRegex(), " ")
        .trim()
        .replace("\\s+".toRegex(), " ")

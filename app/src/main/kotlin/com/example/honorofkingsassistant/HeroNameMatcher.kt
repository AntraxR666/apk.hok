package com.example.honorofkingsassistant

import kotlin.math.max

class HeroNameMatcher(
    heroes: List<Hero>,
    private val minimumScore: Double = 0.70
) {
    private data class Candidate(val hero: Hero, val label: String, val normalized: String)

    private val candidates: List<Candidate> = heroes.flatMap { hero ->
        (listOf(hero.name, hero.id) + hero.aliases)
            .filter { it.isNotBlank() }
            .distinct()
            .map { Candidate(hero, it, CounterCatalog.normalize(it)) }
    }

    fun bestMatch(rawText: String): HeroNameMatch? {
        val normalizedInput = CounterCatalog.normalize(rawText)
        if (normalizedInput.isBlank()) return null

        val fragments = buildFragments(normalizedInput)
        return candidates.asSequence()
            .map { candidate ->
                val score = fragments.maxOf { fragment -> similarity(fragment, candidate.normalized) }
                HeroNameMatch(candidate.hero, score, candidate.label)
            }
            .filter { it.score >= minimumScore }
            .sortedWith(
                compareByDescending<HeroNameMatch> { it.score }
                    .thenByDescending { it.matchedText.length }
                    .thenBy { it.hero.name }
            )
            .firstOrNull()
    }

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
}

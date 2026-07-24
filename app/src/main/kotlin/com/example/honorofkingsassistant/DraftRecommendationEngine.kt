package com.example.honorofkingsassistant

import kotlin.math.round

class DraftRecommendationEngine(
    private val catalog: CounterCatalog
) {
    fun recommend(
        snapshot: DraftSnapshot,
        requestedRole: String? = null,
        bannedHeroNames: Set<String> = emptySet(),
        limit: Int = 3
    ): List<DraftPickRecommendation> {
        if (limit <= 0) return emptyList()
        val excluded = (snapshot.allConfirmedNames + bannedHeroNames)
            .map(CounterCatalog::normalize)
            .toSet()
        val normalizedRole = CounterCatalog.normalize(requestedRole.orEmpty())
        val allies = snapshot.allies.mapNotNull { catalog.findHero(it.heroName) }
        val enemies = snapshot.enemies.mapNotNull { catalog.findHero(it.heroName) }

        return catalog.heroes.asSequence()
            .filter { CounterCatalog.normalize(it.name) !in excluded }
            .filter { normalizedRole.isBlank() || CounterCatalog.normalize(it.role) == normalizedRole }
            .map { candidate -> scoreCandidate(candidate, enemies, allies) }
            .sortedWith(
                compareByDescending<DraftPickRecommendation> { it.score }
                    .thenByDescending { it.confidence }
                    .thenBy { it.hero.name }
            )
            .take(limit)
            .toList()
    }

    private fun scoreCandidate(
        candidate: Hero,
        enemies: List<Hero>,
        allies: List<Hero>
    ): DraftPickRecommendation {
        val evidence = mutableListOf<String>()
        val covered = mutableListOf<String>()
        var score = 0.0
        var positiveEvidenceWeight = 0.0
        var riskWeight = 0.0

        enemies.forEach { enemy ->
            // enemy.counters contains heroes that are advantageous into that enemy.
            val advantage = enemy.counters.firstOrNull {
                CounterCatalog.normalize(it.heroName) == CounterCatalog.normalize(candidate.name)
            }
            if (advantage != null) {
                val weight = advantage.confidence.coerceIn(0.35, 1.0)
                score += 28.0 * weight
                positiveEvidenceWeight += weight
                covered += enemy.name
                evidence += "Ventaja contra ${enemy.name}: ${advantage.reason}"
            }

            // candidate.counters contains enemies that are dangerous for the candidate.
            val danger = candidate.counters.firstOrNull {
                CounterCatalog.normalize(it.heroName) == CounterCatalog.normalize(enemy.name)
            }
            if (danger != null) {
                val weight = danger.confidence.coerceIn(0.35, 1.0)
                score -= 20.0 * weight
                riskWeight += weight
                evidence += "Riesgo frente a ${enemy.name}: ${danger.reason}"
            }
        }

        if (covered.size >= 2) {
            score += 8.0 + (covered.size - 2) * 3.0
            evidence += "Cubre ${covered.size} amenazas enemigas ya confirmadas"
        }

        val allyRoles = allies.map { it.role }
        val duplicateRoleCount = allyRoles.count { it == candidate.role }
        if (duplicateRoleCount == 0) {
            score += 11.0
            evidence += "Completa un rol aún no confirmado: ${candidate.role}"
        } else {
            score -= duplicateRoleCount * 5.0
            evidence += "El equipo ya tiene $duplicateRoleCount selección(es) de ${candidate.role}"
        }

        val frontlineRoles = setOf("Clash Lane", "Roamer/Support")
        val hasFrontline = allies.any { it.role in frontlineRoles }
        if (!hasFrontline && candidate.role in frontlineRoles) {
            score += 8.0
            evidence += "Añade primera línea para proteger y habilitar al equipo"
        }

        val meta = candidate.metaScore
        if (meta != null) {
            score += meta.coerceIn(0.0, 100.0) * 0.16
            evidence += "Fuerza de meta registrada: ${round(meta).toInt()}/100"
        }

        if (enemies.isEmpty()) {
            score += 4.0
            evidence += "Recomendación provisional: todavía no hay enemigos identificados"
        }

        val enemyCount = enemies.size.coerceAtLeast(1)
        val coverageRatio = covered.size.toDouble() / enemyCount
        val evidenceRatio = (positiveEvidenceWeight / enemyCount).coerceIn(0.0, 1.0)
        val riskPenalty = (riskWeight / enemyCount).coerceIn(0.0, 1.0)
        val confidence = (
            0.30 + coverageRatio * 0.30 + evidenceRatio * 0.25 +
                if (meta != null) 0.10 else 0.0 - riskPenalty * 0.20
        ).coerceIn(0.20, 0.96)

        return DraftPickRecommendation(
            hero = candidate,
            score = round(score * 10.0) / 10.0,
            confidence = confidence,
            evidence = evidence.ifEmpty { listOf("Sin evidencia directa suficiente") },
            coveredEnemies = covered.distinct()
        )
    }
}

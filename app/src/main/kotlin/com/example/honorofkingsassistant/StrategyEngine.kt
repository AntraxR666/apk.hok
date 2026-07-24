package com.example.honorofkingsassistant

class StrategyEngine {
    fun build(
        recommendation: DraftPickRecommendation,
        snapshot: DraftSnapshot
    ): StrategyPlan {
        val target = recommendation.coveredEnemies.firstOrNull()
            ?: snapshot.enemies.maxByOrNull { it.confidence }?.heroName
            ?: "el carry enemigo cuando sea identificado"
        val hasRoamer = recommendation.hero.role == "Roamer/Support"
        val opening = if (hasRoamer) {
            "protege la entrada de tu carry y conserva el control principal para responder al engage"
        } else {
            "juega el primer intercambio con visión y no fuerces hasta confirmar la habilidad clave enemiga"
        }
        val teamFight = recommendation.evidence.firstOrNull { it.startsWith("Contra ") }
            ?.substringAfter(": ")
            ?.replaceFirstChar { it.lowercase() }
            ?: "entra después del primer control enemigo y mantén una ruta de salida"
        val objectivePlan = if (snapshot.enemies.size >= 3) {
            "fuerza objetivos solo con visión lateral y cuando la amenaza principal esté lejos o sin definitiva"
        } else {
            "usa cada nuevo pick confirmado para decidir si asegurar visión, torre o neutral"
        }
        val winCondition = if (recommendation.coveredEnemies.size >= 2) {
            "agrupar en peleas donde ${recommendation.hero.name} pueda cubrir a varios enemigos detectados"
        } else {
            "aislar a $target y evitar peleas frontales sin información completa"
        }
        return StrategyPlan(
            opening = opening,
            priorityTarget = "prioriza a $target; no gastes el recurso principal sobre el tanque si el carry está accesible",
            teamFight = teamFight,
            objectivePlan = objectivePlan,
            winCondition = winCondition
        )
    }
}

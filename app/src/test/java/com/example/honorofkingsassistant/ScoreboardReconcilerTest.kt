package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScoreboardReconcilerTest {
    private val catalog = catalog()
    private val reconciler = ScoreboardReconciler(catalog)

    @Test fun exactTitleAndPortraitCorrectWeakDraftIdentity() {
        val result = reconciler.reconcile(
            draft = DraftSnapshot(listOf(ConfirmedHero("Desconocido", TeamSide.ALLY, 0.1)), emptyList(), emptyList()),
            scoreboard = board(ScoreboardRow(TeamSide.ALLY, 1, "La Maga de Fuego", "Angela"))
        )
        assertEquals("Angela", result.snapshot.allies.first().heroName)
        assertTrue(result.appliedCorrections.single().reason.contains("retrato + título"))
    }

    @Test fun manualIdentityIsNeverOverwritten() {
        val result = reconciler.reconcile(
            DraftSnapshot(listOf(ConfirmedHero("Angela", TeamSide.ALLY, 0.4)), emptyList(), emptyList()),
            board(ScoreboardRow(TeamSide.ALLY, 1, "El Arma Suprema", "Bai Qi")),
            ManualTeamAssignments().assign(TeamSide.ALLY, 1, "Angela")
        )
        assertEquals("Angela", result.snapshot.allies.first().heroName)
        assertTrue(result.appliedCorrections.isEmpty())
    }

    @Test fun strongPortraitTitleConflictRequiresConfirmation() {
        val result = reconciler.reconcile(
            DraftSnapshot(emptyList(), emptyList(), emptyList()),
            board(ScoreboardRow(TeamSide.ALLY, 1, "Angela", "Bai Qi"))
        )
        assertTrue(result.pendingConflicts.isNotEmpty())
        assertTrue(result.appliedCorrections.isEmpty())
    }

    private fun board(first: ScoreboardRow): ScoreboardSnapshot {
        fun empty(side: TeamSide) = (1..5).map { slot ->
            if (side == first.side && slot == first.slotIndex) first else ScoreboardRow(side, slot)
        }
        return ScoreboardSnapshot(empty(TeamSide.ALLY), empty(TeamSide.ENEMY))
    }

    private fun catalog(): CounterCatalog {
        val assetFile = listOf(
            File("src/main/assets/hok_counters.json"),
            File("app/src/main/assets/hok_counters.json")
        ).first { it.isFile }
        return CounterCatalogJson.parse(assetFile.readText(Charsets.UTF_8))
    }
}

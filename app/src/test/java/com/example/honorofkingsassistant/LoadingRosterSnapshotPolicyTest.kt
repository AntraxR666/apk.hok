package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Test

class LoadingRosterSnapshotPolicyTest {
    @Test
    fun `manual correction replaces the automatic hero at the exact loading slot`() {
        val loading = loadingRoster(
            LoadingRosterAssignment(TeamSide.ALLY, 1, "Lam", 0.82, false),
            LoadingRosterAssignment(TeamSide.ALLY, 2, "Yao", 0.78, false),
            LoadingRosterAssignment(TeamSide.ENEMY, 1, "Arthur", 0.91, false)
        )
        val manual = ManualTeamAssignments()
            .assign(TeamSide.ALLY, 1, "Angela")

        val snapshot = LoadingRosterSnapshotPolicy.from(loading, manual)

        assertEquals(listOf("Angela", "Yao"), snapshot.allies.map { it.heroName })
        assertEquals(listOf("Arthur"), snapshot.enemies.map { it.heroName })
        assertEquals(1.0, snapshot.allies.first().confidence, 0.0)
    }

    @Test
    fun `manual claim suppresses an automatic duplicate in another loading slot`() {
        val loading = loadingRoster(
            LoadingRosterAssignment(TeamSide.ALLY, 1, "Angela", 0.90, false),
            LoadingRosterAssignment(TeamSide.ALLY, 2, "Yao", 0.86, false)
        )
        val manual = ManualTeamAssignments()
            .assign(TeamSide.ALLY, 2, "Angela")

        val snapshot = LoadingRosterSnapshotPolicy.from(loading, manual)

        assertEquals(listOf("Angela"), snapshot.allies.map { it.heroName })
    }

    @Test
    fun `removing a manual correction reveals the original loading identities again`() {
        val loading = loadingRoster(
            LoadingRosterAssignment(TeamSide.ALLY, 1, "Lam", 0.82, false),
            LoadingRosterAssignment(TeamSide.ALLY, 2, "Yao", 0.78, false)
        )

        val snapshot = LoadingRosterSnapshotPolicy.from(
            loading,
            ManualTeamAssignments()
        )

        assertEquals(listOf("Lam", "Yao"), snapshot.allies.map { it.heroName })
    }

    private fun loadingRoster(
        vararg assignments: LoadingRosterAssignment
    ): LoadingRosterReconciliationResult = LoadingRosterReconciliationResult(
        assignments = assignments.toList(),
        conflicts = emptyList(),
        playerSlotIndex = null
    )
}

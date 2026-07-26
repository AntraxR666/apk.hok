package com.example.honorofkingsassistant

data class ManualTeamSlot(
    val side: TeamSide,
    val slotIndex: Int
) {
    init {
        require(side != TeamSide.UNKNOWN)
        require(slotIndex in 1..5)
    }
}

object ManualTeamEditorPolicy {
    fun slots(matchMode: MatchMode): List<ManualTeamSlot> {
        val allies = (1..5).map { ManualTeamSlot(TeamSide.ALLY, it) }
        return if (matchMode == MatchMode.RANKED_DRAFT) {
            allies + (1..5).map { ManualTeamSlot(TeamSide.ENEMY, it) }
        } else {
            allies
        }
    }
}

data class ManualTeamAssignments(
    private val values: Map<ManualTeamSlot, String> = emptyMap()
) {
    fun assign(side: TeamSide, slotIndex: Int, heroName: String): ManualTeamAssignments {
        val slot = ManualTeamSlot(side, slotIndex)
        val trimmed = heroName.trim()
        require(trimmed.isNotEmpty())
        return copy(values = values + (slot to trimmed))
    }

    fun remove(side: TeamSide, slotIndex: Int): ManualTeamAssignments =
        copy(values = values - ManualTeamSlot(side, slotIndex))

    fun clear(): ManualTeamAssignments = ManualTeamAssignments()

    fun heroAt(side: TeamSide, slotIndex: Int): String? =
        values[ManualTeamSlot(side, slotIndex)]

    fun heroes(side: TeamSide): List<String> =
        (1..5).mapNotNull { heroAt(side, it) }

    fun confirmedHeroes(side: TeamSide): List<ConfirmedHero> =
        heroes(side).map { ConfirmedHero(it, side, 1.0) }
}

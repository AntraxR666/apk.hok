package com.example.honorofkingsassistant

import java.util.ArrayDeque

enum class SlotRecognitionStatus {
    WAITING,
    SCANNING,
    DETECTED,
    UNCERTAIN,
    NOT_DETECTED,
    MANUAL
}

data class SlotRecognitionCandidate(
    val heroName: String,
    val distance: Double,
    val confidence: Double
)

data class SlotRecognitionEvidence(
    val side: TeamSide,
    val slotIndex: Int,
    val visualConfidence: Double,
    val candidates: List<SlotRecognitionCandidate>,
    val acceptedHeroName: String?
)

data class SlotRecognitionState(
    val side: TeamSide,
    val slotIndex: Int,
    val status: SlotRecognitionStatus,
    val heroName: String? = null,
    val confidence: Double = 0.0,
    val candidates: List<SlotRecognitionCandidate> = emptyList(),
    val sampleCount: Int = 0
)

/**
 * Slot-aware temporal consensus for portrait recognition.
 *
 * Hero identity is never pooled across positions: each of the ten draft slots owns its
 * independent five-frame history. Ambiguous evidence remains visible to the correction UI
 * without being published as an automatic hero observation.
 */
class SlotRecognitionTracker(
    private val requiredHits: Int = 3,
    private val historySize: Int = 5
) {
    private data class SlotKey(val side: TeamSide, val index: Int)

    private val history = ArrayDeque<Map<SlotKey, SlotRecognitionEvidence>>()
    private val activeFrames = linkedMapOf<SlotKey, Int>()
    private var activeKeys = emptySet<SlotKey>()
    private var states = waitingStates()

    init {
        require(requiredHits >= 1)
        require(historySize >= requiredHits)
    }

    @Synchronized
    fun observe(
        frame: List<SlotRecognitionEvidence>,
        board: DraftBoardState
    ): List<SlotRecognitionState> {
        val occupied = occupiedKeys(board)
        val newlyOccupied = occupied - activeKeys
        if (newlyOccupied.isNotEmpty()) removeHistoryFor(newlyOccupied)

        activeFrames.keys.retainAll(occupied)
        occupied.forEach { key ->
            activeFrames[key] = (activeFrames[key] ?: 0) + 1
        }
        activeKeys = occupied

        val frameBySlot = frame
            .filter { SlotKey(it.side, it.slotIndex) in occupied }
            .associateBy { SlotKey(it.side, it.slotIndex) }
        history.addLast(frameBySlot)
        while (history.size > historySize) history.removeFirst()

        states = allKeys().map { key ->
            if (key !in occupied) {
                waiting(key)
            } else {
                resolve(key)
            }
        }
        return states
    }

    @Synchronized
    fun current(): List<SlotRecognitionState> = states

    @Synchronized
    fun reset() {
        history.clear()
        activeFrames.clear()
        activeKeys = emptySet()
        states = waitingStates()
    }

    private fun resolve(key: SlotKey): SlotRecognitionState {
        val evidence = history.mapNotNull { it[key] }
        val samples = activeFrames[key] ?: 0
        val candidates = aggregateCandidates(evidence)
        val accepted = evidence.mapNotNull { item ->
            item.acceptedHeroName?.let { name -> name to candidateConfidence(item, name) }
        }
        val acceptedGroups = accepted.groupBy { CounterCatalog.normalize(it.first) }
        val winner = acceptedGroups.values.maxWithOrNull(
            compareBy<List<Pair<String, Double>>> { it.size }
                .thenBy { group -> group.map(Pair<String, Double>::second).average() }
        )
        if (winner != null && winner.size >= requiredHits) {
            return SlotRecognitionState(
                side = key.side,
                slotIndex = key.index,
                status = SlotRecognitionStatus.DETECTED,
                heroName = winner.first().first,
                confidence = winner.map(Pair<String, Double>::second).average().coerceIn(0.0, 1.0),
                candidates = candidates,
                sampleCount = samples
            )
        }

        val conflictingAcceptedHeroes = acceptedGroups.size > 1
        val latest = evidence.lastOrNull()
        val explicitlyAmbiguous = latest != null &&
            latest.acceptedHeroName == null &&
            latest.candidates.isNotEmpty()
        val status = when {
            conflictingAcceptedHeroes || explicitlyAmbiguous -> SlotRecognitionStatus.UNCERTAIN
            accepted.isNotEmpty() -> SlotRecognitionStatus.SCANNING
            samples < requiredHits -> SlotRecognitionStatus.SCANNING
            else -> SlotRecognitionStatus.NOT_DETECTED
        }
        return SlotRecognitionState(
            side = key.side,
            slotIndex = key.index,
            status = status,
            candidates = candidates,
            sampleCount = samples
        )
    }

    private fun candidateConfidence(
        evidence: SlotRecognitionEvidence,
        heroName: String
    ): Double = evidence.candidates.firstOrNull {
        CounterCatalog.normalize(it.heroName) == CounterCatalog.normalize(heroName)
    }?.confidence ?: evidence.visualConfidence

    private fun aggregateCandidates(
        evidence: List<SlotRecognitionEvidence>
    ): List<SlotRecognitionCandidate> = evidence
        .flatMap(SlotRecognitionEvidence::candidates)
        .groupBy { CounterCatalog.normalize(it.heroName) }
        .values
        .map { group ->
            SlotRecognitionCandidate(
                heroName = group.first().heroName,
                distance = group.minOf(SlotRecognitionCandidate::distance),
                confidence = group.map(SlotRecognitionCandidate::confidence)
                    .average()
                    .coerceIn(0.0, 1.0)
            )
        }
        .sortedWith(
            compareByDescending<SlotRecognitionCandidate> { it.confidence }
                .thenBy(SlotRecognitionCandidate::distance)
        )
        .take(MAX_CORRECTION_CANDIDATES)

    private fun removeHistoryFor(keys: Set<SlotKey>) {
        if (history.isEmpty()) return
        val retained = history.map { frame -> frame.filterKeys { it !in keys } }
        history.clear()
        retained.forEach(history::addLast)
    }

    private fun occupiedKeys(board: DraftBoardState): Set<SlotKey> =
        (board.allySlots + board.enemySlots)
            .filter { it.status != DraftSlotStatus.EMPTY }
            .map { SlotKey(it.side, it.index) }
            .toSet()

    private fun allKeys(): List<SlotKey> = listOf(TeamSide.ALLY, TeamSide.ENEMY)
        .flatMap { side -> (1..5).map { index -> SlotKey(side, index) } }

    private fun waitingStates(): List<SlotRecognitionState> = allKeys().map(::waiting)

    private fun waiting(key: SlotKey) = SlotRecognitionState(
        side = key.side,
        slotIndex = key.index,
        status = SlotRecognitionStatus.WAITING
    )

    companion object {
        private const val MAX_CORRECTION_CANDIDATES = 3
    }
}

import com.example.honorofkingsassistant.HeroObservation
import com.example.honorofkingsassistant.PortraitMatchCandidate
import com.example.honorofkingsassistant.PortraitMatchSelector
import com.example.honorofkingsassistant.TeamSide
import com.example.honorofkingsassistant.TemporalDraftTracker

fun main() {
    val ambiguous = PortraitMatchSelector.select(
        listOf(
            PortraitMatchCandidate("Lam", 0.10, 0.90),
            PortraitMatchCandidate("Luna", 0.115, 0.90)
        )
    )
    check(ambiguous == null)

    val clear = PortraitMatchSelector.select(
        listOf(
            PortraitMatchCandidate("Lam", 0.10, 0.90),
            PortraitMatchCandidate("Luna", 0.16, 0.90)
        )
    )
    check(clear?.heroName == "Lam")

    val tracker = TemporalDraftTracker(
        requiredHits = 3,
        historySize = 5,
        minimumObservationConfidence = 0.55
    )
    repeat(3) {
        tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.40)))
    }
    check(tracker.observe(emptyList()).enemies.isEmpty())

    repeat(2) {
        check(tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.90))).enemies.isEmpty())
    }
    val confirmed = tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.92)))
    check(confirmed.enemies.single().heroName == "Lam")
    println("RECOGNITION_CONFIDENCE_SMOKE_OK")
}

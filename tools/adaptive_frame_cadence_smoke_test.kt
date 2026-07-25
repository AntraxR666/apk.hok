import com.example.honorofkingsassistant.AdaptiveFrameCadence

fun main() {
    check(AdaptiveFrameCadence.interval(700L, 0L) == 700L)
    check(AdaptiveFrameCadence.interval(700L, 300L) == 700L)
    check(AdaptiveFrameCadence.interval(700L, 800L) == 1200L)
    check(AdaptiveFrameCadence.interval(700L, 1500L) == 1600L)
    println("ADAPTIVE_FRAME_CADENCE_SMOKE_OK")
}

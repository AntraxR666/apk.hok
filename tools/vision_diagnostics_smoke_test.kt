import com.example.honorofkingsassistant.VisionDiagnosticsTracker

fun main() {
    val tracker = VisionDiagnosticsTracker()
    check(tracker.snapshot().processedFrames == 0L)
    tracker.onDroppedFrame()
    val first = tracker.onProcessedFrame(
        latencyMs = 100,
        captureWidth = 1170,
        captureHeight = 540,
        ocrWidth = 1170,
        ocrHeight = 540
    )
    check(first.processedFrames == 1L)
    check(first.droppedFrames == 1L)
    check(first.lastLatencyMs == 100L)
    check(first.averageLatencyMs == 100L)

    val second = tracker.onProcessedFrame(200, 1170, 540, 1170, 540)
    check(second.processedFrames == 2L)
    check(second.averageLatencyMs == 125L)
    check(second.captureLabel == "1170x540")
    check(second.ocrLabel == "1170x540")
    println("VISION_DIAGNOSTICS_SMOKE_OK")
}

package com.example.honorofkingsassistant

import kotlin.math.roundToLong

data class VisionDiagnostics(
    val processedFrames: Long = 0,
    val droppedFrames: Long = 0,
    val lastLatencyMs: Long = 0,
    val averageLatencyMs: Long = 0,
    val captureWidth: Int = 0,
    val captureHeight: Int = 0,
    val ocrWidth: Int = 0,
    val ocrHeight: Int = 0
) {
    val captureLabel: String
        get() = if (captureWidth > 0 && captureHeight > 0) "${captureWidth}x${captureHeight}" else "—"

    val ocrLabel: String
        get() = if (ocrWidth > 0 && ocrHeight > 0) "${ocrWidth}x${ocrHeight}" else "—"
}

class VisionDiagnosticsTracker(
    private val smoothingFactor: Double = 0.25
) {
    private var state = VisionDiagnostics()

    init {
        require(smoothingFactor > 0.0 && smoothingFactor <= 1.0)
    }

    @Synchronized
    fun onDroppedFrame(): VisionDiagnostics {
        state = state.copy(droppedFrames = state.droppedFrames + 1)
        return state
    }

    @Synchronized
    fun onProcessedFrame(
        latencyMs: Long,
        captureWidth: Int,
        captureHeight: Int,
        ocrWidth: Int,
        ocrHeight: Int
    ): VisionDiagnostics {
        require(latencyMs >= 0)
        require(captureWidth > 0 && captureHeight > 0)
        require(ocrWidth > 0 && ocrHeight > 0)

        val average = if (state.processedFrames == 0L) {
            latencyMs
        } else {
            (state.averageLatencyMs * (1.0 - smoothingFactor) + latencyMs * smoothingFactor)
                .roundToLong()
        }
        state = state.copy(
            processedFrames = state.processedFrames + 1,
            lastLatencyMs = latencyMs,
            averageLatencyMs = average,
            captureWidth = captureWidth,
            captureHeight = captureHeight,
            ocrWidth = ocrWidth,
            ocrHeight = ocrHeight
        )
        return state
    }

    @Synchronized
    fun snapshot(): VisionDiagnostics = state

    @Synchronized
    fun reset() {
        state = VisionDiagnostics()
    }
}

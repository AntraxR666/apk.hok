package com.example.honorofkingsassistant

import kotlin.math.max
import kotlin.math.roundToLong

object AdaptiveFrameCadence {
    fun interval(
        baseIntervalMs: Long,
        averageLatencyMs: Long,
        maximumIntervalMs: Long = PersonalDeviceProfile.DRAFT_MAX_INTERVAL_MS
    ): Long {
        require(baseIntervalMs > 0)
        require(averageLatencyMs >= 0)
        if (baseIntervalMs == Long.MAX_VALUE) return Long.MAX_VALUE
        require(maximumIntervalMs >= baseIntervalMs)

        val latencyBound = if (averageLatencyMs == 0L) {
            baseIntervalMs
        } else {
            (averageLatencyMs * 1.35).roundToLong() + 120L
        }
        return max(baseIntervalMs, latencyBound).coerceAtMost(maximumIntervalMs)
    }
}

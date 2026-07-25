package com.example.honorofkingsassistant

object PersonalDeviceProfile {
    const val MODEL = "JKM-LX3"
    const val PLATFORM = "Android 9 / EMUI 9.1"
    const val SOC = "Kirin 710"
    const val RAM_GB = 4
    const val NATIVE_LONG_EDGE = 2340
    const val NATIVE_SHORT_EDGE = 1080

    // Exact 50% scale of the target device in landscape: 2340x1080 -> 1170x540.
    const val CAPTURE_MAX_LONG_EDGE = 1170
    const val CAPTURE_MAX_PIXELS = 640_000
    const val OCR_MAX_LONG_EDGE = 1170

    const val DRAFT_BASE_INTERVAL_MS = 700L
    const val DRAFT_MAX_INTERVAL_MS = 1_600L
}

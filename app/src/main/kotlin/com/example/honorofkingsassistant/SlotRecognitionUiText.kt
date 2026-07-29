package com.example.honorofkingsassistant

object SlotRecognitionUiText {
    fun compact(state: SlotRecognitionState): String {
        val prefix = "${if (state.side == TeamSide.ALLY) "A" else "E"}${state.slotIndex}"
        return when (state.status) {
            SlotRecognitionStatus.WAITING -> "$prefix · vacío"
            SlotRecognitionStatus.SCANNING -> "$prefix …"
            SlotRecognitionStatus.DETECTED ->
                "$prefix ✓ ${state.heroName.orEmpty().ifBlank { "detectado" }}"
            SlotRecognitionStatus.UNCERTAIN -> "$prefix ? revisar"
            SlotRecognitionStatus.NOT_DETECTED -> "$prefix ! no detectado"
            SlotRecognitionStatus.MANUAL ->
                "$prefix ✎ ${state.heroName.orEmpty().ifBlank { "manual" }}"
        }
    }

    fun correctionTitle(request: QuickCorrectionRequest): String {
        val side = if (request.slot.side == TeamSide.ALLY) "Aliado" else "Enemigo"
        val problem = when (request.status) {
            SlotRecognitionStatus.UNCERTAIN -> "retrato dudoso"
            SlotRecognitionStatus.NOT_DETECTED -> "no detectado"
            else -> "revisar identidad"
        }
        return "$side ${request.slot.slotIndex} · $problem"
    }
}

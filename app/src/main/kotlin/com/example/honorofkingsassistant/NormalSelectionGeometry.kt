package com.example.honorofkingsassistant

data class NormalSelectionGeometry(
    val heroCatalog: PixelRect,
    val selectedHero: PixelRect,
    val alliedColumn: PixelRect,
    val allyRows: List<PixelRect>,
    val confirmAction: PixelRect
) {
    companion object {
        private val heroCatalogEnvelope = NormalizedRect(0.02, 0.05, 0.22, 0.86)
        private val selectedHeroEnvelope = NormalizedRect(0.22, 0.05, 0.76, 0.92)
        private val alliedColumnEnvelope = NormalizedRect(0.76, 0.04, 0.97, 0.88)
        private val confirmActionEnvelope = NormalizedRect(0.80, 0.78, 0.98, 0.98)
        private val allyRowEnvelopes = listOf(
            NormalizedRect(0.76, 0.04, 0.97, 0.166667),
            NormalizedRect(0.76, 0.166667, 0.97, 0.355903),
            NormalizedRect(0.76, 0.355903, 0.97, 0.552083),
            NormalizedRect(0.76, 0.552083, 0.97, 0.746528),
            NormalizedRect(0.76, 0.746528, 0.97, 0.88)
        )

        fun forFrame(width: Int, height: Int): NormalSelectionGeometry {
            require(width > 0 && height > 0)
            val aspectRatio = width.toDouble() / height
            require(aspectRatio in 2.0..2.35) {
                "Normal selection geometry requires the calibrated landscape aspect ratio"
            }
            return NormalSelectionGeometry(
                heroCatalog = heroCatalogEnvelope.toPixelRect(width, height),
                selectedHero = selectedHeroEnvelope.toPixelRect(width, height),
                alliedColumn = alliedColumnEnvelope.toPixelRect(width, height),
                allyRows = allyRowEnvelopes.map { it.toPixelRect(width, height) },
                confirmAction = confirmActionEnvelope.toPixelRect(width, height)
            )
        }
    }
}

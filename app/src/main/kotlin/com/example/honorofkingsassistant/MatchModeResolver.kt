package com.example.honorofkingsassistant

data class PositionedTextLine(
    val text: String,
    val centerX: Int,
    val centerY: Int
)

object NormalSelectionEvidenceDetector {
    fun detect(
        lines: List<PositionedTextLine>,
        frameWidth: Int,
        frameHeight: Int,
        enemyOnRight: Boolean
    ): NormalSelectionEvidence {
        val geometry = runCatching {
            NormalSelectionGeometry.forFrame(frameWidth, frameHeight)
        }.getOrNull()
        if (geometry == null) {
            return NormalSelectionEvidence(
                landscapeCompatible = false,
                heroCatalogVisible = false,
                selectedHeroVisible = false,
                allyRowEvidenceCount = 0,
                enemyPickColumnVisible = false,
                rankedBanLayoutVisible = hasRankedBanText(lines)
            )
        }

        val heroCatalogVisible = lines.any { geometry.heroCatalog.contains(it) }
        val selectedHeroVisible = lines.any { geometry.selectedHero.contains(it) }
        val allyRowEvidenceCount = geometry.allyRows.count { row ->
            lines.any { row.contains(it) }
        }
        val enemyPhysicalLeft = !enemyOnRight
        val rankedEnemyTextVisible = HoKGlobalLandscapeProfile
            .playerNameRegions(enemyPhysicalLeft)
            .map { it.toPixelRect(frameWidth, frameHeight) }
            .any { region -> lines.any { region.contains(it) } }

        return NormalSelectionEvidence(
            landscapeCompatible = true,
            heroCatalogVisible = heroCatalogVisible,
            selectedHeroVisible = selectedHeroVisible,
            allyRowEvidenceCount = allyRowEvidenceCount,
            enemyPickColumnVisible = rankedEnemyTextVisible &&
                !(heroCatalogVisible && selectedHeroVisible),
            rankedBanLayoutVisible = hasRankedBanText(lines)
        )
    }

    private fun hasRankedBanText(lines: List<PositionedTextLine>): Boolean {
        val text = CounterCatalog.normalize(lines.joinToString(" ") { it.text })
        return text.contains("fase de veto") ||
            text.contains("veto") ||
            text.contains(" ban ")
    }

    private fun PixelRect.contains(line: PositionedTextLine): Boolean =
        line.centerX >= left &&
            line.centerX < right &&
            line.centerY >= top &&
            line.centerY < bottom
}

class MatchModeResolver(
    private val requiredFrames: Int = 3,
    initialPreference: MatchMode = MatchMode.AUTO
) {
    private var preference = initialPreference
    private var detected = MatchMode.AUTO
    private var normalEvidenceFrames = 0

    init {
        require(requiredFrames >= 1)
    }

    @Synchronized
    fun observe(evidence: NormalSelectionEvidence): MatchModeState {
        when {
            evidence.rankedBanLayoutVisible || evidence.enemyPickColumnVisible -> {
                normalEvidenceFrames = 0
                detected = MatchMode.RANKED_DRAFT
            }
            evidence.isNormalSelection() -> {
                normalEvidenceFrames++
                if (normalEvidenceFrames >= requiredFrames) {
                    detected = MatchMode.NORMAL_BLIND
                } else if (detected != MatchMode.NORMAL_BLIND) {
                    detected = MatchMode.AUTO
                }
            }
            else -> normalEvidenceFrames = 0
        }
        return current()
    }

    @Synchronized
    fun setPreference(value: MatchMode): MatchModeState {
        preference = value
        return current()
    }

    @Synchronized
    fun current(): MatchModeState = MatchModeState(
        preference = preference,
        detected = detected
    )

    @Synchronized
    fun reset() {
        detected = MatchMode.AUTO
        normalEvidenceFrames = 0
    }

    private fun NormalSelectionEvidence.isNormalSelection(): Boolean =
        landscapeCompatible &&
            heroCatalogVisible &&
            selectedHeroVisible &&
            allyRowEvidenceCount > 0 &&
            !enemyPickColumnVisible &&
            !rankedBanLayoutVisible
}

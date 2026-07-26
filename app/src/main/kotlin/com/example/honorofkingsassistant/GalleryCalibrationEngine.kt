package com.example.honorofkingsassistant

data class GalleryPortraitEvidence(
    val cardIndex: Int,
    val bounds: PixelRect,
    val fingerprint: PortraitFingerprint
) {
    init {
        require(cardIndex > 0)
        require(bounds.width > 0 && bounds.height > 0)
    }
}

data class GalleryTemplateProposal(
    val heroName: String,
    val fingerprint: PortraitFingerprint,
    val stableObservations: Int,
    val persistenceEligible: Boolean
) {
    /**
     * Gallery cards are calibration candidates only. They never publish live draft picks.
     */
    val selectedHeroObservations: List<HeroObservation> = emptyList()
}

/**
 * Produces deliberate portrait-template proposals from exact localized card titles.
 *
 * The engine does not persist while observing and does not emit draft observations. Callers may
 * persist a proposal only after repeated identical evidence or an explicit user confirmation.
 */
class GalleryCalibrationEngine(
    heroes: List<Hero>,
    private val requiredStableObservations: Int = 3
) {
    private val exactHeroByTitle = buildMap {
        heroes.forEach { hero ->
            sequenceOf(hero.name, hero.id)
                .plus(hero.identityAliases.displayTitles.asSequence())
                .map(::normalizeHeroRecognitionText)
                .filter(String::isNotBlank)
                .forEach { title -> putIfAbsent(title, hero) }
        }
    }
    private val stableCounts = linkedMapOf<String, Int>()

    init {
        require(requiredStableObservations > 0)
    }

    fun observe(
        portraits: List<GalleryPortraitEvidence>,
        titleLines: List<PositionedTextLine>
    ): List<GalleryTemplateProposal> {
        val candidatesThisFrame = linkedMapOf<String, Pair<Hero, PortraitFingerprint>>()
        portraits.forEach { portrait ->
            val pairedLine = titleLines.firstOrNull { line ->
                line.centerX >= portrait.bounds.left &&
                    line.centerX < portrait.bounds.right &&
                    line.centerY >= portrait.bounds.bottom &&
                    line.centerY <= portrait.bounds.bottom + portrait.bounds.height
            } ?: return@forEach
            val hero = exactHeroByTitle[normalizeHeroRecognitionText(pairedLine.text)]
                ?: return@forEach
            val stabilityKey =
                CounterCatalog.normalize(hero.name) + ":" + portrait.fingerprint.encode()
            candidatesThisFrame.putIfAbsent(stabilityKey, hero to portrait.fingerprint)
        }

        val nextStableCounts = linkedMapOf<String, Int>()
        val proposals = candidatesThisFrame.map { (stabilityKey, evidence) ->
            val (hero, fingerprint) = evidence
            val count = (stableCounts[stabilityKey] ?: 0) + 1
            nextStableCounts[stabilityKey] = count
            GalleryTemplateProposal(
                heroName = hero.name,
                fingerprint = fingerprint,
                stableObservations = count,
                persistenceEligible = count >= requiredStableObservations
            )
        }
        stableCounts.clear()
        stableCounts.putAll(nextStableCounts)
        return proposals
    }

    fun persist(
        proposal: GalleryTemplateProposal,
        store: PortraitTemplateStore,
        explicitlyConfirmed: Boolean
    ): Boolean {
        if (!proposal.persistenceEligible && !explicitlyConfirmed) return false
        return store.learn(proposal.heroName, proposal.fingerprint)
    }
}

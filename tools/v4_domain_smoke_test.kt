import com.example.honorofkingsassistant.*

private fun checkThat(value: Boolean, message: String) {
    if (!value) error(message)
}

fun main() {
    val heroes = listOf(
        Hero(
            id = "lam",
            name = "Lam",
            role = "Jungler",
            counters = listOf(
                HeroCounter("Donghuang", "Supresión dirigida."),
                HeroCounter("Liang", "Control dirigido."),
                HeroCounter("Zhang Fei", "Protección de retaguardia.")
            ),
            aliases = listOf("Lan"),
            metaScore = 82.0
        ),
        Hero("donghuang", "Donghuang", "Roamer/Support", emptyList(), metaScore = 78.0),
        Hero("liang", "Liang", "Mid Lane", emptyList(), metaScore = 86.0),
        Hero("zhang_fei", "Zhang Fei", "Roamer/Support", emptyList(), metaScore = 66.0),
        Hero("angela", "Angela", "Mid Lane", emptyList(), metaScore = 88.0)
    )

    val matcher = HeroNameMatcher(heroes)
    checkThat(matcher.bestMatch("Lan")?.hero?.name == "Lam", "alias matching failed")
    checkThat(matcher.bestMatch("Donghuarig")?.hero?.name == "Donghuang", "OCR fuzzy match failed")

    val classifier = DraftLayoutClassifier(enemyOnRight = true)
    checkThat(classifier.classify(50, 1000) == TeamSide.ALLY, "left side classification failed")
    checkThat(classifier.classify(950, 1000) == TeamSide.ENEMY, "right side classification failed")
    checkThat(classifier.classify(500, 1000) == TeamSide.UNKNOWN, "center exclusion failed")

    val tracker = TemporalDraftTracker(requiredHits = 2, historySize = 3)
    checkThat(tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.9))).enemies.isEmpty(), "tracker confirmed too early")
    val snapshot = tracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.95)))
    checkThat(snapshot.enemies.single().heroName == "Lam", "tracker did not confirm")

    val conflictTracker = TemporalDraftTracker(requiredHits = 2, historySize = 3)
    conflictTracker.observe(listOf(HeroObservation("Lam", TeamSide.UNKNOWN, 0.7)))
    conflictTracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.9)))
    val conflictSnapshot = conflictTracker.observe(listOf(HeroObservation("Lam", TeamSide.ENEMY, 0.95)))
    checkThat(conflictSnapshot.enemies.single().heroName == "Lam", "side conflict resolution failed")
    checkThat(conflictSnapshot.unknown.isEmpty(), "unknown duplicate was retained")

    val recommendations = DraftRecommendationEngine(CounterCatalog(heroes)).recommend(snapshot, limit = 3)
    checkThat(recommendations.first().hero.name == "Donghuang", "recommendation ranking failed: ${recommendations.map { it.hero.name }}")
    checkThat(recommendations.first().coveredEnemies == listOf("Lam"), "counter coverage missing")

    val plan = StrategyEngine().build(recommendations.first(), snapshot)
    checkThat(plan.priorityTarget.contains("Lam"), "strategy target missing")
    checkThat(plan.asLines().size == 5, "strategy format changed")


    val pixelRect = HoKGlobalLandscapeProfile.titleRegion.toPixelRect(1600, 738)
    checkThat(pixelRect.left in 620..630 && pixelRect.right in 970..980, "title ROI calibration failed: $pixelRect")

    val confirmedSlot = DraftSlotClassifier.classify(
        DraftSlotSignals(SlotVisualStats(0.44, 0.40, 0.045, 0.12), 0.11),
        physicalLeft = true
    )
    checkThat(confirmedSlot.first == DraftSlotStatus.CONFIRMED, "confirmed slot classification failed")
    val previewSlot = DraftSlotClassifier.classify(
        DraftSlotSignals(
            SlotVisualStats(0.39, 0.31, 0.023, 0.01),
            confirmationMarkerScore = 0.004,
            previewHighlightScore = 0.26
        ),
        physicalLeft = false
    )
    checkThat(previewSlot.first == DraftSlotStatus.PREVIEWING, "preview slot classification failed")

    val boardStabilizer = DraftBoardTemporalStabilizer(requiredConfirmationFrames = 2)
    val rawLateDraft = DraftBoardState(
        mode = ScreenMode.DRAFT,
        allySlots = (1..5).map { DraftSlotState(TeamSide.ALLY, it, DraftSlotStatus.CONFIRMED, 0.9) },
        enemySlots = (1..4).map { DraftSlotState(TeamSide.ENEMY, it, DraftSlotStatus.CONFIRMED, 0.9) } +
            DraftSlotState(TeamSide.ENEMY, 5, DraftSlotStatus.PREVIEWING, 0.8),
        activeSide = TeamSide.ENEMY
    )
    checkThat(boardStabilizer.stabilize(rawLateDraft).totalConfirmedCount == 0, "board locked before temporal confirmation")
    val stableLateDraft = boardStabilizer.stabilize(rawLateDraft)
    checkThat(stableLateDraft.allyConfirmedCount == 5, "ally lock stabilization failed")
    checkThat(stableLateDraft.enemyConfirmedCount == 4, "enemy lock stabilization failed")
    checkThat(stableLateDraft.enemySlots.last().status == DraftSlotStatus.PREVIEWING, "active preview was locked")


    val flowPlayerLocked = DraftFlowResolver.resolve(
        stableLateDraft,
        PlayerSlotDetection(slotIndex = 2, side = TeamSide.ALLY, confidence = 1.0)
    )
    checkThat(flowPlayerLocked.moment == DraftMoment.FINAL_ENEMY_PICK, "late draft flow failed: $flowPlayerLocked")
    checkThat(!flowPlayerLocked.shouldRecommendPicks, "recommendations must stop after player lock")
    checkThat(flowPlayerLocked.shouldShowStrategy, "strategy must remain active after player lock")

    val fingerprintA = PortraitFingerprint.fromArgb64(IntArray(64) { index ->
        val value = if (index % 2 == 0) 220 else 30
        (255 shl 24) or (value shl 16) or ((255 - value) shl 8) or 80
    })
    val fingerprintB = PortraitFingerprint.decode(fingerprintA.encode())
    checkThat(fingerprintB != null, "portrait fingerprint serialization failed")
    checkThat(fingerprintA.distance(requireNotNull(fingerprintB)) == 0.0, "portrait fingerprint distance failed")

    val finalPick = DraftPhaseEstimator.estimate(leftConfirmed = 5, rightConfirmed = 4)
    checkThat(finalPick.activePhysicalSide == DraftPhaseEstimator.PhysicalDraftSide.RIGHT, "final pick side failed: $finalPick")
    checkThat(finalPick.step == 5, "final pick step failed: $finalPick")

    println("V4_DOMAIN_SMOKE_OK")
}

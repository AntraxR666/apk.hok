from pathlib import Path

matcher = Path('app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt').read_text(encoding='utf-8')
selector = Path('app/src/main/kotlin/com/example/honorofkingsassistant/PortraitMatchSelector.kt').read_text(encoding='utf-8')
tracker = Path('app/src/main/kotlin/com/example/honorofkingsassistant/TemporalDraftTracker.kt').read_text(encoding='utf-8')
service = Path('app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')
ranked_snapshot = Path(
    'app/src/main/kotlin/com/example/honorofkingsassistant/ConfirmedSlotSnapshotPolicy.kt'
).read_text(encoding='utf-8')
draft_status = service[
    service.index('private fun buildDraftStatus('):
    service.index('private fun learnFromCurrentPreview(')
]

checks = {
    'selector used by matcher': 'PortraitMatchSelector.select(candidates)' in matcher,
    'ambiguity margin': 'MIN_AMBIGUITY_MARGIN = 0.035' in selector,
    'confidence floor': 'minimumObservationConfidence: Double = 0.55' in tracker,
    'low confidence filtered': '.filter { it.confidence >= minimumObservationConfidence }' in tracker,
    'personal 3 hits': 'requiredHits = 3' in service,
    'personal 5 frame history': 'historySize = 5' in service,
    'ranked snapshot uses exact-slot consensus':
        'ConfirmedSlotSnapshotPolicy.from(' in service,
    'ranked snapshot applies manual slot authority':
        'lastSlotRecognition,\n                                    manualAssignments' in service,
    'loading snapshot applies exact-slot manual authority':
        'LoadingRosterSnapshotPolicy.from(' in service,
    'manual corrections refresh ranked recommendations immediately':
        'refreshRankedSelectionSnapshot()' in service,
    'ranked snapshot excludes unresolved states':
        'it.status == SlotRecognitionStatus.DETECTED' in ranked_snapshot,
    'ranked status counts confirmed slots':
        'QuickCorrectionPolicy.applyManualAuthority(' in draft_status and
        'SlotRecognitionStatus.MANUAL' in draft_status,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('V5_RECOGNITION_CONTRACT_FAILED: ' + '; '.join(missing))
print('V5_RECOGNITION_CONTRACT_OK')

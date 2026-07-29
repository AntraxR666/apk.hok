from pathlib import Path

matcher = Path('app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt').read_text(encoding='utf-8')
selector = Path('app/src/main/kotlin/com/example/honorofkingsassistant/PortraitMatchSelector.kt').read_text(encoding='utf-8')
tracker = Path('app/src/main/kotlin/com/example/honorofkingsassistant/TemporalDraftTracker.kt').read_text(encoding='utf-8')
service = Path('app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')

checks = {
    'selector used by matcher': 'PortraitMatchSelector.select(candidates)' in matcher,
    'ambiguity margin': 'MIN_AMBIGUITY_MARGIN = 0.035' in selector,
    'confidence floor': 'minimumObservationConfidence: Double = 0.55' in tracker,
    'low confidence filtered': '.filter { it.confidence >= minimumObservationConfidence }' in tracker,
    'personal 3 hits': 'requiredHits = 3' in service,
    'personal 5 frame history': 'historySize = 5' in service,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('V5_RECOGNITION_CONTRACT_FAILED: ' + '; '.join(missing))
print('V5_RECOGNITION_CONTRACT_OK')

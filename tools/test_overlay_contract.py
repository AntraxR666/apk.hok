from pathlib import Path

manifest = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
overlay = Path('app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt').read_text(encoding='utf-8')
capture = Path('app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')

assert 'SYSTEM_ALERT_WINDOW' in manifest
assert 'FOREGROUND_SERVICE_MEDIA_PROJECTION' in manifest
assert 'android.permission.INTERNET" tools:node="remove"' in manifest
assert 'TYPE_APPLICATION_OVERLAY' in overlay
assert 'startForeground(' not in overlay and 'startForeground(' in capture
assert 'FINAL_ENEMY_PICK' in overlay
assert 'ACTION_SWAP_SIDES' in capture
assert 'ACTION_SET_STAGE' in capture
assert 'AssistantStage.PAUSED' in overlay + capture
assert 'AssistantStage.DRAFT' in overlay + capture
assert 'AssistantStage.IN_GAME' in overlay + capture
assert 'learnFromCurrentPreview' in capture
assert 'DraftFlowResolver.resolve' in capture
assert 'EnemyHeroName' not in overlay + capture
print('V4_OVERLAY_CONTRACT_OK')

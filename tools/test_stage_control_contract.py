from pathlib import Path

root = Path('app/src/main/kotlin/com/example/honorofkingsassistant')
stage = (root / 'AssistantStage.kt').read_text(encoding='utf-8')
overlay = (root / 'OverlayService.kt').read_text(encoding='utf-8')
capture = (root / 'ScreenCaptureService.kt').read_text(encoding='utf-8')
bus = (root / 'AssistantSessionBus.kt').read_text(encoding='utf-8')
layout = Path('app/src/main/res/layout/activity_main.xml').read_text(encoding='utf-8')

for name in ('PAUSED', 'DRAFT', 'IN_GAME'):
    assert name in stage, name
assert 'ACTION_SET_STAGE' in capture
assert 'EXTRA_ASSISTANT_STAGE' in capture
assert 'AssistantStagePolicy.forStage' in capture
assert 'shouldProcessFrames' in capture
assert 'updateCaptureSurfaceForStage' in capture
assert 'display.setSurface(targetSurface)' in capture
assert 'suggestedStage' in bus
assert 'sendStageAction(AssistantStage.DRAFT)' in overlay
assert 'sendStageAction(AssistantStage.IN_GAME)' in overlay
assert 'sendStageAction(AssistantStage.PAUSED)' in overlay
assert 'confirm_switch_to_game' in overlay
for view_id in ('draftModeButton', 'gameModeButton', 'pauseModeButton'):
    assert f'@+id/{view_id}' in layout, view_id
print('V4_STAGE_CONTROL_CONTRACT_OK')

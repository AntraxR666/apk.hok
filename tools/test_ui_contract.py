from pathlib import Path

layout = Path('app/src/main/res/layout/activity_main.xml').read_text(encoding='utf-8')
activity = Path('app/src/main/kotlin/com/example/honorofkingsassistant/MainActivity.kt').read_text(encoding='utf-8')
for view_id in (
    'startAssistantButton', 'stopAssistantButton', 'roleSpinner', 'heroSearch',
    'sideSpinner', 'manualAddButton', 'manualCountersButton', 'clearManualButton',
    'resultsContainer', 'statusText', 'draftModeButton', 'gameModeButton', 'pauseModeButton',
):
    assert f'@+id/{view_id}' in layout, view_id
assert 'android:threshold=' not in layout
assert 'android:completionThreshold="1"' in layout
assert 'ActivityResultContracts' in activity
assert 'MediaProjectionManager' in activity
assert 'EnemyHeroName' not in activity
print('V4_UI_CONTRACT_OK')

assert 'ACTION_SET_STAGE' in activity
assert 'AssistantStage.DRAFT' in activity
assert 'AssistantStage.IN_GAME' in activity
assert 'AssistantStage.PAUSED' in activity

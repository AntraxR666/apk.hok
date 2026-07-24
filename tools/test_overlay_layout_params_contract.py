from pathlib import Path

source = Path('app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt').read_text(encoding='utf-8')

errors = []
if 'import android.widget.FrameLayout' not in source:
    errors.append('FrameLayout import is missing')
if 'ScrollView.LayoutParams(' in source:
    errors.append('ScrollView.LayoutParams is invalid for the ScrollView child')
if 'FrameLayout.LayoutParams(' not in source:
    errors.append('ScrollView child must use FrameLayout.LayoutParams')
if 'this.layoutParams = LinearLayout.LayoutParams(' not in source:
    errors.append('ScrollView layoutParams must be receiver-qualified to avoid local shadowing')

if errors:
    raise SystemExit('OVERLAY_LAYOUT_PARAMS_CONTRACT_FAILED: ' + '; '.join(errors))

print('OVERLAY_LAYOUT_PARAMS_CONTRACT_OK')

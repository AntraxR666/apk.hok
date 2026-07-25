from pathlib import Path

overlay = Path('app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt').read_text(encoding='utf-8')
strings = Path('app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
workflow = Path('.github/workflows/android-ci.yml').read_text(encoding='utf-8')

checks = {
    'diagnostics view field': 'private var diagnosticsView: TextView?' in overlay,
    'diagnostics view added': 'panelContent.addView(requireNotNull(diagnosticsView))' in overlay,
    'diagnostics render': 'diagnostics.averageLatencyMs' in overlay,
    'diagnostics string': 'name="vision_diagnostics"' in strings,
    'V5 validator in CI': 'python3 tools/validate_v5.py' in workflow,
    'recognition contract in CI': 'python3 tools/test_v5_recognition_contract.py' in workflow,
    'diagnostics contract in CI': 'python3 tools/test_v5_diagnostics_contract.py' in workflow,
}
missing = [name for name, ok in checks.items() if not ok]
if missing:
    raise SystemExit('V5_DIAGNOSTICS_CONTRACT_FAILED: ' + '; '.join(missing))
print('V5_DIAGNOSTICS_CONTRACT_OK')

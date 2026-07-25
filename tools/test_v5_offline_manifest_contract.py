from pathlib import Path

manifest = Path("app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-ci.yml").read_text(encoding="utf-8")

required_manifest = [
    'xmlns:tools="http://schemas.android.com/tools"',
    'android.permission.INTERNET" tools:node="remove"',
    'android.permission.ACCESS_NETWORK_STATE" tools:node="remove"',
]
for marker in required_manifest:
    if marker not in manifest:
        raise SystemExit(f"Missing offline manifest marker: {marker}")

required_workflow = [
    "python3 tools/test_v5_offline_manifest_contract.py",
    "Unexpected INTERNET permission in final APK",
    "Unexpected ACCESS_NETWORK_STATE permission in final APK",
]
for marker in required_workflow:
    if marker not in workflow:
        raise SystemExit(f"Missing offline APK verification marker: {marker}")

print("V5_OFFLINE_MANIFEST_CONTRACT_OK")

"""Static release gate for the first personal 1.0 candidate."""

import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
MANIFEST = json.loads((ROOT / "app/src/main/assets/data_manifest.json").read_text(encoding="utf-8"))

for marker in (
    "versionCode 12",
    'versionName "1.0.0-personal-jkm-lx3-rc1"',
):
    assert marker in BUILD, marker

for marker in (
    "HoK-Draft-Assistant-1.0-Personal-JKM-LX3",
    "python3 tools/test_spanish_alias_catalog.py",
    "python3 tools/test_overlay_v55_contract.py",
    "python3 tools/test_scoreboard_scan_contract.py",
    "python3 tools/test_item_catalog.py",
    "python3 tools/test_min_sdk_compat.py",
    "python3 tools/test_v1_delivery_contract.py",
    "assets/hok_items.json",
    "versionCode='12'",
    "versionName='1.0.0-personal-jkm-lx3-rc1'",
):
    assert marker in WORKFLOW, marker

sources = MANIFEST.get("sources", [])
item_source = next((entry for entry in sources if entry.get("snapshot_asset") == "hok_items.json"), None)
assert item_source is not None, "missing item snapshot provenance"
assert item_source["source_url"] == "https://hokmeta.com/api/items.json"
assert item_source["sha256"] == "753B2368437937CAD2EADFA02FEB8802FCF0FFFABDAF126D4024FA5DCDE24612"

print("V1_DELIVERY_CONTRACT_OK")

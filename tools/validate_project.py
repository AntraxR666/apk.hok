#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/kotlin/com/example/honorofkingsassistant"

REQUIRED_FILES = [
    "build.gradle", "settings.gradle", "gradle.properties", "app/build.gradle",
    "app/proguard-rules.pro", "app/src/main/AndroidManifest.xml",
    "app/src/main/assets/hok_counters.json", "app/src/main/assets/data_manifest.json",
    "app/src/main/res/layout/activity_main.xml", "app/src/main/res/values/strings.xml",
    "app/src/main/res/values/themes.xml",
    "app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalog.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/CounterCatalogJson.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/CounterEngine.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/MainActivity.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/DraftFlowResolver.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/HeroPortraitMatcher.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/PortraitTemplateStore.kt",
]


def fail(message: str) -> None:
    raise AssertionError(message)


def validate_required_files() -> None:
    missing = [path for path in REQUIRED_FILES if not (ROOT / path).is_file()]
    if missing:
        fail("Faltan archivos requeridos: " + ", ".join(missing))


def validate_json_catalog() -> None:
    data = json.loads((ROOT / "app/src/main/assets/hok_counters.json").read_text(encoding="utf-8"))
    heroes = data.get("heroes")
    if not isinstance(heroes, list) or len(heroes) != 116:
        fail("El catálogo debe contener exactamente 116 héroes")
    names = [hero.get("name", "").strip() for hero in heroes]
    ids = [hero.get("id", "").strip() for hero in heroes]
    if len(set(names)) != 116 or len(set(ids)) != 116:
        fail("Los nombres e IDs deben ser únicos")
    known = set(names)
    relations = 0
    for hero in heroes:
        counters = hero.get("counters", [])
        if len(counters) != 3:
            fail(f"{hero.get('name')} no tiene tres counters")
        if not hero.get("patch_label") or not hero.get("snapshot_date"):
            fail(f"{hero.get('name')} no tiene procedencia temporal")
        for counter in counters:
            relations += 1
            if counter.get("hero_name") not in known:
                fail(f"Counter inexistente: {counter.get('hero_name')}")
            if not counter.get("reason") or not 0.0 <= float(counter.get("confidence", -1)) <= 1.0:
                fail(f"Relación inválida en {hero.get('name')}")
    if relations != 348:
        fail(f"Se esperaban 348 relaciones, hay {relations}")

    manifest = json.loads((ROOT / "app/src/main/assets/data_manifest.json").read_text(encoding="utf-8"))
    if manifest.get("roster_count") != 116 or manifest.get("vision", {}).get("player_name") != "R-95":
        fail("data_manifest no corresponde a la calibración V4")


def validate_xml_and_resources() -> None:
    manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
    layout_path = ROOT / "app/src/main/res/layout/activity_main.xml"
    strings_path = ROOT / "app/src/main/res/values/strings.xml"
    themes_path = ROOT / "app/src/main/res/values/themes.xml"
    for path in (manifest_path, layout_path, strings_path, themes_path):
        ET.parse(path)

    manifest = manifest_path.read_text(encoding="utf-8")
    for permission in (
        "android.permission.SYSTEM_ALERT_WINDOW",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION",
        "android.permission.POST_NOTIFICATIONS",
    ):
        if permission not in manifest:
            fail(f"Falta permiso: {permission}")
    if "android.permission.INTERNET" in manifest:
        fail("La V4 local no debe declarar INTERNET")
    if 'android:foregroundServiceType="mediaProjection"' not in manifest:
        fail("ScreenCaptureService no declara mediaProjection")

    layout = layout_path.read_text(encoding="utf-8")
    for view_id in (
        "startAssistantButton", "stopAssistantButton", "roleSpinner", "heroSearch",
        "sideSpinner", "manualAddButton", "manualCountersButton", "clearManualButton",
        "resultsContainer", "statusText",
    ):
        if f"@+id/{view_id}" not in layout:
            fail(f"Falta el ID V4: {view_id}")
    if 'android:completionThreshold="1"' not in layout or "android:threshold=" in layout:
        fail("AutoCompleteTextView no está configurado correctamente")

    strings_root = ET.parse(strings_path).getroot()
    string_names = {node.attrib["name"] for node in strings_root.findall("string")}
    referenced = set()
    for path in [manifest_path, layout_path, themes_path, *SRC.glob("*.kt")]:
        text = path.read_text(encoding="utf-8")
        referenced.update(re.findall(r"(?:@string/|R\.string\.)([A-Za-z0-9_]+)", text))
    missing = sorted(referenced - string_names)
    if missing:
        fail("Strings faltantes: " + ", ".join(missing))


def validate_source_contracts() -> None:
    all_source = "\n".join(path.read_text(encoding="utf-8") for path in SRC.glob("*.kt"))
    for forbidden in ("EnemyHeroName", "AccessibilityService", "Runtime.getRuntime().exec", "startActivityForResult"):
        if forbidden in all_source:
            fail(f"Patrón obsoleto/no permitido: {forbidden}")
    for required in (
        "MediaProjectionManager", "TextRecognition.getClient", "TYPE_APPLICATION_OVERLAY",
        "DraftFlowResolver", "FINAL_ENEMY_PICK", "PortraitFingerprint", "learnPortrait",
        "DraftBoardTemporalStabilizer", "R-95",
    ):
        if required not in all_source:
            fail(f"Contrato V4 ausente: {required}")

    gradle = (ROOT / "app/build.gradle").read_text(encoding="utf-8")
    for expected in (
        "namespace 'com.example.honorofkingsassistant'", "compileSdk 33", "minSdk 23",
        "targetSdk 33", "com.google.mlkit:text-recognition:16.0.1",
    ):
        if expected not in gradle:
            fail(f"Configuración Gradle ausente: {expected}")


def main() -> int:
    for name, check in (
        ("archivos V4", validate_required_files),
        ("catálogo y procedencia", validate_json_catalog),
        ("XML y recursos", validate_xml_and_resources),
        ("contratos V4", validate_source_contracts),
    ):
        check()
        print(f"[OK] {name}")
    print("[OK] V4 validada: 116 héroes, 348 relaciones, draft 9/10 y visión local")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, json.JSONDecodeError, ET.ParseError) as error:
        print(f"[ERROR] {error}", file=sys.stderr)
        raise SystemExit(1)

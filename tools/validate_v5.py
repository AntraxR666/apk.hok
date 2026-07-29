#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "app/src/main/kotlin/com/example/honorofkingsassistant"
TESTS = ROOT / "app/src/test/java/com/example/honorofkingsassistant"

errors: list[str] = []


def require(condition: bool, message: str) -> None:
    if not condition:
        errors.append(message)


def read(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"Falta archivo requerido: {relative}")
    return path.read_text(encoding="utf-8") if path.is_file() else ""


app_gradle = read("app/build.gradle")
root_gradle = read("build.gradle")
wrapper = read("gradle/wrapper/gradle-wrapper.properties")
manifest = read("app/src/main/AndroidManifest.xml")
workflow = read(".github/workflows/android-ci.yml")
overlay = read("app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt")
capture = read("app/src/main/kotlin/com/example/honorofkingsassistant/CaptureGeometry.kt")
service = read("app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt")
vision = read("app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt")
tracker = read("app/src/main/kotlin/com/example/honorofkingsassistant/TemporalDraftTracker.kt")
selector = read("app/src/main/kotlin/com/example/honorofkingsassistant/PortraitMatchSelector.kt")
strings = read("app/src/main/res/values/strings.xml")

for relative in (
    "app/src/main/kotlin/com/example/honorofkingsassistant/PersonalDeviceProfile.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/AdaptiveFrameCadence.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/AssistantStage.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/CaptureGeometry.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/OcrBitmapPreprocessor.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/VisionDiagnostics.kt",
    "app/src/main/kotlin/com/example/honorofkingsassistant/PortraitMatchSelector.kt",
    "app/src/test/java/com/example/honorofkingsassistant/CaptureGeometryTest.kt",
    "app/src/test/java/com/example/honorofkingsassistant/AdaptiveFrameCadenceTest.kt",
    "app/src/test/java/com/example/honorofkingsassistant/VisionDiagnosticsTest.kt",
    "app/src/test/java/com/example/honorofkingsassistant/RecognitionConfidenceTest.kt",
):
    require((ROOT / relative).is_file(), f"Falta componente V5: {relative}")

for expected in (
    "compileSdk 36",
    "buildToolsVersion '35.0.0'",
    "minSdk 23",
    "targetSdk 36",
    "versionCode 12",
    'versionName "1.0.0-personal-jkm-lx3-rc1"',
):
    require(expected in app_gradle, f"Configuracion V5 ausente: {expected}")

for expected in (
    "com.android.application' version '8.10.1'",
    "org.jetbrains.kotlin.android' version '1.9.24'",
):
    require(expected in root_gradle, f"Toolchain V5 ausente: {expected}")
require("gradle-8.11.1-bin.zip" in wrapper, "Gradle wrapper debe ser 8.11.1")

require('android.permission.INTERNET" tools:node="remove"' in manifest, "V5 personal debe eliminar INTERNET del manifiesto fusionado")
require("FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest, "Falta permiso FGS mediaProjection")
require('android:foregroundServiceType="mediaProjection"' in manifest, "Falta tipo mediaProjection")
require('android:allowBackup="false"' in manifest, "allowBackup debe permanecer false")
require("startForeground(" not in overlay, "OverlayService no debe ser un segundo FGS en API 36")
require("startService(" in service and "Intent(this, OverlayService::class.java)" in service, "ScreenCaptureService debe mantener el overlay como servicio normal")
require("START_STICKY" not in overlay + service, "Los servicios V5 no deben reiniciarse sin una sesion autorizada")

for expected in (
    "PersonalDeviceProfile.CAPTURE_MAX_LONG_EDGE",
    "PersonalDeviceProfile.CAPTURE_MAX_PIXELS",
    "PersonalDeviceProfile.OCR_MAX_LONG_EDGE",
):
    require(expected in capture, f"Perfil personal incorrecto: {expected}")

for expected in (
    "onCapturedContentResize",
    "display.resize",
    "display.setSurface",
    "CaptureGeometry.fit",
    "requiredHits = 3",
    "historySize = 5",
    "minimumObservationConfidence = 0.55",
):
    require(expected in service, f"Contrato de captura/estabilidad ausente: {expected}")

for expected in (
    "OcrBitmapPreprocessor.prepare",
    "diagnosticsTracker.onDroppedFrame",
    "diagnosticsTracker.onProcessedFrame",
    "AtomicBoolean(false)",
    "addOnSuccessListener(callbackExecutor)",
    "addOnFailureListener(callbackExecutor)",
    "addOnCompleteListener(callbackExecutor)",
):
    require(expected in vision, f"Contrato de vision V5 ausente: {expected}")

profile = read("app/src/main/kotlin/com/example/honorofkingsassistant/PersonalDeviceProfile.kt")
stage = read("app/src/main/kotlin/com/example/honorofkingsassistant/AssistantStage.kt")
cadence = read("app/src/main/kotlin/com/example/honorofkingsassistant/AdaptiveFrameCadence.kt")
for expected in (
    'MODEL = "JKM-LX3"',
    'PLATFORM = "Android 9 / EMUI 9.1"',
    'SOC = "Kirin 710"',
    'RAM_GB = 4',
    'CAPTURE_MAX_LONG_EDGE = 1170',
    'CAPTURE_MAX_PIXELS = 640_000',
    'OCR_MAX_LONG_EDGE = 1170',
):
    require(expected in profile, f"Perfil Huawei ausente: {expected}")
require("PersonalDeviceProfile.DRAFT_BASE_INTERVAL_MS" in stage, "Cadencia base Huawei ausente")
require("AdaptiveFrameCadence.interval" in service, "Cadencia adaptativa no integrada")
require("averageLatencyMs" in cadence, "Cadencia adaptativa no usa latencia")

require("minimumObservationConfidence: Double = 0.55" in tracker, "Piso de confianza temporal incorrecto")
require("MIN_AMBIGUITY_MARGIN = 0.035" in selector, "Margen anti-ambiguedad incorrecto")
require("second.distance - best.distance < minimumMargin" in selector, "No se rechazan retratos ambiguos")

for expected in (
    "private var diagnosticsView: TextView?",
    "R.string.vision_diagnostics",
    "diagnostics.averageLatencyMs",
    "diagnostics.droppedFrames",
):
    require(expected in overlay, f"Diagnostico de overlay ausente: {expected}")
require('name="vision_diagnostics"' in strings, "Falta texto de diagnostico V5")

for expected in (
    'sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"',
    "python3 tools/validate_v5.py",
    "python3 tools/test_v5_build_contract.py",
    "python3 tools/test_v5_recognition_contract.py",
    "python3 tools/test_v5_diagnostics_contract.py",
    "python3 tools/test_v5_single_fgs_contract.py",
    "python3 tools/test_v5_delivery_contract.py",
    "./gradlew --no-daemon clean testDebugUnitTest lintDebug assembleDebug",
    "HoK-Draft-Assistant-1.0-Personal-JKM-LX3",
    "versionCode='12'",
    "versionName='1.0.0-personal-jkm-lx3-rc1'",
):
    require(expected in workflow, f"CI V5 incompleto: {expected}")

try:
    ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
    ET.parse(ROOT / "app/src/main/res/values/strings.xml")
except ET.ParseError as exc:
    errors.append(f"XML invalido: {exc}")

try:
    catalog = json.loads((ROOT / "app/src/main/assets/hok_counters.json").read_text(encoding="utf-8"))
    heroes = catalog.get("heroes", [])
    relations = sum(len(hero.get("counters", [])) for hero in heroes)
    require(len(heroes) == 116, f"Catalogo V5: se esperaban 116 heroes, hay {len(heroes)}")
    require(relations == 348, f"Catalogo V5: se esperaban 348 relaciones, hay {relations}")
except Exception as exc:
    errors.append(f"Catalogo V5 invalido: {exc}")

all_sources = "\n".join(path.read_text(encoding="utf-8") for path in SRC.glob("*.kt"))
for forbidden in (
    "AccessibilityService",
    "Runtime.getRuntime().exec",
    "android.permission.INTERNET",
):
    require(forbidden not in all_sources, f"Patron prohibido en V5: {forbidden}")

if errors:
    print("V5_PERSONAL_VALIDATION_FAILED")
    for error in errors:
        print("-", error)
    sys.exit(1)

print("V5_PERSONAL_VALIDATION_OK")
print("- API 36 / AGP 8.10.1 / Gradle 8.11.1 / JDK 17")
print("- Perfil JKM-LX3: captura 1170x540 objetivo, <= 640,000 pixeles; OCR <= 1170 px")
print("- Confianza personal: 3 de 5, piso 0.55 y rechazo de ambiguedad")
print("- Diagnostico visible y CI con tests, lint, APK y metadatos V5")
print("- 116 heroes / 348 relaciones / sin permiso INTERNET")

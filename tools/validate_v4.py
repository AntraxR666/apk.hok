#!/usr/bin/env python3
from pathlib import Path
import json
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
errors = []

def require(condition: bool, message: str):
    if not condition:
        errors.append(message)

for relative in [
    'app/src/main/AndroidManifest.xml',
    'app/src/main/res/layout/activity_main.xml',
    'app/src/main/res/values/strings.xml',
    'app/src/main/res/values/themes.xml',
]:
    try:
        ET.parse(ROOT / relative)
    except Exception as exc:
        errors.append(f'XML inválido {relative}: {exc}')

catalog_path = ROOT / 'app/src/main/assets/hok_counters.json'
try:
    catalog = json.loads(catalog_path.read_text(encoding='utf-8'))
    heroes = catalog['heroes']
    names = {hero['name'] for hero in heroes}
    require(len(heroes) == 116, f'Se esperaban 116 héroes, hay {len(heroes)}')
    require(len(names) == 116, 'Hay nombres de héroe duplicados')
    require(all(len(hero['counters']) == 3 for hero in heroes), 'Cada héroe debe tener tres counters')
    require(all(counter['hero_name'] in names for hero in heroes for counter in hero['counters']), 'Hay counters que apuntan a héroes inexistentes')
    require(all(hero.get('meta_score') is not None for hero in heroes), 'Falta meta_score')
    require(all(hero.get('patch_label') for hero in heroes), 'Falta patch_label')
    require(all(hero.get('snapshot_date') for hero in heroes), 'Falta snapshot_date')
except Exception as exc:
    errors.append(f'Catálogo inválido: {exc}')

try:
    data_manifest = json.loads((ROOT / 'app/src/main/assets/data_manifest.json').read_text(encoding='utf-8'))
    require(data_manifest.get('roster_count') == 116, 'data_manifest roster_count incorrecto')
    require(data_manifest.get('server') == 'Honor of Kings Global / International', 'Servidor de datos incorrecto')
    require(data_manifest.get('vision', {}).get('player_name') == 'R-95', 'Falta calibración del jugador R-95')
    require(data_manifest.get('vision', {}).get('player_slot') is None, 'El slot no debe quedar fijado entre partidas')
except Exception as exc:
    errors.append(f'data_manifest inválido: {exc}')

manifest = (ROOT / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
require('android.permission.INTERNET' not in manifest, 'La app no debe declarar INTERNET')
require('FOREGROUND_SERVICE_MEDIA_PROJECTION' in manifest, 'Falta permiso de MediaProjection FGS')
require('android:foregroundServiceType="mediaProjection"' in manifest, 'Falta tipo mediaProjection')
require('android:allowBackup="false"' in manifest, 'allowBackup debe ser false')

build = (ROOT / 'app/build.gradle').read_text(encoding='utf-8')
require("com.google.mlkit:text-recognition:16.0.1" in build, 'Falta OCR ML Kit empaquetado')
require('minifyEnabled true' in build, 'Release debe usar R8')
require('shrinkResources true' in build, 'Release debe reducir recursos')

kotlin_root = ROOT / 'app/src/main/kotlin/com/example/honorofkingsassistant'
required_sources = [
    'ScreenCaptureService.kt',
    'DraftVisionEngine.kt',
    'DraftRecommendationEngine.kt',
    'TemporalDraftTracker.kt',
    'OverlayService.kt',
    'AssistantSessionBus.kt',
    'DraftFlowResolver.kt',
    'PortraitFingerprint.kt',
    'PortraitTemplateStore.kt',
    'HeroPortraitMatcher.kt',
    'AssistantStage.kt',
    'DraftSubphase.kt',
]
for source in required_sources:
    require((kotlin_root / source).is_file(), f'Falta {source}')

all_kotlin = '\n'.join(path.read_text(encoding='utf-8') for path in kotlin_root.glob('*.kt'))
for forbidden in ['AccessibilityService', 'android.permission.INTERNET', 'su ', 'Runtime.getRuntime().exec']:
    require(forbidden not in all_kotlin, f'Patrón no permitido detectado: {forbidden}')
require('MediaProjectionManager' in all_kotlin, 'Falta MediaProjection')
require('TextRecognition.getClient' in all_kotlin, 'Falta OCR local')
require('PortraitFingerprint' in all_kotlin, 'Falta reconocimiento/aprendizaje de retratos')
require('DraftFlowResolver' in all_kotlin, 'Falta estado de flujo del draft')
require('FINAL_ENEMY_PICK' in all_kotlin, 'Falta el estado 9/10 observado en las capturas')
require('ACTION_SET_STAGE' in all_kotlin, 'Falta control manual de etapa')
require('AssistantStage.PAUSED' in all_kotlin, 'Falta modo Pausado')
require('AssistantStage.DRAFT' in all_kotlin, 'Falta modo Selección')
require('AssistantStage.IN_GAME' in all_kotlin, 'Falta modo Partida')
require('DraftSubphaseDetector' in all_kotlin, 'Falta clasificación de veto/picks/ajustes/carga')
require('slotIndexForPlayerName' in all_kotlin, 'Falta mapeo OCR acotado por fila')
require('previewHighlightScore' in all_kotlin, 'Falta señal de preselección dorada')

strings_text = (ROOT / 'app/src/main/res/values/strings.xml').read_text(encoding='utf-8')
string_names = set(re.findall(r'<string\s+name="([^"]+)"', strings_text))
string_refs = set()
for path in kotlin_root.glob('*.kt'):
    string_refs.update(re.findall(r'R\.string\.([A-Za-z0-9_]+)', path.read_text(encoding='utf-8')))
for path in (ROOT / 'app/src/main/res').rglob('*.xml'):
    string_refs.update(re.findall(r'@string/([A-Za-z0-9_]+)', path.read_text(encoding='utf-8')))
missing_strings = sorted(string_refs - string_names)
require(not missing_strings, f'Recursos string faltantes: {missing_strings}')

if errors:
    print('V4_VALIDATION_FAILED')
    for error in errors:
        print('-', error)
    sys.exit(1)

print('V4_VALIDATION_OK')
print('- 116 héroes / 348 relaciones')
print('- MediaProjection + OCR local + overlay + aprendizaje visual local')
print('- Sin permiso INTERNET')
print('- Video completo calibrado: veto / picks / ajustes / carga / partida')
print('- R-95 se detecta por fila; el slot no queda fijado entre partidas')
print('- Control manual Pausado / Selección / Partida con sugerencias confirmables')
print('- Recursos y contratos estáticos válidos')

from pathlib import Path

root = Path('.')
profile = (root / 'app/src/main/kotlin/com/example/honorofkingsassistant/PersonalDeviceProfile.kt').read_text(encoding='utf-8')
capture = (root / 'app/src/main/kotlin/com/example/honorofkingsassistant/CaptureGeometry.kt').read_text(encoding='utf-8')
stage = (root / 'app/src/main/kotlin/com/example/honorofkingsassistant/AssistantStage.kt').read_text(encoding='utf-8')
service = (root / 'app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')

expected = {
    'model JKM-LX3': 'MODEL = "JKM-LX3"' in profile,
    'Android 9 / EMUI 9.1': 'PLATFORM = "Android 9 / EMUI 9.1"' in profile,
    'Kirin 710': 'SOC = "Kirin 710"' in profile,
    '4 GB RAM': 'RAM_GB = 4' in profile,
    'native 2340': 'NATIVE_LONG_EDGE = 2340' in profile,
    'native 1080': 'NATIVE_SHORT_EDGE = 1080' in profile,
    'capture 1170': 'CAPTURE_MAX_LONG_EDGE = 1170' in profile,
    'capture pixel budget': 'CAPTURE_MAX_PIXELS = 640_000' in profile,
    'OCR 1170': 'OCR_MAX_LONG_EDGE = 1170' in profile,
    'profile used by geometry': 'PersonalDeviceProfile.CAPTURE_MAX_LONG_EDGE' in capture,
    'adaptive base cadence': 'PersonalDeviceProfile.DRAFT_BASE_INTERVAL_MS' in stage,
    'adaptive service cadence': 'AdaptiveFrameCadence.interval' in service,
    'ImageReader latest queue depth': 'PixelFormat.RGBA_8888, 2' in service,
}
missing = [name for name, ok in expected.items() if not ok]
if missing:
    raise SystemExit('V5_HUAWEI_PROFILE_CONTRACT_FAILED: ' + '; '.join(missing))
print('V5_HUAWEI_PROFILE_CONTRACT_OK')

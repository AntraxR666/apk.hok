from pathlib import Path

manifest = Path('app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
overlay = Path('app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt').read_text(encoding='utf-8')
capture = Path('app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')

errors = []
if 'startForeground(' in overlay:
    errors.append('OverlayService must not be a second foreground service')
if 'startForegroundService(' in capture:
    errors.append('ScreenCaptureService must start OverlayService as a normal same-process service')
if 'startService(' not in capture or 'Intent(this, OverlayService::class.java)' not in capture:
    errors.append('OverlayService must be started by ScreenCaptureService.startService')
if overlay.count('createNotificationChannel') > 0:
    errors.append('OverlayService must not own a redundant notification channel')
if 'START_STICKY' in overlay:
    errors.append('OverlayService must not be sticky after the capture owner stops')
if 'START_STICKY' in capture:
    errors.append('MediaProjection service must not restart without fresh user consent')
if manifest.count('android:foregroundServiceType="mediaProjection"') != 1:
    errors.append('Exactly one mediaProjection foreground service must be declared')
if errors:
    raise SystemExit('V5_SINGLE_FGS_CONTRACT_FAILED: ' + '; '.join(errors))
print('V5_SINGLE_FGS_CONTRACT_OK')

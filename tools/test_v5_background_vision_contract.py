from pathlib import Path

engine = Path('app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt').read_text(encoding='utf-8')
service = Path('app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt').read_text(encoding='utf-8')

errors = []
if 'private val callbackExecutor: java.util.concurrent.Executor' not in engine:
    errors.append('DraftVisionEngine must receive a callback executor')
for listener in ('addOnSuccessListener(callbackExecutor)', 'addOnFailureListener(callbackExecutor)', 'addOnCompleteListener(callbackExecutor)'):
    if listener not in engine:
        errors.append(f'missing background listener: {listener}')
on_create = service.split('override fun onCreate()', 1)[-1].split('override fun onStartCommand', 1)[0]
if 'HandlerThread("HoKDraftCapture")' not in on_create:
    errors.append('vision HandlerThread must start during service creation')
start_projection = service.split('private fun startProjection', 1)[-1].split('private fun initialCaptureSourceSize', 1)[0]
if 'HandlerThread("HoKDraftCapture")' in start_projection:
    errors.append('startProjection must reuse the existing vision HandlerThread')
if 'java.util.concurrent.Executor' not in service:
    errors.append('ScreenCaptureService must wire the handler-backed executor')
if errors:
    raise SystemExit('V5_BACKGROUND_VISION_CONTRACT_FAILED: ' + '; '.join(errors))
print('V5_BACKGROUND_VISION_CONTRACT_OK')

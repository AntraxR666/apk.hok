from pathlib import Path

ps = Path('APLICAR_V4_Y_COMPILAR.ps1').read_bytes()
assert ps.startswith(b'\xef\xbb\xbf')
assert all(value < 128 for value in ps[3:])
text = ps[3:].decode('ascii')
for required in (
    'backup_v4_', 'robocopy', 'HoKBuildTools\\jdk17',
    'clean testDebugUnitTest assembleDebug', 'build-output-v4.txt',
    'app\\build\\outputs\\apk\\debug\\app-debug.apk',
    'HoK_Draft_Assistant_V4_3_RC1_VIDEO_CALIBRATED.apk', 'if ($process.ExitCode -ne 0)',
    'Require-File $apk "compiled APK"',
    'JUnit verification failed', 'APK is missing required entry',
):
    assert required in text, required
assert 'INTEGRATION COMPLETE - APK VERIFIED' not in text
bat = Path('APLICAR_V4_Y_COMPILAR.bat').read_text(encoding='ascii')
assert 'pause >nul' in bat
assert 'exit /b %RESULT%' in bat
print('V4_DELIVERY_CONTRACT_OK')

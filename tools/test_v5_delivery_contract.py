from pathlib import Path

ps_path = Path('PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.ps1')
bat_path = Path('PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.bat')
raw = ps_path.read_bytes()
assert raw.startswith(b'\xef\xbb\xbf'), 'PowerShell script must use UTF-8 BOM'
assert all(byte < 128 for byte in raw[3:]), 'PowerShell body must remain ASCII for Windows PowerShell 5.1'
text = raw[3:].decode('ascii')

required = (
    'Set-StrictMode -Version Latest',
    'AntraxR666/apk.hok',
    'git clone --branch main --single-branch',
    'CREATE CLEAN GITHUB WORKSPACE',
    'remote get-url origin',
    'config user.name',
    'config user.email',
    'tools\\validate_v5.py',
    'git diff --check',
    'release: prepare V5 Huawei JKM-LX3 personal candidate',
    'git push origin HEAD:main',
    'gh run watch',
    '--exit-status',
    'gh run download',
    'HoK-Draft-Assistant-V5-Huawei-JKM-LX3',
    'AndroidManifest.xml',
    'classes.dex',
    'assets/hok_counters.json',
    'V5 HUAWEI JKM-LX3 APK READY',
    'V5 HUAWEI PROCESS STOPPED - NO VALID APK',
    'Start-Transcript',
    'Temporary workspace kept for diagnosis',
    'exit $ExitCode',
)
for value in required:
    assert value in text, value

for forbidden in (
    'Resolve-Project',
    'Is-TargetRepo',
    'Documents\\HoK_Counter_App',
    'HoK_Draft_Assistant_V4_3_RC1_PUSH_TO_APK_HOK_EMAIL_FIX',
    'git reset --hard origin/main',
):
    assert forbidden not in text, forbidden

assert text.index('V5 HUAWEI JKM-LX3 APK READY') > text.index('if ($WatchExit -ne 0 -or $Conclusion -ne "success")')
assert text.index('V5 HUAWEI JKM-LX3 APK READY') > text.index('if ($null -eq $Apk)')

stack = []
pairs = {')': '(', '}': '{', ']': '['}
in_single = False
in_double = False
escaped = False
for line in text.splitlines():
    i = 0
    while i < len(line):
        ch = line[i]
        if in_single:
            if ch == "'":
                if i + 1 < len(line) and line[i + 1] == "'":
                    i += 2
                    continue
                in_single = False
            i += 1
            continue
        if in_double:
            if escaped:
                escaped = False
                i += 1
                continue
            if ch == '`':
                escaped = True
                i += 1
                continue
            if ch == '"':
                in_double = False
            i += 1
            continue
        if ch == '#':
            break
        if ch == "'":
            in_single = True
        elif ch == '"':
            in_double = True
        elif ch in '({[':
            stack.append(ch)
        elif ch in ')}]':
            assert stack and stack.pop() == pairs[ch], f'unbalanced {ch}'
        i += 1
    assert not in_single, 'single-quoted string crosses a line unexpectedly'
    assert not in_double, 'double-quoted string crosses a line unexpectedly'
assert not stack, f'unclosed delimiters: {stack}'

bat = bat_path.read_text(encoding='ascii')
for value in (
    'PREPARAR_V5_HUAWEI_JKM_LX3_Y_SUBIR.ps1',
    'powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT%"',
    'CLEAN CLONE DELIVERY V3',
    'Do not run this file as Administrator.',
    'pause >nul',
    'exit /b %RESULT%',
):
    assert value in bat, value

assert 'System.Management.Automation.Language.Parser' not in bat
assert '^|' not in bat
assert '-Command' not in bat

print('V5_DELIVERY_CONTRACT_OK')

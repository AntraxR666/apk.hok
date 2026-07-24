from pathlib import Path

raw = Path('APLICAR_V4_Y_COMPILAR.ps1').read_bytes()
assert raw.startswith(b'\xef\xbb\xbf')
assert all(byte < 128 for byte in raw[3:])
text = raw[3:].decode('ascii')
assert text.count('{') == text.count('}')
assert text.count('(') == text.count(')')
assert 'Start-Process' in text
assert 'ExitCode -ne 0' in text
assert 'exit 0' in text and 'exit 1' in text
print('V4_POWERSHELL_SHAPE_OK')

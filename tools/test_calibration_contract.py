#!/usr/bin/env python3
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
report = json.loads((ROOT / 'docs/user_screenshot_calibration.json').read_text(encoding='utf-8'))
assert len(report) == 3
assert [item['mode'] for item in report].count('DRAFT') == 2
assert [item['mode'] for item in report].count('IN_GAME') == 1
for item in [entry for entry in report if entry['mode'] == 'DRAFT']:
    assert item['width'] == 1600 and item['height'] == 738
    assert item['left'] == ['CONFIRMED'] * 5
    assert item['right'][:4] == ['CONFIRMED'] * 4
    assert item['right'][4] == 'PREVIEWING'
print('USER_CALIBRATION_CONTRACT_OK')

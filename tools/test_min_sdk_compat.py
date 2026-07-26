"""Reject Java Map APIs that would exclude the declared Android 6 minimum."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sources = ROOT / "app/src/main/kotlin/com/example/honorofkingsassistant"
offenders = []
for source in sources.glob("*.kt"):
    if "putIfAbsent(" in source.read_text(encoding="utf-8"):
        offenders.append(source.name)

assert not offenders, f"putIfAbsent requires Android API 24: {', '.join(offenders)}"
print("MIN_SDK_COMPAT_CONTRACT_OK")

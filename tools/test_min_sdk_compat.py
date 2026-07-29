"""Reject Java Map APIs that would exclude the declared Android 6 minimum."""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sources = ROOT / "app/src/main/kotlin/com/example/honorofkingsassistant"
offenders = []
for source in sources.glob("*.kt"):
    text = source.read_text(encoding="utf-8")
    forbidden = []
    if "putIfAbsent(" in text:
        forbidden.append("putIfAbsent")
    if source.name == "QuickCorrectionPolicy.kt" and ".getOrDefault(" in text:
        forbidden.append("Map.getOrDefault")
    if forbidden:
        offenders.append(f"{source.name} ({', '.join(forbidden)})")

assert not offenders, (
    "Java Map APIs require Android API 24 and violate minSdk 23: "
    + ", ".join(offenders)
)
print("MIN_SDK_COMPAT_CONTRACT_OK")

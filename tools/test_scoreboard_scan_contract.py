from pathlib import Path

root = Path(__file__).resolve().parents[1]
overlay = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt").read_text(encoding="utf-8")
capture = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt").read_text(encoding="utf-8")

assert "ACTION_SCAN_SCOREBOARD" in overlay
assert "ACTION_SCAN_SCOREBOARD" in capture
assert "ScoreboardScanCoordinator" in capture
assert "claimFrame" in capture
assert "analyzeScoreboard" in capture
assert "forceNextFrame = true" in capture
print("SCOREBOARD_SCAN_CONTRACT_OK")

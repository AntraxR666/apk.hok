from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt").read_text(encoding="utf-8")

required = [
    "import android.widget.ScrollView",
    "ScrollView(this)",
    "isFillViewport = true",
    "maxOverlayPanelHeight()",
    "attachDrag(bubble",
    "ACTION_SET_PLAYER_SLOT",
    "ACTION_SET_PLAYER_PICK_OVERRIDE",
    "playerPickButton",
]
for token in required:
    assert token in source, f"Missing overlay behavior: {token}"

assert "attachDrag(root" not in source, "Dragging the full root steals scroll gestures"
print("OVERLAY_SCROLL_CONTRACT_OK")

from pathlib import Path

root = Path(__file__).resolve().parents[1]
overlay = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt").read_text(encoding="utf-8")
service = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt").read_text(encoding="utf-8")
layout = (root / "app/src/main/res/layout/activity_main.xml").read_text(encoding="utf-8")

for token in (
    "PlayerPickOverride.AUTO",
    "PlayerPickOverride.PENDING",
    "PlayerPickOverride.LOCKED",
    "sendPlayerPickOverrideAction",
):
    assert token in overlay, token

assert "ACTION_SET_PLAYER_PICK_OVERRIDE" in service
assert "PlayerPickStatePolicy.apply" in service
assert "@+id/playerPickSpinner" in layout
print("PLAYER_PICK_OVERRIDE_CONTRACT_OK")

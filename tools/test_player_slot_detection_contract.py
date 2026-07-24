from pathlib import Path

root = Path(__file__).resolve().parents[1]
vision = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/DraftVisionEngine.kt").read_text(encoding="utf-8")
service = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt").read_text(encoding="utf-8")

assert "normalizedLine.contains(target)" not in vision
assert "side != TeamSide.ALLY" in vision
assert "slotIndexForPlayerName" in vision
assert "PlayerSlotResolver" in service
assert "ACTION_SET_PLAYER_SLOT" in service
assert "ACTION_SET_PLAYER_PICK_OVERRIDE" in service
assert "PlayerPickStatePolicy.apply" in service
assert "lastPlayerSlot = result.playerSlot ?: lastPlayerSlot" not in service
print("PLAYER_SLOT_DETECTION_CONTRACT_OK")

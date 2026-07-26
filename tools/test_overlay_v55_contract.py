from pathlib import Path

root = Path(__file__).resolve().parents[1]
overlay = (root / "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt").read_text(
    encoding="utf-8"
)
capture = (
    root / "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt"
).read_text(encoding="utf-8")
strings = (root / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")

required_overlay = [
    "COLLAPSED_ALPHA = 0.55f",
    "EXPANDED_ALPHA = 0.82f",
    "MAX_PANEL_HEIGHT_RATIO = 0.72f",
    "DRAG_HANDLE_HEIGHT_DP = 40",
    "ViewConfiguration.get(this).scaledTouchSlop",
    "override fun onConfigurationChanged",
    "attachDrag(dragHandle",
    "showManualEditor",
    "showHeroPicker",
    "InputMode.AUTO_SCAN",
    "InputMode.MANUAL",
    "MatchMode.NORMAL_BLIND",
    "MatchMode.RANKED_DRAFT",
    "ACTION_CONFIRM_LOADING_ROSTER",
    "ACTION_SCAN_SCOREBOARD",
]
for token in required_overlay:
    assert token in overlay, f"Missing V1 overlay behavior: {token}"

for label in [
    "Confirmar equipos antes de partida",
    "Verificar equipos e ítems",
    "Editar equipo",
    "Quitar héroe",
]:
    assert label in strings, f"Missing user-visible label: {label}"

assert "attachDrag(scrollPanel" not in overlay
assert "attachDrag(panelContent" not in overlay
assert "startActivity(" not in overlay, "Manual editing must stay over the game"
assert "AssistantSessionBus.publish(" not in overlay, "Views must send service intents"

required_capture = [
    "ACTION_SET_INPUT_MODE",
    "ACTION_SET_MATCH_MODE",
    "ACTION_MANUAL_ASSIGN_SLOT",
    "ACTION_MANUAL_REMOVE_SLOT",
    "ACTION_CONFIRM_LOADING_ROSTER",
    "ACTION_SCAN_SCOREBOARD",
    "EXTRA_MANUAL_SLOT_INDEX",
]
for token in required_capture:
    assert token in capture, f"Missing capture-service command: {token}"

assert "PortraitTemplateDomain.LOADING_CARD_PORTRAIT" in capture
print("OVERLAY_V55_CONTRACT_OK")

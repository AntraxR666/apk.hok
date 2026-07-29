from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
overlay = (
    ROOT
    / "app/src/main/kotlin/com/example/honorofkingsassistant/OverlayService.kt"
).read_text(encoding="utf-8")
service = (
    ROOT
    / "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt"
).read_text(encoding="utf-8")
strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")


required_overlay_tokens = (
    "renderSlotRecognition(state)",
    "showQuickCorrection(request)",
    "QuickCorrectionPolicy.hasActionableProblem",
    "MIN_TOUCH_TARGET_DP = 48",
    "advancedControls.visibility = if (show) View.VISIBLE else View.GONE",
    "showHeroPicker(request.slot, teachLoading = false)",
)
for token in required_overlay_tokens:
    assert token in overlay, f"missing contextual overlay behavior: {token}"

required_service_tokens = (
    "EXPLICIT_SCAN_FRAME_BUDGET = 5",
    "updateExplicitScanCorrection()",
    "QuickCorrectionPolicy.applyManualAuthority",
    "quickCorrectionRequest = request",
)
for token in required_service_tokens:
    assert token in service, f"missing scan/correction orchestration: {token}"

for resource in (
    'name="scan_draft_now"',
    'name="slot_recognition_title"',
    'name="quick_correction_no_candidate"',
    'name="search_another_hero"',
):
    assert resource in strings, f"missing user-facing correction copy: {resource}"

print("CONTEXTUAL_CORRECTION_CONTRACT_OK")

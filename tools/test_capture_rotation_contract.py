"""Contract for portrait-to-landscape capture resizing on Android 9+."""

from pathlib import Path


source = Path(
    "app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt"
).read_text(encoding="utf-8")

for marker in (
    "import android.content.res.Configuration",
    "override fun onConfigurationChanged(newConfig: Configuration)",
    "refreshCaptureGeometryFromWindow()",
    "private fun refreshCaptureGeometryFromWindow()",
    "val (sourceWidth, sourceHeight) = initialCaptureSourceSize()",
    "resizeCaptureSurface(sourceWidth, sourceHeight)",
):
    assert marker in source, marker

# Entering draft must also repair geometry if EMUI missed a configuration callback.
set_stage_start = source.index("private fun setStage(stage: AssistantStage)")
set_stage_end = source.index("private fun resetForNewDraftSession()", set_stage_start)
set_stage = source[set_stage_start:set_stage_end]
assert set_stage.index("refreshCaptureGeometryFromWindow()") < set_stage.index(
    "updateCaptureSurfaceForStage()"
)

print("CAPTURE_ROTATION_CONTRACT_OK")

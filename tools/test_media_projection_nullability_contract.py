from pathlib import Path

source = Path("app/src/main/kotlin/com/example/honorofkingsassistant/ScreenCaptureService.kt").read_text(encoding="utf-8")

required = [
    "val projection = requireNotNull(",
    "manager.getMediaProjection(resultCode, resultData)",
    "projection.registerCallback(object : MediaProjection.Callback()",
    "mediaProjection = projection",
    "virtualDisplay = projection.createVirtualDisplay(",
]
for marker in required:
    if marker not in source:
        raise SystemExit(f"Missing non-null MediaProjection contract marker: {marker}")

forbidden = [
    "getMediaProjection(resultCode, resultData).also { projection ->",
    "virtualDisplay = requireNotNull(mediaProjection).createVirtualDisplay(",
]
for marker in forbidden:
    if marker in source:
        raise SystemExit(f"Unsafe nullable MediaProjection pattern remains: {marker}")

print("MEDIA_PROJECTION_NULLABILITY_CONTRACT_OK")

import com.example.honorofkingsassistant.CaptureGeometry
import com.example.honorofkingsassistant.PersonalCaptureProfile

fun main() {
    val native = CaptureGeometry.fit(2340, 1080)
    check(native.width == 1170 && native.height == 540)
    check(native.pixelCount <= PersonalCaptureProfile.MAX_PIXELS)

    val recording = CaptureGeometry.fit(848, 392)
    check(recording.width == 848 && recording.height == 392)

    val oversized = CaptureGeometry.fit(2400, 1080)
    check(oversized.longEdge <= PersonalCaptureProfile.MAX_LONG_EDGE)
    check(oversized.pixelCount <= PersonalCaptureProfile.MAX_PIXELS)

    check(runCatching { CaptureGeometry.fit(0, 1080) }.isFailure)
    println("CAPTURE_GEOMETRY_HUAWEI_SMOKE_OK")
}

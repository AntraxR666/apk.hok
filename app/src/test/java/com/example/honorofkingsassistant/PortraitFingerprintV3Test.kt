package com.example.honorofkingsassistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitFingerprintV3Test {
    @Test
    fun v3RoundTripsWhileOlderVersionsStillDecode() {
        val original = PortraitFingerprint.fromArgb144(verticalEdgeFixture())
        val decoded = requireNotNull(PortraitFingerprint.decode(original.encode()))

        assertEquals(3, original.version)
        assertEquals(3, decoded.version)
        assertEquals(original.averageHash, decoded.averageHash)
        assertEquals(original.gradientHash, decoded.gradientHash)
        assertArrayEquals(original.edgeSignature, decoded.edgeSignature)
        assertArrayEquals(original.colorSignature, decoded.colorSignature)
        assertArrayEquals(original.spatialLuminanceSignature, decoded.spatialLuminanceSignature)
        assertNotNull(
            PortraitFingerprint.decode(
                "v2:0000000000000001:0000000000000002:" +
                    List(32) { "1" }.joinToString(",") + ":" +
                    List(12) { "2" }.joinToString(",")
            )
        )
        assertNotNull(
            PortraitFingerprint.decode(
                "0000000000000001:1,1,1,1,1,1,1,1,1,1,1,1"
            )
        )
    }

    @Test
    fun edgeEvidenceSeparatesLayoutsWithTheSameAverageColors() {
        val vertical = PortraitFingerprint.fromArgb144(verticalEdgeFixture())
        val horizontal = PortraitFingerprint.fromArgb144(horizontalEdgeFixture())

        assertTrue(vertical.distance(horizontal) > 0.20)
    }

    @Test
    fun spatialEvidenceIsStableAcrossBrightnessChanges() {
        val original = PortraitFingerprint.fromArgb144(patternFixture(brightness = 0))
        val brighter = PortraitFingerprint.fromArgb144(patternFixture(brightness = 35))
        val inverted = PortraitFingerprint.fromArgb144(patternFixture(brightness = 0).reversedArray())

        assertTrue(original.distance(brighter) < original.distance(inverted))
        assertTrue(original.distance(brighter) < 0.08)
    }

    private fun verticalEdgeFixture(): IntArray = IntArray(144) { index ->
        val x = index % 12
        val value = if (x < 6) 32 else 224
        argb(value, value, value)
    }

    private fun horizontalEdgeFixture(): IntArray = IntArray(144) { index ->
        val y = index / 12
        val value = if (y < 6) 32 else 224
        argb(value, value, value)
    }

    private fun patternFixture(brightness: Int): IntArray = IntArray(144) { index ->
        val x = index % 12
        val y = index / 12
        val base = (x * 13 + y * 7 + if ((x + y) % 3 == 0) 35 else 0)
            .coerceIn(0, 220)
        val value = (base + brightness).coerceIn(0, 255)
        argb(value, value, value)
    }

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}

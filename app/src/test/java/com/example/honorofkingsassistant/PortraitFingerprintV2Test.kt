package com.example.honorofkingsassistant

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortraitFingerprintV2Test {
    @Test
    fun v2RoundTripsWhileVersionOneStillDecodes() {
        val original = PortraitFingerprint.fromArgb144(verticalEdgeFixture())
        val decoded = requireNotNull(PortraitFingerprint.decode(original.encode()))

        assertEquals(2, original.version)
        assertEquals(2, decoded.version)
        assertEquals(original.averageHash, decoded.averageHash)
        assertEquals(original.gradientHash, decoded.gradientHash)
        assertArrayEquals(original.edgeSignature, decoded.edgeSignature)
        assertArrayEquals(original.colorSignature, decoded.colorSignature)
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

    private fun argb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}

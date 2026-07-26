package com.example.honorofkingsassistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreboardGeometryTest {
    @Test fun screenshotGeometryCreatesFiveRowsPerSide() {
        val geometry = ScoreboardGeometry.forFrame(1560, 738)
        assertEquals(5, geometry.allies.size)
        assertEquals(5, geometry.enemies.size)
        assertTrue(geometry.allies.zipWithNext().all { (a, b) -> a.bounds.bottom <= b.bounds.top })
        assertTrue(geometry.enemies.all { it.bounds.left > 780 })
        assertTrue((geometry.allies + geometry.enemies).all { it.items.size == 6 })
    }
}

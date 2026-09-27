package com.planruler.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScaleCheckTest {
    @Test
    fun `iso sheets are recognised in either orientation`() {
        assertEquals("A1", sheetSize(2384.0, 1684.0).isoName)
        assertEquals("A3", sheetSize(842.0, 1191.0).isoName)
        assertNull(sheetSize(1000.0, 1000.0).isoName)
    }

    @Test
    fun `a 1 to 50 A1 plan printed on A3 reads as roughly 1 to 100`() {
        // 5 m at 1:50 is 100 mm of paper; shrunk from A1 to A3 it becomes ~50 mm (141.7 pt).
        val shrunkPoints = 100.0 / 2.0 / (25.4 / 72.0)
        assertEquals(100.0, effectivePdfScale(shrunkPoints, 5.0)!!, 0.01)
        assertEquals(50.0, effectivePdfScale(100.0 / (25.4 / 72.0), 5.0)!!, 0.01)
    }
}

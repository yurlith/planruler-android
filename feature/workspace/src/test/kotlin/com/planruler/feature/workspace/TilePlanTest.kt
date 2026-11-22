package com.planruler.feature.workspace

import com.planruler.model.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TilePlanTest {
    // An A1 sheet in PDF points.
    private val pageWidth = 2384.0
    private val pageHeight = 1684.0

    @Test
    fun `visible area is rendered at the exact display scale and fully covered`() {
        val viewport = ViewportState(zoom = 3.0, centerX = 1200.0, centerY = 800.0)
        val plan = viewportTilePlan(viewport, 1080, 2200, pageWidth, pageHeight)!!
        assertEquals(3.0, plan.scale, 1e-9)
        // Screen 1080x2200 px at zoom 3 shows 360x733 pt; with the margin every screen pixel is covered.
        val left = plan.cells.minOf { it.left }
        val right = plan.cells.maxOf { it.right }
        val top = plan.cells.minOf { it.top }
        val bottom = plan.cells.maxOf { it.bottom }
        assertTrue(left <= 1200.0 - 180.0 && right >= 1200.0 + 180.0)
        assertTrue(top <= 800.0 - 366.0 && bottom >= 800.0 + 366.0)
        plan.cells.forEach { cell ->
            assertTrue((cell.right - cell.left) * plan.scale <= 1024.0 + 1e-6)
            assertTrue((cell.bottom - cell.top) * plan.scale <= 1024.0 + 1e-6)
        }
        assertTrue(plan.cells.size <= 24)
    }

    @Test
    fun `huge zoom keeps the cell count bounded by lowering the scale`() {
        val plan = viewportTilePlan(
            ViewportState(zoom = 3.0, centerX = 1200.0, centerY = 800.0),
            4000, 4000, pageWidth, pageHeight, maxCells = 6,
        )!!
        assertTrue(plan.cells.size <= 6)
        assertTrue(plan.scale < 3.0)
    }

    @Test
    fun `off page viewport renders nothing`() {
        assertNull(viewportTilePlan(ViewportState(2.0, -5000.0, -5000.0), 1080, 2200, pageWidth, pageHeight))
    }
}

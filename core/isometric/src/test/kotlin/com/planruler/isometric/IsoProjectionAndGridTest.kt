package com.planruler.isometric

import com.planruler.model.IsoProjectionStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.sqrt

class IsoProjectionAndGridTest {
    private val iso = IsoProjection.of(IsoProjectionStyle.ISOMETRIC)
    private val frontal = IsoProjection.of(IsoProjectionStyle.FRONTAL)

    private fun angle(p: P2) = Math.toDegrees(atan2(p.y, p.x)).let { if (it < 0) it + 360 else it }

    @Test
    fun `isometric axes are true length at 30, 150 and 90 degrees`() {
        assertEquals(30.0, angle(iso.project(Vec3.X)), 1e-9)
        assertEquals(150.0, angle(iso.project(Vec3.Y)), 1e-9)
        assertEquals(90.0, angle(iso.project(Vec3.Z)), 1e-9)
        listOf(Vec3.X, Vec3.Y, Vec3.Z).forEach { assertEquals(1.0, iso.project(it).length, 1e-12) }
    }

    @Test
    fun `frontal axes keep X horizontal and Y receding at 45 degrees`() {
        assertEquals(0.0, angle(frontal.project(Vec3.X)), 1e-9)
        assertEquals(45.0, angle(frontal.project(Vec3.Y)), 1e-9)
        assertEquals(90.0, angle(frontal.project(Vec3.Z)), 1e-9)
    }

    @Test
    fun `only unambiguous offsets are offered as stroke directions`() {
        // Six axes plus the six plane diagonals that do not project onto an axis.
        assertEquals(12, iso.runDirections.size)
        assertEquals(6, iso.runDirections.count { it.axis != null })
        // Frontal: X+Z and -X-Z coincide with the Y axis on paper and are dropped.
        assertEquals(16, frontal.runDirections.size)
    }

    @Test
    fun `a stroke along a projected axis becomes a run of true length`() {
        val stroke = iso.project(Vec3.X) * 1_250.0 + P2(3.0, -2.0)
        val match = requireNotNull(iso.matchRun(stroke))
        assertEquals(IsoAxis.EAST, match.run.axis)
        assertEquals(1_250.0, match.modelLengthMm, 5.0)
        assertTrue(match.deviationDeg < 1.0)

        val down = requireNotNull(iso.matchRun(P2(0.0, -800.0)))
        assertEquals(IsoAxis.DOWN, down.run.axis)
        assertEquals(800.0, down.modelLengthMm, 1e-9)
    }

    @Test
    fun `a horizontal stroke on the isometric sheet is the south-east floor offset`() {
        val match = requireNotNull(iso.matchRun(P2(1_000.0, 0.0), allowOffsets = true))
        assertNull(match.run.axis)
        val expected = Vec3(1.0, -1.0, 0.0).normalized()
        assertEquals(1.0, match.run.direction.dot(expected), 1e-12)
        // A 45° floor offset is drawn longer than it is: |projection| = cos 30° * sqrt 2.
        assertEquals(1_000.0 / (sqrt(3.0) / 2.0 * sqrt(2.0)), match.modelLengthMm, 1e-6)
        assertEquals(IsoAxis.EAST, iso.matchRun(P2(1_000.0, 200.0), allowOffsets = false)?.run?.axis)
    }

    @Test
    fun `a point unprojected on a plane projects back to the tap`() {
        val target = P2(123.0, -45.0)
        listOf(IsoPlane.HORIZONTAL, IsoPlane.FRONT, IsoPlane.SIDE).forEach { plane ->
            val model = requireNotNull(iso.unprojectOnPlane(target, Vec3(10.0, 20.0, 30.0), plane))
            assertEquals(target.x, iso.project(model).x, 1e-9)
            assertEquals(target.y, iso.project(model).y, 1e-9)
        }
    }

    @Test
    fun `grid step coarsens in 1-2-5 steps when zooming out but never refines below the base`() {
        assertEquals(GridSpec(100.0, 10), IsoGrid.spec(100.0, pxPerMm = 1.0))
        assertEquals(GridSpec(100.0, 10), IsoGrid.spec(100.0, pxPerMm = 50.0))
        assertEquals(GridSpec(500.0, 2), IsoGrid.spec(100.0, pxPerMm = 0.05))
        assertEquals(GridSpec(1_000.0, 10), IsoGrid.spec(100.0, pxPerMm = 0.02))
    }

    @Test
    fun `isometric paper has three line families clipped to the view`() {
        val bounds = Rect2(-1_000.0, -1_000.0, 1_000.0, 1_000.0)
        val lines = IsoGrid.lines(iso, bounds, GridSpec(100.0, 10))
        assertTrue(lines.isNotEmpty())
        val directions = lines.map { line ->
            val d = (line.end - line.start).normalized()
            Math.round(angle(if (d.y < 0 || (d.y == 0.0 && d.x < 0)) -d else d))
        }.toSet()
        assertEquals(setOf(30L, 90L, 150L), directions)
        lines.forEach { line ->
            assertTrue(bounds.expanded(1e-6).contains(line.start))
            assertTrue(bounds.expanded(1e-6).contains(line.end))
        }
        assertTrue(lines.any { it.major } && lines.any { !it.major })
    }

    @Test
    fun `isometric lattice nodes are projections of the model grid`() {
        val model = Vec3(300.0, 200.0, 0.0)
        val tap = iso.project(model) + P2(6.0, -4.0)
        val node = IsoGrid.nearestNode(iso, tap, 100.0)
        assertEquals(iso.project(model).x, node.x, 1e-9)
        assertEquals(iso.project(model).y, node.y, 1e-9)
        val onFloor = requireNotNull(IsoGrid.modelPointOnBase(iso, tap, 100.0, levelMm = 0.0))
        assertEquals(model, onFloor)
    }

    @Test
    fun `frontal taps land on the front wall`() {
        val point = requireNotNull(IsoGrid.modelPointOnBase(frontal, P2(310.0, 490.0), 100.0, levelMm = 0.0))
        assertEquals(Vec3(300.0, 0.0, 500.0), point)
        assertEquals(P2(300.0, 500.0), IsoGrid.nearestNode(frontal, P2(310.0, 490.0), 100.0))
    }

    @Test
    fun `isometric floor taps keep the working level`() {
        val point = requireNotNull(IsoGrid.modelPointOnBase(iso, iso.project(Vec3(500.0, 0.0, 900.0)), 100.0, levelMm = 900.0))
        assertEquals(Vec3(500.0, 0.0, 900.0), point)
        assertNotNull(IsoGrid.families(frontal).firstOrNull())
    }
}

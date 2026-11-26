package com.planruler.feature.pipecalculator

import kotlin.math.hypot
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SceneLabelLayout3DTest {
    private fun line(x0: Float, y0: Float, x1: Float, y1: Float, textWidth: Float = 120f) =
        SceneDimensionLine(x0, y0, x1, y1, textWidth = textWidth, textHeight = 16f)

    private fun SceneDimensionLine.segment() = SceneSegment(startX, startY, endX, endY)

    @Test
    fun `a value with nothing in the way sits above the middle of its line`() {
        val box = layoutSceneDimensionLabels(listOf(line(40f, 200f, 360f, 200f)), emptyList(), emptyList(), 400f, 300f).single()

        assertNotNull(box)
        assertEquals(200f, box!!.centerX, 0.01f)
        assertTrue("above the line", box.bottom <= 200f)
    }

    @Test
    fun `a part marker on the middle of the line moves the value along it`() {
        val dimension = line(40f, 200f, 360f, 200f)
        val marker = SceneMarker(200f, 186f, 18f)

        val box = layoutSceneDimensionLabels(listOf(dimension), listOf(marker), emptyList(), 400f, 300f).single()

        assertNotNull(box)
        assertFalse("covers the marker", box!!.overlaps(marker))
        assertFalse("sits on its own line", box.crosses(dimension.segment()))
        assertTrue("stays beside its line", box.centerX in 40f..360f)
    }

    @Test
    fun `two values whose lines cross never cover each other or the other line`() {
        val run = line(50f, 150f, 350f, 150f, textWidth = 140f)
        val side = line(200f, 20f, 200f, 280f, textWidth = 100f)

        val boxes = layoutSceneDimensionLabels(listOf(run, side), emptyList(), emptyList(), 400f, 300f)

        val first = requireNotNull(boxes[0])
        val second = requireNotNull(boxes[1])
        assertFalse(first.overlaps(second))
        assertFalse(first.crosses(side.segment()))
        assertFalse(second.crosses(run.segment()))
    }

    @Test
    fun `a value along the top edge goes below its line rather than off the canvas`() {
        val box = layoutSceneDimensionLabels(listOf(line(20f, 6f, 380f, 6f, textWidth = 100f)), emptyList(), emptyList(), 400f, 300f).single()

        assertNotNull(box)
        assertTrue(box!!.inside(400f, 300f))
        assertTrue(box.top >= 6f)
    }

    @Test
    fun `a short line at the edge has its value slid onto the canvas, clear of the line`() {
        val dimension = line(2f, 150f, 30f, 150f, textWidth = 120f)

        val box = layoutSceneDimensionLabels(listOf(dimension), emptyList(), emptyList(), 400f, 300f).single()

        assertNotNull(box)
        assertTrue(box!!.inside(400f, 300f))
        assertFalse(box.crosses(dimension.segment()))
    }

    @Test
    fun `a value keeps off the route when there is room beside it`() {
        val dimension = line(40f, 200f, 360f, 200f)
        // The route runs just above the line along its whole length.
        val route = listOf(SceneSegment(20f, 190f, 380f, 190f))

        val box = layoutSceneDimensionLabels(listOf(dimension), emptyList(), route, 400f, 300f).single()

        assertNotNull(box)
        assertFalse(box!!.crosses(route.single()))
        assertTrue("moved to the free side", box.top >= 200f)
    }

    @Test
    fun `a line seen end on still gets its value above it`() {
        val box = layoutSceneDimensionLabels(listOf(line(200f, 150f, 200f, 150f, textWidth = 60f)), emptyList(), emptyList(), 400f, 300f).single()

        assertNotNull(box)
        assertEquals(200f, box!!.centerX, 0.01f)
        assertTrue(box.bottom <= 150f)
    }

    @Test
    fun `in random scenes no value is ever written over another`() {
        val random = Random(7)
        var written = 0
        var asked = 0
        repeat(2_000) {
            val width = 360f
            val height = 320f
            val dimensions = List(3) {
                var x0: Float
                var y0: Float
                var x1: Float
                var y1: Float
                do {
                    x0 = random.nextFloat() * width
                    y0 = random.nextFloat() * height
                    x1 = random.nextFloat() * width
                    y1 = random.nextFloat() * height
                } while (hypot(x1 - x0, y1 - y0) < 140f)
                SceneDimensionLine(x0, y0, x1, y1, textWidth = 70f + random.nextFloat() * 60f, textHeight = 16f)
            }
            val markers = List(7) { SceneMarker(random.nextFloat() * width, random.nextFloat() * height, 18f) }
            val route = markers.zipWithNext { a, b -> SceneSegment(a.x, a.y, b.x, b.y) }

            val boxes = layoutSceneDimensionLabels(dimensions, markers, route, width, height)

            assertNotNull("the first value always has a place", boxes.first())
            val shown = boxes.filterNotNull()
            shown.forEachIndexed { index, box ->
                shown.drop(index + 1).forEach { other -> assertFalse("$box covers $other", box.overlaps(other)) }
            }
            written += shown.size
            asked += boxes.size
        }
        assertTrue("almost every value is written ($written of $asked)", written >= asked * 0.99)
    }
}

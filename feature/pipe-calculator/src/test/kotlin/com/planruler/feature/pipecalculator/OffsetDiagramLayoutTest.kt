package com.planruler.feature.pipecalculator

import com.planruler.pipecalculator.PipeOffsetInput
import com.planruler.pipecalculator.PipeOffsetResult
import com.planruler.pipecalculator.pipeOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class OffsetDiagramLayoutTest {
    private data class Screen(val width: Float, val height: Float, val unit: Float)

    private val screens = listOf(
        Screen(990f, 660f, 2.75f), // phone, 360 × 240 dp
        Screen(760f, 520f, 2.0f),
        Screen(1700f, 700f, 2.0f), // tablet landscape
    )

    private fun layout(result: PipeOffsetResult, screen: Screen, od: Double? = 60.3): OffsetDiagram {
        val labelHeight = 13f * screen.unit * 1.17f
        fun mm(value: Double) = String.format(Locale.GERMANY, "%.1f", value)
        return layoutOffsetDiagram(
            result = result,
            pipeOutsideDiameterMm = od,
            widthPx = screen.width,
            heightPx = screen.height,
            unitPx = screen.unit,
            labelHeightPx = labelHeight,
            textWidth = { it.length * labelHeight * 0.56f },
            texts = OffsetDiagramTexts(
                cut = "C = ${mm(result.cutLengthMm)}",
                offset = "H = ${mm(result.offsetMm)}",
                travel = "L = ${mm(result.travelMm)}",
                advance = "X = ${mm(result.advanceMm)}",
                angle = "${angleText(Math.round(result.angleDeg * 10) / 10.0)}°",
            ),
        )
    }

    private fun assertClean(diagram: OffsetDiagram, screen: Screen, context: String) {
        val segments = buildList {
            listOf(diagram.runIn, diagram.sloped, diagram.runOut).forEach { add(it to diagram.pipeWidthPx / 2f) }
            diagram.dimensions.forEach { dimension ->
                add(dimension.line to 1.5f * screen.unit)
                dimension.extensions.forEach { add(it to 1f * screen.unit) }
            }
        }
        diagram.labels.forEach { label ->
            assertTrue("$context: ${label.kind} outside", label.left >= 0f && label.top >= 0f)
            assertTrue("$context: ${label.kind} outside", label.right <= screen.width && label.bottom <= screen.height)
            segments.forEach { (segment, half) ->
                assertFalse("$context: ${label.kind} covers a line", segmentHitsBox(segment, label, half))
            }
        }
        diagram.labels.forEachIndexed { i, a ->
            diagram.labels.drop(i + 1).forEach { b ->
                assertFalse("$context: ${a.kind} overlaps ${b.kind}", a.overlaps(b, 0f))
            }
        }
    }

    @Test
    fun `values never overlap each other or the sketch`() {
        var cases = 0
        for (screen in screens) {
            for (offset in listOf(25.0, 60.0, 150.0, 500.0, 1_500.0, 6_000.0)) {
                for (angle in listOf(5.0, 15.0, 22.5, 30.0, 45.0, 60.0, 80.0, 90.0)) {
                    for (takeout in listOf(0.0, 31.5, 152.5, 457.0)) {
                        val result = pipeOffset(PipeOffsetInput(offset, 0.0, angle, takeout, 2.0))
                        assertClean(layout(result, screen), screen, "H=$offset α=$angle A=$takeout $screen")
                        cases++
                    }
                }
            }
        }
        assertEquals(3 * 6 * 8 * 4, cases)
    }

    @Test
    fun `the offset and the cut length are always written`() {
        for (screen in screens) {
            for (offset in listOf(150.0, 500.0, 1_500.0)) {
                for (angle in listOf(15.0, 22.5, 30.0, 45.0, 60.0, 90.0)) {
                    val result = pipeOffset(PipeOffsetInput(offset, 0.0, angle, 31.5, 2.0))
                    val diagram = layout(result, screen)
                    val kinds = diagram.labels.map { it.kind }.toSet()
                    val context = "H=$offset α=$angle $screen"
                    assertNotNull(context, diagram.cut)
                    assertTrue("$context: $kinds", DiagramLabelKind.OFFSET in kinds)
                    assertTrue("$context: $kinds", DiagramLabelKind.CUT in kinds)
                    if (angle < 90.0) {
                        assertTrue("$context: $kinds", DiagramLabelKind.TRAVEL in kinds)
                        assertTrue("$context: $kinds", DiagramLabelKind.ADVANCE in kinds)
                    }
                }
            }
        }
    }

    @Test
    fun `the sketch is to scale`() {
        val result = pipeOffset(PipeOffsetInput(400.0, 0.0, 30.0, 0.0, 0.0))
        val diagram = layout(result, screens.first())
        val dx = diagram.sloped.b.x - diagram.sloped.a.x
        val dy = diagram.sloped.a.y - diagram.sloped.b.y
        assertEquals(result.advanceMm / result.offsetMm, (dx / dy).toDouble(), 1e-3)
    }

    @Test
    fun `elbows that do not fit leave no piece to cut`() {
        val result = pipeOffset(PipeOffsetInput(40.0, 0.0, 45.0, 31.5, 2.0))
        val diagram = layout(result, screens.first())
        assertEquals(null, diagram.cut)
        assertFalse(diagram.labels.any { it.kind == DiagramLabelKind.CUT })
    }
}

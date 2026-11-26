package com.planruler.feature.workspace

import com.planruler.model.MeasurementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class PlanLabelLayoutTest {
    private val metrics = PlanLabelMetrics(
        valueTextPx = 36f,
        segmentTextPx = 30f,
        riseTextPx = 29f,
        lineHeight = { it * 1.17f },
        textWidth = { text, px -> text.length * px * 0.55f },
        paddingPx = 12f,
        gapPx = 8f,
        handleRadiusPx = 22f,
        lineHalfWidthPx = 4f,
    )
    private val width = 1080f
    private val height = 1900f

    private fun p(x: Float, y: Float) = LabelPoint(x, y)

    private fun distance(key: String, a: LabelPoint, b: LabelPoint, value: String = "1.25 m") =
        LabelGeometry(key, MeasurementType.DISTANCE, listOf(a, b), value)

    /** The guarantees: no value over another, over a measured point, or off the screen. */
    private fun assertClean(geometry: List<LabelGeometry>, labels: List<PlacedPlanLabel>) {
        labels.forEach { label ->
            assertTrue("$label is off screen", label.left >= 0f && label.top >= 0f && label.right <= width && label.bottom <= height)
            geometry.forEach { m ->
                m.points.forEach { point ->
                    val r = metrics.handleRadiusPx
                    val covers = point.x > label.left - r && point.x < label.right + r && point.y > label.top - r && point.y < label.bottom + r
                    assertFalse("$label covers a point of ${m.key}", covers)
                }
            }
        }
        labels.forEachIndexed { i, a ->
            labels.drop(i + 1).forEach { b -> assertFalse("$a overlaps $b", a.overlaps(b, 0f)) }
        }
        // Segment lengths and riser heights never sit on a line; only a total may, as a last resort.
        labels.filter { it.kind != PlanLabelKind.VALUE }.forEach { label ->
            geometry.forEach { m ->
                val closing = if (m.type == MeasurementType.AREA && m.points.size > 2) listOf(m.points.last() to m.points.first()) else emptyList()
                (m.points.zipWithNext() + closing).forEach { (a, b) ->
                    val h = metrics.lineHalfWidthPx
                    assertFalse(
                        "$label lies on a line of ${m.key}",
                        segmentTouchesBox(a.x, a.y, b.x, b.y, label.left - h, label.top - h, label.right + h, label.bottom + h),
                    )
                }
            }
        }
    }

    @Test
    fun `a ruler's value sits beside its middle, above a level line`() {
        val geometry = listOf(distance("a", p(200f, 900f), p(800f, 900f)))
        val labels = layoutPlanLabels(geometry, width, height, metrics)
        val value = labels.single()
        assertEquals(PlanLabelKind.VALUE, value.kind)
        assertEquals(500f, value.left + value.width / 2f, 1f)
        assertTrue("value should be above the line", value.bottom < 900f)
        assertClean(geometry, labels)
    }

    @Test
    fun `chained rulers sharing ends keep every value apart`() {
        val corners = listOf(p(150f, 300f), p(650f, 300f), p(650f, 800f), p(300f, 800f), p(300f, 1300f), p(900f, 1300f))
        val geometry = corners.zipWithNext().mapIndexed { i, (a, b) -> distance("d$i", a, b, "${i + 1}.000 m") }
        val labels = layoutPlanLabels(geometry, width, height, metrics)
        assertEquals(geometry.size, labels.count { it.kind == PlanLabelKind.VALUE })
        assertClean(geometry, labels)
        labels.forEach { assertEquals("a roomy plan needs no leaders", null, it.leaderFrom) }
    }

    @Test
    fun `parallel rulers close together do not stack their values`() {
        val geometry = (0 until 6).map { i -> distance("p$i", p(150f, 600f + i * 40f), p(950f, 600f + i * 40f), "${4 + i}.250 m") }
        val labels = layoutPlanLabels(geometry, width, height, metrics)
        assertClean(geometry, labels)
        assertEquals(6, labels.count { it.kind == PlanLabelKind.VALUE })
    }

    @Test
    fun `segment lengths sit on their own segments and short ones are left out`() {
        val polyline = LabelGeometry(
            key = "run",
            type = MeasurementType.POLYLINE,
            points = listOf(p(100f, 1000f), p(700f, 1000f), p(700f, 1030f), p(700f, 400f)),
            value = "13.62 m",
            segments = listOf("6.00 m", "0.30 m", "6.30 m"),
        )
        val labels = layoutPlanLabels(listOf(polyline), width, height, metrics)
        assertClean(listOf(polyline), labels)
        val segments = labels.filter { it.kind == PlanLabelKind.SEGMENT }.map { it.index }.toSet()
        assertEquals(setOf(0, 2), segments)
        assertNotNull(labels.firstOrNull { it.kind == PlanLabelKind.VALUE })
    }

    @Test
    fun `the selected measurement and the one being drawn win a crowded spot`() {
        val crowd = (0 until 40).map { i ->
            val y = 700f + (i % 8) * 18f
            distance("c$i", p(100f + (i / 8) * 30f, y), p(1000f - (i / 8) * 30f, y + 6f), "${10 + i}.00 m")
        }
        val selected = distance("sel", p(120f, 760f), p(980f, 770f), "7.77 m").copy(selected = true)
        val draft = distance("draft", p(130f, 740f), p(990f, 745f), "3.33 m").copy(draft = true)
        val geometry = crowd + selected + draft
        val labels = layoutPlanLabels(geometry, width, height, metrics)
        assertClean(geometry, labels)
        assertTrue(labels.any { it.key == "sel" && it.kind == PlanLabelKind.VALUE })
        assertTrue(labels.any { it.key == "draft" && it.kind == PlanLabelKind.VALUE })
    }

    @Test
    fun `random dense plans never produce overlapping values`() {
        val random = Random(42)
        repeat(30) { round ->
            val geometry = (0 until 120).map { i ->
                val type = MeasurementType.entries[random.nextInt(MeasurementType.entries.size)]
                val count = when (type) {
                    MeasurementType.DISTANCE -> 2
                    MeasurementType.ANGLE -> 3
                    MeasurementType.COUNTER, MeasurementType.ANNOTATION -> 1
                    else -> 2 + random.nextInt(4)
                }
                val start = p(random.nextFloat() * width, random.nextFloat() * height)
                val points = (0 until count).runningFold(start) { last, _ ->
                    p((last.x + random.nextFloat() * 500f - 250f), (last.y + random.nextFloat() * 500f - 250f))
                }.take(count)
                LabelGeometry(
                    key = "r$round-$i",
                    type = type,
                    points = points,
                    value = "${random.nextInt(1, 999)}.${random.nextInt(10, 99)} m",
                    segments = if (type == MeasurementType.POLYLINE && count > 2) points.zipWithNext().map { (a, b) -> "%.2f m".format(hypot(b.x - a.x, b.y - a.y) / 100f) } else emptyList(),
                    rises = if (type == MeasurementType.POLYLINE && random.nextBoolean()) listOf(1 to "↑ 300 mm") else emptyList(),
                    selected = i == 7,
                )
            }
            val labels = layoutPlanLabels(geometry, width, height, metrics)
            assertClean(geometry, labels)
        }
    }

    @Test
    fun `a riser height is written under its mark, or beside it when a pipe runs below`() {
        val straight = LabelGeometry(
            key = "riser",
            type = MeasurementType.POLYLINE,
            points = listOf(p(200f, 500f), p(600f, 500f), p(1000f, 500f)),
            value = "",
            rises = listOf(1 to "↑ 1200 mm"),
        )
        val under = layoutPlanLabels(listOf(straight), width, height, metrics).single { it.kind == PlanLabelKind.RISE }
        assertTrue(under.top > 500f)
        assertEquals(600f, under.left + under.width / 2f, 1f)

        val corner = straight.copy(points = listOf(p(200f, 500f), p(600f, 500f), p(600f, 1100f)))
        val labels = layoutPlanLabels(listOf(corner), width, height, metrics)
        assertNotNull(labels.singleOrNull { it.kind == PlanLabelKind.RISE })
        assertClean(listOf(corner), labels)
    }

    @Test
    fun `an area's value goes to its middle`() {
        val room = LabelGeometry(
            key = "room",
            type = MeasurementType.AREA,
            points = listOf(p(200f, 400f), p(900f, 400f), p(900f, 1200f), p(200f, 1200f)),
            value = "18.40 m²",
        )
        val label = layoutPlanLabels(listOf(room), width, height, metrics).single()
        assertEquals(550f, label.left + label.width / 2f, 1f)
        assertEquals(800f, label.top + label.height / 2f, 1f)
    }

    @Test
    fun `hundreds of measurements lay out quickly`() {
        val random = Random(7)
        val geometry = (0 until 600).map { i ->
            val a = p(random.nextFloat() * width, random.nextFloat() * height)
            distance("q$i", a, p(a.x + random.nextFloat() * 300f, a.y + random.nextFloat() * 300f))
        }
        layoutPlanLabels(geometry, width, height, metrics) // warm-up
        val started = System.nanoTime()
        val labels = layoutPlanLabels(geometry, width, height, metrics)
        val millis = (System.nanoTime() - started) / 1_000_000
        assertTrue("layout took $millis ms", millis < 250)
        assertClean(geometry, labels)
    }
}

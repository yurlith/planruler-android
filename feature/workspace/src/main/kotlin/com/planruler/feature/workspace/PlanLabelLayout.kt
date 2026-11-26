package com.planruler.feature.workspace

import com.planruler.model.MeasurementType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A screen point in canvas pixels. */
internal data class LabelPoint(val x: Float, val y: Float)

internal enum class PlanLabelKind { VALUE, SEGMENT, RISE }

/** What one measurement wants written on the plan, already in screen space. */
internal data class LabelGeometry(
    val key: String,
    val type: MeasurementType,
    val points: List<LabelPoint>,
    val value: String,
    val segments: List<String> = emptyList(),
    /** Vertex index and text of each riser or drop mark. */
    val rises: List<Pair<Int, String>> = emptyList(),
    val selected: Boolean = false,
    val draft: Boolean = false,
)

internal class PlanLabelMetrics(
    val valueTextPx: Float,
    val segmentTextPx: Float,
    val riseTextPx: Float,
    /** Line height of a label at the given text size, padding excluded. */
    val lineHeight: (Float) -> Float,
    val textWidth: (String, Float) -> Float,
    val paddingPx: Float,
    val gapPx: Float,
    val handleRadiusPx: Float,
    val lineHalfWidthPx: Float,
)

internal data class PlacedPlanLabel(
    val key: String,
    val kind: PlanLabelKind,
    val index: Int,
    val text: String,
    val textPx: Float,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    /** Where a leader line starts when the label had to stand off its anchor. */
    val leaderFrom: LabelPoint? = null,
) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
    fun overlaps(other: PlacedPlanLabel, gap: Float): Boolean =
        left < other.right + gap && other.left < right + gap && top < other.bottom + gap && other.top < bottom + gap
}

/**
 * Places measurement values on the plan so that no value covers another value or any
 * measured point, and none covers a measured line unless a total has no other place.
 * Lengths of single segments sit beside their own segment and are dropped when there is no
 * room; totals stay, beside their measurement or on a short leader.
 *
 * Order decides who gets the good spots: the selected measurement, then the one being
 * drawn, then totals, risers and segment lengths of the rest, newest first.
 */
internal fun layoutPlanLabels(
    measurements: List<LabelGeometry>,
    width: Float,
    height: Float,
    metrics: PlanLabelMetrics,
): List<PlacedPlanLabel> {
    if (width <= 0f || height <= 0f) return emptyList()
    val index = LabelIndex(width, height, cell = 56f)
    val margin = 64f
    fun onScreen(p: LabelPoint) = p.x >= -margin && p.y >= -margin && p.x <= width + margin && p.y <= height + margin

    measurements.forEach { m ->
        m.points.forEach { if (onScreen(it)) index.addPoint(it, metrics.handleRadiusPx) }
        m.points.zipWithNext().forEach { (a, b) -> index.addLine(a, b, metrics.lineHalfWidthPx) }
        if (m.type == MeasurementType.AREA && m.points.size > 2) {
            index.addLine(m.points.last(), m.points.first(), metrics.lineHalfWidthPx)
        }
    }

    val placed = ArrayList<PlacedPlanLabel>()
    val ordered = measurements.withIndex().sortedWith(
        compareBy<IndexedValue<LabelGeometry>>({ if (it.value.selected) 0 else if (it.value.draft) 1 else 2 }, { -it.index }),
    ).map { it.value }

    fun tryPlace(
        m: LabelGeometry,
        kind: PlanLabelKind,
        index2: Int,
        text: String,
        textPx: Float,
        candidates: List<Candidate>,
        allowLines: Boolean,
    ): Boolean {
        val w = metrics.textWidth(text, textPx) + 2 * metrics.paddingPx
        val h = metrics.lineHeight(textPx) + 2 * metrics.paddingPx
        val passes = if (allowLines) listOf(false, true) else listOf(false)
        for (crossLines in passes) {
            for (candidate in candidates) {
                val box = candidate.box(w, h)
                val left = box.first
                val top = box.second
                if (left < 0f || top < 0f || left + w > width || top + h > height) continue
                if (!index.isFree(left, top, w, h, metrics.gapPx, crossLines)) continue
                val label = PlacedPlanLabel(m.key, kind, index2, text, textPx, left, top, w, h, candidate.leaderFrom)
                placed += label
                index.addLabel(label)
                return true
            }
        }
        return false
    }

    // Totals first, so a crowded plan loses segment lengths before it loses any total.
    ordered.forEach { m ->
        if (m.value.isBlank() || m.points.isEmpty()) return@forEach
        tryPlace(
            m, PlanLabelKind.VALUE, 0, m.value, metrics.valueTextPx,
            valueCandidates(m, metrics),
            allowLines = true,
        )
    }
    ordered.forEach { m ->
        m.rises.forEach { (vertex, text) ->
            val anchor = m.points.getOrNull(vertex) ?: return@forEach
            if (!onScreen(anchor)) return@forEach
            tryPlace(m, PlanLabelKind.RISE, vertex, text, metrics.riseTextPx, ring(anchor, metrics, riseOrder), allowLines = false)
        }
    }
    ordered.forEach { m ->
        m.segments.forEachIndexed { i, text ->
            val a = m.points.getOrNull(i) ?: return@forEachIndexed
            val b = m.points.getOrNull(i + 1) ?: return@forEachIndexed
            if (text.isBlank()) return@forEachIndexed
            val w = metrics.textWidth(text, metrics.segmentTextPx) + 2 * metrics.paddingPx
            // A length longer than its own segment reads as belonging to the neighbour.
            if (hypot(b.x - a.x, b.y - a.y) < max(w * 0.8f, 4 * metrics.handleRadiusPx)) return@forEachIndexed
            tryPlace(m, PlanLabelKind.SEGMENT, i, text, metrics.segmentTextPx, besideSegment(a, b, metrics), allowLines = false)
        }
    }
    return placed
}

/** A candidate label position: centred on [cx], [cy], optionally with a leader to its anchor. */
internal class Candidate(private val cx: Float, private val cy: Float, val leaderFrom: LabelPoint? = null, private val dir: LabelPoint? = null, private val reach: Float = 0f) {
    /** Top-left of a [w] × [h] box; a directional candidate keeps its near edge [reach] from the anchor. */
    fun box(w: Float, h: Float): Pair<Float, Float> {
        val d = dir ?: return Pair(cx - w / 2f, cy - h / 2f)
        val extent = abs(d.x) * w / 2f + abs(d.y) * h / 2f
        val x = cx + d.x * (reach + extent)
        val y = cy + d.y * (reach + extent)
        return Pair(x - w / 2f, y - h / 2f)
    }
}

private val SQRT_HALF = 0.70710677f

/** Up-right first: where values have always appeared, so the plan still reads the same. */
private val valueOrder = listOf(
    LabelPoint(SQRT_HALF, -SQRT_HALF), LabelPoint(1f, 0f), LabelPoint(SQRT_HALF, SQRT_HALF), LabelPoint(0f, -1f),
    LabelPoint(0f, 1f), LabelPoint(-SQRT_HALF, -SQRT_HALF), LabelPoint(-1f, 0f), LabelPoint(-SQRT_HALF, SQRT_HALF),
)

/** Below first: a riser mark sits on the line, its height reads under it. */
private val riseOrder = listOf(
    LabelPoint(0f, 1f), LabelPoint(SQRT_HALF, SQRT_HALF), LabelPoint(-SQRT_HALF, SQRT_HALF), LabelPoint(1f, 0f),
    LabelPoint(-1f, 0f), LabelPoint(SQRT_HALF, -SQRT_HALF), LabelPoint(-SQRT_HALF, -SQRT_HALF), LabelPoint(0f, -1f),
)

private fun ring(anchor: LabelPoint, metrics: PlanLabelMetrics, order: List<LabelPoint>, rings: Int = 1): List<Candidate> = buildList {
    // One pixel beyond the clearance, so a label never merely touches what it must clear.
    val first = metrics.handleRadiusPx + metrics.gapPx + 1f
    for (r in 0 until rings) {
        val reach = first + r * (metrics.lineHeight(metrics.valueTextPx) + 2 * metrics.paddingPx) * 1.2f
        order.forEach { d -> add(Candidate(anchor.x, anchor.y, leaderFrom = if (r == 0) null else anchor, dir = d, reach = reach)) }
    }
}

private fun besideSegment(a: LabelPoint, b: LabelPoint, metrics: PlanLabelMetrics, extraSteps: Int = 1): List<Candidate> {
    val length = hypot(b.x - a.x, b.y - a.y)
    if (length < 1e-3f) return emptyList()
    // Screen y grows downwards: prefer the side above a line, or left of a vertical one.
    var nx = (a.y - b.y) / length
    var ny = (b.x - a.x) / length
    if (ny > 0f || (ny == 0f && nx > 0f)) { nx = -nx; ny = -ny }
    return buildList {
        for (step in 0 until extraSteps) {
            val reach = metrics.lineHalfWidthPx + metrics.gapPx + 1f + step * metrics.lineHeight(metrics.valueTextPx)
            listOf(0.5f, 0.35f, 0.65f).forEach { t ->
                val cx = a.x + (b.x - a.x) * t
                val cy = a.y + (b.y - a.y) * t
                add(Candidate(cx, cy, dir = LabelPoint(nx, ny), reach = reach))
                add(Candidate(cx, cy, dir = LabelPoint(-nx, -ny), reach = reach))
            }
        }
    }
}

private fun valueCandidates(m: LabelGeometry, metrics: PlanLabelMetrics): List<Candidate> {
    val points = m.points
    return when (m.type) {
        MeasurementType.DISTANCE ->
            if (points.size >= 2) {
                besideSegment(points[0], points[1], metrics, extraSteps = 2) +
                    ring(points.last(), metrics, valueOrder, rings = 3) + ring(points.first(), metrics, valueOrder, rings = 3)
            } else {
                ring(points.last(), metrics, valueOrder, rings = 3)
            }
        MeasurementType.AREA -> {
            val c = centroid(points)
            listOf(Candidate(c.x, c.y)) + ring(c, metrics, valueOrder, rings = 3)
        }
        MeasurementType.ANGLE -> ring(points.getOrElse(1) { points.last() }, metrics, valueOrder, rings = 3)
        MeasurementType.ANNOTATION, MeasurementType.COUNTER -> ring(points.first(), metrics, valueOrder, rings = 3)
        MeasurementType.POLYLINE -> ring(points.last(), metrics, valueOrder, rings = 3) + ring(points.first(), metrics, valueOrder, rings = 2)
    }
}

private fun centroid(points: List<LabelPoint>): LabelPoint {
    if (points.size < 3) return LabelPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    var area = 0.0
    var cx = 0.0
    var cy = 0.0
    points.indices.forEach { i ->
        val p = points[i]
        val q = points[(i + 1) % points.size]
        val cross = p.x.toDouble() * q.y - q.x.toDouble() * p.y
        area += cross
        cx += (p.x + q.x) * cross
        cy += (p.y + q.y) * cross
    }
    if (abs(area) < 1e-6) return LabelPoint(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
    return LabelPoint((cx / (3 * area)).toFloat(), (cy / (3 * area)).toFloat())
}

/** A coarse grid over the screen holding points, lines and placed labels for quick lookups. */
private class LabelIndex(private val width: Float, private val height: Float, private val cell: Float) {
    private val columns = max(1, (width / cell).toInt() + 1)
    private val rows = max(1, (height / cell).toInt() + 1)
    private val points = arrayOfNulls<ArrayList<FloatArray>>(columns * rows)
    private val lines = arrayOfNulls<ArrayList<FloatArray>>(columns * rows)
    private val labels = arrayOfNulls<ArrayList<PlacedPlanLabel>>(columns * rows)

    private fun col(x: Float) = floor(x / cell).toInt().coerceIn(0, columns - 1)
    private fun row(y: Float) = floor(y / cell).toInt().coerceIn(0, rows - 1)

    private inline fun <T> each(left: Float, top: Float, right: Float, bottom: Float, grid: Array<ArrayList<T>?>, action: (T) -> Boolean): Boolean {
        if (right < 0f || bottom < 0f || left > width || top > height) return true
        for (r in row(top)..row(bottom)) for (c in col(left)..col(right)) {
            grid[r * columns + c]?.forEach { if (!action(it)) return false }
        }
        return true
    }

    private fun <T> add(left: Float, top: Float, right: Float, bottom: Float, grid: Array<ArrayList<T>?>, item: T) {
        if (right < 0f || bottom < 0f || left > width || top > height) return
        for (r in row(top)..row(bottom)) for (c in col(left)..col(right)) {
            val i = r * columns + c
            (grid[i] ?: ArrayList<T>().also { grid[i] = it }).add(item)
        }
    }

    fun addPoint(p: LabelPoint, radius: Float) =
        add(p.x - radius, p.y - radius, p.x + radius, p.y + radius, points, floatArrayOf(p.x, p.y, radius))

    fun addLine(a: LabelPoint, b: LabelPoint, halfWidth: Float) {
        // Clip to the screen first so a long line does not fill every cell it never shows in.
        val clipped = clip(a.x, a.y, b.x, b.y) ?: return
        val (x1, y1, x2, y2) = clipped
        add(min(x1, x2) - halfWidth, min(y1, y2) - halfWidth, max(x1, x2) + halfWidth, max(y1, y2) + halfWidth, lines, floatArrayOf(x1, y1, x2, y2, halfWidth))
    }

    fun addLabel(label: PlacedPlanLabel) = add(label.left, label.top, label.right, label.bottom, labels, label)

    fun isFree(left: Float, top: Float, w: Float, h: Float, gap: Float, crossLines: Boolean): Boolean {
        val right = left + w
        val bottom = top + h
        val labelsFree = each(left - gap, top - gap, right + gap, bottom + gap, labels) { other ->
            !(left < other.right + gap && other.left < right + gap && top < other.bottom + gap && other.top < bottom + gap)
        }
        if (!labelsFree) return false
        val pointsFree = each(left - gap, top - gap, right + gap, bottom + gap, points) { p ->
            val r = p[2] + gap
            !(p[0] > left - r && p[0] < right + r && p[1] > top - r && p[1] < bottom + r)
        }
        if (!pointsFree) return false
        if (crossLines) return true
        return each(left - gap, top - gap, right + gap, bottom + gap, lines) { l ->
            !segmentTouchesBox(l[0], l[1], l[2], l[3], left - l[4] - gap, top - l[4] - gap, right + l[4] + gap, bottom + l[4] + gap)
        }
    }

    private fun clip(ax: Float, ay: Float, bx: Float, by: Float): FloatArray? {
        val pad = 8f
        var t0 = 0f
        var t1 = 1f
        val dx = bx - ax
        val dy = by - ay
        val checks = floatArrayOf(-dx, ax + pad, dx, width + pad - ax, -dy, ay + pad, dy, height + pad - ay)
        var i = 0
        while (i < 8) {
            val p = checks[i]
            val q = checks[i + 1]
            if (p == 0f) {
                if (q < 0f) return null
            } else {
                val r = q / p
                if (p < 0f) { if (r > t1) return null; if (r > t0) t0 = r } else { if (r < t0) return null; if (r < t1) t1 = r }
            }
            i += 2
        }
        if (t0 > t1) return null
        return floatArrayOf(ax + dx * t0, ay + dy * t0, ax + dx * t1, ay + dy * t1)
    }
}

/** Liang–Barsky: true when the segment reaches into the box. */
internal fun segmentTouchesBox(ax: Float, ay: Float, bx: Float, by: Float, left: Float, top: Float, right: Float, bottom: Float): Boolean {
    var t0 = 0f
    var t1 = 1f
    val dx = bx - ax
    val dy = by - ay
    val checks = floatArrayOf(-dx, ax - left, dx, right - ax, -dy, ay - top, dy, bottom - ay)
    var i = 0
    while (i < 8) {
        val p = checks[i]
        val q = checks[i + 1]
        if (p == 0f) {
            if (q < 0f) return false
        } else {
            val r = q / p
            if (p < 0f) { if (r > t1) return false; if (r > t0) t0 = r } else { if (r < t0) return false; if (r < t1) t1 = r }
        }
        i += 2
    }
    return t0 <= t1
}


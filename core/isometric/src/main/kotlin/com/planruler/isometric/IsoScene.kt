package com.planruler.isometric

import com.planruler.model.IsoComponent
import com.planruler.model.IsoComponentId
import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoNode
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipe
import com.planruler.model.IsoPipeId
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsometricSheet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

enum class IsoWeight { THICK, MEDIUM, THIN }

enum class IsoRole { PIPE, SYMBOL, FITTING, DIMENSION, ELEVATION, TAG, LABEL, PREVIEW }

enum class IsoTextAlign { START, CENTER, END }

data class IsoStroke(
    val points: List<P2>,
    val colorArgb: Long,
    val weight: IsoWeight,
    val role: IsoRole,
    val dashed: Boolean = false,
    val closed: Boolean = false,
    val filled: Boolean = false,
    val pipeId: IsoPipeId? = null,
    val nodeId: IsoNodeId? = null,
    val componentId: IsoComponentId? = null,
)

/**
 * Text anchored at the vertical middle of the line at [align]. [angleDeg] is counter-clockwise
 * in drawing space (Y up) and already turned so the text reads left to right.
 */
data class IsoText(
    val text: String,
    val anchor: P2,
    val angleDeg: Double,
    val heightMm: Double,
    val colorArgb: Long,
    val role: IsoRole,
    val align: IsoTextAlign = IsoTextAlign.CENTER,
    val pipeId: IsoPipeId? = null,
    val nodeId: IsoNodeId? = null,
)

data class IsoDot(
    val center: P2,
    val radiusMm: Double,
    val colorArgb: Long,
    val filled: Boolean,
    val role: IsoRole,
    val nodeId: IsoNodeId? = null,
)

/** Renderer-neutral drawing of a sheet in drawing millimetres (Y up). */
data class IsoScene(
    val strokes: List<IsoStroke>,
    val texts: List<IsoText>,
    val dots: List<IsoDot>,
    val bounds: Rect2?,
)

/** Annotation sizes in drawing millimetres. Screens keep them constant in pixels, paper in mm. */
data class IsoSceneStyle(
    val textHeightMm: Double,
    val dimensionOffsetMm: Double,
    val tickMm: Double,
    val symbolMm: Double,
    val inkArgb: Long = 0xFF1F2937,
    val selectionArgb: Long = 0xFF2563EB,
    val previewArgb: Long = 0xFF0EA5E9,
) {
    companion object {
        /** Sizes that stay the same number of pixels at [pxPerMm] (screen pixels per drawing mm). */
        fun forScreen(pxPerMm: Double, textPx: Double, scale: Double = 1.0): IsoSceneStyle {
            val unit = textPx / pxPerMm.coerceAtLeast(1e-9)
            return IsoSceneStyle(
                textHeightMm = unit * scale,
                dimensionOffsetMm = unit * 2.4 * scale,
                tickMm = unit * 0.7 * scale,
                symbolMm = unit * 1.6 * scale,
            )
        }

        /** A printed sheet at 1:[scale] with 2.5 mm text (ISO 3098 / GOST 2.304 size 2.5). */
        fun forPaper(scale: Double): IsoSceneStyle = IsoSceneStyle(
            textHeightMm = 2.5 * scale,
            dimensionOffsetMm = 6.0 * scale,
            tickMm = 1.8 * scale,
            symbolMm = 4.0 * scale,
        )
    }
}

/** Localised wording of the drawing; the engine never chooses a language. */
interface IsoLabeler {
    fun length(mm: Double): String
    fun elevation(mm: Double): String
    fun pipeTag(spec: IsoPipeSpec): String?
}

/** Language-neutral defaults: millimetres, metres for levels, DN sizes. */
object DefaultIsoLabeler : IsoLabeler {
    override fun length(mm: Double): String = String.format(Locale.US, "%.0f", mm)
    override fun elevation(mm: Double): String =
        (if (mm >= 0) "+" else "−") + String.format(Locale.US, "%.3f", abs(mm) / 1_000.0)
    override fun pipeTag(spec: IsoPipeSpec): String? = spec.nominalDiameter?.let { "DN$it" }
}

data class IsoSelection(
    val pipeIds: Set<IsoPipeId> = emptySet(),
    val nodeIds: Set<IsoNodeId> = emptySet(),
    val componentIds: Set<IsoComponentId> = emptySet(),
)

/** Line colour and type of each service; dashed lines follow the usual return/circulation convention. */
object IsoSystemStyle {
    fun colorOf(spec: IsoPipeSpec): Long = spec.colorArgb ?: when (spec.system) {
        IsoPipeSystem.HEATING_SUPPLY -> 0xFFC62828
        IsoPipeSystem.HEATING_RETURN -> 0xFF283593
        IsoPipeSystem.COLD_WATER -> 0xFF1565C0
        IsoPipeSystem.HOT_WATER -> 0xFFE64A19
        IsoPipeSystem.HOT_WATER_CIRCULATION -> 0xFFAD1457
        IsoPipeSystem.SEWER -> 0xFF6D4C41
        IsoPipeSystem.GAS -> 0xFFF9A825
        IsoPipeSystem.OTHER -> 0xFF37474F
    }

    fun dashed(system: IsoPipeSystem) =
        system == IsoPipeSystem.HEATING_RETURN || system == IsoPipeSystem.HOT_WATER_CIRCULATION
}

object IsoSceneBuilder {
    fun build(
        sheet: IsometricSheet,
        style: IsoSceneStyle,
        labeler: IsoLabeler = DefaultIsoLabeler,
        selection: IsoSelection = IsoSelection(),
        preview: RunPlan? = null,
    ): IsoScene {
        val projection = IsoProjection.of(sheet.projection)
        val network = IsoNetwork(sheet)
        val strokes = mutableListOf<IsoStroke>()
        val texts = mutableListOf<IsoText>()
        val dots = mutableListOf<IsoDot>()
        val projected = sheet.nodes.associate { it.id to projection.project(it.position) }
        val centroid = projected.values.takeIf { it.isNotEmpty() }?.let { points ->
            P2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
        } ?: P2.ZERO

        sheet.pipes.forEach { pipe ->
            val a = projected[pipe.from] ?: return@forEach
            val b = projected[pipe.to] ?: return@forEach
            strokes += IsoStroke(
                points = listOf(a, b),
                colorArgb = if (pipe.id in selection.pipeIds) style.selectionArgb else IsoSystemStyle.colorOf(pipe.spec),
                weight = IsoWeight.THICK,
                role = IsoRole.PIPE,
                dashed = IsoSystemStyle.dashed(pipe.spec.system),
                pipeId = pipe.id,
            )
        }
        // Annotations avoid the runs and each other: every placed dimension becomes an obstacle.
        val obstacles = strokes.map { it.points[0] to it.points[1] }.toMutableList()
        sheet.pipes.forEach { pipe ->
            val a = projected[pipe.from] ?: return@forEach
            val b = projected[pipe.to] ?: return@forEach
            val side = annotationSide(network, projection, pipe, a, b, projected, centroid, obstacles, style)
            if (sheet.showDimensions) {
                dimension(network, pipe, a, b, side, style, labeler, pipe.id in selection.pipeIds, strokes, texts)
                    ?.let { obstacles += it }
            }
            if (sheet.showSystemCodes) {
                val tag = listOfNotNull(labeler.pipeTag(pipe.spec), pipe.label).joinToString(" ").takeIf(String::isNotBlank)
                if (tag != null) tag(network, pipe, tag, a, b, side, style, texts)
            }
        }

        network.fittings().forEach { fitting ->
            val p = projected[fitting.nodeId] ?: return@forEach
            when (fitting.kind) {
                IsoFittingKind.TEE, IsoFittingKind.CROSS, IsoFittingKind.MANIFOLD ->
                    dots += IsoDot(p, style.symbolMm * 0.22, style.inkArgb, filled = true, role = IsoRole.FITTING, nodeId = fitting.nodeId)
                IsoFittingKind.END ->
                    dots += IsoDot(p, style.symbolMm * 0.18, style.inkArgb, filled = false, role = IsoRole.FITTING, nodeId = fitting.nodeId)
                IsoFittingKind.REDUCER -> reducer(network, projection, fitting, p, style, strokes)
                IsoFittingKind.ELBOW, IsoFittingKind.COUPLING -> Unit
            }
        }

        sheet.components.forEach { component -> component(network, projection, component, style, selection, strokes, dots, texts) }

        if (sheet.showElevations) {
            // Level marks also keep clear of words and symbols already on the sheet.
            val occupied = obstacles +
                texts.flatMap { text -> textBox(text).let { box -> box.zip(box.drop(1) + box.first()) } } +
                strokes.filter { it.role == IsoRole.SYMBOL || it.role == IsoRole.FITTING }.flatMap { it.points.zipWithNext() }
            elevations(network, sheet, projected, style, labeler, occupied, strokes, texts)
        }

        sheet.nodes.forEach { node ->
            val p = projected[node.id] ?: return@forEach
            node.label?.let { label ->
                texts += IsoText(
                    label,
                    p + P2(style.textHeightMm * 0.6, -style.textHeightMm * 1.1),
                    0.0,
                    style.textHeightMm,
                    style.inkArgb,
                    IsoRole.LABEL,
                    IsoTextAlign.START,
                    nodeId = node.id,
                )
            }
            if (node.id in selection.nodeIds) {
                dots += IsoDot(p, style.symbolMm * 0.45, style.selectionArgb, filled = false, role = IsoRole.FITTING, nodeId = node.id)
            }
        }

        preview?.let { plan ->
            val a = projection.project(plan.start)
            val b = projection.project(plan.end)
            strokes += IsoStroke(listOf(a, b), style.previewArgb, IsoWeight.THICK, IsoRole.PREVIEW, dashed = true)
            val d = (b - a).normalized()
            texts += IsoText(
                labeler.length(plan.lengthMm),
                (a + b) / 2.0 + d.perpendicular() * (style.textHeightMm * 1.1),
                readableAngle(d),
                style.textHeightMm * 1.15,
                style.previewArgb,
                IsoRole.PREVIEW,
            )
            dots += IsoDot(b, style.symbolMm * 0.25, style.previewArgb, filled = true, role = IsoRole.PREVIEW)
        }

        val bounds = Rect2.around(
            strokes.flatMap { it.points } +
                dots.flatMap { listOf(it.center - P2(it.radiusMm, it.radiusMm), it.center + P2(it.radiusMm, it.radiusMm)) } +
                texts.flatMap { textBox(it) },
        )
        return IsoScene(strokes, texts, dots, bounds)
    }

    /**
     * Side of a run that receives its dimension: the extension follows a model axis (the iso
     * drafting convention) and the side with more free paper wins; on a tie the side facing
     * away from the neighbouring runs is used.
     */
    private fun annotationSide(
        network: IsoNetwork,
        projection: IsoProjection,
        pipe: IsoPipe,
        a: P2,
        b: P2,
        projected: Map<IsoNodeId, P2>,
        centroid: P2,
        obstacles: List<Pair<P2, P2>>,
        style: IsoSceneStyle,
    ): Offset {
        val d = (b - a).normalized()
        val from = network.position(pipe.from)
        val to = network.position(pipe.to)
        val horizontal = from != null && to != null && abs(to.z - from.z) < 1.0
        val preferred = if (horizontal) listOf(IsoAxis.UP, IsoAxis.EAST, IsoAxis.NORTH) else listOf(IsoAxis.EAST, IsoAxis.NORTH, IsoAxis.UP)
        val base = preferred
            .map { projection.screenOf(it.unit).normalized() }
            .firstOrNull { abs(d.cross(it)) > 0.35 }
            ?: d.perpendicular()
        val neighbours = (network.incident[pipe.from].orEmpty() + network.incident[pipe.to].orEmpty())
            .filter { it.id != pipe.id }
            .flatMap { listOfNotNull(projected[it.from], projected[it.to]) }
        val reference = if (neighbours.isEmpty()) {
            centroid
        } else {
            P2(neighbours.sumOf { it.x } / neighbours.size, neighbours.sumOf { it.y } / neighbours.size)
        }
        val away = (a + b) / 2.0 - reference
        val heuristic = when {
            away.length > 1e-9 -> if (away.dot(base) < 0.0) -1.0 else 1.0
            else -> if (base.y < 0.0) -1.0 else 1.0
        }
        fun offset(sign: Double): Offset {
            val extension = base * sign
            var normal = d.perpendicular()
            if (normal.dot(extension) < 0.0) normal = -normal
            return Offset(extension, normal)
        }
        fun clearance(side: Offset): Double {
            val shift = side.extension / abs(side.extension.dot(side.normal)).coerceAtLeast(0.35) * style.dimensionOffsetMm
            val start = a + shift
            val end = b + shift
            val middle = (start + end) / 2.0
            val probes = listOf(start, end, middle, (start + middle) / 2.0, (middle + end) / 2.0, middle + side.normal * (style.textHeightMm * 0.75))
            return obstacles
                .filterNot { (p, q) -> (p == a && q == b) || (p == b && q == a) }
                .minOfOrNull { (p, q) -> probes.minOf { distanceToSegment(it, p, q).first } }
                ?: Double.MAX_VALUE
        }
        val cap = style.dimensionOffsetMm * 2.5
        val preferredSide = offset(heuristic)
        val otherSide = offset(-heuristic)
        val preferredRoom = clearance(preferredSide).coerceAtMost(cap)
        val otherRoom = clearance(otherSide).coerceAtMost(cap)
        return if (otherRoom > preferredRoom * 1.15 + 1e-9) otherSide else preferredSide
    }

    /** Oblique extension direction (parallel to a model axis) and the screen normal on the same side. */
    private data class Offset(val extension: P2, val normal: P2)

    /** Draws the dimension and returns its line, or null when the run is too short to dimension. */
    private fun dimension(
        network: IsoNetwork,
        pipe: IsoPipe,
        a: P2,
        b: P2,
        side: Offset,
        style: IsoSceneStyle,
        labeler: IsoLabeler,
        selected: Boolean,
        strokes: MutableList<IsoStroke>,
        texts: MutableList<IsoText>,
    ): Pair<P2, P2>? {
        val length = network.length(pipe)
        val screenLength = a.distanceTo(b)
        if (length < IsoNetwork.MIN_LENGTH_MM || screenLength < style.textHeightMm * 1.5) return null
        val d = (b - a) / screenLength
        // The extension is oblique, so its offset is scaled to keep a constant normal distance.
        val normalShare = abs(side.extension.dot(side.normal)).coerceAtLeast(0.35)
        val e = side.extension / normalShare
        val gap = style.dimensionOffsetMm * 0.15
        val offset = style.dimensionOffsetMm
        val overshoot = style.dimensionOffsetMm * 0.25
        val color = if (selected) style.selectionArgb else style.inkArgb
        val start = a + e * offset
        val end = b + e * offset
        strokes += IsoStroke(listOf(a + e * gap, a + e * (offset + overshoot)), color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id)
        strokes += IsoStroke(listOf(b + e * gap, b + e * (offset + overshoot)), color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id)
        strokes += IsoStroke(listOf(start - d * (style.tickMm * 0.6), end + d * (style.tickMm * 0.6)), color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id)
        // Architectural 45° ticks (GOST 2.307 / ISO 129 oblique stroke).
        val tick = rotate(d, 45.0) * (style.tickMm / 2.0)
        listOf(start, end).forEach { point ->
            strokes += IsoStroke(listOf(point - tick, point + tick), color, IsoWeight.MEDIUM, IsoRole.DIMENSION, pipeId = pipe.id)
        }
        texts += IsoText(
            labeler.length(length),
            (start + end) / 2.0 + side.normal * (style.textHeightMm * 0.75),
            readableAngle(d),
            style.textHeightMm,
            color,
            IsoRole.DIMENSION,
            pipeId = pipe.id,
        )
        return start to end
    }

    /** System code on the free side of the run, moved along it so it never covers a fitting symbol. */
    private fun tag(
        network: IsoNetwork,
        pipe: IsoPipe,
        text: String,
        a: P2,
        b: P2,
        side: Offset,
        style: IsoSceneStyle,
        texts: MutableList<IsoText>,
    ) {
        val screenLength = a.distanceTo(b)
        val height = style.textHeightMm * 0.85
        val width = estimateWidth(text, height)
        val needed = width + style.textHeightMm * 2.0
        if (screenLength < needed) return
        val d = (b - a) / screenLength
        val symbols = network.componentsOn(pipe.id).map { a + (b - a) * it.offset }
        val clearance = width / 2.0 + style.symbolMm * 0.7
        val position = listOf(0.5, 0.3, 0.7, 0.2, 0.8)
            .map { a + (b - a) * it }
            .filter { center -> center.distanceTo(a) >= width / 2.0 && center.distanceTo(b) >= width / 2.0 }
            .firstOrNull { center -> symbols.none { it.distanceTo(center) < clearance } }
            ?: return
        texts += IsoText(
            text,
            position - side.normal * (style.textHeightMm * 0.8),
            readableAngle(d),
            height,
            IsoSystemStyle.colorOf(pipe.spec),
            IsoRole.TAG,
            pipeId = pipe.id,
        )
    }

    private fun reducer(
        network: IsoNetwork,
        projection: IsoProjection,
        fitting: IsoFitting,
        p: P2,
        style: IsoSceneStyle,
        strokes: MutableList<IsoStroke>,
    ) {
        val pipes = network.incident[fitting.nodeId].orEmpty()
        val large = pipes.maxByOrNull { it.spec.nominalDiameter ?: 0 } ?: return
        val towardsLarge = network.directionFrom(large, fitting.nodeId)?.let { projection.screenOf(it).normalized() } ?: return
        val n = towardsLarge.perpendicular()
        val s = style.symbolMm * 0.5
        val wide = p + towardsLarge * s
        val narrow = p - towardsLarge * s
        strokes += IsoStroke(
            listOf(wide + n * (s * 0.7), narrow + n * (s * 0.35), narrow - n * (s * 0.35), wide - n * (s * 0.7)),
            style.inkArgb,
            IsoWeight.MEDIUM,
            IsoRole.FITTING,
            closed = true,
            nodeId = fitting.nodeId,
        )
    }

    private fun component(
        network: IsoNetwork,
        projection: IsoProjection,
        component: IsoComponent,
        style: IsoSceneStyle,
        selection: IsoSelection,
        strokes: MutableList<IsoStroke>,
        dots: MutableList<IsoDot>,
        texts: MutableList<IsoText>,
    ) {
        val color = if (component.id in selection.componentIds) style.selectionArgb else style.inkArgb
        val s = style.symbolMm
        fun stroke(points: List<P2>, closed: Boolean = false, filled: Boolean = false, weight: IsoWeight = IsoWeight.MEDIUM) {
            strokes += IsoStroke(points, color, weight, IsoRole.SYMBOL, closed = closed, filled = filled, componentId = component.id)
        }
        val inline = component.pipeId?.let { network.pipesById[it] }
        if (inline != null) {
            val a = network.position(inline.from)?.let(projection::project) ?: return
            val b = network.position(inline.to)?.let(projection::project) ?: return
            val c = a + (b - a) * component.offset
            val d = (b - a).normalized()
            val n = d.perpendicular()
            fun bowtie(fillSecond: Boolean) {
                stroke(listOf(c, c - d * (s / 2) + n * (s / 3), c - d * (s / 2) - n * (s / 3)), closed = true)
                stroke(listOf(c, c + d * (s / 2) + n * (s / 3), c + d * (s / 2) - n * (s / 3)), closed = true, filled = fillSecond)
            }
            when (component.kind) {
                IsoComponentKind.GATE_VALVE -> {
                    bowtie(false)
                    stroke(listOf(c, c + n * (s * 0.55)))
                    stroke(listOf(c + n * (s * 0.55) - d * (s * 0.2), c + n * (s * 0.55) + d * (s * 0.2)))
                }
                IsoComponentKind.BALL_VALVE -> {
                    bowtie(false)
                    dots += IsoDot(c, s * 0.14, color, filled = true, role = IsoRole.SYMBOL)
                }
                IsoComponentKind.CHECK_VALVE -> bowtie(true)
                IsoComponentKind.BALANCING_VALVE -> {
                    bowtie(false)
                    val tip = c + d * (s * 0.45) + n * (s * 0.5)
                    stroke(listOf(c - d * (s * 0.45) - n * (s * 0.5), tip))
                    stroke(listOf(tip - rotate((tip - c).normalized(), 25.0) * (s * 0.22), tip, tip - rotate((tip - c).normalized(), -25.0) * (s * 0.22)))
                }
                IsoComponentKind.STRAINER -> {
                    val leg = c + (-n - d).normalized() * (s * 0.6)
                    stroke(listOf(c - d * (s * 0.3), c + d * (s * 0.3)), weight = IsoWeight.THICK)
                    stroke(listOf(c, leg))
                    stroke(listOf(leg + rotate((leg - c).normalized(), 90.0) * (s * 0.15), leg - rotate((leg - c).normalized(), 90.0) * (s * 0.15)))
                }
                IsoComponentKind.PUMP -> {
                    stroke(circle(c, s * 0.5), closed = true)
                    stroke(listOf(c + d * (s * 0.4), c - d * (s * 0.25) + n * (s * 0.3), c - d * (s * 0.25) - n * (s * 0.3)), closed = true)
                }
                IsoComponentKind.METER -> {
                    stroke(listOf(c - d * (s * 0.5) + n * (s * 0.3), c + d * (s * 0.5) + n * (s * 0.3), c + d * (s * 0.5) - n * (s * 0.3), c - d * (s * 0.5) - n * (s * 0.3)), closed = true)
                    stroke(circle(c, s * 0.18), closed = true)
                }
                else -> terminal(component.kind, c, d, s, ::stroke, dots, color)
            }
            component.label?.let { label ->
                texts += IsoText(label, c + n * (s * 0.95), readableAngle(d), style.textHeightMm * 0.85, color, IsoRole.LABEL)
            }
            return
        }
        val nodeId = component.nodeId ?: return
        val p = network.position(nodeId)?.let(projection::project) ?: return
        // Terminal symbols continue beyond the open end of the run that reaches them.
        val outward = network.incident[nodeId].orEmpty().firstOrNull()
            ?.let { network.directionFrom(it, nodeId) }
            ?.let { -projection.screenOf(it).normalized() }
            ?: P2(1.0, 0.0)
        terminal(component.kind, p, outward, s, ::stroke, dots, color)
        component.label?.let { label ->
            texts += IsoText(label, p + outward * (s * 1.6) + P2(0.0, s * 0.8), 0.0, style.textHeightMm * 0.85, color, IsoRole.LABEL)
        }
    }

    private fun terminal(
        kind: IsoComponentKind,
        p: P2,
        outward: P2,
        s: Double,
        stroke: (List<P2>, Boolean, Boolean, IsoWeight) -> Unit,
        dots: MutableList<IsoDot>,
        color: Long,
    ) {
        val n = outward.perpendicular()
        when (kind) {
            IsoComponentKind.RADIATOR -> {
                val c = p + outward * (s * 0.9)
                val w = s * 0.8
                val h = s * 0.55
                stroke(listOf(c + P2(-w, -h), c + P2(w, -h), c + P2(w, h), c + P2(-w, h)), true, false, IsoWeight.MEDIUM)
                for (i in 1..4) {
                    val x = -w + i * (2 * w / 5)
                    stroke(listOf(c + P2(x, -h), c + P2(x, h)), false, false, IsoWeight.THIN)
                }
            }
            IsoComponentKind.FIXTURE -> {
                val c = p + outward * (s * 0.7)
                stroke(listOf(c + P2(-s * 0.6, s * 0.4), c + P2(-s * 0.6, -s * 0.3), c + P2(s * 0.6, -s * 0.3), c + P2(s * 0.6, s * 0.4)), false, false, IsoWeight.MEDIUM)
            }
            IsoComponentKind.AIR_VENT -> {
                stroke(listOf(p, p + P2(0.0, s * 0.6)), false, false, IsoWeight.MEDIUM)
                stroke(circle(p + P2(0.0, s * 0.75), s * 0.15), true, false, IsoWeight.MEDIUM)
            }
            IsoComponentKind.DRAIN -> {
                stroke(listOf(p, p - P2(0.0, s * 0.45)), false, false, IsoWeight.MEDIUM)
                stroke(listOf(p - P2(s * 0.25, s * 0.45), p - P2(-s * 0.25, s * 0.45), p - P2(0.0, s * 0.8)), true, true, IsoWeight.MEDIUM)
            }
            IsoComponentKind.PLUG -> stroke(listOf(p - n * (s * 0.4), p + n * (s * 0.4)), false, false, IsoWeight.THICK)
            else -> dots += IsoDot(p, s * 0.25, color, filled = true, role = IsoRole.SYMBOL)
        }
    }

    private fun elevations(
        network: IsoNetwork,
        sheet: IsometricSheet,
        projected: Map<IsoNodeId, P2>,
        style: IsoSceneStyle,
        labeler: IsoLabeler,
        obstacles: List<Pair<P2, P2>>,
        strokes: MutableList<IsoStroke>,
        texts: MutableList<IsoText>,
    ) {
        val marked = mutableListOf<P2>()
        sheet.nodes.forEach { node ->
            val incident = network.incident[node.id].orEmpty()
            val vertical = incident.any { pipe ->
                val a = network.position(pipe.from) ?: return@any false
                val b = network.position(pipe.to) ?: return@any false
                abs(b.z - a.z) >= 1.0
            }
            val ends = incident.size == 1 && abs(node.position.z) >= 1.0
            if (!vertical && !ends) return@forEach
            val p = projected[node.id] ?: return@forEach
            if (marked.any { it.distanceTo(p) < style.textHeightMm * 0.5 }) return@forEach
            marked += p
            val text = labeler.elevation(node.position.z)
            val h = style.textHeightMm
            val width = estimateWidth(text, h * 0.85) + h * 0.4
            // GOST 2.307 level mark: open triangle on a leader with a shelf carrying the value,
            // drawn in whichever of the four quadrants around the joint has the most free paper.
            fun box(horizontal: Double, vertical: Double): List<P2> {
                val apex = p + P2(horizontal * h * 0.9, 0.0)
                val shelfY = apex.y + vertical * h * 0.75
                val textY = shelfY + vertical * h * 0.6
                val far = apex.x + horizontal * width
                return listOf(
                    apex,
                    P2(far, shelfY),
                    P2((apex.x + far) / 2.0, shelfY),
                    P2(apex.x, textY + vertical * h * 0.45),
                    P2((apex.x + far) / 2.0, textY),
                    P2(far, textY + vertical * h * 0.45),
                    P2((apex.x + far) / 2.0, textY + vertical * h * 0.45),
                )
            }
            fun room(horizontal: Double, vertical: Double): Double {
                val probes = box(horizontal, vertical)
                return obstacles.minOfOrNull { (s, e) -> probes.minOf { distanceToSegment(it, s, e).first } } ?: Double.MAX_VALUE
            }
            val quadrants = listOf(1.0 to 1.0, -1.0 to 1.0, 1.0 to -1.0, -1.0 to -1.0)
            val best = quadrants.maxBy { (horizontal, vertical) ->
                // A small bonus keeps the conventional upper-right mark when space is equal.
                room(horizontal, vertical).coerceAtMost(h * 3.0) + if (horizontal > 0 && vertical > 0) h * 0.2 else 0.0
            }
            val sign = best.first
            val flip = best.second
            val apex = p + P2(sign * h * 0.9, 0.0)
            val shelfY = apex.y + flip * h * 0.75
            strokes += IsoStroke(listOf(p, apex), style.inkArgb, IsoWeight.THIN, IsoRole.ELEVATION, nodeId = node.id)
            strokes += IsoStroke(
                listOf(apex, P2(apex.x - h * 0.42, shelfY), P2(apex.x + h * 0.42, shelfY)),
                style.inkArgb,
                IsoWeight.THIN,
                IsoRole.ELEVATION,
                closed = true,
                nodeId = node.id,
            )
            strokes += IsoStroke(listOf(P2(apex.x, shelfY), P2(apex.x + sign * width, shelfY)), style.inkArgb, IsoWeight.THIN, IsoRole.ELEVATION, nodeId = node.id)
            texts += IsoText(
                text,
                P2(apex.x + sign * h * 0.25, shelfY + flip * h * 0.6),
                0.0,
                h * 0.85,
                style.inkArgb,
                IsoRole.ELEVATION,
                if (sign > 0) IsoTextAlign.START else IsoTextAlign.END,
                nodeId = node.id,
            )
        }
    }

    /** Angle of [direction] turned by 180° when needed so that text never reads upside down. */
    fun readableAngle(direction: P2): Double {
        var angle = Math.toDegrees(atan2(direction.y, direction.x))
        if (angle > 90.0) angle -= 180.0
        if (angle <= -90.0) angle += 180.0
        return angle
    }

    internal fun estimateWidth(text: String, heightMm: Double) = text.length * heightMm * 0.62

    /** Corner points of a text's box, for bounds. */
    internal fun textBox(text: IsoText): List<P2> {
        val width = estimateWidth(text.text, text.heightMm)
        val along = P2(cos(Math.toRadians(text.angleDeg)), sin(Math.toRadians(text.angleDeg)))
        val up = along.perpendicular()
        val start = when (text.align) {
            IsoTextAlign.START -> 0.0
            IsoTextAlign.CENTER -> -width / 2
            IsoTextAlign.END -> -width
        }
        val half = text.heightMm * 0.6
        return listOf(
            text.anchor + along * start - up * half,
            text.anchor + along * (start + width) - up * half,
            text.anchor + along * (start + width) + up * half,
            text.anchor + along * start + up * half,
        )
    }

    internal fun rotate(vector: P2, degrees: Double): P2 {
        val r = Math.toRadians(degrees)
        return P2(vector.x * cos(r) - vector.y * sin(r), vector.x * sin(r) + vector.y * cos(r))
    }

    private fun circle(center: P2, radius: Double, segments: Int = 20): List<P2> =
        (0 until segments).map { i ->
            val angle = 2.0 * Math.PI * i / segments
            center + P2(cos(angle) * radius, sin(angle) * radius)
        }
}

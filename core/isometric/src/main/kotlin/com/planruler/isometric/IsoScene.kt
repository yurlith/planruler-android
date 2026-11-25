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
import kotlin.math.hypot
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
    /** Dimension lines stack outwards at most this many tiers before a run is left undimensioned. */
    private const val DIMENSION_TIERS = 6

    /** Nearby dimensions tried for moving aside when a run finds no place for its own. */
    private const val RIP_UP_LIMIT = 6

    /** Rearrangements tried per sheet before the remaining runs keep their lengths along the run. */
    private const val RIP_UP_BUDGET = 40

    /** Tiers tried for a riser whose height the level marks already give. */
    private const val RISER_TIERS = 2

    /** Cost of an extension line lying on a neighbouring run, against 1–2 per crossing. */
    private const val ALONG_RUN_COST = 10

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
        // Symbols are laid out first so that every annotation is placed around them; they are
        // still painted after the dimensions and tags, as on a drawing.
        val symbolStrokes = mutableListOf<IsoStroke>()
        val symbolDots = mutableListOf<IsoDot>()
        val symbolTexts = mutableListOf<IsoText>()
        network.fittings().forEach { fitting ->
            val p = projected[fitting.nodeId] ?: return@forEach
            when (fitting.kind) {
                IsoFittingKind.TEE, IsoFittingKind.CROSS, IsoFittingKind.MANIFOLD ->
                    symbolDots += IsoDot(p, style.symbolMm * 0.22, style.inkArgb, filled = true, role = IsoRole.FITTING, nodeId = fitting.nodeId)
                IsoFittingKind.END ->
                    symbolDots += IsoDot(p, style.symbolMm * 0.18, style.inkArgb, filled = false, role = IsoRole.FITTING, nodeId = fitting.nodeId)
                IsoFittingKind.REDUCER -> reducer(network, projection, fitting, p, style, symbolStrokes)
                IsoFittingKind.ELBOW, IsoFittingKind.COUPLING -> Unit
            }
        }
        sheet.components.forEach { component ->
            component(network, projection, component, style, selection, symbolStrokes, symbolDots, symbolTexts)
        }

        // Annotations keep clear of runs, symbols and of each other: whatever is placed joins
        // the occupied paper before the next one is laid out. Dimensions go first, shortest
        // runs first because they have the fewest places left; codes fill the gaps after.
        val extent = Rect2.around(projected.values)?.let { maxOf(it.width, it.height) } ?: 0.0
        val occupancy = Occupancy(cell = maxOf(style.textHeightMm * 6.0, extent / 64.0, 1e-6))
        strokes.forEach(occupancy::add)
        symbolStrokes.forEach(occupancy::add)
        symbolDots.forEach(occupancy::add)
        symbolTexts.forEach(occupancy::add)
        val sides = mutableMapOf<IsoPipeId, Offset>()
        val order = mutableListOf<IsoPipe>()
        // The most constrained runs choose first: fewest clean extension directions, then the
        // shortest, because they have the fewest places left for their value.
        sheet.pipes
            .filter { projected[it.from] != null && projected[it.to] != null }
            .sortedWith(
                compareBy<IsoPipe>(
                    { pipe -> axisOffsets(network, projection, pipe, projected).count { !alongRun(network, pipe, projected, it) } },
                    { pipe -> projected.getValue(pipe.from).distanceTo(projected.getValue(pipe.to)) },
                ),
            )
            .forEach { pipe -> order += pipe }
        val placed = LinkedHashMap<IsoPipeId, Dimension>()
        fun place(pipe: IsoPipe): Dimension? {
            val a = projected.getValue(pipe.from)
            val b = projected.getValue(pipe.to)
            val candidates = annotationSides(network, projection, pipe, a, b, projected, centroid, occupancy, style)
            sides[pipe.id] = candidates.first()
            if (!sheet.showDimensions) return null
            val dimension = dimension(
                network, pipe, a, b, candidates, projected, style, labeler,
                pipe.id in selection.pipeIds, sheet.showElevations, occupancy,
            ) ?: return null
            dimension.items.forEach(occupancy::add)
            placed[pipe.id] = dimension
            sides[pipe.id] = dimension.side
            return dimension
        }
        fun lift(dimension: Dimension) {
            dimension.items.forEach(occupancy::remove)
            placed.remove(dimension.pipeId)
        }
        fun restore(dimension: Dimension) {
            dimension.items.forEach(occupancy::add)
            placed[dimension.pipeId] = dimension
            sides[dimension.pipeId] = dimension.side
        }
        // With level marks shown, risers come last: the marks already give their height, so a
        // riser's dimension only takes paper that the runs and the marks leave free.
        val risers = if (sheet.showElevations) order.filter { isRiser(network, it) } else emptyList()
        val unplaced = order.filter { it !in risers && place(it) == null }
        if (sheet.showDimensions) {
            // A run left without a dimension may take the place of a nearby one if that one
            // then finds another place: both keep their dimensions and nothing overlaps. Long
            // runs try first, and the total work is bounded so large schemes stay quick.
            var budget = RIP_UP_BUDGET
            unplaced.sortedByDescending { projected.getValue(it.from).distanceTo(projected.getValue(it.to)) }.forEach { pipe ->
                if (budget <= 0) return@forEach
                val a = projected.getValue(pipe.from)
                val b = projected.getValue(pipe.to)
                val reachable = Rect2.around(listOf(a, b))!!.expanded(style.dimensionOffsetMm * 4.0 + style.textHeightMm * 4.0)
                val blockers = placed.values
                    .filter { dimension ->
                        dimension.items.any { item ->
                            item.maxX >= reachable.minX && item.minX <= reachable.maxX && item.maxY >= reachable.minY && item.minY <= reachable.maxY
                        }
                    }
                    .sortedBy { dimension -> dimension.text.anchor.distanceTo((a + b) / 2.0) }
                    .take(RIP_UP_LIMIT)
                // Single neighbours first, then pairs and a triple of the nearest ones.
                val groups = blockers.map { listOf(it) } +
                    blockers.take(4).let { near -> near.indices.flatMap { i -> (i + 1 until near.size).map { j -> listOf(near[i], near[j]) } } } +
                    listOfNotNull(blockers.take(3).takeIf { it.size == 3 })
                for (group in groups) {
                    if (budget-- <= 0) break
                    group.forEach(::lift)
                    val moved = mutableListOf<Dimension>()
                    val mine = place(pipe)?.also { moved += it }
                    val rest = mine != null && group.all { blocker ->
                        place(network.pipesById.getValue(blocker.pipeId))?.also { moved += it } != null
                    }
                    if (rest) break
                    moved.forEach(::lift)
                    group.forEach(::restore)
                }
            }
        }
        val markStrokes = mutableListOf<IsoStroke>()
        val markTexts = mutableListOf<IsoText>()
        if (sheet.showElevations) {
            elevations(network, sheet, projected, style, labeler, occupancy, markStrokes, markTexts)
        }
        risers.forEach(::place)
        order.mapNotNull { placed[it.id] }.forEach { dimension ->
            strokes += dimension.strokes
            texts += dimension.text
        }
        if (sheet.showDimensions) {
            // A run whose dimension found no place without overlapping carries its length along
            // the run instead, the compact notation of pipe isometrics.
            order.filter { it.id !in placed }.forEach { pipe ->
                val length = network.length(pipe)
                if (length < IsoNetwork.MIN_LENGTH_MM) return@forEach
                label(
                    pipe, labeler.length(length), projected.getValue(pipe.from), projected.getValue(pipe.to),
                    sides.getValue(pipe.id), style, occupancy, texts,
                    role = IsoRole.DIMENSION,
                    colorArgb = if (pipe.id in selection.pipeIds) style.selectionArgb else style.inkArgb,
                    heightMm = style.textHeightMm,
                )
            }
        }
        if (sheet.showSystemCodes) {
            sheet.pipes.forEach { pipe ->
                val a = projected[pipe.from] ?: return@forEach
                val b = projected[pipe.to] ?: return@forEach
                val side = sides[pipe.id] ?: return@forEach
                val tag = listOfNotNull(labeler.pipeTag(pipe.spec), pipe.label).joinToString(" ").takeIf(String::isNotBlank)
                if (tag != null) {
                    label(
                        pipe, tag, a, b, side, style, occupancy, texts,
                        role = IsoRole.TAG,
                        colorArgb = IsoSystemStyle.colorOf(pipe.spec),
                        heightMm = style.textHeightMm * 0.85,
                    )
                }
            }
        }
        strokes += symbolStrokes
        dots += symbolDots
        texts += symbolTexts
        strokes += markStrokes
        texts += markTexts

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
            val extra = preview(sheet, plan, style, labeler)
            strokes += extra.strokes
            texts += extra.texts
            dots += extra.dots
        }

        val bounds = Rect2.around(
            strokes.flatMap { it.points } +
                dots.flatMap { listOf(it.center - P2(it.radiusMm, it.radiusMm), it.center + P2(it.radiusMm, it.radiusMm)) } +
                texts.flatMap { textBox(it) },
        )
        return IsoScene(strokes, texts, dots, bounds)
    }

    /**
     * Where a run's dimension may go, best first. Extension lines follow a model axis (the
     * iso drafting convention): vertical for horizontal runs, then the plan axes; for each
     * axis the side with more free paper comes first, on a tie the side facing away from the
     * neighbouring runs. A riser rising from a main gets its extensions along the other plan
     * axis, because along the main they would lie on the main itself.
     */
    private fun annotationSides(
        network: IsoNetwork,
        projection: IsoProjection,
        pipe: IsoPipe,
        a: P2,
        b: P2,
        projected: Map<IsoNodeId, P2>,
        centroid: P2,
        occupancy: Occupancy,
        style: IsoSceneStyle,
    ): List<Offset> {
        val d = (b - a).normalized()
        val bases = axisOffsets(network, projection, pipe, projected).map { it.extension }.filterIndexed { index, _ -> index % 2 == 0 }
        val neighbours = (network.incident[pipe.from].orEmpty() + network.incident[pipe.to].orEmpty())
            .filter { it.id != pipe.id }
            .flatMap { listOfNotNull(projected[it.from], projected[it.to]) }
        val reference = if (neighbours.isEmpty()) {
            centroid
        } else {
            P2(neighbours.sumOf { it.x } / neighbours.size, neighbours.sumOf { it.y } / neighbours.size)
        }
        val away = (a + b) / 2.0 - reference
        val cap = style.dimensionOffsetMm * 2.5
        // Chained dimensions of runs meeting at a joint share their extension lines.
        val adjacent = (network.incident[pipe.from].orEmpty() + network.incident[pipe.to].orEmpty()).mapTo(mutableSetOf()) { it.id }
        val skip = { item: Occupancy.Item ->
            (item.kind == Occupancy.Kind.RUN && item.pipeId == pipe.id) ||
                ((item.kind == Occupancy.Kind.DIMENSION_LINE || item.kind == Occupancy.Kind.EXTENSION) && item.pipeId in adjacent)
        }
        fun clearance(side: Offset): Double {
            val shift = side.extension / abs(side.extension.dot(side.normal)).coerceAtLeast(0.35) * style.dimensionOffsetMm
            val start = a + shift
            val end = b + shift
            val label = (start + end) / 2.0 + side.normal * (style.textHeightMm * 0.75)
            val text = IsoText("0000", label, readableAngle(d), style.textHeightMm, style.inkArgb, IsoRole.DIMENSION)
            return minOf(
                occupancy.clearance(listOf(start, end), closed = false, cap = cap, skip = skip),
                occupancy.clearance(textBox(text), closed = true, cap = cap, skip = skip),
            )
        }
        return bases.flatMap { base ->
            val heuristic = when {
                away.length > 1e-9 -> if (away.dot(base) < 0.0) -1.0 else 1.0
                else -> if (base.y < 0.0) -1.0 else 1.0
            }
            val preferredSide = offsetOf(base * heuristic, d)
            val otherSide = offsetOf(base * -heuristic, d)
            val preferredRoom = clearance(preferredSide).coerceAtMost(cap)
            val otherRoom = clearance(otherSide).coerceAtMost(cap)
            if (otherRoom > preferredRoom * 1.15 + 1e-9) listOf(otherSide, preferredSide) else listOf(preferredSide, otherSide)
        }
    }

    /** Oblique extension direction (parallel to a model axis) and the screen normal on the same side. */
    private data class Offset(val extension: P2, val normal: P2)

    /** A run's dimension: what is drawn and the paper it takes. */
    private class Dimension(
        val pipeId: IsoPipeId,
        val side: Offset,
        val strokes: List<IsoStroke>,
        val text: IsoText,
        val items: List<Occupancy.Item>,
    )

    private fun offsetOf(extension: P2, d: P2): Offset {
        var normal = d.perpendicular()
        if (normal.dot(extension) < 0.0) normal = -normal
        return Offset(extension, normal)
    }

    /**
     * Both sides of every model axis a dimension's extension lines may follow, in drafting
     * preference: vertical extensions for horizontal runs, plan axes for vertical ones. Axes
     * nearly parallel to the run on paper are left out.
     */
    private fun axisOffsets(network: IsoNetwork, projection: IsoProjection, pipe: IsoPipe, projected: Map<IsoNodeId, P2>): List<Offset> {
        val a = projected[pipe.from] ?: return emptyList()
        val b = projected[pipe.to] ?: return emptyList()
        val d = (b - a).normalized()
        val from = network.position(pipe.from)
        val to = network.position(pipe.to)
        val horizontal = from != null && to != null && abs(to.z - from.z) < 1.0
        val preferred = if (horizontal) listOf(IsoAxis.UP, IsoAxis.EAST, IsoAxis.NORTH) else listOf(IsoAxis.EAST, IsoAxis.NORTH, IsoAxis.UP)
        return preferred
            .map { projection.screenOf(it.unit).normalized() }
            .filter { abs(d.cross(it)) > 0.35 }
            .ifEmpty { listOf(d.perpendicular()) }
            .flatMap { base -> listOf(offsetOf(base, d), offsetOf(-base, d)) }
    }

    private fun isRiser(network: IsoNetwork, pipe: IsoPipe): Boolean {
        val from = network.position(pipe.from) ?: return false
        val to = network.position(pipe.to) ?: return false
        return hypot(to.x - from.x, to.y - from.y) < 1.0 && abs(to.z - from.z) >= 1.0
    }

    /** True when an extension line on [side] would be drawn on top of a run leaving the same joint. */
    private fun alongRun(network: IsoNetwork, pipe: IsoPipe, projected: Map<IsoNodeId, P2>, side: Offset): Boolean {
        val direction = side.extension.normalized()
        return listOf(pipe.from, pipe.to).any { node ->
            val at = projected[node] ?: return@any false
            network.incident[node].orEmpty().any { other ->
                if (other.id == pipe.id) return@any false
                val far = projected[network.other(other, node)] ?: return@any false
                val towards = far - at
                towards.length > 1e-9 && towards.normalized().dot(direction) > 0.996
            }
        }
    }

    /**
     * Dimension of one run, placed on the first free tier (stacked dimension lines as in
     * GOST 2.307 / ISO 129), trying [sides] in order on each tier. Dimensions never overlap:
     * a value never touches anything; a dimension line never touches another dimension line,
     * a text or a symbol, nor crosses another dimension's extension line except where it ends
     * on it (stacked and chained dimensions share extension lines); an extension line never
     * runs through a text or the middle of another dimension line. A dimension never sits
     * across a run parallel to its own, where it would read as that run's. Crossing other runs,
     * or an extension line lying on a run that leaves the same joint (the run then serves as the
     * extension), is accepted only when every tier and side would. While level marks are shown
     * a riser is dimensioned only on the first two tiers, since the marks already give its
     * height. Returns the placement, not yet on the sheet, or null when the run is left
     * without a dimension.
     */
    private fun dimension(
        network: IsoNetwork,
        pipe: IsoPipe,
        a: P2,
        b: P2,
        sides: List<Offset>,
        projected: Map<IsoNodeId, P2>,
        style: IsoSceneStyle,
        labeler: IsoLabeler,
        selected: Boolean,
        elevationsShown: Boolean,
        occupancy: Occupancy,
    ): Dimension? {
        val length = network.length(pipe)
        val screenLength = a.distanceTo(b)
        val h = style.textHeightMm
        if (length < IsoNetwork.MIN_LENGTH_MM || screenLength < h * 1.5) return null
        val d = (b - a) / screenLength
        val color = if (selected) style.selectionArgb else style.inkArgb
        val value = labeler.length(length)
        val halfWidth = estimateWidth(value, h) / 2.0
        val margin = h * 0.15
        val gap = style.dimensionOffsetMm * 0.15
        val overshoot = style.dimensionOffsetMm * 0.25
        val spacing = maxOf(style.dimensionOffsetMm * 0.75, h * 1.8)
        val tick = rotate(d, 45.0) * (style.tickMm / 2.0)
        val adjacent = (network.incident[pipe.from].orEmpty() + network.incident[pipe.to].orEmpty())
            .mapTo(mutableSetOf()) { it.id }
            .apply { remove(pipe.id) }

        class Layout(val side: Offset, offset: Double, t: Double, textSide: Double) {
            // The extension is oblique, so its offset is scaled to keep a constant normal distance.
            private val e = side.extension / abs(side.extension.dot(side.normal)).coerceAtLeast(0.35)
            private val start = a + e * offset
            private val end = b + e * offset
            val line = listOf(start - d * (style.tickMm * 0.6), end + d * (style.tickMm * 0.6))
            val extensions = listOf(
                listOf(a + e * gap, a + e * (offset + overshoot)),
                listOf(b + e * gap, b + e * (offset + overshoot)),
            )
            // Architectural 45° ticks (GOST 2.307 / ISO 129 oblique stroke).
            val ticks = listOf(start, end).map { listOf(it - tick, it + tick) }
            val text = IsoText(
                value,
                start + (end - start) * t + side.normal * (textSide * h * 0.75),
                readableAngle(d),
                h,
                color,
                IsoRole.DIMENSION,
                pipeId = pipe.id,
            )
        }

        // Ends of dimension lines: the tick and the overshoot past the extension line.
        val reach = style.tickMm * 1.5
        fun ownRun(item: Occupancy.Item) = item.kind == Occupancy.Kind.RUN && item.pipeId == pipe.id

        // A dimension across a run parallel to its own would read as that run's dimension.
        fun parallel(item: Occupancy.Item): Boolean {
            val v = item.points.last() - item.points.first()
            val length = v.length
            return length > 1e-9 && abs(d.cross(v / length)) < 0.05
        }
        fun chained(item: Occupancy.Item) = item.pipeId != null && item.pipeId in adjacent
        val ends = setOf(pipe.from, pipe.to)
        val incidentAt = ends.associateWith { node -> network.incident[node].orEmpty().mapTo(mutableSetOf()) { it.id } }

        /**
         * Null when the layout would make dimensions overlap: its value touching anything; its
         * line touching another dimension's line or extension line, a text or a symbol; an
         * extension line crossing another dimension line or a text. Lines and ticks standing on
         * this run's own joints are shared with chained dimensions and are not conflicts.
         * Otherwise a cost: crossing runs and symbols is accepted only when nothing better fits.
         */
        fun cost(layout: Layout): Int? {
            if (occupancy.clearance(textBox(layout.text), closed = true, cap = margin, skip = ::ownRun) < margin) return null
            // Ticks stand on this run's own joints, where only texts can be in their way.
            if (layout.ticks.any { occupancy.clearance(it, closed = false, cap = margin) { item -> item.kind != Occupancy.Kind.TEXT } < margin }) {
                return null
            }
            var cost = 0
            for (item in occupancy.near(layout.line, margin)) {
                if (ownRun(item) || (item.kind == Occupancy.Kind.EXTENSION && item.nodeId in ends)) continue
                if (Occupancy.distance(layout.line, false, item) >= margin) continue
                when (item.kind) {
                    Occupancy.Kind.RUN -> if (parallel(item)) return null else cost += 2
                    Occupancy.Kind.DIMENSION_LINE ->
                        if (!(chained(item) && meetsAtJoint(layout.line, item.points, style.tickMm * 1.5, margin))) return null
                    // Ending on another dimension's extension line shares it, as in stacked
                    // dimensions; crossing one in the middle does not.
                    Occupancy.Kind.EXTENSION ->
                        if (!touchesOnlyNearEnds(layout.line, item.points, reach, margin)) return null
                    Occupancy.Kind.SYMBOL, Occupancy.Kind.TEXT -> return null
                }
            }
            layout.extensions.forEachIndexed { index, extension ->
                val base = if (index == 0) pipe.from else pipe.to
                val sharing = incidentAt.getValue(base)
                for (item in occupancy.near(extension, margin)) {
                    if (ownRun(item) || (item.kind != Occupancy.Kind.TEXT && item.pipeId != null && item.pipeId in sharing)) continue
                    if (Occupancy.distance(extension, false, item) >= margin * 0.5) continue
                    when (item.kind) {
                        Occupancy.Kind.TEXT -> return null
                        Occupancy.Kind.DIMENSION_LINE ->
                            if (!touchesOnlyNearEnds(item.points, extension, reach, margin * 0.5)) return null
                        Occupancy.Kind.RUN -> if (parallel(item)) return null else cost += 1
                        Occupancy.Kind.SYMBOL -> cost += 1
                        Occupancy.Kind.EXTENSION -> Unit
                    }
                }
            }
            return cost
        }

        // A riser's height is already in the level marks at both ends, so its dimension stays
        // next to it or is left out rather than being pushed out across the sheet.
        val riser = elevationsShown && isRiser(network, pipe)
        val tiers = if (riser) RISER_TIERS else DIMENSION_TIERS
        // Sides whose extension lines would lie on a neighbouring run come last and cost extra.
        val lying = sides.filter { alongRun(network, pipe, projected, it) }.toSet()
        val ordered = sides.sortedBy { it in lying }
        var best: Pair<Layout, Int>? = null
        search@ for (tier in 0 until tiers) {
            val offset = style.dimensionOffsetMm + tier * spacing
            for (side in ordered) {
                // The value stands outside its line, or inside it when a parallel run's dimension
                // takes the outside; it may slide along the line while it still stands over it.
                for (textSide in listOf(1.0, -1.0)) {
                    for (t in listOf(0.5, 0.32, 0.68)) {
                        if (t != 0.5 && halfWidth > minOf(t, 1.0 - t) * screenLength) continue
                        val layout = Layout(side, offset, t, textSide)
                        val cost = (cost(layout) ?: continue) + (if (side in lying) ALONG_RUN_COST else 0) + (if (textSide < 0) 1 else 0)
                        if (best == null || cost < best.second) best = layout to cost
                        if (cost == 0) break@search
                    }
                }
            }
        }
        val layout = best?.first ?: return null
        val ext = Occupancy.Kind.EXTENSION
        val strokes = listOf(
            IsoStroke(layout.extensions[0], color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id),
            IsoStroke(layout.extensions[1], color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id),
            IsoStroke(layout.line, color, IsoWeight.THIN, IsoRole.DIMENSION, pipeId = pipe.id),
            IsoStroke(layout.ticks[0], color, IsoWeight.MEDIUM, IsoRole.DIMENSION, pipeId = pipe.id),
            IsoStroke(layout.ticks[1], color, IsoWeight.MEDIUM, IsoRole.DIMENSION, pipeId = pipe.id),
        )
        val items = listOf(
            Occupancy.item(strokes[0], ext, pipe.from),
            Occupancy.item(strokes[1], ext, pipe.to),
            Occupancy.item(strokes[2], Occupancy.Kind.DIMENSION_LINE, null),
            Occupancy.item(strokes[3], ext, pipe.from),
            Occupancy.item(strokes[4], ext, pipe.to),
            Occupancy.item(layout.text),
        )
        return Dimension(pipe.id, layout.side, strokes, layout.text, items)
    }

    /**
     * True when [line] comes within [margin] of [other] only within [reach] of its own ends:
     * a dimension line that ends on an extension line, never one cut through the middle.
     */
    private fun touchesOnlyNearEnds(line: List<P2>, other: List<P2>, reach: Double, margin: Double): Boolean {
        if (line.size != 2) return false
        val a = line[0]
        val b = line[1]
        val length = a.distanceTo(b)
        if (length <= reach * 2.0) return true
        val u = (b - a) / length
        val inner = listOf(a + u * reach, b - u * reach)
        return other.zipWithNext().all { (p, q) -> segmentDistance(inner[0], inner[1], p, q) >= margin }
    }

    /**
     * Chained dimensions of runs meeting at a joint may touch only where they meet on their
     * shared extension line: end to end along one line, or at a corner. Away from the joint,
     * within [tolerance] of the meeting ends, they must keep [margin] apart.
     */
    private fun meetsAtJoint(line: List<P2>, other: List<P2>, tolerance: Double, margin: Double): Boolean {
        if (line.size != 2 || other.size != 2) return false
        val (mine, theirs) = line.flatMap { p -> other.map { q -> p to q } }.minBy { (p, q) -> p.distanceTo(q) }
        if (mine.distanceTo(theirs) > tolerance * 1.5) return false
        fun trimmed(segment: List<P2>, end: P2): Pair<P2, P2>? {
            val far = if (segment[0] == end) segment[1] else segment[0]
            val length = end.distanceTo(far)
            if (length <= tolerance * 1.5) return null
            return end + (far - end) * (tolerance * 1.5 / length) to far
        }
        val a = trimmed(line, mine) ?: return true
        val b = trimmed(other, theirs) ?: return true
        return segmentDistance(a.first, a.second, b.first, b.second) >= margin
    }

    /**
     * Text along a run (its system code, or its length when no dimension fits): first on the
     * side away from the dimension, then between the run and its dimension, at the first spot
     * that covers nothing. A crowded run gets no text rather than an unreadable one; a code's
     * colour and line type still tell the system, a length stays in the bill of materials.
     */
    private fun label(
        pipe: IsoPipe,
        text: String,
        a: P2,
        b: P2,
        side: Offset,
        style: IsoSceneStyle,
        occupancy: Occupancy,
        texts: MutableList<IsoText>,
        role: IsoRole,
        colorArgb: Long,
        heightMm: Double,
    ) {
        val screenLength = a.distanceTo(b)
        val height = heightMm
        val width = estimateWidth(text, height)
        if (screenLength < width + style.textHeightMm * 2.0) return
        val d = (b - a) / screenLength
        val margin = style.textHeightMm * 0.2
        val ownRun = { item: Occupancy.Item -> item.kind == Occupancy.Kind.RUN && item.pipeId == pipe.id }
        for (sideSign in listOf(-1.0, 1.0)) {
            for (t in listOf(0.5, 0.35, 0.65, 0.2, 0.8)) {
                val center = a + (b - a) * t
                if (center.distanceTo(a) < width / 2.0 || center.distanceTo(b) < width / 2.0) continue
                val candidate = IsoText(
                    text,
                    center + side.normal * (sideSign * (height * 0.7 + style.textHeightMm * 0.25)),
                    readableAngle(d),
                    height,
                    colorArgb,
                    role,
                    pipeId = pipe.id,
                )
                if (occupancy.clearance(textBox(candidate), closed = true, cap = margin * 2.0, skip = ownRun) >= margin) {
                    texts += candidate
                    occupancy.add(candidate)
                    return
                }
            }
        }
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
        occupancy: Occupancy,
        strokes: MutableList<IsoStroke>,
        texts: MutableList<IsoText>,
    ) {
        val marked = mutableListOf<P2>()
        val h = style.textHeightMm
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
            if (marked.any { it.distanceTo(p) < h * 0.5 }) return@forEach
            marked += p
            val value = labeler.elevation(node.position.z)
            val width = estimateWidth(value, h * 0.85) + h * 0.4

            // GOST 2.307 level mark: open triangle on a leader with a shelf carrying the value.
            // The leader leaves the joint horizontally, or slanted when the corner is crowded.
            val joined = incident.mapTo(mutableSetOf()) { it.id }
            class Mark(sign: Double, flip: Double, reach: Double, rise: Double) {
                val apex = p + P2(sign * h * reach, flip * h * rise)
                val shelfY = apex.y + flip * h * 0.75
                val leader = listOf(p, apex)
                val triangle = listOf(apex, P2(apex.x - h * 0.42, shelfY), P2(apex.x + h * 0.42, shelfY))
                val shelf = listOf(P2(apex.x, shelfY), P2(apex.x + sign * width, shelfY))
                val text = IsoText(
                    value,
                    P2(apex.x + sign * h * 0.25, shelfY + flip * h * 0.6),
                    0.0,
                    h * 0.85,
                    style.inkArgb,
                    IsoRole.ELEVATION,
                    if (sign > 0) IsoTextAlign.START else IsoTextAlign.END,
                    nodeId = node.id,
                )
                val upperRight = sign > 0 && flip > 0
                val leaderLength = p.distanceTo(apex)

                /** Free paper around the value, shelf and triangle; zero when they touch anything. */
                fun room(): Double {
                    val value = occupancy.clearance(textBox(text), closed = true, cap = h)
                    if (value < h * 0.25) return value
                    val line = occupancy.clearance(shelf, closed = false, cap = h)
                    if (line < h * 0.25) return line
                    return minOf(value, line, occupancy.clearance(triangle, closed = true, cap = h))
                }

                /**
                 * What the leader crosses, weighted: a dimension line counts ten, a run, symbol
                 * or extension line one; null when it would cross a text. Runs meeting at the
                 * joint are where the leader starts.
                 */
                fun crossings(): Int? {
                    val whole = listOf(p, apex)
                    val away = listOf(p + (apex - p) * (h * 0.4 / leaderLength.coerceAtLeast(h * 0.4)), apex)
                    var count = 0
                    for (item in occupancy.near(whole, h * 0.1)) {
                        if (item.kind == Occupancy.Kind.RUN && item.pipeId in joined) continue
                        // Near its own joint the leader meets the runs, symbols and extension lines
                        // that stand there; texts and dimension lines are never touched at all.
                        val atJoint = item.kind == Occupancy.Kind.RUN || item.kind == Occupancy.Kind.SYMBOL ||
                            (item.kind == Occupancy.Kind.EXTENSION && item.nodeId == node.id)
                        if (Occupancy.distance(if (atJoint) away else whole, false, item) >= h * 0.1) continue
                        when (item.kind) {
                            Occupancy.Kind.TEXT -> return null
                            Occupancy.Kind.DIMENSION_LINE -> count += 10
                            Occupancy.Kind.RUN, Occupancy.Kind.SYMBOL, Occupancy.Kind.EXTENSION -> count++
                        }
                    }
                    return count
                }
            }

            // The shortest clear leader that crosses nothing wins, the conventional upper right
            // first among equals; then the clear one whose thin leader crosses the least. The
            // mark itself never covers a dimension or a text: with no clear place it is left out.
            val quadrants = listOf(1.0 to 1.0, -1.0 to 1.0, 1.0 to -1.0, -1.0 to -1.0)
            val candidates = listOf(0.9, 2.2, 3.6, 5.2, 7.0, 9.5)
                .flatMap { reach -> listOf(0.0, 1.6, 3.2, 5.0).map { rise -> reach to rise } }
                .sortedBy { (reach, rise) -> kotlin.math.hypot(reach, rise) }
            var chosen: Mark? = null
            var fallback: Pair<Mark, Int>? = null
            for ((reach, rise) in candidates) {
                val clear = quadrants
                    .map { (sign, flip) -> Mark(sign, flip, reach, rise) }
                    .mapNotNull { mark -> mark.room().takeIf { it >= h * 0.25 }?.let { room -> mark.crossings()?.let { Triple(mark, room, it) } } }
                val clean = clear.filter { it.third == 0 }
                if (clean.isNotEmpty()) {
                    chosen = clean.maxBy { (mark, room, _) -> room + if (mark.upperRight) h * 0.2 else 0.0 }.first
                    break
                }
                clear.minByOrNull { it.third }?.let { (mark, _, crossed) ->
                    if (fallback == null || crossed < fallback.second) fallback = mark to crossed
                }
            }
            val mark = chosen ?: fallback?.first ?: return@forEach
            val placed = listOf(
                IsoStroke(mark.leader, style.inkArgb, IsoWeight.THIN, IsoRole.ELEVATION, nodeId = node.id),
                IsoStroke(mark.triangle, style.inkArgb, IsoWeight.THIN, IsoRole.ELEVATION, closed = true, nodeId = node.id),
                IsoStroke(mark.shelf, style.inkArgb, IsoWeight.THIN, IsoRole.ELEVATION, nodeId = node.id),
            )
            strokes += placed
            texts += mark.text
            placed.forEach(occupancy::add)
            occupancy.add(mark.text)
        }
    }

    /**
     * The rubber-band run while a stroke is drawn: cheap, so a screen can draw it every frame
     * over a scene laid out once.
     */
    fun preview(sheet: IsometricSheet, plan: RunPlan, style: IsoSceneStyle, labeler: IsoLabeler = DefaultIsoLabeler): IsoScene {
        val projection = IsoProjection.of(sheet.projection)
        val a = projection.project(plan.start)
        val b = projection.project(plan.end)
        val d = (b - a).normalized()
        val text = IsoText(
            labeler.length(plan.lengthMm),
            (a + b) / 2.0 + d.perpendicular() * (style.textHeightMm * 1.1),
            readableAngle(d),
            style.textHeightMm * 1.15,
            style.previewArgb,
            IsoRole.PREVIEW,
        )
        return IsoScene(
            strokes = listOf(IsoStroke(listOf(a, b), style.previewArgb, IsoWeight.THICK, IsoRole.PREVIEW, dashed = true)),
            texts = listOf(text),
            dots = listOf(IsoDot(b, style.symbolMm * 0.25, style.previewArgb, filled = true, role = IsoRole.PREVIEW)),
            bounds = Rect2.around(listOf(a, b)),
        )
    }

    /** Angle of [direction] turned by 180° when needed so that text never reads upside down. */
    fun readableAngle(direction: P2): Double {
        var angle = Math.toDegrees(atan2(direction.y, direction.x))
        if (angle > 90.0) angle -= 180.0
        if (angle <= -90.0) angle += 180.0
        return angle
    }

    /** Advance of [text] at cap height [heightMm]; 0.84 caps per character covers Roboto, Inter and DejaVu. */
    internal fun estimateWidth(text: String, heightMm: Double) = text.length * heightMm * 0.84

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
        // Mid-cap anchor: caps above, commas and Cyrillic descenders below.
        val half = text.heightMm * 0.7
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

/**
 * Paper already used on a sheet: runs, dimension and leader lines, symbols and texts.
 * Annotations ask how much free paper surrounds a candidate before they are placed. A coarse
 * grid keeps each question local, so large schemes stay fast to redraw.
 */
internal class Occupancy(private val cell: Double) {
    enum class Kind { RUN, DIMENSION_LINE, EXTENSION, SYMBOL, TEXT }

    class Item(val points: List<P2>, val closed: Boolean, val kind: Kind, val pipeId: IsoPipeId?, val nodeId: IsoNodeId? = null) {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        internal var visit = 0
    }

    private val cells = HashMap<Long, MutableList<Item>>()
    private var visit = 0

    private fun index(value: Double): Int = kotlin.math.floor(value / cell).coerceIn(-1.0e9, 1.0e9).toInt()
    private fun key(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFF_FFFFL)

    fun add(item: Item) {
        for (x in index(item.minX)..index(item.maxX)) {
            for (y in index(item.minY)..index(item.maxY)) cells.getOrPut(key(x, y)) { mutableListOf() } += item
        }
    }

    fun add(stroke: IsoStroke, kind: Kind = kindOf(stroke.role), nodeId: IsoNodeId? = null) {
        if (stroke.points.isEmpty()) return
        add(item(stroke, kind, nodeId))
    }

    fun add(text: IsoText) = add(item(text))

    fun remove(item: Item) {
        for (x in index(item.minX)..index(item.maxX)) {
            for (y in index(item.minY)..index(item.maxY)) cells[key(x, y)]?.removeAll { it === item }
        }
    }

    fun add(dot: IsoDot) {
        val r = dot.radiusMm
        val c = dot.center
        add(Item(listOf(c + P2(-r, -r), c + P2(r, -r), c + P2(r, r), c + P2(-r, r)), true, Kind.SYMBOL, null))
    }

    /** Items whose bounding boxes come within [margin] of the bounding box of [shape]. */
    fun near(shape: List<P2>, margin: Double): List<Item> {
        if (shape.isEmpty()) return emptyList()
        visit++
        val minX = shape.minOf { it.x } - margin
        val minY = shape.minOf { it.y } - margin
        val maxX = shape.maxOf { it.x } + margin
        val maxY = shape.maxOf { it.y } + margin
        val found = mutableListOf<Item>()
        for (x in index(minX)..index(maxX)) {
            for (y in index(minY)..index(maxY)) {
                val bucket = cells[key(x, y)] ?: continue
                for (item in bucket) {
                    if (item.visit == visit) continue
                    item.visit = visit
                    if (item.maxX < minX || item.minX > maxX || item.maxY < minY || item.minY > maxY) continue
                    found += item
                }
            }
        }
        return found
    }

    /**
     * Distance from [shape] (a polyline, or a polygon when [closed]) to the nearest occupied
     * item that [skip] does not exclude, at most [cap]; zero when they touch.
     */
    fun clearance(shape: List<P2>, closed: Boolean, cap: Double, skip: (Item) -> Boolean = { false }): Double {
        var best = cap
        for (item in near(shape, cap)) {
            if (skip(item)) continue
            best = minOf(best, distance(shape, closed, item))
            if (best <= 0.0) return 0.0
        }
        return best
    }

    companion object {
        fun item(stroke: IsoStroke, kind: Kind = kindOf(stroke.role), nodeId: IsoNodeId? = null) =
            Item(stroke.points, (stroke.closed || stroke.filled) && stroke.points.size > 2, kind, stroke.pipeId, nodeId)

        fun item(text: IsoText) = Item(IsoSceneBuilder.textBox(text), true, Kind.TEXT, text.pipeId)

        fun kindOf(role: IsoRole) = when (role) {
            IsoRole.PIPE -> Kind.RUN
            IsoRole.DIMENSION -> Kind.DIMENSION_LINE
            else -> Kind.SYMBOL
        }

        fun distance(shape: List<P2>, closed: Boolean, item: Item): Double = when {
            closed && item.closed -> polygonDistance(shape, item.points)
            closed -> pieces(item.points).minOf { (a, b) -> polygonSegmentDistance(shape, a, b) }
            item.closed -> pieces(shape).minOf { (a, b) -> polygonSegmentDistance(item.points, a, b) }
            else -> pieces(shape).minOf { (a, b) -> pieces(item.points).minOf { (c, d) -> segmentDistance(a, b, c, d) } }
        }

        /** True when the polyline [shape] crosses any edge of [item] at a single inner point. */
        fun crosses(shape: List<P2>, item: Item): Boolean {
            val edges = if (item.closed) item.points.zipWithNext() + (item.points.last() to item.points.first()) else pieces(item.points)
            return pieces(shape).any { (a, b) -> edges.any { (c, d) -> segmentsCross(a, b, c, d) } }
        }

        private fun pieces(points: List<P2>): List<Pair<P2, P2>> =
            points.zipWithNext().ifEmpty { listOf(points[0] to points[0]) }
    }
}

package com.planruler.isometric

import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * Layout guarantees of the scene: dimensions never overlap one another, and nothing placed
 * later covers them.
 */
class IsoAnnotationLayoutTest {
    private fun <T> IsoResult<T>.ok(): T = (this as? IsoResult.Ok)?.value ?: throw AssertionError("Expected success: $this")

    private val styles = listOf(
        "paper 1:20" to IsoSceneStyle.forPaper(20.0),
        "paper 1:50" to IsoSceneStyle.forPaper(50.0),
        "screen far" to IsoSceneStyle.forScreen(pxPerMm = 0.12, textPx = 30.0),
        "screen near" to IsoSceneStyle.forScreen(pxPerMm = 0.6, textPx = 30.0),
    )

    private fun engine(name: String, projection: IsoProjectionStyle): IsometricEngine {
        var ids = 0
        return IsometricEngine(IsometricEngine.newSheet(name, name, 0L, projection), clock = { 1L }, idGenerator = { "$name-${ids++}" })
    }

    /** Main with a riser from a tee, a branch on top to a radiator and a 45° offset. */
    private fun riser(projection: IsoProjectionStyle): IsometricSheet {
        val engine = engine("riser", projection)
        val dn25 = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25)
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(4_000.0, 0.0, 0.0), dn25).ok()
        engine.addComponent(IsoComponentKind.BALL_VALVE, main.pipeId, 0.15).ok()
        val offset = engine.addRun(RunStart.Node(main.endNodeId), Vec3(500.0, 500.0, 0.0), dn25).ok()
        engine.addRun(RunStart.Node(offset.endNodeId), Vec3(1_000.0, 0.0, 0.0), dn25).ok()
        val up = engine.addRun(RunStart.OnPipe(main.pipeId, 2_000.0), Vec3(0.0, 0.0, 2_700.0), dn25.copy(nominalDiameter = 20)).ok()
        val branch = engine.addRun(RunStart.Node(up.endNodeId), Vec3(0.0, 600.0, 0.0), dn25.copy(nominalDiameter = 20)).ok()
        engine.addComponentAtNode(IsoComponentKind.RADIATOR, branch.endNodeId).ok()
        return engine.sheet
    }

    /** Two-pipe heating: parallel supply and return mains, risers, radiator connections. */
    private fun heating(projection: IsoProjectionStyle): IsometricSheet {
        val engine = engine("heating", projection)
        val supply = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25)
        val ret = IsoPipeSpec(IsoPipeSystem.HEATING_RETURN, 25)
        fun route(start: Vec3, spec: IsoPipeSpec, vararg legs: Vec3): List<RunAdded> {
            var from: RunStart = RunStart.Point(start)
            return legs.map { leg -> engine.addRun(from, leg, spec).ok().also { from = RunStart.Node(it.endNodeId) } }
        }
        val t1 = route(Vec3(0.0, 0.0, 300.0), supply, Vec3(5_000.0, 0.0, 0.0), Vec3(0.0, 3_000.0, 0.0), Vec3(0.0, 0.0, 2_700.0))
        val t2 = route(Vec3(0.0, -250.0, 150.0), ret, Vec3(5_250.0, 0.0, 0.0), Vec3(0.0, 3_250.0, 0.0), Vec3(0.0, 0.0, 2_850.0))
        engine.addComponent(IsoComponentKind.BALL_VALVE, t1[0].pipeId, 0.08).ok()
        engine.addComponent(IsoComponentKind.PUMP, t2[0].pipeId, 0.2).ok()
        engine.addComponentAtNode(IsoComponentKind.AIR_VENT, t1[2].endNodeId).ok()
        val supplyBranch = engine.addRun(RunStart.OnPipe(t1[0].pipeId, 2_500.0), Vec3(0.0, -1_200.0, 0.0), supply.copy(nominalDiameter = 20)).ok()
        val supplyRiser = engine.addRun(RunStart.Node(supplyBranch.endNodeId), Vec3(0.0, 0.0, 350.0), supply.copy(nominalDiameter = 20)).ok()
        engine.addComponentAtNode(IsoComponentKind.RADIATOR, supplyRiser.endNodeId).ok()
        val returnBranch = engine.addRun(RunStart.OnPipe(t2[0].pipeId, 2_700.0), Vec3(0.0, -950.0, 0.0), ret.copy(nominalDiameter = 20)).ok()
        engine.addComponent(IsoComponentKind.BALANCING_VALVE, returnBranch.pipeId, 0.55).ok()
        engine.addRun(RunStart.Node(returnBranch.endNodeId), Vec3(0.0, 0.0, 300.0), ret.copy(nominalDiameter = 20)).ok()
        return engine.sheet
    }

    /** Six parallel runs 150 mm apart with branches: the densest case for dimension tiers. */
    private fun bundle(projection: IsoProjectionStyle): IsometricSheet {
        val engine = engine("bundle", projection)
        repeat(6) { index ->
            val spec = IsoPipeSpec(IsoPipeSystem.entries[index % 4], 20 + index)
            val run = engine.addRun(RunStart.Point(Vec3(0.0, index * 150.0, 0.0)), Vec3(3_000.0 + index * 100.0, 0.0, 0.0), spec).ok()
            engine.addRun(RunStart.OnPipe(run.pipeId, 600.0 + index * 300.0), Vec3(0.0, 0.0, 900.0 + index * 100.0), spec).ok()
        }
        return engine.sheet
    }

    /** Random walk along the axes with tees and valves. */
    private fun random(seed: Int, projection: IsoProjectionStyle): IsometricSheet {
        val random = Random(seed)
        val engine = engine("random$seed", projection)
        val axes = listOf(Vec3.X, -Vec3.X, Vec3.Y, -Vec3.Y, Vec3.Z, -Vec3.Z)
        var start: RunStart = RunStart.Point(Vec3.ZERO)
        repeat(16) {
            val leg = axes[random.nextInt(axes.size)] * (100.0 * random.nextInt(3, 30))
            val spec = IsoPipeSpec(IsoPipeSystem.entries[random.nextInt(4)], listOf(15, 20, 25, 32)[random.nextInt(4)])
            start = when (val result = engine.addRun(start, leg, spec)) {
                is IsoResult.Ok -> {
                    if (random.nextInt(3) == 0) engine.addComponent(IsoComponentKind.entries[random.nextInt(7)], result.value.pipeId, 0.5)
                    if (random.nextInt(4) == 0) RunStart.Node(engine.sheet.nodes.random(random).id) else RunStart.Node(result.value.endNodeId)
                }
                is IsoResult.Error -> RunStart.Node(engine.sheet.nodes.random(random).id)
            }
        }
        return engine.sheet
    }

    @Test
    fun `dimensions never overlap on typical schemes`() {
        for (projection in IsoProjectionStyle.entries) {
            for (sheet in listOf(riser(projection), heating(projection), bundle(projection))) {
                styles.forEach { (name, style) -> assertLayout(sheet, style, "${sheet.name} $projection $name") }
            }
        }
    }

    @Test
    fun `dimensions never overlap on random networks`() {
        for (seed in 0 until 30) {
            for (projection in IsoProjectionStyle.entries) {
                val sheet = random(seed, projection)
                styles.forEach { (name, style) -> assertLayout(sheet, style, "seed $seed $projection $name") }
            }
        }
    }

    @Test
    fun `each run of the typical schemes keeps its length on the sheet`() {
        for (projection in IsoProjectionStyle.entries) {
            val sheet = heating(projection)
            val scene = IsoSceneBuilder.build(sheet, IsoSceneStyle.forPaper(IsoExport.paperScaleFor(sheet)))
            val values = scene.texts.filter { it.role == IsoRole.DIMENSION }.map { it.text }
            // Horizontal runs always keep a dimension; risers may leave theirs to the level marks.
            listOf("2500", "3000", "2700", "2550", "3250", "1200", "950").forEach { value ->
                assertTrue("$projection: $value missing in $values", value in values)
            }
            val levels = scene.texts.filter { it.role == IsoRole.ELEVATION }.map { it.text }
            assertTrue(levels.toString(), levels.any { it.contains("3.000") || it.contains("3,000") })
        }
    }

    @Test
    fun `chained dimensions of a straight main share their extension line`() {
        val engine = engine("chain", IsoProjectionStyle.ISOMETRIC)
        val spec = IsoPipeSpec(IsoPipeSystem.COLD_WATER, 20)
        val main = engine.addRun(RunStart.Point(Vec3.ZERO), Vec3(6_000.0, 0.0, 0.0), spec).ok()
        // The first tee splits the main; the remaining first part keeps the main's id.
        engine.addRun(RunStart.OnPipe(main.pipeId, 4_000.0), Vec3(0.0, 1_000.0, 0.0), spec).ok()
        engine.addRun(RunStart.OnPipe(main.pipeId, 2_000.0), Vec3(0.0, 1_000.0, 0.0), spec).ok()
        val scene = IsoSceneBuilder.build(engine.sheet, IsoSceneStyle.forPaper(20.0))
        val values = scene.texts.filter { it.role == IsoRole.DIMENSION }.map { it.text }.sorted()
        assertEquals(listOf("1000", "1000", "2000", "2000", "2000"), values)
        assertLayout(engine.sheet, IsoSceneStyle.forPaper(20.0), "chain")
    }

    /** True when two segments come together only within [reach] of one end of each. */
    private fun meetOnlyAtEnds(first: List<P2>, second: List<P2>, reach: Double): Boolean {
        val (p, q) = first.flatMap { a -> second.map { b -> a to b } }.minBy { (a, b) -> a.distanceTo(b) }
        if (p.distanceTo(q) > reach) return false
        fun cut(segment: List<P2>, end: P2): Pair<P2, P2>? {
            val far = if (segment[0] == end) segment[1] else segment[0]
            val length = end.distanceTo(far)
            return if (length <= reach) null else end + (far - end) * (reach / length) to far
        }
        val a = cut(first, p) ?: return true
        val b = cut(second, q) ?: return true
        return segmentDistance(a.first, a.second, b.first, b.second) > 0.0
    }

    private fun assertLayout(sheet: IsometricSheet, style: IsoSceneStyle, label: String) {
        val scene = IsoSceneBuilder.build(sheet, style)
        val network = IsoNetwork(sheet)
        val projection = IsoProjection.of(sheet.projection)
        val direction = sheet.pipes.associate { pipe ->
            val a = projection.project(network.position(pipe.from)!!)
            val b = projection.project(network.position(pipe.to)!!)
            pipe.id to (b - a).normalized()
        }
        fun neighbours(first: com.planruler.model.IsoPipeId?, second: com.planruler.model.IsoPipeId?): Boolean {
            val a = network.pipesById[first] ?: return false
            val b = network.pipesById[second] ?: return false
            return setOf(a.from, a.to).intersect(setOf(b.from, b.to)).isNotEmpty()
        }
        val dimensionStrokes = scene.strokes.filter { it.role == IsoRole.DIMENSION }
        val lines = dimensionStrokes.filter { stroke ->
            val owner = stroke.pipeId?.let(direction::get) ?: return@filter false
            stroke.weight == IsoWeight.THIN && abs((stroke.points[1] - stroke.points[0]).normalized().cross(owner)) < 1e-9
        }
        val extensions = dimensionStrokes - lines.toSet()
        val values = scene.texts.filter { it.role == IsoRole.DIMENSION }

        // A value touches no other text, no other run and no line of another dimension or level mark.
        values.forEach { value ->
            val box = IsoSceneBuilder.textBox(value)
            scene.texts.filter { it !== value }.forEach { other ->
                assertTrue("$label: ${value.text} touches ${other.role} ${other.text}", polygonDistance(box, IsoSceneBuilder.textBox(other)) > 0.0)
            }
            scene.strokes
                .filter { it.pipeId != value.pipeId && it.role in setOf(IsoRole.PIPE, IsoRole.DIMENSION, IsoRole.ELEVATION) }
                .forEach { stroke ->
                    stroke.points.zipWithNext().forEach { (p, q) ->
                        assertTrue("$label: ${value.text} touches ${stroke.role} of ${stroke.pipeId}", polygonSegmentDistance(box, p, q) > 0.0)
                    }
                }
        }
        // Dimension lines of different runs never meet, except chained ones where they share an
        // extension line at their common joint (end to end, or at a corner).
        lines.forEachIndexed { index, first ->
            lines.drop(index + 1).filter { it.pipeId != first.pipeId }.forEach { second ->
                val gap = segmentDistance(first.points[0], first.points[1], second.points[0], second.points[1])
                if (gap > 0.0) return@forEach
                assertTrue(
                    "$label: dimension lines of ${first.pipeId} and ${second.pipeId} cross",
                    neighbours(first.pipeId, second.pipeId) && meetOnlyAtEnds(first.points, second.points, style.tickMm * 2.25),
                )
            }
        }
        // Extension lines and ticks never cut through the line of a dimension of an unrelated run;
        // a dimension line may end on one (stacked dimensions share extension lines).
        val reach = style.tickMm * 1.5
        extensions.forEach { extension ->
            lines.filter { it.pipeId != extension.pipeId && !neighbours(it.pipeId, extension.pipeId) }.forEach { line ->
                val u = (line.points[1] - line.points[0]).normalized()
                val inner = if (line.points[0].distanceTo(line.points[1]) <= reach * 2.0) return@forEach else
                    listOf(line.points[0] + u * reach, line.points[1] - u * reach)
                val gap = segmentDistance(extension.points[0], extension.points[1], inner[0], inner[1])
                assertTrue("$label: extension of ${extension.pipeId} cuts dimension of ${line.pipeId}", gap > 0.0)
            }
        }
        // Level marks (triangle and shelf) never cover a dimension line; only a thin leader may
        // cross one when the mark has no other clear place.
        val joints = sheet.nodes.map { projection.project(it.position) }
        scene.strokes.filter { it.role == IsoRole.ELEVATION && joints.none { joint -> joint.distanceTo(it.points.first()) < 1e-6 } }.forEach { mark ->
            lines.forEach { line ->
                mark.points.zipWithNext().forEach { (p, q) ->
                    assertTrue("$label: level mark crosses dimension of ${line.pipeId}", segmentDistance(p, q, line.points[0], line.points[1]) > 0.0)
                }
            }
        }
    }
}

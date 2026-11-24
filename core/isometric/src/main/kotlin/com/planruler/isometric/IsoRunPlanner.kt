package com.planruler.isometric

import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipeId
import com.planruler.model.IsometricSheet
import kotlin.math.abs
import kotlin.math.round

data class RunPlanOptions(
    /** Lengths read from a stroke are rounded to this step, millimetres. */
    val lengthStepMm: Double = 10.0,
    /** Also accept the 45° plane offsets that read unambiguously on the sheet. */
    val allowOffsets: Boolean = false,
    /** A stroke ending this close (drawing mm) to a reachable joint ends exactly on it. */
    val joinToleranceMm: Double = 0.0,
)

/** A run proposed by a stroke, ready for [IsometricEngine.addRun] and for live preview. */
data class RunPlan(
    val start: Vec3,
    val direction: RunDirection,
    val lengthMm: Double,
    val end: Vec3,
    /** Existing joint the run ends on. */
    val joinNodeId: IsoNodeId? = null,
    /** Existing run the new run tees into. */
    val joinPipeId: IsoPipeId? = null,
) {
    val delta: Vec3 get() = end - start
}

/** Turns a pen stroke on the sheet into a model run: direction, rounded length and joins. */
object IsoRunPlanner {
    fun plan(
        sheet: IsometricSheet,
        projection: IsoProjection,
        start: Vec3,
        strokeEnd: P2,
        options: RunPlanOptions = RunPlanOptions(),
    ): RunPlan? {
        val match = projection.matchRun(strokeEnd - projection.project(start), options.allowOffsets) ?: return null
        val direction = match.run.direction
        val network = IsoNetwork(sheet)

        data class Join(val length: Double, val screenDistance: Double, val node: IsoNodeId?, val pipe: IsoPipeId?)
        val joins = mutableListOf<Join>()
        if (options.joinToleranceMm > 0.0) {
            sheet.nodes.forEach { node ->
                val offset = node.position.toVec() - start
                val along = offset.dot(direction)
                if (along > IsometricEngine.MERGE_MM && (offset - direction * along).length <= IsometricEngine.MERGE_MM) {
                    val distance = projection.project(node.position).distanceTo(strokeEnd)
                    if (distance <= options.joinToleranceMm) joins += Join(along, distance, node.id, null)
                }
            }
            sheet.pipes.forEach { pipe ->
                val a = network.position(pipe.from) ?: return@forEach
                val b = network.position(pipe.to) ?: return@forEach
                val hit = lineSegmentIntersection(start, direction, a, b) ?: return@forEach
                val (t, s) = hit
                if (t <= IsometricEngine.MERGE_MM || s <= 0.0 || s >= 1.0) return@forEach
                val distance = projection.project(start + direction * t).distanceTo(strokeEnd)
                if (distance <= options.joinToleranceMm) joins += Join(t, distance, null, pipe.id)
            }
        }
        joins.minByOrNull { it.screenDistance }?.let { join ->
            return RunPlan(start, match.run, join.length, start + direction * join.length, join.node, join.pipe)
        }

        val step = options.lengthStepMm
        val length = if (step.isFinite() && step > 0.0) round(match.modelLengthMm / step) * step else match.modelLengthMm
        if (!length.isFinite() || length < IsoNetwork.MIN_LENGTH_MM) return null
        return RunPlan(start, match.run, length, start + direction * length)
    }

    /**
     * Parameter t along the ray `origin + t * direction` and s along segment [a, b] where the two
     * meet in 3D, or null when they pass each other.
     */
    internal fun lineSegmentIntersection(origin: Vec3, direction: Vec3, a: Vec3, b: Vec3): Pair<Double, Double>? {
        val segment = b - a
        val w = origin - a
        val aa = direction.dot(direction)
        val bb = direction.dot(segment)
        val cc = segment.dot(segment)
        val dd = direction.dot(w)
        val ee = segment.dot(w)
        val denominator = aa * cc - bb * bb
        if (abs(denominator) < 1e-12 || cc <= 0.0) return null
        val t = (bb * ee - cc * dd) / denominator
        val s = (aa * ee - bb * dd) / denominator
        val gap = (origin + direction * t) - (a + segment * s)
        return if (gap.length <= IsometricEngine.MERGE_MM) t to s else null
    }
}

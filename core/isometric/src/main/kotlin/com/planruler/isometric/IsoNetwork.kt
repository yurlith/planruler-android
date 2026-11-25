package com.planruler.isometric

import com.planruler.model.IsoComponent
import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoNode
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipe
import com.planruler.model.IsoPipeId
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsometricSheet
import kotlin.math.abs
import kotlin.math.acos

enum class IsoFittingKind { ELBOW, TEE, CROSS, MANIFOLD, REDUCER, COUPLING, END }

/** A fitting implied by the network topology at one node. Nothing here is stored. */
data class IsoFitting(
    val nodeId: IsoNodeId,
    val kind: IsoFittingKind,
    val system: IsoPipeSystem,
    /** Change of direction for an elbow, degrees (0 = straight on). */
    val angleDeg: Double? = null,
    /** Catalogue angle the bend matches within tolerance, or null for a special bend. */
    val standardAngleDeg: Int? = null,
    /** Run size; for reducing fittings the larger size. */
    val nominalDiameter: Int? = null,
    /** Branch or smaller size of a reducing fitting. */
    val branchDiameter: Int? = null,
)

/** Read-only topology view of a sheet: adjacency, directions and implied fittings. */
class IsoNetwork(val sheet: IsometricSheet) {
    val nodesById: Map<IsoNodeId, IsoNode> = sheet.nodes.associateBy { it.id }
    val pipesById: Map<IsoPipeId, IsoPipe> = sheet.pipes.associateBy { it.id }
    val incident: Map<IsoNodeId, List<IsoPipe>> = buildMap<IsoNodeId, MutableList<IsoPipe>> {
        sheet.nodes.forEach { put(it.id, mutableListOf()) }
        sheet.pipes.forEach { pipe ->
            getOrPut(pipe.from) { mutableListOf() } += pipe
            if (pipe.to != pipe.from) getOrPut(pipe.to) { mutableListOf() } += pipe
        }
    }

    fun position(id: IsoNodeId): Vec3? = nodesById[id]?.position?.toVec()

    fun length(pipe: IsoPipe): Double {
        val a = position(pipe.from) ?: return 0.0
        val b = position(pipe.to) ?: return 0.0
        return a.distanceTo(b)
    }

    /** Unit direction of [pipe] pointing away from [node], or null for a degenerate pipe. */
    fun directionFrom(pipe: IsoPipe, node: IsoNodeId): Vec3? {
        val a = position(pipe.from) ?: return null
        val b = position(pipe.to) ?: return null
        val d = if (node == pipe.from) b - a else a - b
        return d.takeIf { it.length > MIN_LENGTH_MM }?.normalized()
    }

    fun other(pipe: IsoPipe, node: IsoNodeId): IsoNodeId = if (pipe.from == node) pipe.to else pipe.from

    fun componentsAt(node: IsoNodeId): List<IsoComponent> = sheet.components.filter { it.nodeId == node }
    fun componentsOn(pipe: IsoPipeId): List<IsoComponent> = sheet.components.filter { it.pipeId == pipe }

    /**
     * Nodes that move together with the far end of [pipe] when the near end [keep] stays.
     * Returns null when [keep] is reachable as well, i.e. the pipe closes a loop.
     */
    fun sideOf(pipe: IsoPipe, keep: IsoNodeId): Set<IsoNodeId>? {
        val start = other(pipe, keep)
        val seen = linkedSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            incident[node].orEmpty().forEach { candidate ->
                if (candidate.id == pipe.id) return@forEach
                val next = other(candidate, node)
                if (next == keep) return null
                if (seen.add(next)) queue.addLast(next)
            }
        }
        return seen
    }

    /** Fittings of every node, in node order. Straight same-size joints are reported as couplings. */
    fun fittings(): List<IsoFitting> = sheet.nodes.mapNotNull { node -> fittingAt(node.id) }

    fun fittingAt(nodeId: IsoNodeId): IsoFitting? {
        val pipes = incident[nodeId].orEmpty()
        if (pipes.isEmpty()) return null
        val system = pipes.groupingBy { it.spec.system }.eachCount().maxByOrNull { it.value }?.key ?: IsoPipeSystem.OTHER
        val sizes = pipes.mapNotNull { it.spec.nominalDiameter }
        return when (pipes.size) {
            1 -> if (componentsAt(nodeId).any { it.kind in TERMINAL_KINDS }) {
                null
            } else {
                IsoFitting(nodeId, IsoFittingKind.END, system, nominalDiameter = pipes[0].spec.nominalDiameter)
            }
            2 -> {
                val first = directionFrom(pipes[0], nodeId) ?: return null
                val second = directionFrom(pipes[1], nodeId) ?: return null
                // Outgoing directions: straight on means they point in opposite senses.
                val bend = 180.0 - Math.toDegrees(acos(first.dot(second).coerceIn(-1.0, 1.0)))
                val large = sizes.maxOrNull()
                val small = sizes.minOrNull()?.takeIf { sizes.size == 2 && it != large }
                when {
                    bend < STRAIGHT_TOLERANCE_DEG && small != null ->
                        IsoFitting(nodeId, IsoFittingKind.REDUCER, system, 0.0, null, large, small)
                    bend < STRAIGHT_TOLERANCE_DEG ->
                        IsoFitting(nodeId, IsoFittingKind.COUPLING, system, 0.0, null, large)
                    else -> IsoFitting(
                        nodeId,
                        IsoFittingKind.ELBOW,
                        system,
                        angleDeg = bend,
                        standardAngleDeg = standardAngle(bend),
                        nominalDiameter = large,
                        branchDiameter = small,
                    )
                }
            }
            3 -> {
                val directions = pipes.map { directionFrom(it, nodeId) }
                // The run is the pair that continues straight through the node.
                var run: Pair<Int, Int>? = null
                for (i in 0 until 3) for (j in i + 1 until 3) {
                    val a = directions[i] ?: continue
                    val b = directions[j] ?: continue
                    if (run == null && a.dot(b) < -STRAIGHT_COSINE) run = i to j
                }
                val runSizes = run?.let { listOfNotNull(pipes[it.first].spec.nominalDiameter, pipes[it.second].spec.nominalDiameter) }
                    ?: sizes
                val branchIndex = run?.let { (0 until 3).first { index -> index != it.first && index != it.second } }
                val branchSize = branchIndex?.let { pipes[it].spec.nominalDiameter } ?: sizes.minOrNull()
                val runSize = runSizes.maxOrNull()
                IsoFitting(
                    nodeId,
                    IsoFittingKind.TEE,
                    system,
                    nominalDiameter = runSize,
                    branchDiameter = branchSize?.takeIf { it != runSize },
                )
            }
            4 -> IsoFitting(
                nodeId,
                IsoFittingKind.CROSS,
                system,
                nominalDiameter = sizes.maxOrNull(),
                branchDiameter = sizes.minOrNull()?.takeIf { it != sizes.maxOrNull() },
            )
            else -> IsoFitting(nodeId, IsoFittingKind.MANIFOLD, system, nominalDiameter = sizes.maxOrNull())
        }
    }

    companion object {
        const val MIN_LENGTH_MM = 0.5
        const val STRAIGHT_TOLERANCE_DEG = 1.0
        private val STRAIGHT_COSINE = kotlin.math.cos(Math.toRadians(STRAIGHT_TOLERANCE_DEG))
        private val STANDARD_ANGLES = listOf(90, 60, 45, 30, 15)
        private const val STANDARD_TOLERANCE_DEG = 1.5

        /** Components that close a run, so an open end there is not a missing fitting. */
        val TERMINAL_KINDS = setOf(
            IsoComponentKind.RADIATOR,
            IsoComponentKind.FIXTURE,
            IsoComponentKind.PLUG,
            IsoComponentKind.AIR_VENT,
            IsoComponentKind.DRAIN,
            IsoComponentKind.METER,
            IsoComponentKind.PUMP,
        )

        fun standardAngle(bendDeg: Double): Int? =
            STANDARD_ANGLES.firstOrNull { abs(it - bendDeg) <= STANDARD_TOLERANCE_DEG }
    }
}

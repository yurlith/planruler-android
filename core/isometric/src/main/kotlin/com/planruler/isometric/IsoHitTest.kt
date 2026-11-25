package com.planruler.isometric

import com.planruler.model.IsoComponentId
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipeId
import com.planruler.model.IsometricSheet

sealed interface IsoHit {
    data class Node(val id: IsoNodeId, val screenDistanceMm: Double) : IsoHit
    data class Component(val id: IsoComponentId, val screenDistanceMm: Double) : IsoHit
    /** [distanceAlongMm] is the true model distance from the pipe's `from` node. */
    data class Pipe(val id: IsoPipeId, val distanceAlongMm: Double, val screenDistanceMm: Double) : IsoHit
}

/** Picks what a finger touched on the sheet: joints first, then fittings, then runs. */
object IsoHitTester {
    fun hit(sheet: IsometricSheet, point: P2, toleranceMm: Double): IsoHit? {
        val projection = IsoProjection.of(sheet.projection)
        val network = IsoNetwork(sheet)
        sheet.nodes
            .map { it.id to projection.project(it.position).distanceTo(point) }
            .filter { it.second <= toleranceMm }
            .minByOrNull { it.second }
            ?.let { return IsoHit.Node(it.first, it.second) }

        sheet.components
            .mapNotNull { component ->
                val center = component.pipeId?.let { pipeId ->
                    val pipe = network.pipesById[pipeId] ?: return@let null
                    val a = network.position(pipe.from)?.let(projection::project) ?: return@let null
                    val b = network.position(pipe.to)?.let(projection::project) ?: return@let null
                    a + (b - a) * component.offset
                } ?: component.nodeId?.let { nodeId -> network.position(nodeId)?.let(projection::project) }
                center?.let { component.id to it.distanceTo(point) }
            }
            .filter { it.second <= toleranceMm }
            .minByOrNull { it.second }
            ?.let { return IsoHit.Component(it.first, it.second) }

        return sheet.pipes
            .mapNotNull { pipe ->
                val a3 = network.position(pipe.from) ?: return@mapNotNull null
                val b3 = network.position(pipe.to) ?: return@mapNotNull null
                val (distance, t) = distanceToSegment(point, projection.project(a3), projection.project(b3))
                if (distance <= toleranceMm) IsoHit.Pipe(pipe.id, t * a3.distanceTo(b3), distance) else null
            }
            .minByOrNull { it.screenDistanceMm }
    }
}

package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyGraph3D
import com.planruler.fabrication3d.AssemblyIssue3D
import com.planruler.fabrication3d.AssemblyIssueCode
import com.planruler.fabrication3d.AssemblyTolerances3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.ObstacleBox3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ParametricPartDefinition3D
import com.planruler.fabrication3d.PartConnection3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.Quaternion
import com.planruler.fabrication3d.Transform3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.Frame3D
import com.planruler.fabrication3d.alignFrame
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal class DefaultAssemblyGraph3D(
    private val limits: EngineLimits3D,
) : AssemblyGraph3D {

    override fun validate(
        assembly: ParametricAssembly3D,
        tolerances: AssemblyTolerances3D,
    ): List<AssemblyIssue3D> {
        val issues = mutableListOf<AssemblyIssue3D>()
        val usedPorts = mutableSetOf<PortReference3D>()
        assembly.connections.forEach { connection ->
            if (!usedPorts.add(connection.first)) {
                issues += AssemblyIssue3D(
                    connection.id,
                    AssemblyIssueCode.PORT_ALREADY_CONNECTED,
                    connection.first.toString(),
                )
            }
            if (!usedPorts.add(connection.second)) {
                issues += AssemblyIssue3D(
                    connection.id,
                    AssemblyIssueCode.PORT_ALREADY_CONNECTED,
                    connection.second.toString(),
                )
            }
            val first = assembly.worldPortOrNull(connection.first) ?: run {
                issues += AssemblyIssue3D(
                    connection.id,
                    AssemblyIssueCode.UNKNOWN_PORT,
                    connection.first.toString(),
                )
                return@forEach
            }
            val second = assembly.worldPortOrNull(connection.second) ?: run {
                issues += AssemblyIssue3D(
                    connection.id,
                    AssemblyIssueCode.UNKNOWN_PORT,
                    connection.second.toString(),
                )
                return@forEach
            }
            if (first.nominalDiameter != second.nominalDiameter ||
                abs(first.outsideDiameterMm - second.outsideDiameterMm) > tolerances.positionMm
            ) {
                issues += AssemblyIssue3D(connection.id, AssemblyIssueCode.INCOMPATIBLE_DIAMETER)
            }
            if (first.connectionKind != second.connectionKind) {
                issues += AssemblyIssue3D(connection.id, AssemblyIssueCode.INCOMPATIBLE_CONNECTION)
            }
            if (connection.axialGapMm < 0.0) {
                issues += AssemblyIssue3D(connection.id, AssemblyIssueCode.NEGATIVE_GAP)
            }
            val delta = second.frame.position - first.frame.position
            val separation = delta.length
            if (abs(separation - connection.axialGapMm) > tolerances.positionMm) {
                issues += AssemblyIssue3D(
                    connection.id,
                    AssemblyIssueCode.GAP_MISMATCH,
                    "expected=${connection.axialGapMm}, actual=$separation",
                )
            }
            if (first.frame.forward.dot(second.frame.forward) > -1.0 + tolerances.direction) {
                issues += AssemblyIssue3D(connection.id, AssemblyIssueCode.PORTS_NOT_FACING)
            }
            if (separation > tolerances.positionMm) {
                val axis = delta.normalizedOrNull()
                if (axis == null || axis.dot(first.frame.forward) < 1.0 - tolerances.direction) {
                    issues += AssemblyIssue3D(connection.id, AssemblyIssueCode.GAP_NOT_AXIAL)
                }
            }
        }
        return issues
    }

    override fun attach(
        assembly: ParametricAssembly3D,
        anchor: PortReference3D,
        newPartId: String,
        newPartCode: String,
        definition: ParametricPartDefinition3D,
        attachingPortId: String,
        axialGapMm: Double,
        rollDeg: Double,
    ): Fabrication3DResult<ParametricAssembly3D> {
        requireIdentifier("newPartId", newPartId)?.let { return it }
        if (assembly.parts.size + 1 > limits.maxParts) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.PARTS, limits.maxParts, assembly.parts.size + 1),
            )
        }
        if (assembly.parts.any { it.id == newPartId }) {
            return invalid("newPartId", ParameterRule3D.DUPLICATE_ID, newPartId)
        }
        requireFinite("rollDeg", rollDeg)?.let { return it }
        requireAtLeast("axialGapMm", axialGapMm, 0.0)?.let { return it }

        val anchorWorld = assembly.worldPortOrNull(anchor)
            ?: return Fabrication3DResult.Failure(Fabrication3DError.UnknownPort(anchor))
        if (anchor in assembly.connectedPorts()) {
            return Fabrication3DResult.Failure(Fabrication3DError.PortNotFree(anchor))
        }
        val localPort = definition.portOrNull(attachingPortId)
            ?: return Fabrication3DResult.Failure(
                Fabrication3DError.UnknownPort(PortReference3D(newPartId, attachingPortId)),
            )
        if (anchorWorld.nominalDiameter != localPort.nominalDiameter ||
            abs(anchorWorld.outsideDiameterMm - localPort.outsideDiameterMm) > DIAMETER_TOLERANCE_MM
        ) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.PortsIncompatible(anchor, ParameterRule3D.DIAMETER_MISMATCH),
            )
        }
        if (anchorWorld.connectionKind != localPort.connectionKind) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.PortsIncompatible(anchor, ParameterRule3D.CONNECTION_MISMATCH),
            )
        }

        return guarded("attach") {
            val attachmentUp = Quaternion.axisAngle(anchorWorld.frame.forward, rollDeg)
                .rotate(anchorWorld.frame.up)
            val targetFrame = Frame3D.of(
                position = anchorWorld.frame.position + anchorWorld.frame.forward * axialGapMm,
                forward = -anchorWorld.frame.forward,
                upHint = attachmentUp,
            )
            val instance = PartInstance3D(
                id = newPartId,
                code = newPartCode,
                definition = definition,
                transform = alignFrame(localPort.frame, targetFrame),
            )
            val connection = PartConnection3D(
                id = "C${assembly.connections.size + 1}",
                first = anchor,
                second = PortReference3D(newPartId, attachingPortId),
                axialGapMm = axialGapMm,
            )
            assembly.copy(
                parts = assembly.parts + instance,
                connections = assembly.connections + connection,
            )
        }
    }

    override fun removePart(
        assembly: ParametricAssembly3D,
        partId: String,
    ): Fabrication3DResult<ParametricAssembly3D> {
        if (assembly.partOrNull(partId) == null) {
            return Fabrication3DResult.Failure(Fabrication3DError.UnknownPart(partId))
        }
        return guarded("removePart") {
            assembly.copy(
                parts = assembly.parts.filterNot { it.id == partId },
                connections = assembly.connections.filterNot {
                    it.first.partId == partId || it.second.partId == partId
                },
            )
        }
    }

    override fun transformed(assembly: ParametricAssembly3D, transform: Transform3D): ParametricAssembly3D =
        assembly.copy(parts = assembly.parts.map { it.copy(transform = transform * it.transform) })

    override fun selfIntersections(
        assembly: ParametricAssembly3D,
        clearanceMm: Double,
    ): List<AssemblyIssue3D> {
        val neighbours = assembly.connections
            .flatMap { listOf(it.first.partId to it.second.partId, it.second.partId to it.first.partId) }
            .toSet()
        val segments = assembly.parts.flatMap(Centerline3D::of)
        val issues = mutableListOf<AssemblyIssue3D>()
        for (i in segments.indices) {
            for (j in i + 1 until segments.size) {
                val first = segments[i]
                val second = segments[j]
                if (first.partId == second.partId) continue
                if ((first.partId to second.partId) in neighbours) continue
                val required = first.outerRadiusMm + second.outerRadiusMm + clearanceMm
                val distance = Centerline3D.distance(first.start, first.end, second.start, second.end)
                if (distance < required - CLEARANCE_EPSILON_MM) {
                    issues += AssemblyIssue3D(
                        connectionId = "${first.partId}/${second.partId}",
                        code = AssemblyIssueCode.SELF_INTERSECTION,
                        details = "distance=$distance, required=$required",
                    )
                }
            }
        }
        return issues.distinctBy { it.connectionId }
    }

    override fun obstructions(
        assembly: ParametricAssembly3D,
        obstacles: List<ObstacleBox3D>,
        clearanceMm: Double,
    ): List<String> {
        if (obstacles.isEmpty()) return emptyList()
        val segments = assembly.parts.flatMap(Centerline3D::of)
        return obstacles.filter { obstacle ->
            segments.any { segment ->
                val margin = segment.outerRadiusMm + clearanceMm
                segmentHitsBox(segment.start, segment.end, obstacle, margin)
            }
        }.map { it.id }
    }

    /** Slab test against the obstacle grown by the swept pipe radius. */
    private fun segmentHitsBox(
        start: Vec3,
        end: Vec3,
        obstacle: ObstacleBox3D,
        marginMm: Double,
    ): Boolean {
        val minimum = obstacle.bounds.minimum - Vec3(marginMm, marginMm, marginMm)
        val maximum = obstacle.bounds.maximum + Vec3(marginMm, marginMm, marginMm)
        val direction = end - start
        var enter = 0.0
        var exit = 1.0
        val startAxes = doubleArrayOf(start.x, start.y, start.z)
        val directionAxes = doubleArrayOf(direction.x, direction.y, direction.z)
        val minimumAxes = doubleArrayOf(minimum.x, minimum.y, minimum.z)
        val maximumAxes = doubleArrayOf(maximum.x, maximum.y, maximum.z)
        for (axis in 0..2) {
            val origin = startAxes[axis]
            val delta = directionAxes[axis]
            if (abs(delta) < 1e-12) {
                if (origin < minimumAxes[axis] || origin > maximumAxes[axis]) return false
            } else {
                val firstHit = (minimumAxes[axis] - origin) / delta
                val secondHit = (maximumAxes[axis] - origin) / delta
                enter = max(enter, min(firstHit, secondHit))
                exit = min(exit, max(firstHit, secondHit))
                if (enter > exit) return false
            }
        }
        return true
    }

    private companion object {
        const val DIAMETER_TOLERANCE_MM = 1e-6
        const val CLEARANCE_EPSILON_MM = 1e-6
    }
}

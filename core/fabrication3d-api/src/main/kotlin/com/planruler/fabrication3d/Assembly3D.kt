package com.planruler.fabrication3d

enum class FabricationPartKind { PIPE, ELBOW, TEE, FLANGE, REDUCER, CAP }

enum class PortConnectionKind { BUTT_WELD, FLANGE_FACE, OPEN_END }

data class PartPort3D(
    val id: String,
    val frame: Frame3D,
    val nominalDiameter: Int,
    val outsideDiameterMm: Double,
    val connectionKind: PortConnectionKind,
)

sealed interface PartGeometry3D

data class StraightPipeGeometry3D(
    val lengthMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) : PartGeometry3D

/** A positive angle turns from local +X towards +Y; a negative angle turns towards -Y. */
data class ElbowGeometry3D(
    val angleDeg: Double,
    val centerlineRadiusMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) : PartGeometry3D

data class WeldNeckFlangeGeometry3D(
    val faceToWeldMm: Double,
    val thicknessMm: Double,
    val outsideDiameterMm: Double,
    val pipeOutsideDiameterMm: Double,
    val pipeWallThicknessMm: Double,
    val boltCircleDiameterMm: Double,
    val boltHoleCount: Int,
    val boltHoleDiameterMm: Double,
) : PartGeometry3D

data class EqualTeeGeometry3D(
    val overallRunMm: Double,
    val branchCenterToEndMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) : PartGeometry3D

/**
 * A reducer runs from the large bore at local origin to the small bore at [lengthMm].
 * An eccentric reducer keeps the local -Z side flat, which is how it is installed to
 * keep a pipe run drainable.
 */
data class ReducerGeometry3D(
    val lengthMm: Double,
    val largeOutsideDiameterMm: Double,
    val largeWallThicknessMm: Double,
    val smallOutsideDiameterMm: Double,
    val smallWallThicknessMm: Double,
    val eccentric: Boolean,
) : PartGeometry3D

data class CapGeometry3D(
    val heightMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) : PartGeometry3D

data class ParametricPartDefinition3D(
    val id: String,
    val catalogId: String?,
    val kind: FabricationPartKind,
    val geometry: PartGeometry3D,
    val ports: List<PartPort3D>,
) {
    init {
        require(id.isNotBlank()) { "Part definition id cannot be blank" }
        require(ports.size >= 2 || kind == FabricationPartKind.CAP) { "A connectable part requires ports" }
        require(ports.map { it.id }.distinct().size == ports.size) { "Port ids must be unique within a part" }
    }

    fun portOrNull(id: String): PartPort3D? = ports.singleOrNull { it.id == id }

    fun port(id: String): PartPort3D = portOrNull(id)
        ?: throw IllegalArgumentException("Unknown port $id on definition ${this.id}")
}

data class PartInstance3D(
    val id: String,
    val code: String,
    val definition: ParametricPartDefinition3D,
    val transform: Transform3D,
) {
    fun worldPortOrNull(portId: String): PartPort3D? = definition.portOrNull(portId)
        ?.let { local -> local.copy(frame = local.frame.transformed(transform)) }

    fun worldPort(portId: String): PartPort3D = worldPortOrNull(portId)
        ?: throw IllegalArgumentException("Unknown port $portId on part $id")
}

data class PortReference3D(
    val partId: String,
    val portId: String,
) {
    override fun toString(): String = "$partId:$portId"
}

data class PartConnection3D(
    val id: String,
    val first: PortReference3D,
    val second: PortReference3D,
    val axialGapMm: Double = 0.0,
)

data class AssemblyMetadata3D(
    val name: String,
    val nominalDiameter: Int,
    val pressureClass: Int?,
    val sourceCalculation: String,
)

/** Fabrication tolerances; defaults are the closure limits used by the shop drawings. */
data class AssemblyTolerances3D(
    val positionMm: Double = 1e-5,
    val direction: Double = 1e-7,
) {
    init {
        require(positionMm >= 0.0 && positionMm.isFinite()) { "Position tolerance must be finite and non-negative" }
        require(direction >= 0.0 && direction.isFinite()) { "Direction tolerance must be finite and non-negative" }
    }
}

/**
 * The parametric assembly graph. This type carries data and pure lookups only; every
 * operation that changes it lives behind [AssemblyGraph3D] so the rules have one home.
 */
data class ParametricAssembly3D(
    val schemaVersion: Int = CURRENT_ASSEMBLY_3D_SCHEMA,
    val metadata: AssemblyMetadata3D,
    val parts: List<PartInstance3D>,
    val connections: List<PartConnection3D>,
) {
    init {
        require(schemaVersion > 0) { "Assembly schema version must be positive" }
        require(parts.map { it.id }.distinct().size == parts.size) { "Part instance ids must be unique" }
        require(connections.map { it.id }.distinct().size == connections.size) { "Connection ids must be unique" }
    }

    fun partOrNull(id: String): PartInstance3D? = parts.singleOrNull { it.id == id }

    fun part(id: String): PartInstance3D = partOrNull(id)
        ?: throw IllegalArgumentException("Unknown assembly part $id")

    fun worldPortOrNull(reference: PortReference3D): PartPort3D? =
        partOrNull(reference.partId)?.worldPortOrNull(reference.portId)

    fun worldPort(reference: PortReference3D): PartPort3D = worldPortOrNull(reference)
        ?: throw IllegalArgumentException("Unknown assembly port $reference")

    fun connectedPorts(): Set<PortReference3D> = connections
        .flatMapTo(linkedSetOf()) { listOf(it.first, it.second) }

    fun freePorts(): List<PortReference3D> {
        val connected = connectedPorts()
        return parts.flatMap { part ->
            part.definition.ports.map { PortReference3D(part.id, it.id) }
        }.filterNot(connected::contains)
    }

    /** Connections that touch [partId], in declaration order. */
    fun connectionsOf(partId: String): List<PartConnection3D> =
        connections.filter { it.first.partId == partId || it.second.partId == partId }

    fun elbowCount(): Int = parts.count { it.definition.kind == FabricationPartKind.ELBOW }

    fun pipeCount(): Int = parts.count { it.definition.kind == FabricationPartKind.PIPE }
}

enum class AssemblyIssueCode {
    UNKNOWN_PORT,
    PORT_ALREADY_CONNECTED,
    INCOMPATIBLE_DIAMETER,
    INCOMPATIBLE_CONNECTION,
    NEGATIVE_GAP,
    GAP_MISMATCH,
    GAP_NOT_AXIAL,
    PORTS_NOT_FACING,
    SELF_INTERSECTION,
}

data class AssemblyIssue3D(
    val connectionId: String,
    val code: AssemblyIssueCode,
    val details: String? = null,
)

const val CURRENT_ASSEMBLY_3D_SCHEMA = 1

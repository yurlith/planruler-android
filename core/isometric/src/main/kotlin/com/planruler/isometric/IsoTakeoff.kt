package com.planruler.isometric

import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsometricSheet
import kotlin.math.abs
import kotlin.math.roundToInt

data class IsoPipeTotal(
    val system: IsoPipeSystem,
    val nominalDiameter: Int?,
    val material: String?,
    val runCount: Int,
    val lengthMm: Double,
    /** Part of [lengthMm] that runs vertically (risers and drops). */
    val verticalMm: Double,
)

data class IsoFittingTotal(
    val kind: IsoFittingKind,
    val system: IsoPipeSystem,
    val nominalDiameter: Int?,
    val branchDiameter: Int?,
    /** Catalogue angle for elbows; null for other kinds or special bends. */
    val standardAngleDeg: Int?,
    /** Rounded bend of a special elbow, so 37° bends are not merged with 52° bends. */
    val specialAngleDeg: Int?,
    val count: Int,
)

data class IsoComponentTotal(val kind: IsoComponentKind, val nominalDiameter: Int?, val count: Int)

data class IsoBillOfMaterials(
    val pipes: List<IsoPipeTotal>,
    val fittings: List<IsoFittingTotal>,
    val components: List<IsoComponentTotal>,
) {
    val totalLengthMm: Double get() = pipes.sumOf { it.lengthMm }
    val elbowCount: Int get() = fittings.filter { it.kind == IsoFittingKind.ELBOW }.sumOf { it.count }
    val teeCount: Int get() = fittings.filter { it.kind == IsoFittingKind.TEE }.sumOf { it.count }
}

/** The bill of materials of a sheet: one calculation for the screen, CSV and drawings. */
object IsoTakeoff {
    fun of(sheet: IsometricSheet): IsoBillOfMaterials {
        val network = IsoNetwork(sheet)
        val pipes = sheet.pipes
            .groupBy { Triple(it.spec.system, it.spec.nominalDiameter, it.spec.material?.trim()?.takeIf(String::isNotEmpty)) }
            .map { (key, group) ->
                var vertical = 0.0
                val length = group.sumOf { pipe ->
                    val a = network.position(pipe.from) ?: return@sumOf 0.0
                    val b = network.position(pipe.to) ?: return@sumOf 0.0
                    vertical += abs(b.z - a.z).takeIf { horizontalLength(a, b) < VERTICAL_SLACK_MM } ?: 0.0
                    a.distanceTo(b)
                }
                IsoPipeTotal(key.first, key.second, key.third, group.size, length, vertical)
            }
            .sortedWith(compareBy<IsoPipeTotal> { it.system.ordinal }.thenByDescending { it.nominalDiameter ?: 0 })

        val fittings = network.fittings()
            .filter { it.kind != IsoFittingKind.COUPLING }
            .groupBy {
                FittingKey(
                    it.kind,
                    it.system,
                    it.nominalDiameter,
                    it.branchDiameter,
                    it.standardAngleDeg,
                    if (it.kind == IsoFittingKind.ELBOW && it.standardAngleDeg == null) it.angleDeg?.roundToInt() else null,
                )
            }
            .map { (key, group) ->
                IsoFittingTotal(
                    key.kind,
                    key.system,
                    key.nominal,
                    key.branch,
                    key.standardAngle,
                    key.specialAngle,
                    group.size,
                )
            }
            .sortedWith(
                compareBy<IsoFittingTotal> { it.kind.ordinal }
                    .thenBy { it.system.ordinal }
                    .thenByDescending { it.nominalDiameter ?: 0 }
                    .thenByDescending { it.standardAngleDeg ?: it.specialAngleDeg ?: 0 },
            )

        val components = sheet.components
            .groupBy { component ->
                val size = component.pipeId?.let { network.pipesById[it]?.spec?.nominalDiameter }
                    ?: component.nodeId?.let { node -> network.incident[node].orEmpty().mapNotNull { it.spec.nominalDiameter }.maxOrNull() }
                component.kind to size
            }
            .map { (key, group) -> IsoComponentTotal(key.first, key.second, group.size) }
            .sortedWith(compareBy<IsoComponentTotal> { it.kind.ordinal }.thenByDescending { it.nominalDiameter ?: 0 })

        return IsoBillOfMaterials(pipes, fittings, components)
    }

    private fun horizontalLength(a: Vec3, b: Vec3) = kotlin.math.hypot(b.x - a.x, b.y - a.y)

    private data class FittingKey(
        val kind: IsoFittingKind,
        val system: IsoPipeSystem,
        val nominal: Int?,
        val branch: Int?,
        val standardAngle: Int?,
        val specialAngle: Int?,
    )

    private const val VERTICAL_SLACK_MM = 1.0
}

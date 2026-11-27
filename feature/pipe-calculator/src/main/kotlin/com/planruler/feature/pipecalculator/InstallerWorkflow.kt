package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.OffsetPlacement3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

internal enum class InstallerCalculationPath { AUTO_ROUTE, MANUAL_TEMPLATE }

/** Installer terms translated once into the coordinate frame understood by the 3D engine. */
internal data class InstallerRouteRequest(
    val taskType: InstallationTaskType,
    val calculationPath: InstallerCalculationPath,
    val target: Vec3,
    val endDirection: Vec3,
    val preferredAngleDeg: Double?,
    val minimumStraightMm: Double,
    val manualTemplate: ChainPlan3D? = null,
    val referenceCorrectionMm: Double = 0.0,
    /** Start face; moved by the site weld gap when the run starts from an existing pipe end. */
    val start: Vec3 = Vec3.ZERO,
    val startTerminal: ChainTerminal3D = ChainTerminal3D.FLANGE,
    val endTerminal: ChainTerminal3D = ChainTerminal3D.FLANGE,
    /** Gap left at each site weld to an existing pipe; zero when both ends are flanges. */
    val siteWeldGapMm: Double = 0.0,
    /** The far point exactly as measured on site, before any weld gap is taken off. */
    val measuredTarget: Vec3 = target,
    /** Where a two-elbow offset sits between the ends; other routes ignore it. */
    val offsetPlacement: OffsetPlacement3D = OffsetPlacement3D.CENTERED,
)

/** Tasks whose ends stay on one axis: the end always faces forward, whatever an older job said. */
private val forwardTasks = setOf(
    InstallationTaskType.STRAIGHT_INSERT,
    InstallationTaskType.FLAT_OFFSET,
    InstallationTaskType.ROLLING_OFFSET,
    InstallationTaskType.FLANGED_OFFSET,
)

/**
 * The fitter says what was touched at each end. A flange face is a flange; anything else is
 * an existing pipe or fitting the spool is welded to on site, so that end stays open and gives
 * up one weld gap.
 */
internal fun InstallationMeasurementReference.terminal(): ChainTerminal3D =
    if (this == InstallationMeasurementReference.FLANGE_FACE) ChainTerminal3D.FLANGE else ChainTerminal3D.OPEN_END

internal fun installerRouteRequest(job: InstallationJob): InstallerRouteRequest {
    val input = job.input
    val pipe = PIPE_INSTALLATION_SERIES.firstOrNull { it.dn == input.nominalDiameter }
    val radius = (pipe?.outsideDiameterMm ?: 0.0) / 2.0
    val correction = radialReferenceOffset(input.endReference, radius) -
        radialReferenceOffset(input.startReference, radius)
    // Edge references correct the size that was actually taped: sideways, or the rise of a
    // flat offset that climbs instead of stepping aside.
    val climbs = job.taskType == InstallationTaskType.FLAT_OFFSET &&
        input.lateralOffsetMm == 0.0 && input.verticalOffsetMm > 0.0
    val lateralMagnitude = if (climbs) 0.0 else (input.lateralOffsetMm + correction).coerceAtLeast(0.0)
    val verticalMagnitude = if (climbs) (input.verticalOffsetMm + correction).coerceAtLeast(0.0) else input.verticalOffsetMm
    // The world is right-handed and stands on Z: walking along +X, the right-hand side is -Y.
    // (Written as 0 - x so that a zero size stays +0.0 and compares equal.)
    val lateral = when (input.lateralDirection) {
        InstallationLateralDirection.LEFT -> lateralMagnitude
        InstallationLateralDirection.RIGHT -> 0.0 - lateralMagnitude
    }
    val vertical = when (input.verticalDirection) {
        InstallationVerticalDirection.UP -> verticalMagnitude
        InstallationVerticalDirection.DOWN -> 0.0 - verticalMagnitude
    }
    val taskTarget = when (job.taskType) {
        InstallationTaskType.STRAIGHT_INSERT -> Vec3(input.alongMm, 0.0, 0.0)
        // A flat offset steps either sideways or up/down; the draft keeps the other size at zero.
        else -> Vec3(input.alongMm, lateral, vertical)
    }
    val path = when (job.taskType) {
        InstallationTaskType.OBSTACLE_BYPASS,
        InstallationTaskType.TEE_BRANCH,
        InstallationTaskType.REDUCER,
        InstallationTaskType.MANUAL,
        -> InstallerCalculationPath.MANUAL_TEMPLATE
        else -> InstallerCalculationPath.AUTO_ROUTE
    }
    val endDirection = if (job.taskType in forwardTasks) Vec3.UNIT_X else input.endDirection.vector()
    // Prepared templates and the flange-to-flange spool are flanged by definition.
    val referencesDecide = path == InstallerCalculationPath.AUTO_ROUTE && job.taskType != InstallationTaskType.FLANGED_OFFSET
    val startTerminal = if (referencesDecide) input.startReference.terminal() else ChainTerminal3D.FLANGE
    val endTerminal = if (referencesDecide) input.endReference.terminal() else ChainTerminal3D.FLANGE
    val gap = input.weldGapMm.coerceAtLeast(0.0)
    val start = if (startTerminal == ChainTerminal3D.OPEN_END) Vec3.UNIT_X * gap else Vec3.ZERO
    val target = if (endTerminal == ChainTerminal3D.OPEN_END) taskTarget - endDirection * gap else taskTarget
    return InstallerRouteRequest(
        taskType = job.taskType,
        calculationPath = path,
        target = target,
        endDirection = endDirection,
        preferredAngleDeg = input.angleDeg.takeIf { job.taskType != InstallationTaskType.STRAIGHT_INSERT },
        minimumStraightMm = input.minimumStraightMm,
        manualTemplate = if (path == InstallerCalculationPath.MANUAL_TEMPLATE) {
            manualTemplate(job.taskType, input, lateral, vertical)
        } else {
            null
        },
        referenceCorrectionMm = correction,
        start = start,
        startTerminal = startTerminal,
        endTerminal = endTerminal,
        siteWeldGapMm = if (startTerminal == ChainTerminal3D.OPEN_END || endTerminal == ChainTerminal3D.OPEN_END) gap else 0.0,
        measuredTarget = taskTarget,
        offsetPlacement = when (input.offsetPlacement) {
            InstallationOffsetPlacement.START -> OffsetPlacement3D.Share(0.0)
            InstallationOffsetPlacement.CENTER -> OffsetPlacement3D.CENTERED
            InstallationOffsetPlacement.END -> OffsetPlacement3D.Share(1.0)
            InstallationOffsetPlacement.EXACT -> OffsetPlacement3D.LeadCut(
                input.offsetLeadMm.coerceAtLeast(input.minimumStraightMm),
            )
        },
    )
}

/**
 * The 2D spool calculation behind the workshop's hero, cut list, stock plan and passport, and
 * the profile the 3D card is built from.
 *
 * For flange to flange it is the fitter's spool, laid out from the same measured target the 3D
 * route closes on and split around the offset with the route solver's own rule
 * ([OffsetPlacement3D.leadCutMm]) and shortest cut, so both calculations give one set of cuts
 * for every placement and angle. Any other task only borrows the catalog parts of a spool large
 * enough to always lay out; its real pieces come from the route.
 */
internal fun workshopAssembly(job: InstallationJob): FlangedOffsetAssemblyResult {
    val input = job.input
    if (job.taskType != InstallationTaskType.FLANGED_OFFSET) return borrowedCatalogSpool(input)
    val request = installerRouteRequest(job)
    val measured = request.measuredTarget
    val spool = FlangedOffsetAssemblyInput(
        dn = input.nominalDiameter,
        pn = input.pressureClass,
        targetOffsetMm = hypot(measured.y, measured.z),
        overallFaceToFaceMm = measured.x,
        angleDeg = input.angleDeg,
        weldGapMm = input.weldGapMm,
        quantity = input.quantity,
        stockLengthMm = input.stockLengthMm.toDouble(),
        sawKerfMm = input.sawKerfMm,
    )
    // The centred spool fixes the geometry: the straight both tails share and the catalog
    // profile the route is solved with. If even the centred spool cannot be laid out, no
    // placement can, so its refusal stands.
    val centred = calculateFlangedOffsetAssembly(spool)
    val minimumCut = centred.toAssemblyProfile3D().rules.minimumCutMm(request.minimumStraightMm)
    val spare = centred.inletPipeCutMm + centred.outletPipeCutMm
    return calculateFlangedOffsetAssembly(
        spool.copy(
            inletPipeCutMm = request.offsetPlacement.leadCutMm(spare, minimumCut),
            minimumPipeCutMm = minimumCut,
        ),
    )
}

/**
 * A spool that always lays out, lent to every task whose pieces come from the 3D route: only its
 * catalog parts (pipe, elbow and flange for the job's DN/PN) and its weld gap are used. Its size,
 * quantity and stock are fixed, so a long run, a large offset or a short stock bar on the job can
 * never make the borrowed spool fail and take the 3D card with it (a 13 m run used to borrow a
 * 13 m spool whose tails no longer fitted a 6 m bar).
 */
internal fun borrowedCatalogSpool(input: InstallationJobInput): FlangedOffsetAssemblyResult =
    calculateFlangedOffsetAssembly(
        FlangedOffsetAssemblyInput(
            dn = input.nominalDiameter,
            pn = input.pressureClass,
            targetOffsetMm = 500.0,
            overallFaceToFaceMm = 4_000.0,
            // Only the catalog profile is borrowed. A large DN at 90° would not fit
            // this dummy spool, even when the installer's real route is feasible.
            angleDeg = 45.0,
            weldGapMm = input.weldGapMm,
        ),
    )

private fun radialReferenceOffset(reference: InstallationMeasurementReference, radiusMm: Double): Double =
    when (reference) {
        InstallationMeasurementReference.PIPE_OUTER_EDGE -> radiusMm
        InstallationMeasurementReference.PIPE_INNER_EDGE -> -radiusMm
        else -> 0.0
    }

private fun InstallationEndDirection.vector(): Vec3 = when (this) {
    InstallationEndDirection.FORWARD -> Vec3.UNIT_X
    InstallationEndDirection.BACK -> -Vec3.UNIT_X
    InstallationEndDirection.LEFT -> Vec3.UNIT_Y
    InstallationEndDirection.RIGHT -> -Vec3.UNIT_Y
    InstallationEndDirection.UP -> Vec3.UNIT_Z
    InstallationEndDirection.DOWN -> -Vec3.UNIT_Z
}

private fun manualTemplate(
    task: InstallationTaskType,
    input: InstallationJobInput,
    lateral: Double,
    vertical: Double,
): ChainPlan3D {
    val straight = max(input.minimumStraightMm, input.alongMm / 4.0)
    val transfer = max(input.minimumStraightMm, hypot(lateral, vertical))
    val roll = Math.toDegrees(atan2(vertical, lateral.takeIf { it != 0.0 } ?: 1.0))
    val angle = input.angleDeg.coerceIn(1.0, 179.0)
    return when (task) {
        InstallationTaskType.OBSTACLE_BYPASS -> ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(straight),
                ChainStep3D.Elbow(angle, roll),
                ChainStep3D.Pipe(transfer),
                ChainStep3D.Elbow(-angle, 0.0),
                ChainStep3D.Pipe(straight),
                ChainStep3D.Flange(),
            ),
        )
        InstallationTaskType.TEE_BRANCH -> ChainPlan3D(
            steps = listOf(
                ChainStep3D.Pipe(straight),
                ChainStep3D.Tee(
                    rollDeg = roll,
                    branch = listOf(ChainStep3D.Pipe(transfer), ChainStep3D.Flange()),
                ),
                ChainStep3D.Pipe(straight),
                ChainStep3D.Flange(),
            ),
        )
        InstallationTaskType.REDUCER -> {
            val series = PIPE_INSTALLATION_SERIES
            val currentIndex = series.indexOfFirst { it.dn == input.nominalDiameter }
            val smaller = series.getOrNull((currentIndex - 1).coerceAtLeast(0)) ?: series.first()
            ChainPlan3D(
                steps = listOf(
                    ChainStep3D.Pipe(straight),
                    ChainStep3D.Reducer(
                        lengthMm = max(60.0, smaller.outsideDiameterMm * 1.5),
                        smallNominalDiameter = smaller.dn,
                        smallOutsideDiameterMm = smaller.outsideDiameterMm,
                        smallWallThicknessMm = smaller.wallThicknessMm,
                    ),
                    ChainStep3D.Pipe(straight),
                    ChainStep3D.Flange(),
                ),
            )
        }
        InstallationTaskType.MANUAL -> ChainPlan3D()
        else -> ChainPlan3D()
    }
}

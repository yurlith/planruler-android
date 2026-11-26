package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ChainTerminal3D
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** Elbow angles a fitter can pick for an offset, smallest first. */
internal val OFFSET_ELBOW_ANGLES = listOf(15.0, 22.5, 30.0, 45.0, 60.0, 90.0)

/**
 * The set-out of a two-elbow offset between parallel ends, from the same profile the route
 * solver builds with: how far apart the bend centres are, where they sit from the start
 * point, and how much room the offset needs. All lengths are millimetres along the run as
 * measured on site, so the numbers can be marked straight onto the pipe.
 */
internal data class OffsetSetOut(
    val angleDeg: Double,
    /** True offset between the two pipe axes. */
    val offsetMm: Double,
    /** Turn of the offset plane away from the vertical; null when the offset stays in one axis. */
    val rollDeg: Double?,
    /** Between the two bend centres, along the sloped piece. */
    val travelMm: Double,
    /** Between the two bend centres, along the run. */
    val advanceMm: Double,
    val takeoutMm: Double,
    val alongMm: Double,
    val minimumAlongMm: Double,
    val minimumOffsetMm: Double,
    /** Longest cut that may go before the offset and still leave the shortest cut after it. */
    val maximumLeadMm: Double,
    private val startAllowanceMm: Double,
    private val weldGapMm: Double,
) {
    val alongFits: Boolean get() = alongMm + TOLERANCE_MM >= minimumAlongMm
    val offsetFits: Boolean get() = offsetMm + TOLERANCE_MM >= minimumOffsetMm
    val fits: Boolean get() = alongFits && offsetFits

    /** From the measured start point to the first bend centre, for a given cut before the offset. */
    fun firstBendMm(leadCutMm: Double): Double = startAllowanceMm + leadCutMm + weldGapMm + takeoutMm

    fun secondBendMm(leadCutMm: Double): Double = firstBendMm(leadCutMm) + advanceMm

    private companion object {
        const val TOLERANCE_MM = 1e-6
    }
}

/**
 * Lays out the offset the request asks for at [angleDeg], or returns null when the request is
 * not a two-elbow offset or its ends do not actually step apart.
 */
internal fun offsetSetOut(request: InstallerRouteRequest, profile: AssemblyProfile3D, angleDeg: Double): OffsetSetOut? {
    if (!request.taskType.isOffset() || request.calculationPath != InstallerCalculationPath.AUTO_ROUTE) return null
    if (!(angleDeg > 0.0 && angleDeg <= 90.0)) return null
    val measured = request.measuredTarget
    val offset = hypot(measured.y, measured.z)
    if (offset < 1e-6) return null

    val radians = Math.toRadians(angleDeg)
    val travel = offset / sin(radians)
    val advance = if (angleDeg >= 90.0) 0.0 else offset / tan(radians)
    val takeout = profile.elbow.centerlineRadiusMm * tan(radians / 2.0)
    val gap = profile.weldGapMm
    val minimumCut = maxOf(request.minimumStraightMm, profile.rules.minPipeLengthMm)
    // What each end adds to the run besides the straight cuts: a flange with its weld, or the
    // gap left for the site weld to an existing pipe.
    val flangeAllowance = profile.flange.faceToWeldMm + gap
    val startAllowance = if (request.startTerminal == ChainTerminal3D.FLANGE) flangeAllowance else request.siteWeldGapMm
    val endAllowance = if (request.endTerminal == ChainTerminal3D.FLANGE) flangeAllowance else request.siteWeldGapMm
    val fixed = startAllowance + endAllowance + 2.0 * gap + 2.0 * takeout + advance

    return OffsetSetOut(
        angleDeg = angleDeg,
        offsetMm = offset,
        rollDeg = if (abs(measured.y) > 1e-6 && abs(measured.z) > 1e-6) {
            Math.toDegrees(atan2(abs(measured.y), abs(measured.z)))
        } else {
            null
        },
        travelMm = travel,
        advanceMm = advance,
        takeoutMm = takeout,
        alongMm = measured.x,
        minimumAlongMm = fixed + 2.0 * minimumCut,
        minimumOffsetMm = (2.0 * takeout + 2.0 * gap + minimumCut) * sin(radians),
        maximumLeadMm = measured.x - fixed - minimumCut,
        startAllowanceMm = startAllowance,
        weldGapMm = gap,
    )
}

/** Standard angles at which the requested offset fits between its ends. */
internal fun fittingOffsetAngles(request: InstallerRouteRequest, profile: AssemblyProfile3D): List<Double> =
    OFFSET_ELBOW_ANGLES.filter { angle -> offsetSetOut(request, profile, angle)?.fits == true }

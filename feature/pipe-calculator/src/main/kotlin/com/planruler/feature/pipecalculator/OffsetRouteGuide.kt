package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.OffsetPlacement3D
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
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

    /** Whether an exact cut of [leadCutMm] before the offset still leaves the shortest cut after it. */
    fun leadFits(leadCutMm: Double): Boolean = leadCutMm <= maximumLeadMm + TOLERANCE_MM

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
    val minimumCut = profile.rules.minimumCutMm(request.minimumStraightMm)
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
    OFFSET_ELBOW_ANGLES.filter { angle ->
        val setOut = offsetSetOut(request, profile, angle) ?: return@filter false
        val lead = request.offsetPlacement as? OffsetPlacement3D.LeadCut
        setOut.fits && (lead == null || setOut.leadFits(lead.lengthMm))
    }

/**
 * True when the offset of [request] cannot be laid out at its own elbow angle: the run is too
 * short, the offset too small, or an exact cut before the offset leaves no pipe after it.
 */
internal fun offsetDoesNotFit(request: InstallerRouteRequest, profile: AssemblyProfile3D): Boolean {
    val angle = request.preferredAngleDeg ?: return false
    val setOut = offsetSetOut(request, profile, angle) ?: return false
    val lead = request.offsetPlacement as? OffsetPlacement3D.LeadCut
    return !setOut.fits || (lead != null && !setOut.leadFits(lead.lengthMm))
}

/** What the screen needs to say why an offset does not fit, and which change would make it fit. */
internal data class OffsetFitAdvice(val request: InstallerRouteRequest, val profile: AssemblyProfile3D)

/**
 * Advice for an offset job that cannot be laid out as measured. Null when the job is not an
 * offset, or when it fails for another reason (an unknown flange, a cut longer than the stock
 * bar), which the screen then reports as it is.
 */
internal fun offsetFitAdvice(job: InstallationJob): OffsetFitAdvice? {
    if (!job.taskType.isOffset()) return null
    val request = installerRouteRequest(job)
    val profile = catalogProfile(job.input) ?: return null
    return OffsetFitAdvice(request, profile).takeIf { offsetDoesNotFit(request, profile) }
}

/**
 * The catalog profile the 3D card solves this job with, before any advanced override. It
 * depends only on the catalog parts and the weld gap, so it is borrowed from a spool that always
 * lays out, and is there even when the fitter's own spool is not.
 */
private fun catalogProfile(input: InstallationJobInput): AssemblyProfile3D? =
    runCatching { borrowedCatalogSpool(input).toAssemblyProfile3D() }.getOrNull()

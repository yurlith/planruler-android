package com.planruler.pipecalculator

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tan

/** Elbow angles sold as stock fittings, in the order a fitter reaches for them. */
val STANDARD_OFFSET_ANGLES: List<Double> = listOf(15.0, 22.5, 30.0, 45.0, 60.0, 90.0)

/**
 * Centre-to-end ("take-out") of a bend with centreline radius R: A = R × tan(α / 2).
 * Long-radius (3D) elbows of every angle share the radius of their DN, so one catalogue
 * radius gives the take-out of a 15° to 90° elbow.
 */
fun bendTakeoutMm(centerlineRadiusMm: Double, angleDeg: Double): Double {
    positive(centerlineRadiusMm, "Centreline radius")
    within(angleDeg, 1.0, 90.0, "Angle")
    return centerlineRadiusMm * tan(Math.toRadians(angleDeg / 2.0))
}

/**
 * An offset ("Etage") between two parallel runs, made of two equal elbows and one straight
 * piece. [firstMm] is the offset measured in one plane (usually the rise between the axes),
 * [secondMm] the offset in the other plane for a rolling offset, or zero.
 */
data class PipeOffsetInput(
    val firstMm: Double,
    val secondMm: Double = 0.0,
    val angleDeg: Double,
    /** Centre-to-end of one elbow; the Z dimension for press and socket fittings. */
    val takeoutMm: Double,
    /** Weld gap or allowance at each of the two joints of the piece. */
    val jointGapMm: Double = 0.0,
)

data class PipeOffsetResult(
    /** True offset between the axes, T = √(first² + second²). */
    val offsetMm: Double,
    /** Turn of the offset plane away from the first direction; zero for a plain offset. */
    val rollDeg: Double,
    val angleDeg: Double,
    /** Elbow centre to elbow centre along the sloped leg, L = T / sin α. */
    val travelMm: Double,
    /** How far the second run starts further along, X = T / tan α (zero at 90°). */
    val advanceMm: Double,
    val takeoutMm: Double,
    val jointGapMm: Double,
    /** Between the two elbow ends, F = L − 2A. */
    val faceToFaceMm: Double,
    /** Length to cut, C = L − 2A − 2g. Not positive when the elbows do not fit. */
    val cutLengthMm: Double,
    /** The smallest offset these elbows can make, where C reaches zero. */
    val minimumOffsetMm: Double,
) {
    val fits: Boolean get() = cutLengthMm > 0.0
}

fun pipeOffset(input: PipeOffsetInput): PipeOffsetResult {
    nonNegative(input.firstMm, "Offset")
    nonNegative(input.secondMm, "Second offset")
    val offset = positive(hypot(input.firstMm, input.secondMm), "Offset")
    within(input.angleDeg, 1.0, 90.0, "Angle")
    val takeout = nonNegative(input.takeoutMm, "Elbow take-out")
    val gap = nonNegative(input.jointGapMm, "Joint gap")
    val radians = Math.toRadians(input.angleDeg)
    val travel = offset / sin(radians)
    // tan(90°) is not exactly infinite in floating point; a right-angle offset has no advance.
    val advance = if (abs(input.angleDeg - 90.0) < 1e-9) 0.0 else offset / tan(radians)
    val faceToFace = travel - 2.0 * takeout
    return PipeOffsetResult(
        offsetMm = offset,
        rollDeg = Math.toDegrees(atan2(input.secondMm, input.firstMm)),
        angleDeg = input.angleDeg,
        travelMm = travel,
        advanceMm = advance,
        takeoutMm = takeout,
        jointGapMm = gap,
        faceToFaceMm = faceToFace,
        cutLengthMm = faceToFace - 2.0 * gap,
        minimumOffsetMm = (2.0 * takeout + 2.0 * gap) * sin(radians),
    )
}

/**
 * The elbow angle that makes [offsetMm] within the available [advanceMm] along the run:
 * α = atan(T / X). Used when the place, not the fitting, fixes the geometry.
 */
fun offsetAngleForAdvanceDeg(offsetMm: Double, advanceMm: Double): Double {
    positive(offsetMm, "Offset")
    nonNegative(advanceMm, "Advance")
    return Math.toDegrees(atan2(offsetMm, advanceMm))
}

/** The stock angle closest to [angleDeg]; ties go to the smaller, gentler angle. */
fun nearestStandardOffsetAngle(angleDeg: Double): Double =
    STANDARD_OFFSET_ANGLES.minWith(compareBy<Double> { abs(it - angleDeg) }.thenBy { it })

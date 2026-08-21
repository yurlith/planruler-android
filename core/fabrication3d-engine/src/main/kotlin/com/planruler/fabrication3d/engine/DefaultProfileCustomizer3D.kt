package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.AssemblyProfileOverrides3D
import com.planruler.fabrication3d.ElbowRadiusMode3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.FlangeProfile3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ProfileCustomizer3D
import com.planruler.fabrication3d.fold

/**
 * Applies the fitter's overrides to a catalog profile. Every rejection names the field
 * and the rule it broke, so the screen can explain the refusal without guessing.
 */
internal class DefaultProfileCustomizer3D(
    private val limits: EngineLimits3D,
) : ProfileCustomizer3D {

    override fun apply(
        profile: AssemblyProfile3D,
        overrides: AssemblyProfileOverrides3D,
    ): Fabrication3DResult<AssemblyProfile3D> {
        if (overrides.isEmpty) return ok(profile)

        val pipeWall = overrides.pipeWallThicknessMm ?: profile.pipe.wallThicknessMm
        requireTube("pipe.wallThicknessMm", "pipe.wallThicknessMm", profile.pipe.outsideDiameterMm, pipeWall)
            ?.let { return it }

        val elbowWall = overrides.elbowWallThicknessMm ?: profile.elbow.wallThicknessMm
        requireTube("elbow.wallThicknessMm", "elbow.wallThicknessMm", profile.elbow.outsideDiameterMm, elbowWall)
            ?.let { return it }

        val radius = resolveRadius(profile, overrides).fold({ it }, { return Fabrication3DResult.Failure(it) })
        if (radius <= profile.elbow.outsideDiameterMm / 2.0) {
            return invalid(
                "elbowCenterlineRadiusMm",
                ParameterRule3D.RADIUS_TOO_SMALL,
                radius,
                profile.elbow.outsideDiameterMm / 2.0,
            )
        }

        val weldGap = overrides.weldGapMm ?: profile.rules.weldGapMm
        requireAtLeast("weldGapMm", weldGap, 0.0)?.let { return it }
        if (weldGap > MAX_WELD_GAP_MM) {
            return invalid("weldGapMm", ParameterRule3D.ABOVE_MAXIMUM, weldGap, MAX_WELD_GAP_MM)
        }

        val minPipe = overrides.minPipeLengthMm ?: profile.rules.minPipeLengthMm
        requirePositive("minPipeLengthMm", minPipe)?.let { return it }

        val maxElbows = overrides.maxElbows ?: profile.rules.maxElbows
        if (maxElbows < 0) return invalid("maxElbows", ParameterRule3D.MUST_BE_POSITIVE, maxElbows)
        if (maxElbows > limits.maxElbows) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, limits.maxElbows, maxElbows),
            )
        }

        val angles = overrides.allowedElbowAnglesDeg ?: profile.rules.allowedElbowAnglesDeg
        if (angles.isEmpty()) return invalid("allowedElbowAnglesDeg", ParameterRule3D.EMPTY_SET)
        // The solver evaluates one candidate route per allowed angle, so the set is a quota.
        if (angles.size > limits.maxRouteCandidates) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(
                    EngineQuota3D.ROUTE_CANDIDATES,
                    limits.maxRouteCandidates,
                    angles.size,
                ),
            )
        }
        angles.forEachIndexed { index, angle ->
            requireFinite("allowedElbowAnglesDeg[$index]", angle)?.let { return it }
            if (angle <= 0.0 || angle > 180.0) {
                return invalid("allowedElbowAnglesDeg[$index]", ParameterRule3D.OUT_OF_RANGE, angle, "0 < a <= 180")
            }
        }

        val minAngle = overrides.minElbowAngleDeg ?: profile.rules.minElbowAngleDeg
        val maxAngle = overrides.maxElbowAngleDeg ?: profile.rules.maxElbowAngleDeg
        requirePositive("minElbowAngleDeg", minAngle)?.let { return it }
        requirePositive("maxElbowAngleDeg", maxAngle)?.let { return it }
        if (maxAngle > 180.0) {
            return invalid("maxElbowAngleDeg", ParameterRule3D.ABOVE_MAXIMUM, maxAngle, 180.0)
        }
        if (minAngle > maxAngle) {
            return invalid("minElbowAngleDeg", ParameterRule3D.OUT_OF_RANGE, minAngle, maxAngle)
        }

        val flange = resolveFlange(profile, overrides)
            .fold({ it }, { return Fabrication3DResult.Failure(it) })

        return guarded("applyOverrides") {
            profile.copy(
                flange = flange,
                pipe = profile.pipe.copy(wallThicknessMm = pipeWall),
                elbow = profile.elbow.copy(
                    centerlineRadiusMm = radius,
                    wallThicknessMm = elbowWall,
                ),
                rules = profile.rules.copy(
                    weldGapMm = weldGap,
                    minPipeLengthMm = minPipe,
                    maxElbows = maxElbows,
                    allowedElbowAnglesDeg = angles.sorted(),
                    minElbowAngleDeg = minAngle,
                    maxElbowAngleDeg = maxAngle,
                    allowDiameterChange = overrides.allowDiameterChange ?: profile.rules.allowDiameterChange,
                ),
            )
        }
    }

    /**
     * A flange the fitter dimensioned themselves. The bolt circle has to stay inside the
     * disc and outside the pipe, otherwise the drilled holes fall off the part.
     */
    private fun resolveFlange(
        profile: AssemblyProfile3D,
        overrides: AssemblyProfileOverrides3D,
    ): Fabrication3DResult<FlangeProfile3D> {
        val current = profile.flange
        val faceToWeld = overrides.flangeFaceToWeldMm ?: current.faceToWeldMm
        val thickness = overrides.flangeThicknessMm ?: current.thicknessMm
        val outside = overrides.flangeOutsideDiameterMm ?: current.outsideDiameterMm
        val boltCircle = overrides.flangeBoltCircleDiameterMm ?: current.boltCircleDiameterMm
        val boltCount = overrides.flangeBoltHoleCount ?: current.boltHoleCount
        val boltHole = overrides.flangeBoltHoleDiameterMm ?: current.boltHoleDiameterMm

        requirePositive("flangeFaceToWeldMm", faceToWeld)?.let { return it }
        requirePositive("flangeThicknessMm", thickness)?.let { return it }
        requirePositive("flangeOutsideDiameterMm", outside)?.let { return it }
        requirePositive("flangeBoltHoleDiameterMm", boltHole)?.let { return it }
        if (thickness > faceToWeld) {
            return invalid("flangeThicknessMm", ParameterRule3D.ABOVE_MAXIMUM, thickness, faceToWeld)
        }
        if (outside <= profile.pipe.outsideDiameterMm) {
            return invalid(
                "flangeOutsideDiameterMm",
                ParameterRule3D.BELOW_MINIMUM,
                outside,
                profile.pipe.outsideDiameterMm,
            )
        }
        if (boltCircle < profile.pipe.outsideDiameterMm || boltCircle > outside) {
            return invalid(
                "flangeBoltCircleDiameterMm",
                ParameterRule3D.OUT_OF_RANGE,
                boltCircle,
                "${profile.pipe.outsideDiameterMm}..$outside",
            )
        }
        if (boltCount < 2) {
            return invalid("flangeBoltHoleCount", ParameterRule3D.BELOW_MINIMUM, boltCount, 2)
        }
        if (boltCount > MAX_BOLT_HOLES) {
            return invalid("flangeBoltHoleCount", ParameterRule3D.ABOVE_MAXIMUM, boltCount, MAX_BOLT_HOLES)
        }
        // Neighbouring holes must not run into each other around the circle.
        val pitch = Math.PI * boltCircle / boltCount
        if (pitch <= boltHole) {
            return invalid("flangeBoltHoleCount", ParameterRule3D.ABOVE_MAXIMUM, boltCount, pitch)
        }
        return ok(
            current.copy(
                faceToWeldMm = faceToWeld,
                thicknessMm = thickness,
                outsideDiameterMm = outside,
                boltCircleDiameterMm = boltCircle,
                boltHoleCount = boltCount,
                boltHoleDiameterMm = boltHole,
            ),
        )
    }

    private fun resolveRadius(
        profile: AssemblyProfile3D,
        overrides: AssemblyProfileOverrides3D,
    ): Fabrication3DResult<Double> = when (overrides.elbowRadiusMode) {
        ElbowRadiusMode3D.CATALOG ->
            ok(overrides.elbowCenterlineRadiusMm ?: profile.elbow.centerlineRadiusMm)

        ElbowRadiusMode3D.CUSTOM -> {
            val value = overrides.elbowCenterlineRadiusMm
                ?: return invalid("elbowCenterlineRadiusMm", ParameterRule3D.MUST_BE_POSITIVE)
            requirePositive("elbowCenterlineRadiusMm", value)?.let { return it }
            ok(value)
        }

        else -> {
            val factor = overrides.elbowRadiusMode.diameterFactor
                ?: return invalid("elbowRadiusMode", ParameterRule3D.OUT_OF_RANGE, overrides.elbowRadiusMode)
            ok(factor * profile.nominalDiameter)
        }
    }

    private companion object {
        const val MAX_WELD_GAP_MM = 10.0
        const val MAX_BOLT_HOLES = 96
    }
}

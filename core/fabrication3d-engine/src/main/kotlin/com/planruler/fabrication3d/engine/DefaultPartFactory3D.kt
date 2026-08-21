package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.Frame3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ParametricPartDefinition3D
import com.planruler.fabrication3d.PartFactory3D
import com.planruler.fabrication3d.PartPort3D
import com.planruler.fabrication3d.PortConnectionKind
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

internal class DefaultPartFactory3D : PartFactory3D {
    override fun straightPipe(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        lengthMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requirePositive("lengthMm", lengthMm)?.let { return it }
        requireTube("outsideDiameterMm", "wallThicknessMm", outsideDiameterMm, wallThicknessMm)?.let { return it }
        return guarded("straightPipe") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.PIPE,
                geometry = StraightPipeGeometry3D(lengthMm, outsideDiameterMm, wallThicknessMm),
                ports = listOf(
                    buttWeldPort("start", Vec3.ZERO, -Vec3.UNIT_X, nominalDiameter, outsideDiameterMm),
                    buttWeldPort("end", Vec3(lengthMm, 0.0, 0.0), Vec3.UNIT_X, nominalDiameter, outsideDiameterMm),
                ),
            )
        }
    }

    override fun elbow(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        angleDeg: Double,
        centerlineRadiusMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requireFinite("angleDeg", angleDeg)?.let { return it }
        if (abs(angleDeg) <= ELBOW_MIN_MAGNITUDE_DEG || abs(angleDeg) > 180.0) {
            return invalid("angleDeg", ParameterRule3D.OUT_OF_RANGE, angleDeg, "0 < |angle| <= 180")
        }
        requireTube("outsideDiameterMm", "wallThicknessMm", outsideDiameterMm, wallThicknessMm)?.let { return it }
        requirePositive("centerlineRadiusMm", centerlineRadiusMm)?.let { return it }
        if (centerlineRadiusMm <= outsideDiameterMm / 2.0) {
            return invalid(
                "centerlineRadiusMm",
                ParameterRule3D.RADIUS_TOO_SMALL,
                centerlineRadiusMm,
                outsideDiameterMm / 2.0,
            )
        }
        val absoluteRadians = Math.toRadians(abs(angleDeg))
        val sign = if (angleDeg >= 0.0) 1.0 else -1.0
        val end = Vec3(
            centerlineRadiusMm * sin(absoluteRadians),
            sign * centerlineRadiusMm * (1.0 - cos(absoluteRadians)),
            0.0,
        )
        val endTangent = Vec3(cos(absoluteRadians), sign * sin(absoluteRadians), 0.0)
        return guarded("elbow") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.ELBOW,
                geometry = ElbowGeometry3D(angleDeg, centerlineRadiusMm, outsideDiameterMm, wallThicknessMm),
                ports = listOf(
                    buttWeldPort("start", Vec3.ZERO, -Vec3.UNIT_X, nominalDiameter, outsideDiameterMm),
                    buttWeldPort("end", end, endTangent, nominalDiameter, outsideDiameterMm),
                ),
            )
        }
    }

    override fun weldNeckFlange(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        faceToWeldMm: Double,
        thicknessMm: Double,
        outsideDiameterMm: Double,
        pipeOutsideDiameterMm: Double,
        pipeWallThicknessMm: Double,
        boltCircleDiameterMm: Double,
        boltHoleCount: Int,
        boltHoleDiameterMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requirePositive("faceToWeldMm", faceToWeldMm)?.let { return it }
        requirePositive("thicknessMm", thicknessMm)?.let { return it }
        requireTube(
            "pipeOutsideDiameterMm",
            "pipeWallThicknessMm",
            pipeOutsideDiameterMm,
            pipeWallThicknessMm,
        )?.let { return it }
        if (outsideDiameterMm <= pipeOutsideDiameterMm) {
            return invalid("outsideDiameterMm", ParameterRule3D.BELOW_MINIMUM, outsideDiameterMm, pipeOutsideDiameterMm)
        }
        if (boltCircleDiameterMm < pipeOutsideDiameterMm || boltCircleDiameterMm > outsideDiameterMm) {
            return invalid(
                "boltCircleDiameterMm",
                ParameterRule3D.OUT_OF_RANGE,
                boltCircleDiameterMm,
                "$pipeOutsideDiameterMm..$outsideDiameterMm",
            )
        }
        if (boltHoleCount < 2) {
            return invalid("boltHoleCount", ParameterRule3D.BELOW_MINIMUM, boltHoleCount, 2)
        }
        requirePositive("boltHoleDiameterMm", boltHoleDiameterMm)?.let { return it }
        return guarded("weldNeckFlange") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.FLANGE,
                geometry = WeldNeckFlangeGeometry3D(
                    faceToWeldMm,
                    thicknessMm,
                    outsideDiameterMm,
                    pipeOutsideDiameterMm,
                    pipeWallThicknessMm,
                    boltCircleDiameterMm,
                    boltHoleCount,
                    boltHoleDiameterMm,
                ),
                ports = listOf(
                    PartPort3D(
                        id = "face",
                        frame = Frame3D.of(Vec3.ZERO, -Vec3.UNIT_X),
                        nominalDiameter = nominalDiameter,
                        outsideDiameterMm = pipeOutsideDiameterMm,
                        connectionKind = PortConnectionKind.FLANGE_FACE,
                    ),
                    buttWeldPort(
                        id = "weld",
                        position = Vec3(faceToWeldMm, 0.0, 0.0),
                        direction = Vec3.UNIT_X,
                        nominalDiameter = nominalDiameter,
                        outsideDiameterMm = pipeOutsideDiameterMm,
                    ),
                ),
            )
        }
    }

    override fun equalTee(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        overallRunMm: Double,
        branchCenterToEndMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requirePositive("overallRunMm", overallRunMm)?.let { return it }
        requirePositive("branchCenterToEndMm", branchCenterToEndMm)?.let { return it }
        requireTube("outsideDiameterMm", "wallThicknessMm", outsideDiameterMm, wallThicknessMm)?.let { return it }
        val halfRun = overallRunMm / 2.0
        return guarded("equalTee") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.TEE,
                geometry = EqualTeeGeometry3D(
                    overallRunMm,
                    branchCenterToEndMm,
                    outsideDiameterMm,
                    wallThicknessMm,
                ),
                ports = listOf(
                    buttWeldPort(
                        "run-start",
                        Vec3(-halfRun, 0.0, 0.0),
                        -Vec3.UNIT_X,
                        nominalDiameter,
                        outsideDiameterMm,
                    ),
                    buttWeldPort(
                        "run-end",
                        Vec3(halfRun, 0.0, 0.0),
                        Vec3.UNIT_X,
                        nominalDiameter,
                        outsideDiameterMm,
                    ),
                    buttWeldPort(
                        "branch",
                        Vec3(0.0, branchCenterToEndMm, 0.0),
                        Vec3.UNIT_Y,
                        nominalDiameter,
                        outsideDiameterMm,
                    ),
                ),
            )
        }
    }

    override fun reducer(
        id: String,
        catalogId: String?,
        largeNominalDiameter: Int,
        smallNominalDiameter: Int,
        lengthMm: Double,
        largeOutsideDiameterMm: Double,
        largeWallThicknessMm: Double,
        smallOutsideDiameterMm: Double,
        smallWallThicknessMm: Double,
        eccentric: Boolean,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requirePositive("lengthMm", lengthMm)?.let { return it }
        requireTube(
            "largeOutsideDiameterMm",
            "largeWallThicknessMm",
            largeOutsideDiameterMm,
            largeWallThicknessMm,
        )?.let { return it }
        requireTube(
            "smallOutsideDiameterMm",
            "smallWallThicknessMm",
            smallOutsideDiameterMm,
            smallWallThicknessMm,
        )?.let { return it }
        if (smallOutsideDiameterMm >= largeOutsideDiameterMm) {
            return invalid(
                "smallOutsideDiameterMm",
                ParameterRule3D.ABOVE_MAXIMUM,
                smallOutsideDiameterMm,
                largeOutsideDiameterMm,
            )
        }
        // An eccentric reducer keeps the low side flat, which is how a drainable run is built.
        val offsetZ = if (eccentric) (largeOutsideDiameterMm - smallOutsideDiameterMm) / 2.0 else 0.0
        return guarded("reducer") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.REDUCER,
                geometry = ReducerGeometry3D(
                    lengthMm,
                    largeOutsideDiameterMm,
                    largeWallThicknessMm,
                    smallOutsideDiameterMm,
                    smallWallThicknessMm,
                    eccentric,
                ),
                ports = listOf(
                    buttWeldPort(
                        "large",
                        Vec3.ZERO,
                        -Vec3.UNIT_X,
                        largeNominalDiameter,
                        largeOutsideDiameterMm,
                    ),
                    buttWeldPort(
                        "small",
                        Vec3(lengthMm, 0.0, offsetZ),
                        Vec3.UNIT_X,
                        smallNominalDiameter,
                        smallOutsideDiameterMm,
                    ),
                ),
            )
        }
    }

    override fun cap(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        heightMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D> {
        requireIdentifier("id", id)?.let { return it }
        requirePositive("heightMm", heightMm)?.let { return it }
        requireTube("outsideDiameterMm", "wallThicknessMm", outsideDiameterMm, wallThicknessMm)?.let { return it }
        return guarded("cap") {
            ParametricPartDefinition3D(
                id = id,
                catalogId = catalogId,
                kind = FabricationPartKind.CAP,
                geometry = CapGeometry3D(heightMm, outsideDiameterMm, wallThicknessMm),
                ports = listOf(
                    buttWeldPort("weld", Vec3.ZERO, -Vec3.UNIT_X, nominalDiameter, outsideDiameterMm),
                ),
            )
        }
    }

    private fun buttWeldPort(
        id: String,
        position: Vec3,
        direction: Vec3,
        nominalDiameter: Int,
        outsideDiameterMm: Double,
    ) = PartPort3D(
        id = id,
        frame = Frame3D.of(position, direction),
        nominalDiameter = nominalDiameter,
        outsideDiameterMm = outsideDiameterMm,
        connectionKind = PortConnectionKind.BUTT_WELD,
    )

    private companion object {
        const val ELBOW_MIN_MAGNITUDE_DEG = 1e-6
    }
}

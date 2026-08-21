package com.planruler.fabrication3d

data class PipeProfile3D(
    val catalogId: String?,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) {
    init {
        require(outsideDiameterMm > 0.0) { "Pipe outside diameter must be positive" }
        require(wallThicknessMm > 0.0 && wallThicknessMm * 2.0 < outsideDiameterMm) {
            "Pipe wall thickness must leave a bore"
        }
    }
}

data class ElbowProfile3D(
    val catalogId: String?,
    val centerlineRadiusMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) {
    init {
        require(outsideDiameterMm > 0.0) { "Elbow outside diameter must be positive" }
        require(wallThicknessMm > 0.0 && wallThicknessMm * 2.0 < outsideDiameterMm) {
            "Elbow wall thickness must leave a bore"
        }
        require(centerlineRadiusMm > outsideDiameterMm / 2.0) { "Elbow radius must exceed the tube radius" }
    }
}

data class FlangeProfile3D(
    val catalogId: String?,
    val faceToWeldMm: Double,
    val thicknessMm: Double,
    val outsideDiameterMm: Double,
    val boltCircleDiameterMm: Double,
    val boltHoleCount: Int,
    val boltHoleDiameterMm: Double,
) {
    init {
        require(faceToWeldMm > 0.0 && thicknessMm > 0.0) { "Flange lengths must be positive" }
        require(outsideDiameterMm > 0.0) { "Flange outside diameter must be positive" }
        require(boltHoleCount >= 2 && boltHoleDiameterMm > 0.0) { "A flange needs at least two bolt holes" }
        require(boltCircleDiameterMm > 0.0 && boltCircleDiameterMm <= outsideDiameterMm) {
            "Bolt circle must fit inside the flange"
        }
    }
}

data class TeeProfile3D(
    val catalogId: String?,
    val overallRunMm: Double,
    val branchCenterToEndMm: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
) {
    init {
        require(overallRunMm > 0.0 && branchCenterToEndMm > 0.0) { "Tee lengths must be positive" }
        require(outsideDiameterMm > 0.0) { "Tee outside diameter must be positive" }
        require(wallThicknessMm > 0.0 && wallThicknessMm * 2.0 < outsideDiameterMm) {
            "Tee wall thickness must leave a bore"
        }
    }
}

/** How an elbow centreline radius is derived; [CUSTOM] takes the millimetre value verbatim. */
enum class ElbowRadiusMode3D(val diameterFactor: Double?) {
    CATALOG(null),
    SHORT_1D(1.0),
    LONG_1_5D(1.5),
    LARGE_3D(3.0),
    LARGE_5D(5.0),
    CUSTOM(null),
}

/**
 * Shop rules that used to be compiled-in constants. Everything here is editable by
 * the fitter within the engine quota.
 */
data class FabricationRules3D(
    val weldGapMm: Double,
    val minPipeLengthMm: Double = DEFAULT_MIN_PIPE_LENGTH_MM,
    val maxElbows: Int = DEFAULT_MAX_ELBOWS,
    val allowedElbowAnglesDeg: List<Double> = DEFAULT_ELBOW_ANGLES_DEG,
    val minElbowAngleDeg: Double = DEFAULT_MIN_ELBOW_ANGLE_DEG,
    val maxElbowAngleDeg: Double = DEFAULT_MAX_ELBOW_ANGLE_DEG,
    val allowDiameterChange: Boolean = false,
) {
    init {
        require(weldGapMm.isFinite() && weldGapMm >= 0.0) { "Weld gap must be finite and non-negative" }
        require(minPipeLengthMm.isFinite() && minPipeLengthMm > 0.0) { "Minimum pipe length must be positive" }
        require(maxElbows >= 0) { "Elbow limit cannot be negative" }
        require(allowedElbowAnglesDeg.isNotEmpty()) { "At least one elbow angle must be allowed" }
        require(minElbowAngleDeg > 0.0 && maxElbowAngleDeg <= 180.0 && minElbowAngleDeg <= maxElbowAngleDeg) {
            "Elbow angle window must sit inside (0, 180] degrees"
        }
    }

    fun permits(angleDeg: Double): Boolean {
        val magnitude = kotlin.math.abs(angleDeg)
        return angleDeg.isFinite() && magnitude >= minElbowAngleDeg && magnitude <= maxElbowAngleDeg
    }

    companion object {
        const val DEFAULT_MIN_PIPE_LENGTH_MM = 10.0
        const val DEFAULT_MAX_ELBOWS = 5
        const val DEFAULT_MIN_ELBOW_ANGLE_DEG = 1.0
        const val DEFAULT_MAX_ELBOW_ANGLE_DEG = 180.0
        val DEFAULT_ELBOW_ANGLES_DEG = listOf(11.25, 15.0, 22.5, 30.0, 45.0, 60.0, 90.0)
    }
}

/** Catalog dimensions plus shop rules; the single input both the editor and solver read. */
data class AssemblyProfile3D(
    val nominalDiameter: Int,
    val pressureClass: Int?,
    val pipe: PipeProfile3D,
    val elbow: ElbowProfile3D,
    val flange: FlangeProfile3D,
    val rules: FabricationRules3D,
    /** Null when the catalog carries no equal tee for this diameter. */
    val tee: TeeProfile3D? = null,
) {
    init {
        require(nominalDiameter > 0) { "Nominal diameter must be positive" }
        require(flange.outsideDiameterMm > pipe.outsideDiameterMm) {
            "Flange must be larger than its pipe"
        }
    }

    val weldGapMm: Double get() = rules.weldGapMm
}

/**
 * User overrides layered on top of the catalog. A null field keeps the catalog value,
 * so a saved profile stays meaningful when the catalog is corrected later.
 */
data class AssemblyProfileOverrides3D(
    val elbowRadiusMode: ElbowRadiusMode3D = ElbowRadiusMode3D.CATALOG,
    val elbowCenterlineRadiusMm: Double? = null,
    val pipeWallThicknessMm: Double? = null,
    val elbowWallThicknessMm: Double? = null,
    val weldGapMm: Double? = null,
    val minPipeLengthMm: Double? = null,
    val maxElbows: Int? = null,
    val allowedElbowAnglesDeg: List<Double>? = null,
    val minElbowAngleDeg: Double? = null,
    val maxElbowAngleDeg: Double? = null,
    val allowDiameterChange: Boolean? = null,
    /**
     * Flange geometry. The face port keeps the pipe's diameter, so these change the part
     * a fitter welds on without touching what it may connect to.
     */
    val flangeFaceToWeldMm: Double? = null,
    val flangeThicknessMm: Double? = null,
    val flangeOutsideDiameterMm: Double? = null,
    val flangeBoltCircleDiameterMm: Double? = null,
    val flangeBoltHoleCount: Int? = null,
    val flangeBoltHoleDiameterMm: Double? = null,
) {
    val isEmpty: Boolean
        get() = this == NONE

    companion object {
        val NONE = AssemblyProfileOverrides3D()
    }
}

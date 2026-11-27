package com.planruler.pipecalculator

import kotlin.math.tan

data class ElbowDimensions(
    val centerToEndMm: Double,
    val centerlineArcMm: Double,
    val insideArcMm: Double,
    val outsideArcMm: Double,
)

/** Ideal circular elbow geometry. Arc lengths do not include forming allowances or weld gaps. */
fun elbowDimensions(radiusMm: Double, outsideDiameterMm: Double, angleDeg: Double): ElbowDimensions {
    require(radiusMm.isFinite() && outsideDiameterMm.isFinite() && angleDeg.isFinite())
    require(outsideDiameterMm > 0.0 && radiusMm > outsideDiameterMm / 2.0)
    require(angleDeg > 0.0 && angleDeg < 180.0)
    val radians = Math.toRadians(angleDeg)
    return ElbowDimensions(
        radiusMm * tan(radians / 2.0), radiusMm * radians,
        (radiusMm - outsideDiameterMm / 2.0) * radians,
        (radiusMm + outsideDiameterMm / 2.0) * radians,
    )
}

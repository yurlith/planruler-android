package com.planruler.pipecalculator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

enum class FlowRegime { LAMINAR, TRANSITION, TURBULENT }

data class FrictionResult(val factor: Double, val regime: FlowRegime)

data class HydraulicResult(
    val massFlowKgS: Double,
    val volumeFlowM3H: Double,
    val innerDiameterMm: Double,
    val velocityMS: Double,
    val reynolds: Double,
    val regime: FlowRegime,
    val frictionFactor: Double,
    val linearLossPa: Double,
    val localLossPa: Double,
    val totalLossPa: Double,
    val pipeVolumeLitres: Double,
    val trace: CalculationTrace,
)

fun innerDiameterM(pipe: PipeDimensions): Double =
    positive(
        positive(pipe.outsideDiameterMm, "Outside diameter") -
            2 * positive(pipe.wallThicknessMm, "Wall thickness"),
        "Inner diameter",
    ) / 1_000

fun pipeAreaM2(innerDiameterM: Double): Double = PI * positive(innerDiameterM, "Inner diameter").pow(2) / 4

fun massFlowKgS(powerKw: Double, specificHeatKjKgK: Double, deltaTK: Double): Double =
    positive(powerKw, "Power") /
        (positive(specificHeatKjKgK, "Specific heat") * positive(deltaTK, "Temperature difference"))

fun volumeFlowM3S(massFlowKgS: Double, densityKgM3: Double): Double =
    positive(massFlowKgS, "Mass flow") / positive(densityKgM3, "Density")

fun velocityMS(volumeFlowM3S: Double, areaM2: Double): Double =
    positive(volumeFlowM3S, "Volume flow") / positive(areaM2, "Cross-section area")

fun reynoldsNumber(
    densityKgM3: Double,
    velocityMS: Double,
    diameterM: Double,
    dynamicViscosityPaS: Double,
): Double = positive(densityKgM3, "Density") * positive(velocityMS, "Velocity") *
    positive(diameterM, "Diameter") / positive(dynamicViscosityPaS, "Dynamic viscosity")

private fun colebrook(reynolds: Double, relativeRoughness: Double): Double {
    var factor = 0.02
    repeat(30) {
        val inverseRoot = -2 * log10(relativeRoughness / 3.7 + 2.51 / (reynolds * sqrt(factor)))
        val next = 1 / inverseRoot.pow(2)
        if (abs(next - factor) < 1e-12) return next
        factor = next
    }
    return factor
}

fun frictionFactor(reynolds: Double, roughnessM: Double, diameterM: Double): FrictionResult {
    positive(reynolds, "Reynolds number")
    nonNegative(roughnessM, "Roughness")
    positive(diameterM, "Diameter")
    if (reynolds < 2_300) return FrictionResult(64 / reynolds, FlowRegime.LAMINAR)
    val turbulent = colebrook(max(reynolds, 4_000.0), roughnessM / diameterM)
    if (reynolds >= 4_000) return FrictionResult(turbulent, FlowRegime.TURBULENT)
    val laminarAt2300 = 64 / 2_300.0
    val ratio = (reynolds - 2_300) / 1_700
    return FrictionResult(laminarAt2300 + ratio * (turbulent - laminarAt2300), FlowRegime.TRANSITION)
}

fun linearPressureLossPa(
    factor: Double,
    lengthM: Double,
    diameterM: Double,
    densityKgM3: Double,
    velocityMS: Double,
): Double = positive(factor, "Friction factor") * positive(lengthM, "Length") /
    positive(diameterM, "Diameter") * positive(densityKgM3, "Density") *
    positive(velocityMS, "Velocity").pow(2) / 2

fun localPressureLossPa(coefficient: Double, densityKgM3: Double, velocityMS: Double): Double =
    nonNegative(coefficient, "Local resistance coefficient") * positive(densityKgM3, "Density") *
        positive(velocityMS, "Velocity").pow(2) / 2

fun pipeVolumeLitres(diameterM: Double, lengthM: Double): Double =
    pipeAreaM2(diameterM) * positive(lengthM, "Length") * 1_000

fun calculateHydraulics(input: HydraulicInput): HydraulicResult {
    val diameter = innerDiameterM(input.pipe)
    val massFlow = massFlowKgS(input.powerKw, input.fluid.specificHeatKjKgK, input.deltaTK)
    val volumeFlow = volumeFlowM3S(massFlow, input.fluid.densityKgM3)
    val velocity = velocityMS(volumeFlow, pipeAreaM2(diameter))
    val reynolds = reynoldsNumber(
        input.fluid.densityKgM3,
        velocity,
        diameter,
        input.fluid.dynamicViscosityPaS,
    )
    val friction = frictionFactor(reynolds, nonNegative(input.pipe.roughnessMm, "Roughness") / 1_000, diameter)
    val linearLoss = linearPressureLossPa(
        friction.factor,
        input.lengthM,
        diameter,
        input.fluid.densityKgM3,
        velocity,
    )
    val localLoss = localPressureLossPa(input.localLossCoefficient, input.fluid.densityKgM3, velocity)
    val warnings = buildList {
        if (friction.regime == FlowRegime.TRANSITION) {
            add("Transition flow regime: friction has increased uncertainty.")
        }
        if (velocity > 2) add("Velocity exceeds 2 m/s; verify noise, erosion and project limits.")
        if (input.fluid.source.validationStatus != ValidationStatus.VERIFIED) {
            add("Fluid properties are advisory and require project-specific verification.")
        }
    }
    return HydraulicResult(
        massFlowKgS = massFlow,
        volumeFlowM3H = volumeFlow * 3_600,
        innerDiameterMm = diameter * 1_000,
        velocityMS = velocity,
        reynolds = reynolds,
        regime = friction.regime,
        frictionFactor = friction.factor,
        linearLossPa = linearLoss,
        localLossPa = localLoss,
        totalLossPa = linearLoss + localLoss,
        pipeVolumeLitres = pipeVolumeLitres(diameter, input.lengthM),
        trace = CalculationTrace(
            formula = "Darcy-Weisbach + Colebrook; mass flow = P/(cp·ΔT)",
            sourceIds = listOf(input.pipe.source.id, input.fluid.source.id),
            warnings = warnings,
        ),
    )
}

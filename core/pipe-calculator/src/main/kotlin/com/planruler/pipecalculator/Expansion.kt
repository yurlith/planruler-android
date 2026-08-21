package com.planruler.pipecalculator

data class ExpansionVesselResult(
    val expansionCoefficient: Double,
    val expansionVolumeLitres: Double,
    val requiredAcceptanceLitres: Double,
    val acceptanceFactor: Double,
    val minimumNominalVesselLitres: Double,
    val trace: CalculationTrace,
)

fun expansionCoefficient(densityAtMinimumKgM3: Double, densityAtMaximumKgM3: Double): Double {
    val coefficient = positive(densityAtMinimumKgM3, "Density at minimum temperature") /
        positive(densityAtMaximumKgM3, "Density at maximum temperature") - 1
    if (coefficient <= 0) {
        throw CalculationException("Density at maximum temperature must be lower than initial density")
    }
    return coefficient
}

fun calculateExpansionVessel(input: ExpansionVesselInput): ExpansionVesselResult {
    positive(input.systemVolumeLitres, "System volume")
    nonNegative(input.reserveLitres, "Reserve volume")
    nonNegative(input.prechargeBarGauge, "Pre-charge pressure")
    positive(input.finalPressureBarGauge, "Final pressure")
    positive(input.safetyValveBarGauge, "Safety valve pressure")
    if (input.finalPressureBarGauge <= input.prechargeBarGauge) {
        throw CalculationException("Final pressure must exceed pre-charge pressure")
    }
    if (input.finalPressureBarGauge >= input.safetyValveBarGauge) {
        throw CalculationException("Final pressure must be below safety-valve pressure")
    }
    val coefficient = expansionCoefficient(input.densityAtMinimumKgM3, input.densityAtMaximumKgM3)
    val expansionVolume = input.systemVolumeLitres * coefficient
    val requiredAcceptance = expansionVolume + input.reserveLitres
    val acceptanceFactor = (input.finalPressureBarGauge - input.prechargeBarGauge) /
        (input.finalPressureBarGauge + 1)
    return ExpansionVesselResult(
        expansionCoefficient = coefficient,
        expansionVolumeLitres = expansionVolume,
        requiredAcceptanceLitres = requiredAcceptance,
        acceptanceFactor = acceptanceFactor,
        minimumNominalVesselLitres = requiredAcceptance / positive(acceptanceFactor, "Acceptance factor"),
        trace = CalculationTrace(
            formula = "e=ρmin/ρmax−1; Vn=(Vsystem·e+Vreserve)/((pe−p0)/(pe+1))",
            sourceIds = listOf("EN12828-SIA384-pending-licensed-validation"),
            warnings = listOf(
                "Preliminary result: verify against the licensed EN 12828 and SIA 384/1 editions.",
                "Select the next suitable vessel and verify its pressure/temperature limits separately.",
            ),
        ),
    )
}

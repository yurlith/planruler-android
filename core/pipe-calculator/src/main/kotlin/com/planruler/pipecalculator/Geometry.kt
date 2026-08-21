package com.planruler.pipecalculator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

data class OffsetResult(
    val offsetMm: Double,
    val angleDeg: Double,
    val centerTravelMm: Double,
    val advanceMm: Double,
    val straightBetweenFittingsMm: Double,
)

data class TwoElbowAssemblyInput(
    val targetHeightMm: Double,
    val angleDeg: Double,
    val elbowTakeoutEachMm: Double,
    val weldGapEachMm: Double,
    val pipe: PipeDimensions,
    val quantity: Int = 1,
    val stockLengthMm: Double = 6_000.0,
    val sawKerfMm: Double = 3.0,
)

data class TwoElbowAssemblyResult(
    val targetHeightMm: Double,
    val angleDeg: Double,
    val elbowTakeoutEachMm: Double,
    val weldGapEachMm: Double,
    val centerTravelMm: Double,
    val horizontalAdvanceMm: Double,
    val fittingFaceDistanceMm: Double,
    val insertCutLengthMm: Double,
    val minimumPossibleHeightMm: Double,
    val pipeMassEachKg: Double,
    val totalNetPipeLengthMm: Double,
    val piecesPerStock: Int,
    val stockBarsRequired: Int,
    val stockLengthMm: Double,
    val totalPurchasedLengthMm: Double,
    val estimatedKerfLossMm: Double,
    val estimatedOffcutMm: Double,
)

fun straightSpoolCutLengthMm(
    overallLengthMm: Double,
    takeoutsMm: List<Double>,
    weldGapsMm: List<Double> = emptyList(),
): Double {
    positive(overallLengthMm, "Overall length")
    val result = overallLengthMm - takeoutsMm.sumOf { nonNegative(it, "Take-out") } -
        weldGapsMm.sumOf { nonNegative(it, "Weld gap") }
    if (result <= 0) throw CalculationException("Cut length is not positive after deductions")
    return result
}

fun twoElbowOffsetMm(offsetMm: Double, angleDeg: Double, takeoutEachMm: Double): OffsetResult {
    positive(offsetMm, "Offset")
    within(angleDeg, 0.1, 89.9, "Angle")
    nonNegative(takeoutEachMm, "Elbow take-out")
    val radians = Math.toRadians(angleDeg)
    val centerTravel = offsetMm / sin(radians)
    val straight = centerTravel - 2 * takeoutEachMm
    if (straight <= 0) throw CalculationException("Offset is too small for the selected elbows")
    return OffsetResult(
        offsetMm = offsetMm,
        angleDeg = angleDeg,
        centerTravelMm = centerTravel,
        advanceMm = offsetMm / tan(radians),
        straightBetweenFittingsMm = straight,
    )
}

/**
 * Cut plan for an offset assembled from two equal elbows and one straight insert.
 * H is perpendicular to the parallel end runs, X is the horizontal advance and the
 * physical insert is the center travel less two fitting take-outs and two weld gaps.
 */
fun calculateTwoElbowAssembly(input: TwoElbowAssemblyInput): TwoElbowAssemblyResult {
    positive(input.targetHeightMm, "Target height")
    within(input.angleDeg, 0.1, 89.9, "Angle")
    val takeout = nonNegative(input.elbowTakeoutEachMm, "Elbow take-out")
    val gap = nonNegative(input.weldGapEachMm, "Weld gap")
    positive(input.pipe.outsideDiameterMm, "Outside diameter")
    positive(input.pipe.wallThicknessMm, "Wall thickness")
    if (input.quantity <= 0) throw CalculationException("Quantity must be positive")
    val stockLength = positive(input.stockLengthMm, "Stock length")
    val kerf = nonNegative(input.sawKerfMm, "Saw kerf")

    val radians = Math.toRadians(input.angleDeg)
    val centerTravel = input.targetHeightMm / sin(radians)
    val fittingFaceDistance = centerTravel - 2.0 * takeout
    val insertCut = fittingFaceDistance - 2.0 * gap
    val minimumHeight = (2.0 * takeout + 2.0 * gap) * sin(radians)
    if (insertCut <= 0.0) {
        throw CalculationException("Target height is too small for the selected elbows and weld gaps")
    }

    val piecesPerStock = floor((stockLength + kerf) / (insertCut + kerf)).toInt()
    if (piecesPerStock <= 0) {
        throw CalculationException("Selected stock length is shorter than one insert including saw kerf")
    }
    val stockBars = ceil(input.quantity.toDouble() / piecesPerStock).toInt()
    val netLength = insertCut * input.quantity
    val purchasedLength = stockBars * stockLength
    val kerfLoss = kerf * input.quantity
    val offcut = purchasedLength - netLength - kerfLoss

    return TwoElbowAssemblyResult(
        targetHeightMm = input.targetHeightMm,
        angleDeg = input.angleDeg,
        elbowTakeoutEachMm = takeout,
        weldGapEachMm = gap,
        centerTravelMm = centerTravel,
        horizontalAdvanceMm = input.targetHeightMm / tan(radians),
        fittingFaceDistanceMm = fittingFaceDistance,
        insertCutLengthMm = insertCut,
        minimumPossibleHeightMm = minimumHeight,
        pipeMassEachKg = theoreticalPipeMassKg(
            input.pipe.outsideDiameterMm,
            input.pipe.wallThicknessMm,
            insertCut / 1_000.0,
        ),
        totalNetPipeLengthMm = netLength,
        piecesPerStock = piecesPerStock,
        stockBarsRequired = stockBars,
        stockLengthMm = stockLength,
        totalPurchasedLengthMm = purchasedLength,
        estimatedKerfLossMm = kerfLoss,
        estimatedOffcutMm = offcut,
    )
}

fun trueLength3dMm(xMm: Double, yMm: Double, zMm: Double): Double =
    positive(hypot(hypot(abs(xMm), abs(yMm)), abs(zMm)), "True length")

fun theoreticalPipeMassKg(
    outsideDiameterMm: Double,
    wallThicknessMm: Double,
    lengthM: Double,
    densityKgM3: Double = 7_850.0,
): Double {
    positive(outsideDiameterMm, "Outside diameter")
    positive(wallThicknessMm, "Wall thickness")
    positive(lengthM, "Length")
    positive(densityKgM3, "Material density")
    val insideMm = outsideDiameterMm - 2 * wallThicknessMm
    if (insideMm <= 0) throw CalculationException("Wall thickness is incompatible with outside diameter")
    val metalAreaM2 = PI / 4 * ((outsideDiameterMm / 1_000).pow(2) - (insideMm / 1_000).pow(2))
    return metalAreaM2 * lengthM * densityKgM3
}

enum class PipeSupportMaterial { STEEL, COPPER }

data class SupportSpanResult(
    val maximumSpanM: Double,
    val trace: CalculationTrace,
)

private val ASME_B31_1_SUPPORT_SPAN_SOURCE = DataSource(
    id = "asme-b31-1-table-121-5",
    organisation = "ASME",
    document = "ASME B31.1 Table 121.5 — suggested pipe support spacing",
    edition = "2022",
    kind = SourceKind.NORMATIVE,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "2026-08-19",
)

/**
 * Suggested maximum support spacing from ASME B31.1 Table 121.5's rule of thumb —
 * steel: span[ft] = DN[in] + 10; copper: linearly interpolated between the table's
 * published anchors (1 in -> 8 ft, 4 in -> 12 ft), clamped to that published range.
 * This is a spacing rule of thumb, not a sag or stress (guided-cantilever) calculation.
 */
fun maximumSupportSpanM(nominalDiameterMm: Double, material: PipeSupportMaterial): SupportSpanResult {
    positive(nominalDiameterMm, "Nominal diameter")
    val inches = nominalDiameterMm / 25.4
    val spanFt = when (material) {
        PipeSupportMaterial.STEEL -> inches + 10.0
        PipeSupportMaterial.COPPER -> {
            val clampedInches = inches.coerceIn(1.0, 4.0)
            8.0 + (clampedInches - 1.0) / (4.0 - 1.0) * (12.0 - 8.0)
        }
    }
    val spanM = spanFt * 0.3048
    return SupportSpanResult(
        maximumSpanM = spanM,
        trace = CalculationTrace(
            formula = when (material) {
                PipeSupportMaterial.STEEL -> "span[ft] = DN[in] + 10"
                PipeSupportMaterial.COPPER -> "span[ft] = interpolate(DN[in]; 1in->8ft, 4in->12ft)"
            },
            sourceIds = listOf(ASME_B31_1_SUPPORT_SPAN_SOURCE.id),
            warnings = listOf(
                "Spacing rule of thumb only, not a sag or stress calculation. Verify against " +
                    "insulation weight, fluid weight, and seismic or wind bracing requirements before fabrication.",
            ),
            methodId = "planruler-support-span-asme-b31-1-v1",
            status = CalculationStatus.PRELIMINARY,
            steps = listOf(CalculationStep("support.span", "span[ft] * 0.3048", spanM, "m")),
        ),
    )
}

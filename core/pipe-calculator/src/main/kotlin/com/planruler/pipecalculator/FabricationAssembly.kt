package com.planruler.pipecalculator

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

enum class FabricationElementKind { FLANGE, PIPE, ELBOW, WELD_GAP }

data class FabricationPointMm(val x: Double, val y: Double)

data class FabricationElement(
    val code: String,
    val kind: FabricationElementKind,
    val start: FabricationPointMm,
    val end: FabricationPointMm,
    val control: FabricationPointMm? = null,
    val cutLengthMm: Double? = null,
)

data class FabricationPipeCut(
    val code: String,
    val lengthMm: Double,
    val quantity: Int,
    val startCutDeg: Double = 90.0,
    val endCutDeg: Double = 90.0,
)

data class FabricationStockBar(
    val number: Int,
    val cutsMm: List<Double>,
    val netUsedMm: Double,
    val kerfLossMm: Double,
    val offcutMm: Double,
)

data class FlangedOffsetAssemblyInput(
    val dn: Int,
    val pn: Int,
    val targetOffsetMm: Double,
    val overallFaceToFaceMm: Double,
    val angleDeg: Double = 45.0,
    val weldGapMm: Double = 2.0,
    val quantity: Int = 1,
    val stockLengthMm: Double = 6_000.0,
    val sawKerfMm: Double = 3.0,
    /** Share of the available horizontal straight length assigned to P1. */
    val inletTailShare: Double = 0.5,
)

data class FlangedOffsetAssemblyResult(
    val input: FlangedOffsetAssemblyInput,
    val pipe: PipeCatalogEntry,
    val elbow: ElbowCatalogEntry,
    val flange: WeldNeckFlangeCatalogEntry,
    val elbowTakeoutMm: Double,
    val diagonalCenterTravelMm: Double,
    val horizontalCenterAdvanceMm: Double,
    val diagonalFaceToFaceMm: Double,
    val inletPipeCutMm: Double,
    val diagonalPipeCutMm: Double,
    val outletPipeCutMm: Double,
    val minimumOffsetMm: Double,
    val minimumOverallFaceToFaceMm: Double,
    val totalNetPipeLengthMm: Double,
    val totalPipeMassKg: Double,
    val weldCount: Int,
    val flangeBoltCount: Int,
    val cuts: List<FabricationPipeCut>,
    val stockBars: List<FabricationStockBar>,
    val elements: List<FabricationElement>,
    val startFace: FabricationPointMm,
    val endFace: FabricationPointMm,
    val warnings: List<String>,
) {
    val stockBarsRequired: Int get() = stockBars.size
    val totalPurchasedLengthMm: Double get() = stockBars.size * input.stockLengthMm
    val totalKerfLossMm: Double get() = stockBars.sumOf { it.kerfLossMm }
    val totalOffcutMm: Double get() = stockBars.sumOf { it.offcutMm }
}

/**
 * Generates a complete, dimensionally closed offset assembly:
 * flange — P1 — elbow — P2 — elbow — P3 — flange.
 *
 * H is measured between the two parallel pipe axes and X between flange sealing
 * faces. Pipe cuts are physical saw lengths; every butt-weld gap is represented
 * explicitly instead of being hidden in a fitting take-out.
 */
fun calculateFlangedOffsetAssembly(input: FlangedOffsetAssemblyInput): FlangedOffsetAssemblyResult {
    val height = positive(input.targetOffsetMm, "Target offset")
    val overall = positive(input.overallFaceToFaceMm, "Overall face-to-face length")
    val angle = within(input.angleDeg, 15.0, 90.0, "Elbow angle")
    val gap = within(input.weldGapMm, 0.0, 10.0, "Weld gap")
    val stockLength = positive(input.stockLengthMm, "Stock length")
    val kerf = within(input.sawKerfMm, 0.0, 20.0, "Saw kerf")
    val inletShare = within(input.inletTailShare, 0.05, 0.95, "Inlet tail share")
    if (input.quantity !in 1..1_000) throw CalculationException("Quantity must be between 1 and 1000")

    val pipe = PIPE_INSTALLATION_SERIES.singleOrNull { it.dn == input.dn }
        ?: throw CalculationException("Pipe DN ${input.dn} is not available in the installation series")
    val elbow = ELBOW_45_3D_CATALOG.singleOrNull { it.dn == input.dn }
        ?: throw CalculationException("Elbow DN ${input.dn} is not available")
    val flange = WELD_NECK_FLANGE_TYPE11_CATALOG.singleOrNull { it.dn == input.dn && it.pn == input.pn }
        ?: throw CalculationException("Type 11 flange DN ${input.dn} PN ${input.pn} is not available")

    val radians = Math.toRadians(angle)
    val direction = FabricationPointMm(cos(radians), sin(radians))
    val takeout = elbow.centerlineRadiusMm * tan(radians / 2.0)
    val centerTravel = height / sin(radians)
    val horizontalAdvance = if (abs(angle - 90.0) < 1e-9) 0.0 else height / tan(radians)
    val diagonalFaceToFace = centerTravel - 2.0 * takeout
    val diagonalCut = diagonalFaceToFace - 2.0 * gap
    val minimumOffset = (2.0 * takeout + 2.0 * gap) * sin(radians)
    if (diagonalCut <= 0.0) {
        throw CalculationException("Target offset is too small for the selected DN, angle and weld gaps")
    }

    val minimumOverall =
        2.0 * flange.faceToWeldMm + 4.0 * gap + 2.0 * takeout + horizontalAdvance
    val availableTailLength = overall - minimumOverall
    if (availableTailLength <= 0.0) {
        throw CalculationException("Overall face-to-face length is too small for the flanges and elbows")
    }
    val inletCut = availableTailLength * inletShare
    val outletCut = availableTailLength - inletCut
    if (inletCut <= 0.0 || outletCut <= 0.0) {
        throw CalculationException("Straight tail pipe cuts must be positive")
    }

    val p0 = FabricationPointMm(0.0, 0.0)
    val p1 = p0 + FabricationPointMm(flange.faceToWeldMm, 0.0)
    val p2 = p1 + FabricationPointMm(gap, 0.0)
    val p3 = p2 + FabricationPointMm(inletCut, 0.0)
    val p4 = p3 + FabricationPointMm(gap, 0.0)
    val p5 = p4 + FabricationPointMm(takeout, 0.0)
    val p6 = p5 + direction * takeout
    val p7 = p6 + direction * gap
    val p8 = p7 + direction * diagonalCut
    val p9 = p8 + direction * gap
    val p10 = p9 + direction * takeout
    val p11 = p10 + FabricationPointMm(takeout, 0.0)
    val p12 = p11 + FabricationPointMm(gap, 0.0)
    val p13 = p12 + FabricationPointMm(outletCut, 0.0)
    val p14 = p13 + FabricationPointMm(gap, 0.0)
    val p15 = p14 + FabricationPointMm(flange.faceToWeldMm, 0.0)

    if (abs(p15.x - overall) > 1e-6 || abs(p15.y - height) > 1e-6) {
        throw CalculationException("Generated assembly does not close at the requested dimensions")
    }

    val elements = listOf(
        FabricationElement("F1", FabricationElementKind.FLANGE, p0, p1),
        FabricationElement("W1", FabricationElementKind.WELD_GAP, p1, p2),
        FabricationElement("P1", FabricationElementKind.PIPE, p2, p3, cutLengthMm = inletCut),
        FabricationElement("W2", FabricationElementKind.WELD_GAP, p3, p4),
        FabricationElement("E1", FabricationElementKind.ELBOW, p4, p6, control = p5),
        FabricationElement("W3", FabricationElementKind.WELD_GAP, p6, p7),
        FabricationElement("P2", FabricationElementKind.PIPE, p7, p8, cutLengthMm = diagonalCut),
        FabricationElement("W4", FabricationElementKind.WELD_GAP, p8, p9),
        FabricationElement("E2", FabricationElementKind.ELBOW, p9, p11, control = p10),
        FabricationElement("W5", FabricationElementKind.WELD_GAP, p11, p12),
        FabricationElement("P3", FabricationElementKind.PIPE, p12, p13, cutLengthMm = outletCut),
        FabricationElement("W6", FabricationElementKind.WELD_GAP, p13, p14),
        FabricationElement("F2", FabricationElementKind.FLANGE, p14, p15),
    )
    val cuts = listOf(
        FabricationPipeCut("P1", inletCut, input.quantity),
        FabricationPipeCut("P2", diagonalCut, input.quantity),
        FabricationPipeCut("P3", outletCut, input.quantity),
    )
    val stockBars = createStockPlan(cuts, stockLength, kerf)
    val netLength = cuts.sumOf { it.lengthMm * it.quantity }
    val warnings = buildList {
        if (abs(angle - elbow.angleDeg) > 1e-6) {
            add("Custom angle uses A = R × tan(α/2); verify the actual manufactured or trimmed elbow")
        }
        add("Verify actual fitting take-outs, flange facing and the approved welding procedure before cutting")
    }

    return FlangedOffsetAssemblyResult(
        input = input,
        pipe = pipe,
        elbow = elbow,
        flange = flange,
        elbowTakeoutMm = takeout,
        diagonalCenterTravelMm = centerTravel,
        horizontalCenterAdvanceMm = horizontalAdvance,
        diagonalFaceToFaceMm = diagonalFaceToFace,
        inletPipeCutMm = inletCut,
        diagonalPipeCutMm = diagonalCut,
        outletPipeCutMm = outletCut,
        minimumOffsetMm = minimumOffset,
        minimumOverallFaceToFaceMm = minimumOverall,
        totalNetPipeLengthMm = netLength,
        totalPipeMassKg = theoreticalPipeMassKg(
            pipe.outsideDiameterMm,
            pipe.wallThicknessMm,
            netLength / 1_000.0,
        ),
        weldCount = 6 * input.quantity,
        flangeBoltCount = 2 * flange.boltHoleCount * input.quantity,
        cuts = cuts,
        stockBars = stockBars,
        elements = elements,
        startFace = p0,
        endFace = p15,
        warnings = warnings,
    )
}

private fun createStockPlan(
    cuts: List<FabricationPipeCut>,
    stockLengthMm: Double,
    kerfMm: Double,
): List<FabricationStockBar> {
    val pieces = cuts.flatMap { cut -> List(cut.quantity) { cut.lengthMm } }.sortedDescending()
    val bars = mutableListOf<MutableList<Double>>()
    pieces.forEach { length ->
        if (length + kerfMm > stockLengthMm + 1e-9) {
            throw CalculationException("A pipe cut is longer than the selected stock bar")
        }
        val target = bars.indexOfFirst { existing ->
            existing.sum() + length + kerfMm * (existing.size + 1) <= stockLengthMm + 1e-9
        }
        if (target >= 0) bars[target].add(length) else bars.add(mutableListOf(length))
    }
    return bars.mapIndexed { index, lengths ->
        val net = lengths.sum()
        val kerf = lengths.size * kerfMm
        FabricationStockBar(
            number = index + 1,
            cutsMm = lengths.toList(),
            netUsedMm = net,
            kerfLossMm = kerf,
            offcutMm = stockLengthMm - net - kerf,
        )
    }
}

private operator fun FabricationPointMm.plus(other: FabricationPointMm) =
    FabricationPointMm(x + other.x, y + other.y)

private operator fun FabricationPointMm.times(scale: Double) =
    FabricationPointMm(x * scale, y * scale)

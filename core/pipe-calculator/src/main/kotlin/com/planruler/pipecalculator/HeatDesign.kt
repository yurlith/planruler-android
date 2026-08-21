package com.planruler.pipecalculator

import kotlin.math.ceil
import kotlin.math.max

private const val PRELIMINARY_HEAT_LOSS_SOURCE_ID = "planruler-preliminary-heat-loss-0.3"

val SIA_384_2_REFERENCE = DataSource(
    id = "sia-384-2-2020-c1-2021",
    organisation = "SIA",
    document = "SIA 384/2:2020 with correction C1:2021",
    edition = "2020+C1:2021",
    kind = SourceKind.NORMATIVE,
    validationStatus = ValidationStatus.REQUIRES_LICENSED_VALIDATION,
    checkedAt = "2026-08-14",
    url = "https://shop.sia.ch/normenwerk/architekt/384-2_2020_d/D/Product",
)

data class LinearThermalBridge(
    val lengthM: Double,
    val psiWmK: Double,
    val description: String = "",
)

data class HeatLossInput(
    val heatedAreaM2: Double,
    val wallsAreaM2: Double,
    val roofAreaM2: Double,
    val floorAreaM2: Double,
    val windowsAreaM2: Double,
    val floorHeightM: Double = 2.7,
    val floors: Int = 1,
    val wallUValueWm2K: Double = 0.30,
    val roofUValueWm2K: Double = 0.20,
    val floorUValueWm2K: Double = 0.25,
    val windowUValueWm2K: Double = 1.10,
    val indoorTemperatureC: Double = 21.0,
    val designOutdoorTemperatureC: Double = -8.0,
    val airChangesPerHour: Double = 0.50,
    /** Area-based thermal bridge allowance ΔU-WB. This is not a linear ψ value. */
    val thermalBridgeWm2K: Double = 0.05,
    val linearThermalBridges: List<LinearThermalBridge> = emptyList(),
    val infiltrationAirChangesPerHour: Double = 0.0,
    val heatRecoveryEfficiency: Double = 0.0,
    val airHeatCapacityWhM3K: Double = 0.34,
    /** True when [heatedAreaM2] already contains the sum of all heated floors. */
    val heatedAreaIncludesAllFloors: Boolean = true,
    val calculationStandardProfileId: String = CalculationStandards.PRELIMINARY_ID,
)

data class HeatLossBreakdown(
    val wallsW: Double,
    val roofW: Double,
    val floorW: Double,
    val windowsW: Double,
    val ventilationW: Double,
    val areaThermalBridgeAllowanceW: Double,
    val linearThermalBridgesW: Double,
)

data class HeatLossResult(
    val standardProfile: CalculationStandardProfile,
    val designDeltaTK: Double,
    val heatedVolumeM3: Double,
    val breakdown: HeatLossBreakdown,
    val totalHeatLossW: Double,
    val totalHeatLossKw: Double,
    val specificHeatLossWm2: Double,
    val trace: CalculationTrace,
)

data class HeatingDesignInput(
    val building: HeatLossInput,
    val hydronicDeltaTK: Double = 10.0,
    val heatPumpCoverageFactor: Double? = null,
    val coolingToHeatingRatio: Double? = null,
    val freshAirLsM2: Double = 0.35,
    val zoneAreaM2: Double? = null,
    val occupancy: Int? = null,
    val domesticHotWaterLitresPerOccupant: Double? = null,
    val fluid: FluidProperties = waterAt(40.0),
    val criticalCircuit: List<HydraulicInput> = emptyList(),
    val terminalAndValveLossKpa: Double = 0.0,
)

data class HeatingDesignResult(
    val heatLoss: HeatLossResult,
    val designFlowLitresPerHour: Double,
    val recommendedPumpHeadKpa: Double?,
    val targetHeatPumpCapacityKw: Double?,
    val estimatedCoolingCapacityKw: Double?,
    val freshAirFlowLs: Double,
    val domesticHotWaterOccupancy: Int,
    val suggestedDomesticHotWaterStorageLitres: Double?,
    val controlZoneCount: Int?,
    val trace: CalculationTrace,
)

fun calculateHeatLoss(input: HeatLossInput): HeatLossResult {
    val standardProfile = CalculationStandards.requireRunnable(input.calculationStandardProfileId)
    val heatedArea = positive(input.heatedAreaM2, "Heated area")
    val wallsArea = nonNegative(input.wallsAreaM2, "Walls area")
    val roofArea = nonNegative(input.roofAreaM2, "Roof area")
    val floorArea = nonNegative(input.floorAreaM2, "Floor area")
    val windowsArea = nonNegative(input.windowsAreaM2, "Windows area")
    val floorHeight = positive(input.floorHeightM, "Floor height")
    if (input.floors <= 0) throw CalculationException("Floors must be positive")

    val deltaT = input.indoorTemperatureC - input.designOutdoorTemperatureC
    positive(deltaT, "Design temperature difference")
    val volume = heatedArea * floorHeight * if (input.heatedAreaIncludesAllFloors) 1 else input.floors
    val wallLoss = nonNegative(input.wallUValueWm2K, "Wall U-value") * wallsArea * deltaT
    val roofLoss = nonNegative(input.roofUValueWm2K, "Roof U-value") * roofArea * deltaT
    val floorLoss = nonNegative(input.floorUValueWm2K, "Floor U-value") * floorArea * deltaT
    val windowLoss = nonNegative(input.windowUValueWm2K, "Window U-value") * windowsArea * deltaT
    val ventilationRate = nonNegative(input.airChangesPerHour, "Air changes")
    val infiltrationRate = nonNegative(input.infiltrationAirChangesPerHour, "Infiltration air changes")
    val recovery = within(input.heatRecoveryEfficiency, 0.0, 1.0, "Heat recovery efficiency")
    val effectiveAirChanges = infiltrationRate + ventilationRate * (1.0 - recovery)
    val ventilationLoss = positive(input.airHeatCapacityWhM3K, "Air heat capacity") * effectiveAirChanges * volume * deltaT
    val envelopeArea = wallsArea + roofArea + floorArea + windowsArea
    val areaBridgeLoss = nonNegative(input.thermalBridgeWm2K, "Area thermal bridge allowance") * envelopeArea * deltaT
    val linearBridgeLoss = input.linearThermalBridges.sumOf { bridge ->
        nonNegative(bridge.lengthM, "Thermal bridge length") *
            nonNegative(bridge.psiWmK, "Linear thermal bridge psi") * deltaT
    }
    val total = wallLoss + roofLoss + floorLoss + windowLoss + ventilationLoss + areaBridgeLoss + linearBridgeLoss

    return HeatLossResult(
        standardProfile = standardProfile,
        designDeltaTK = deltaT,
        heatedVolumeM3 = volume,
        breakdown = HeatLossBreakdown(
            wallLoss,
            roofLoss,
            floorLoss,
            windowLoss,
            ventilationLoss,
            areaBridgeLoss,
            linearBridgeLoss,
        ),
        totalHeatLossW = total,
        totalHeatLossKw = total / 1_000.0,
        specificHeatLossWm2 = total / heatedArea,
        trace = CalculationTrace(
            formula = "Σ(U·A·ΔT) + c_air·(n_inf+n_vent·(1-η_HR))·V·ΔT + ΔU_WB·A·ΔT + Σ(ψ·L·ΔT)",
            sourceIds = listOf(PRELIMINARY_HEAT_LOSS_SOURCE_ID, SIA_384_2_REFERENCE.id),
            warnings = listOf(
                "Preliminary heat-loss estimate. It is not an EN 12831 or SIA 384/2 compliance calculation.",
                "SIA publication inputs and licensed reference examples have not yet been validated in this engine.",
            ),
            methodId = "planruler-preliminary-heat-loss-v2",
            status = CalculationStatus.REQUIRES_LICENSED_VALIDATION,
            steps = listOf(
                CalculationStep("transmission.walls", "U_wall·A_wall·ΔT", wallLoss, "W"),
                CalculationStep("transmission.roof", "U_roof·A_roof·ΔT", roofLoss, "W"),
                CalculationStep("transmission.floor", "U_floor·A_floor·ΔT", floorLoss, "W"),
                CalculationStep("transmission.windows", "U_window·A_window·ΔT", windowLoss, "W"),
                CalculationStep("ventilation", "c_air·n_eff·V·ΔT", ventilationLoss, "W"),
                CalculationStep("thermal-bridges.area", "ΔU_WB·A·ΔT", areaBridgeLoss, "W"),
                CalculationStep("thermal-bridges.linear", "Σ(ψ·L·ΔT)", linearBridgeLoss, "W"),
                CalculationStep("total", "ΣQ", total, "W"),
            ),
        ),
    )
}

fun calculateHeatingDesign(input: HeatingDesignInput): HeatingDesignResult {
    val heatLoss = calculateHeatLoss(input.building)
    val hydronicDelta = positive(input.hydronicDeltaTK, "Hydronic temperature difference")
    val coverage = input.heatPumpCoverageFactor?.let { positive(it, "Heat-pump coverage factor") }
    val coolingRatio = input.coolingToHeatingRatio?.let { nonNegative(it, "Cooling ratio") }
    val freshAir = nonNegative(input.freshAirLsM2, "Fresh-air rate")
    val zoneArea = input.zoneAreaM2?.let { positive(it, "Zone area") }
    val area = input.building.heatedAreaM2
    val occupancy = input.occupancy ?: max(1, ceil(area / 35.0).toInt())
    if (occupancy <= 0) throw CalculationException("Occupancy must be positive")
    val dhwPerOccupant = input.domesticHotWaterLitresPerOccupant?.let {
        positive(it, "Domestic hot-water storage per occupant")
    }
    val designMassFlow = massFlowKgS(heatLoss.totalHeatLossKw, input.fluid.specificHeatKjKgK, hydronicDelta)
    val designVolumeFlowM3H = designMassFlow / positive(input.fluid.densityKgM3, "Fluid density") * 3_600.0
    val circuitLossKpa = input.criticalCircuit.takeIf { it.isNotEmpty() }
        ?.sumOf { calculateHydraulics(it).totalLossPa / 1_000.0 }
        ?.plus(nonNegative(input.terminalAndValveLossKpa, "Terminal and valve loss"))
    val traceWarnings = buildList {
        addAll(heatLoss.trace.warnings)
        if (circuitLossKpa == null) add("Pump head is not calculated until a critical hydraulic circuit is provided.")
        if (coverage != null) add("Heat-pump coverage factor is a user planning assumption, not a normative sizing result.")
        if (coolingRatio != null) add("Cooling-to-heating ratio is a user planning assumption, not a cooling-load calculation.")
        if (dhwPerOccupant != null) add("DHW litres per occupant is a user planning assumption.")
    }

    return HeatingDesignResult(
        heatLoss = heatLoss,
        designFlowLitresPerHour = designVolumeFlowM3H * 1_000.0,
        recommendedPumpHeadKpa = circuitLossKpa,
        targetHeatPumpCapacityKw = coverage?.let { heatLoss.totalHeatLossKw * it },
        estimatedCoolingCapacityKw = coolingRatio?.let { heatLoss.totalHeatLossKw * it },
        freshAirFlowLs = area * freshAir,
        domesticHotWaterOccupancy = occupancy,
        suggestedDomesticHotWaterStorageLitres = dhwPerOccupant?.let { occupancy * it },
        controlZoneCount = zoneArea?.let { max(1, ceil(area / it).toInt()) },
        trace = CalculationTrace(
            formula = "ṁ=Q/(cp·ΔT); V̇=ṁ/ρ; pump=ΣΔp critical circuit",
            sourceIds = listOf(PRELIMINARY_HEAT_LOSS_SOURCE_ID, input.fluid.source.id),
            warnings = traceWarnings,
            methodId = "planruler-integrated-heating-design-v2",
            status = CalculationStatus.REQUIRES_LICENSED_VALIDATION,
            steps = listOf(
                CalculationStep("hydronics.mass-flow", "Q/(cp·ΔT)", designMassFlow, "kg/s"),
                CalculationStep("hydronics.volume-flow", "ṁ/ρ", designVolumeFlowM3H, "m³/h"),
            ) + listOfNotNull(
                circuitLossKpa?.let { CalculationStep("hydronics.critical-pressure-loss", "ΣΔp", it, "kPa") },
            ),
        ),
    )
}

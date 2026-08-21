package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatDesignTest {
    private val building = HeatLossInput(
        heatedAreaM2 = 100.0,
        wallsAreaM2 = 120.0,
        roofAreaM2 = 100.0,
        floorAreaM2 = 100.0,
        windowsAreaM2 = 20.0,
        floorHeightM = 2.7,
        floors = 1,
        indoorTemperatureC = 21.0,
        designOutdoorTemperatureC = -20.0,
    )

    @Test
    fun `preliminary heat loss is explicit and fully traceable`() {
        val result = calculateHeatLoss(building)

        assertEquals(41.0, result.designDeltaTK, 1e-9)
        assertEquals(270.0, result.heatedVolumeM3, 1e-9)
        assertEquals(6_801.9, result.totalHeatLossW, 1e-6)
        assertEquals(68.019, result.specificHeatLossWm2, 1e-6)
        assertEquals(CalculationStatus.REQUIRES_LICENSED_VALIDATION, result.trace.status)
        assertEquals(CalculationStandards.PRELIMINARY_ID, result.standardProfile.id)
        assertFalse(result.trace.methodId.contains("12831", ignoreCase = true))
        assertEquals(result.totalHeatLossW, result.trace.steps.single { it.id == "total" }.value, 1e-9)
    }

    @Test
    fun `published and draft compliance profiles stay locked until licensed validation`() {
        listOf(
            CalculationStandards.SIA_384_2_CH_ID,
            CalculationStandards.DIN_EN_12831_DE_ID,
            CalculationStandards.PREN_12831_DRAFT_ID,
        ).forEach { profileId ->
            val failure = runCatching {
                calculateHeatLoss(building.copy(calculationStandardProfileId = profileId))
            }.exceptionOrNull()
            assertTrue(failure is CalculationException)
        }
    }

    @Test
    fun `total heated floor area is not multiplied by floor count`() {
        val result = calculateHeatLoss(building.copy(heatedAreaM2 = 200.0, floors = 2))
        assertEquals(540.0, result.heatedVolumeM3, 1e-9)

        val perFloorArea = calculateHeatLoss(
            building.copy(heatedAreaM2 = 100.0, floors = 2, heatedAreaIncludesAllFloors = false),
        )
        assertEquals(540.0, perFloorArea.heatedVolumeM3, 1e-9)
    }

    @Test
    fun `linear psi bridge is multiplied by length rather than area`() {
        val result = calculateHeatLoss(
            building.copy(
                thermalBridgeWm2K = 0.0,
                linearThermalBridges = listOf(LinearThermalBridge(lengthM = 12.0, psiWmK = 0.08)),
            ),
        )
        assertEquals(39.36, result.breakdown.linearThermalBridgesW, 1e-9)
        assertEquals(0.0, result.breakdown.areaThermalBridgeAllowanceW, 1e-9)
    }

    @Test
    fun `combined design uses fluid properties and suppresses unsupported heuristics`() {
        val result = calculateHeatingDesign(HeatingDesignInput(building = building))
        val fluid = waterAt(40.0)
        val expectedMassFlow = result.heatLoss.totalHeatLossKw / (fluid.specificHeatKjKgK * 10.0)
        val expectedLitresHour = expectedMassFlow / fluid.densityKgM3 * 3_600_000.0

        assertEquals(expectedLitresHour, result.designFlowLitresPerHour, 1e-6)
        assertNull(result.recommendedPumpHeadKpa)
        assertNull(result.targetHeatPumpCapacityKw)
        assertNull(result.estimatedCoolingCapacityKw)
        assertNull(result.suggestedDomesticHotWaterStorageLitres)
        assertNull(result.controlZoneCount)
        assertTrue(result.trace.warnings.any { it.contains("critical hydraulic circuit") })
    }

    @Test
    fun `pump head is sum of calculated critical circuit and terminal losses`() {
        val pipe = PipeDimensions(26.9, 2.3, 0.045, MANUAL_INPUT_SOURCE)
        val circuit = HydraulicInput(
            powerKw = 6.0,
            deltaTK = 10.0,
            pipe = pipe,
            lengthM = 30.0,
            localLossCoefficient = 8.0,
            fluid = waterAt(40.0),
        )
        val expected = calculateHydraulics(circuit).totalLossPa / 1_000.0 + 12.0
        val result = calculateHeatingDesign(
            HeatingDesignInput(building = building, criticalCircuit = listOf(circuit), terminalAndValveLossKpa = 12.0),
        )
        assertEquals(expected, requireNotNull(result.recommendedPumpHeadKpa), 1e-9)
    }
}

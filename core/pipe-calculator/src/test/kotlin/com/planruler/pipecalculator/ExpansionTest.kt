package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionTest {
    @Test
    fun `calculates preliminary closed heating vessel`() {
        val result = calculateExpansionVessel(
            ExpansionVesselInput(
                systemVolumeLitres = 500.0,
                densityAtMinimumKgM3 = 998.2,
                densityAtMaximumKgM3 = 958.4,
                reserveLitres = 5.0,
                prechargeBarGauge = 1.0,
                finalPressureBarGauge = 2.5,
                safetyValveBarGauge = 3.0,
            ),
        )
        assertEquals(20.7638, result.expansionVolumeLitres, 0.001)
        assertTrue(result.minimumNominalVesselLitres > 50.0)
        assertTrue(result.trace.sourceIds.single().contains("pending"))
    }

    @Test(expected = CalculationException::class)
    fun `rejects final pressure at safety valve`() {
        calculateExpansionVessel(ExpansionVesselInput(500.0, 998.2, 958.4, 5.0, 1.0, 3.0, 3.0))
    }

    @Test(expected = CalculationException::class)
    fun `rejects inverted density relation`() {
        expansionCoefficient(950.0, 1_000.0)
    }
}

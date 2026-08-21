package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HydraulicsTest {
    private val pipe = PipeDimensions(60.3, 2.9, 0.05, MANUAL_INPUT_SOURCE)

    @Test
    fun `calculates inner diameter and thermal mass flow`() {
        assertEquals(0.0545, innerDiameterM(pipe), 1e-10)
        assertEquals(1.25, massFlowKgS(100.0, 4.0, 20.0), 1e-10)
    }

    @Test
    fun `classifies all flow regimes`() {
        assertEquals(FlowRegime.LAMINAR, frictionFactor(1_000.0, 0.00005, 0.05).regime)
        assertEquals(FlowRegime.TRANSITION, frictionFactor(3_000.0, 0.00005, 0.05).regime)
        assertEquals(FlowRegime.TURBULENT, frictionFactor(10_000.0, 0.00005, 0.05).regime)
    }

    @Test
    fun `calculates a complete water circuit with trace`() {
        val result = calculateHydraulics(
            HydraulicInput(
                powerKw = 50.0,
                deltaTK = 20.0,
                pipe = pipe,
                lengthM = 25.0,
                localLossCoefficient = 4.0,
                fluid = waterAt(60.0),
            ),
        )
        assertTrue(result.volumeFlowM3H in 2.0..3.0)
        assertEquals(58.32, result.pipeVolumeLitres, 0.05)
        assertTrue(result.totalLossPa > 0)
        assertTrue(result.trace.formula.contains("Darcy-Weisbach"))
        assertTrue(result.trace.warnings.isNotEmpty())
    }

    @Test
    fun `pipe volume equals area times length`() {
        assertEquals(78.5398, pipeVolumeLitres(0.1, 10.0), 0.0002)
    }

    @Test(expected = CalculationException::class)
    fun `rejects wall thicker than pipe radius`() {
        innerDiameterM(PipeDimensions(20.0, 11.0, 0.05, MANUAL_INPUT_SOURCE))
    }
}

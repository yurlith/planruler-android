package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FluidsTest {
    @Test
    fun `water checkpoints are exact`() {
        val water = waterAt(60.0)
        assertEquals(983.2, water.densityKgM3, 1e-9)
        assertEquals(4.185, water.specificHeatKjKgK, 1e-9)
        assertEquals(0.000467, water.dynamicViscosityPaS, 1e-12)
    }

    @Test
    fun `interpolation remains within adjacent water values`() {
        for (temperature in 1..99) {
            val water = waterAt(temperature.toDouble())
            assertTrue(water.densityKgM3 in 958.4..999.84)
            assertTrue(water.specificHeatKjKgK > 4.0)
            assertTrue(water.dynamicViscosityPaS > 0.0)
        }
    }

    @Test
    fun `bilinear interpolation uses temperature and concentration`() {
        val source = MANUAL_INPUT_SOURCE
        val dataset = FluidDataset(
            "test",
            "Test glycol",
            listOf(
                FluidGridPoint(0.0, 0.0, 1_000.0, 4.0, 0.001),
                FluidGridPoint(100.0, 0.0, 900.0, 4.2, 0.0005),
                FluidGridPoint(0.0, 50.0, 1_100.0, 3.0, 0.003),
                FluidGridPoint(100.0, 50.0, 1_000.0, 3.2, 0.0015),
            ),
            source,
        )
        val fluid = interpolateFluid(dataset, 50.0, 25.0)
        assertEquals(1_000.0, fluid.densityKgM3, 1e-9)
        assertEquals(3.6, fluid.specificHeatKjKgK, 1e-9)
        assertEquals(0.0015, fluid.dynamicViscosityPaS, 1e-9)
    }

    @Test
    fun `DOWFROST published checkpoint keeps units and provenance`() {
        val glycol = dowfrostAt(40.0, 40.0)
        assertEquals(1_026.49, glycol.densityKgM3, 1e-9)
        assertEquals(3.768, glycol.specificHeatKjKgK, 1e-9)
        assertEquals(0.0022389, glycol.dynamicViscosityPaS, 1e-12)
        assertEquals(40.0, glycol.concentrationPercent ?: error("missing concentration"), 0.0)
        assertEquals(ValidationStatus.VERIFIED, glycol.source.validationStatus)
        assertTrue(glycol.source.url?.startsWith("https://www.dow.com/") == true)
    }

    @Test
    fun `DOWFROST interpolation stays between published nodes`() {
        val glycol = dowfrostAt(52.5, 35.0)
        assertTrue(glycol.densityKgM3 in 1_004.26..1_026.49)
        assertTrue(glycol.specificHeatKjKgK in 3.768..3.972)
        assertTrue(glycol.dynamicViscosityPaS in 0.0009144..0.0022389)
    }

    @Test(expected = CalculationException::class)
    fun `DOWFROST rejects extrapolation outside published concentration`() {
        dowfrostAt(40.0, 20.0)
    }

    @Test(expected = CalculationException::class)
    fun `rejects water temperature outside table`() {
        waterAt(120.0)
    }
}

package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GeometryTest {
    @Test
    fun `calculates spool cut length`() {
        assertEquals(1_844.0, straightSpoolCutLengthMm(2_000.0, listOf(76.0, 76.0), listOf(2.0, 2.0)), 1e-9)
    }

    @Test
    fun `calculates two elbow offset`() {
        val result = twoElbowOffsetMm(500.0, 45.0, 76.0)
        assertEquals(707.106, result.centerTravelMm, 0.001)
        assertEquals(555.106, result.straightBetweenFittingsMm, 0.001)
    }

    @Test
    fun `calculates dimensioned insert and stock plan for target height`() {
        val result = calculateTwoElbowAssembly(
            TwoElbowAssemblyInput(
                targetHeightMm = 500.0,
                angleDeg = 45.0,
                elbowTakeoutEachMm = 31.5,
                weldGapEachMm = 2.0,
                pipe = PipeDimensions(60.3, 2.9, 0.05, MANUAL_INPUT_SOURCE),
                quantity = 10,
                stockLengthMm = 6_000.0,
                sawKerfMm = 3.0,
            ),
        )
        assertEquals(707.106, result.centerTravelMm, 0.001)
        assertEquals(500.0, result.horizontalAdvanceMm, 0.001)
        assertEquals(644.106, result.fittingFaceDistanceMm, 0.001)
        assertEquals(640.106, result.insertCutLengthMm, 0.001)
        assertEquals(47.376, result.minimumPossibleHeightMm, 0.001)
        assertEquals(2.628, result.pipeMassEachKg, 0.01)
        assertEquals(9, result.piecesPerStock)
        assertEquals(2, result.stockBarsRequired)
        assertEquals(6_401.068, result.totalNetPipeLengthMm, 0.01)
        assertEquals(30.0, result.estimatedKerfLossMm, 0.0)
        assertEquals(5_568.932, result.estimatedOffcutMm, 0.01)
    }

    @Test(expected = CalculationException::class)
    fun `rejects target height that cannot contain selected elbows`() {
        calculateTwoElbowAssembly(
            TwoElbowAssemblyInput(
                targetHeightMm = 40.0,
                angleDeg = 45.0,
                elbowTakeoutEachMm = 31.5,
                weldGapEachMm = 2.0,
                pipe = PipeDimensions(60.3, 2.9, 0.05, MANUAL_INPUT_SOURCE),
            ),
        )
    }

    @Test(expected = CalculationException::class)
    fun `rejects stock shorter than required continuous insert`() {
        calculateTwoElbowAssembly(
            TwoElbowAssemblyInput(
                targetHeightMm = 3_000.0,
                angleDeg = 45.0,
                elbowTakeoutEachMm = 31.5,
                weldGapEachMm = 2.0,
                pipe = PipeDimensions(60.3, 2.9, 0.05, MANUAL_INPUT_SOURCE),
                stockLengthMm = 3_000.0,
            ),
        )
    }

    @Test
    fun `calculates true 3d length and steel mass`() {
        assertEquals(1_300.0, trueLength3dMm(300.0, 400.0, 1_200.0), 1e-9)
        assertEquals(4.106, theoreticalPipeMassKg(60.3, 2.9, 1.0), 0.01)
    }

    @Test
    fun `steel support span follows DN plus 10 feet`() {
        val result = maximumSupportSpanM(254.0, PipeSupportMaterial.STEEL)
        assertEquals(6.096, result.maximumSpanM, 1e-6)
        assertEquals(CalculationStatus.PRELIMINARY, result.trace.status)
    }

    @Test
    fun `copper support span interpolates between the table anchors`() {
        assertEquals(2.4384, maximumSupportSpanM(25.4, PipeSupportMaterial.COPPER).maximumSpanM, 1e-6)
        assertEquals(3.6576, maximumSupportSpanM(101.6, PipeSupportMaterial.COPPER).maximumSpanM, 1e-6)
        assertEquals(3.048, maximumSupportSpanM(63.5, PipeSupportMaterial.COPPER).maximumSpanM, 1e-6)
    }

    @Test
    fun `copper support span clamps beyond the published table range`() {
        val beyond = maximumSupportSpanM(200.0, PipeSupportMaterial.COPPER)
        assertEquals(3.6576, beyond.maximumSpanM, 1e-6)
    }

    @Test
    fun `spool deduction property holds for deterministic samples`() {
        val random = Random(42)
        repeat(250) {
            val takeoutA = random.nextDouble(0.0, 200.0)
            val takeoutB = random.nextDouble(0.0, 200.0)
            val gap = random.nextDouble(0.0, 5.0)
            val overall = takeoutA + takeoutB + gap + 100.0
            val cut = straightSpoolCutLengthMm(overall, listOf(takeoutA, takeoutB), listOf(gap))
            assertEquals(100.0, cut, 1e-9)
            assertTrue(cut > 0)
        }
    }
}

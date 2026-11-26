package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PipeOffsetTest {
    @Test
    fun `45 degree offset with DN50 3D elbows and weld gaps`() {
        val radius = ELBOW_45_3D_CATALOG.single { it.dn == 50 }.centerlineRadiusMm
        val takeout = bendTakeoutMm(radius, 45.0)
        assertEquals(31.480, takeout, 0.001)
        val result = pipeOffset(PipeOffsetInput(firstMm = 500.0, angleDeg = 45.0, takeoutMm = takeout, jointGapMm = 2.0))
        assertEquals(500.0, result.offsetMm, 1e-9)
        assertEquals(707.107, result.travelMm, 0.001)
        assertEquals(500.0, result.advanceMm, 1e-9)
        assertEquals(644.146, result.faceToFaceMm, 0.001)
        assertEquals(640.146, result.cutLengthMm, 0.001)
        assertEquals(0.0, result.rollDeg, 1e-12)
        assertTrue(result.fits)
    }

    @Test
    fun `matches the existing two-elbow assembly formula`() {
        val assembly = calculateTwoElbowAssembly(
            TwoElbowAssemblyInput(
                targetHeightMm = 430.0,
                angleDeg = 30.0,
                elbowTakeoutEachMm = 20.4,
                weldGapEachMm = 1.5,
                pipe = PipeDimensions(60.3, 2.9, 0.05, MANUAL_INPUT_SOURCE),
            ),
        )
        val offset = pipeOffset(PipeOffsetInput(firstMm = 430.0, angleDeg = 30.0, takeoutMm = 20.4, jointGapMm = 1.5))
        assertEquals(assembly.centerTravelMm, offset.travelMm, 1e-9)
        assertEquals(assembly.horizontalAdvanceMm, offset.advanceMm, 1e-9)
        assertEquals(assembly.insertCutLengthMm, offset.cutLengthMm, 1e-9)
        assertEquals(assembly.minimumPossibleHeightMm, offset.minimumOffsetMm, 1e-9)
    }

    @Test
    fun `right angle offset has no advance and loses one radius per elbow`() {
        val radius = ELBOW_45_3D_CATALOG.single { it.dn == 25 }.centerlineRadiusMm
        val takeout = bendTakeoutMm(radius, 90.0)
        assertEquals(radius, takeout, 1e-9)
        val result = pipeOffset(PipeOffsetInput(firstMm = 300.0, angleDeg = 90.0, takeoutMm = takeout))
        assertEquals(300.0, result.travelMm, 1e-9)
        assertEquals(0.0, result.advanceMm, 0.0)
        assertEquals(300.0 - 2 * radius, result.cutLengthMm, 1e-9)
    }

    @Test
    fun `rolling offset uses the true offset and reports the roll of its plane`() {
        val result = pipeOffset(PipeOffsetInput(firstMm = 300.0, secondMm = 400.0, angleDeg = 45.0, takeoutMm = 0.0))
        assertEquals(500.0, result.offsetMm, 1e-9)
        assertEquals(500.0 * sqrt(2.0), result.travelMm, 1e-9)
        assertEquals(53.130, result.rollDeg, 0.001)
        val sideOnly = pipeOffset(PipeOffsetInput(firstMm = 0.0, secondMm = 250.0, angleDeg = 60.0, takeoutMm = 0.0))
        assertEquals(250.0, sideOnly.offsetMm, 1e-9)
        assertEquals(90.0, sideOnly.rollDeg, 1e-9)
    }

    @Test
    fun `too small an offset is reported with the smallest one that fits`() {
        val result = pipeOffset(PipeOffsetInput(firstMm = 40.0, angleDeg = 45.0, takeoutMm = 31.48, jointGapMm = 2.0))
        assertFalse(result.fits)
        assertEquals((2 * 31.48 + 4.0) * sqrt(0.5), result.minimumOffsetMm, 1e-9)
        val atMinimum = pipeOffset(
            PipeOffsetInput(firstMm = result.minimumOffsetMm, angleDeg = 45.0, takeoutMm = 31.48, jointGapMm = 2.0),
        )
        assertEquals(0.0, atMinimum.cutLengthMm, 1e-9)
    }

    @Test
    fun `angle for a given advance and the nearest stock angle`() {
        assertEquals(45.0, offsetAngleForAdvanceDeg(500.0, 500.0), 1e-9)
        assertEquals(90.0, offsetAngleForAdvanceDeg(500.0, 0.0), 1e-9)
        assertEquals(30.0, offsetAngleForAdvanceDeg(1.0, sqrt(3.0)), 1e-9)
        assertEquals(45.0, nearestStandardOffsetAngle(41.0), 0.0)
        assertEquals(22.5, nearestStandardOffsetAngle(26.25), 0.0)
        assertEquals(90.0, nearestStandardOffsetAngle(88.0), 0.0)
    }

    @Test(expected = CalculationException::class)
    fun `zero offset is rejected`() {
        pipeOffset(PipeOffsetInput(firstMm = 0.0, angleDeg = 45.0, takeoutMm = 10.0))
    }

    @Test(expected = CalculationException::class)
    fun `angles beyond a right angle are rejected`() {
        pipeOffset(PipeOffsetInput(firstMm = 100.0, angleDeg = 120.0, takeoutMm = 10.0))
    }
}

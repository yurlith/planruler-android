package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.tan

class FabricationAssemblyTest {
    @Test
    fun `generates dimensionally closed DN50 PN16 workshop assembly`() {
        val result = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(
                dn = 50,
                pn = 16,
                targetOffsetMm = 500.0,
                overallFaceToFaceMm = 1_600.0,
            ),
        )

        assertEquals(60.3, result.pipe.outsideDiameterMm, 0.0)
        assertEquals(165.0, result.flange.outsideDiameterMm, 0.0)
        assertEquals(45.0, result.flange.faceToWeldMm, 0.0)
        assertEquals(31.480, result.elbowTakeoutMm, 0.001)
        assertEquals(707.106, result.diagonalCenterTravelMm, 0.001)
        assertEquals(640.146, result.diagonalPipeCutMm, 0.001)
        assertEquals(469.520, result.inletPipeCutMm, 0.001)
        assertEquals(result.inletPipeCutMm, result.outletPipeCutMm, 1e-9)
        assertEquals(1_600.0, result.endFace.x, 1e-6)
        assertEquals(500.0, result.endFace.y, 1e-6)
        assertEquals(6, result.weldCount)
        assertEquals(8, result.flangeBoltCount)
        assertEquals(1, result.stockBarsRequired)
        assertEquals(13, result.elements.size)
    }

    /**
     * The working drawing dimensions the three set-out stations — start face to the first
     * elbow corner, corner to corner, corner to end face — above the overall size. A chain
     * that does not add up to the overall would send the fitter the wrong marks, so the
     * arithmetic behind it is pinned here rather than trusted to the drawing code.
     */
    @Test
    fun `the set-out stations add up to the overall face to face length`() {
        listOf(30.0, 45.0, 60.0).forEach { angle ->
            listOf(400.0, 500.0, 900.0).forEach { offset ->
                val result = calculateFlangedOffsetAssembly(
                    FlangedOffsetAssemblyInput(
                        dn = 50,
                        pn = 16,
                        targetOffsetMm = offset,
                        overallFaceToFaceMm = 2_400.0,
                        angleDeg = angle,
                    ),
                )
                val firstCorner = requireNotNull(result.elements.single { it.code == "E1" }.control)
                val secondCorner = requireNotNull(result.elements.single { it.code == "E2" }.control)
                val inlet = firstCorner.x - result.startFace.x
                val advance = secondCorner.x - firstCorner.x
                val outlet = result.endFace.x - secondCorner.x

                val label = "angle=$angle offset=$offset"
                assertTrue("$label: inlet run is not positive", inlet > 0.0)
                assertTrue("$label: outlet run is not positive", outlet > 0.0)
                assertEquals(
                    "$label: the station chain does not close on the overall size",
                    result.input.overallFaceToFaceMm,
                    inlet + advance + outlet,
                    1e-6,
                )
                // The corner-to-corner advance is what the offset geometry actually demands.
                assertEquals(
                    "$label: centre advance disagrees with the calculated one",
                    result.horizontalCenterAdvanceMm,
                    advance,
                    1e-6,
                )
                // A 45 degree offset advances horizontally exactly as much as it rises.
                if (angle == 45.0) assertEquals("$label", offset, advance, 1e-6)
            }
        }
    }

    @Test
    fun `uses diameter-specific takeouts and closes for every catalog DN`() {
        listOf(30.0, 45.0, 60.0, 90.0).forEach { angle ->
            PIPE_INSTALLATION_SERIES.forEach { pipe ->
                val elbow = ELBOW_45_3D_CATALOG.single { it.dn == pipe.dn }
                val flange = WELD_NECK_FLANGE_TYPE11_CATALOG.single { it.dn == pipe.dn && it.pn == 16 }
                val radians = Math.toRadians(angle)
                val takeout = elbow.centerlineRadiusMm * tan(radians / 2.0)
                val height = (2.0 * takeout + 4.0) * sin(radians) + 500.0
                val advance = if (angle == 90.0) 0.0 else height / tan(radians)
                val overall = 2.0 * flange.faceToWeldMm + 8.0 + 2.0 * takeout + advance + 1_000.0
                val result = calculateFlangedOffsetAssembly(
                    FlangedOffsetAssemblyInput(pipe.dn, 16, height, overall, angle),
                )
                assertEquals(overall, result.endFace.x, 1e-6)
                assertEquals(height, result.endFace.y, 1e-6)
                assertTrue(result.cuts.all { it.lengthMm > 0.0 })
            }
        }
    }

    @Test
    fun `custom angle is explicit and stock plan accounts for every cut and kerf`() {
        val result = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(
                dn = 100,
                pn = 25,
                targetOffsetMm = 800.0,
                overallFaceToFaceMm = 2_600.0,
                angleDeg = 60.0,
                quantity = 3,
                stockLengthMm = 6_000.0,
                sawKerfMm = 3.0,
            ),
        )

        assertTrue(result.warnings.any { "Custom angle" in it })
        assertEquals(9, result.stockBars.sumOf { it.cutsMm.size })
        assertEquals(27.0, result.totalKerfLossMm, 1e-9)
        assertEquals(
            result.totalPurchasedLengthMm,
            result.totalNetPipeLengthMm + result.totalKerfLossMm + result.totalOffcutMm,
            1e-6,
        )
    }

    @Test(expected = CalculationException::class)
    fun `rejects an envelope too short for selected fittings`() {
        calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(300, 40, 500.0, 600.0),
        )
    }

    @Test(expected = CalculationException::class)
    fun `rejects a pipe piece longer than stock`() {
        calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(50, 16, 2_000.0, 5_000.0, stockLengthMm = 1_000.0),
        )
    }
}

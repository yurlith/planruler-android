package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyProfileOverrides3D
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import com.planruler.fabrication3d.catalog.elbowCatalogPosition
import com.planruler.fabrication3d.catalog.teeCatalogPosition
import com.planruler.fabrication3d.catalog.weldNeckFlangeOptions
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The flange series is the one place the catalog offers a real choice inside a single
 * diameter, so it is the one the fitter gets as a list. Everything else holds a single
 * position per DN and is reported instead.
 */
class CatalogOptionsTest {
    @Test
    fun `every installation diameter offers several flange classes`() {
        PIPE_INSTALLATION_SERIES.forEach { pipe ->
            val options = weldNeckFlangeOptions(pipe.dn)
            assertTrue("DN ${pipe.dn} offers ${options.size} flanges", options.size >= 2)
            assertEquals(
                "DN ${pipe.dn} lists a class twice",
                options.map { it.pressureClass }.distinct().size,
                options.size,
            )
            assertEquals(
                "DN ${pipe.dn} is not ordered by class",
                options.map { it.pressureClass }.sorted(),
                options.map { it.pressureClass },
            )
            options.forEach { option ->
                assertTrue(option.outsideDiameterMm > pipe.outsideDiameterMm)
                assertTrue(option.boltHoleCount >= 4)
                assertTrue(option.catalogId.isNotBlank())
            }
        }
    }

    @Test
    fun `a heavier class really is a heavier flange`() {
        val options = weldNeckFlangeOptions(200)
        val light = options.first { it.pressureClass == 6 }
        val heavy = options.first { it.pressureClass == 40 }

        assertTrue(heavy.outsideDiameterMm > light.outsideDiameterMm)
        assertTrue(heavy.boltHoleCount >= light.boltHoleCount)
    }

    /** Picking a position must produce exactly that part, not something near it. */
    @Test
    fun `a picked catalog flange reaches the fabricated part`() {
        val profile = workshopProfile()
        val picked = weldNeckFlangeOptions(50).first { it.pressureClass == 40 }

        val custom = engine.profiles.apply(
            profile,
            AssemblyProfileOverrides3D(
                flangeOutsideDiameterMm = picked.outsideDiameterMm,
                flangeFaceToWeldMm = picked.faceToWeldMm,
                flangeThicknessMm = picked.thicknessMm,
                flangeBoltCircleDiameterMm = picked.boltCircleDiameterMm,
                flangeBoltHoleCount = picked.boltHoleCount,
                flangeBoltHoleDiameterMm = picked.boltHoleDiameterMm,
            ),
        ).unwrap()

        val editor = engine.chains.create(custom).unwrap()
        val geometry = editor.assembly.part("F1").definition.geometry as WeldNeckFlangeGeometry3D
        assertEquals(picked.outsideDiameterMm, geometry.outsideDiameterMm, 1e-9)
        assertEquals(picked.boltHoleCount, geometry.boltHoleCount)
        assertEquals(picked.boltCircleDiameterMm, geometry.boltCircleDiameterMm, 1e-9)
        assertEquals(picked.faceToWeldMm, geometry.faceToWeldMm, 1e-9)
        assertValid(editor.assembly)
    }

    @Test
    fun `every catalog flange is accepted by the engine rules`() {
        PIPE_INSTALLATION_SERIES.forEach { pipe ->
            val profile = installationProfile(pipe.dn)
            weldNeckFlangeOptions(pipe.dn).forEach { option ->
                val applied = engine.profiles.apply(
                    profile,
                    AssemblyProfileOverrides3D(
                        flangeOutsideDiameterMm = option.outsideDiameterMm,
                        flangeFaceToWeldMm = option.faceToWeldMm,
                        flangeThicknessMm = option.thicknessMm,
                        flangeBoltCircleDiameterMm = option.boltCircleDiameterMm,
                        flangeBoltHoleCount = option.boltHoleCount,
                        flangeBoltHoleDiameterMm = option.boltHoleDiameterMm,
                    ),
                )
                assertTrue(
                    "DN ${pipe.dn} PN ${option.pressureClass} was refused: $applied",
                    applied is com.planruler.fabrication3d.Fabrication3DResult.Ok,
                )
            }
        }
    }

    @Test
    fun `the elbow and tee positions are reported for the diameters that have them`() {
        assertNotNull(elbowCatalogPosition(50))
        assertNotNull(teeCatalogPosition(50))
        // The tee catalog stops at DN 150; nothing invents a position beyond it.
        assertNull(teeCatalogPosition(300))
    }
}

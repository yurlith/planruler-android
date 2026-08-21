package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogTest {
    @Test
    fun `catalog contains expected verified manufacturer series`() {
        assertEquals(14, PIPE_INSTALLATION_SERIES.size)
        assertEquals(14, ELBOW_45_3D_CATALOG.size)
        assertEquals(11, EQUAL_TEE_CATALOG.size)
        assertEquals(13, ECCENTRIC_REDUCER_CATALOG.size)
        assertEquals(80, FLANGE_CONNECTING_DIMENSIONS.size)
        assertEquals(70, WELD_NECK_FLANGE_TYPE11_CATALOG.size)
        listOf(
            PIPE_INSTALLATION_SERIES.map { it.id },
            ELBOW_45_3D_CATALOG.map { it.id },
            EQUAL_TEE_CATALOG.map { it.id },
            ECCENTRIC_REDUCER_CATALOG.map { it.id },
            FLANGE_CONNECTING_DIMENSIONS.map { it.id },
            WELD_NECK_FLANGE_TYPE11_CATALOG.map { it.id },
        ).forEach { ids -> assertEquals(ids.size, ids.distinct().size) }
    }

    @Test
    fun `DN50 dimensions align across pipe fittings`() {
        val pipe = PIPE_INSTALLATION_SERIES.single { it.dn == 50 }
        val elbow = ELBOW_45_3D_CATALOG.single { it.dn == 50 }
        val tee = EQUAL_TEE_CATALOG.single { it.dn == 50 }
        assertEquals(60.3, pipe.outsideDiameterMm, 0.0)
        assertEquals(2.9, pipe.wallThicknessMm, 0.0)
        assertEquals(pipe.outsideDiameterMm, elbow.outsideDiameterMm, 0.0)
        assertEquals(pipe.wallThicknessMm, tee.wallThicknessMm, 0.0)
        assertEquals(76.0, elbow.centerlineRadiusMm, 0.0)
        assertEquals(31.48, elbow.centerToEndMm, 0.01)
        assertEquals(64.0, tee.centerToEndMm, 0.0)
        assertTrue(pipe.theoreticalMassKgM in 4.0..4.2)
    }

    @Test
    fun `PN flange checkpoints match published connecting table`() {
        val dn200pn16 = FLANGE_CONNECTING_DIMENSIONS.single { it.dn == 200 && it.pn == 16 }
        assertEquals(340.0, dn200pn16.outsideDiameterMm, 0.0)
        assertEquals(295.0, dn200pn16.boltCircleDiameterMm, 0.0)
        assertEquals(12, dn200pn16.boltHoleCount)
        assertEquals(22.0, dn200pn16.boltHoleDiameterMm, 0.0)

        val dn400pn40 = FLANGE_CONNECTING_DIMENSIONS.single { it.dn == 400 && it.pn == 40 }
        assertEquals(660.0, dn400pn40.outsideDiameterMm, 0.0)
        assertEquals(585.0, dn400pn40.boltCircleDiameterMm, 0.0)
        assertEquals(16, dn400pn40.boltHoleCount)
        assertEquals(39.0, dn400pn40.boltHoleDiameterMm, 0.0)
    }

    @Test
    fun `all catalog rows are positive and traceable`() {
        PIPE_INSTALLATION_SERIES.forEach {
            assertTrue(it.dn > 0 && it.innerDiameterMm > 0 && it.theoreticalMassKgM > 0)
            assertTrue(it.source.url?.startsWith("https://") == true)
        }
        ELBOW_45_3D_CATALOG.forEach {
            assertTrue(it.centerlineRadiusMm > 0 && it.radiusToleranceMm > 0)
            assertTrue(it.source.url?.startsWith("https://") == true)
        }
        FLANGE_CONNECTING_DIMENSIONS.forEach {
            assertTrue(it.pn > 0 && it.dn > 0 && it.boltHoleCount >= 4)
            assertTrue(it.source.validationStatus == ValidationStatus.VERIFIED)
        }
        WELD_NECK_FLANGE_TYPE11_CATALOG.forEach {
            assertTrue(it.thicknessMm > 0.0 && it.faceToWeldMm > it.thicknessMm)
            assertTrue(it.pipeOutsideDiameterMm > 0.0)
            assertTrue(it.source.validationStatus == ValidationStatus.VERIFIED)
        }
    }
}

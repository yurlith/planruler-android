package com.planruler.pipecalculator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PipeTablesTest {
    @Test
    fun `tables have unique ids and plausible rows`() {
        val ids = PIPE_REFERENCE_TABLES.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        PIPE_REFERENCE_TABLES.forEach { table ->
            assertTrue(table.id, table.rows.isNotEmpty())
            table.rows.forEach { row ->
                assertTrue(row.label, row.innerDiameterMm > 0 && row.innerDiameterMm < row.outsideDiameterMm)
                // Printed inside diameters may deviate from OD - 2s by rounding, never by a wall.
                val derived = row.outsideDiameterMm - 2 * row.wallThicknessMm
                assertTrue("${table.id} ${row.label}", abs(derived - row.innerDiameterMm) <= 2.1)
                row.massKgM?.let { assertTrue(row.label, it > 0.0) }
            }
        }
    }

    @Test
    fun `derived areas match the printed suissetec checkpoints`() {
        fun area(tableId: String, od: Double, wall: Double) =
            pipeTableById(tableId)!!.rows.single { it.outsideDiameterMm == od && it.wallThicknessMm == wall }.flowAreaMm2

        assertEquals(122.7, area("din-en-10255-gewinderohr", 17.2, 2.35), 0.1)
        assertEquals(8708.6, area("din-en-10255-gewinderohr", 114.3, 4.5), 0.1)
        assertEquals(1963.5, area("din-en-1057-kupfer", 54.0, 2.0), 0.1)
        assertEquals(8958.4, area("din-en-10220-siederohr", 114.0, 3.6), 0.1)
        assertEquals(1384.7, area("din-16893-pe-x", 63.0, 10.5), 1.0)
        assertEquals(2123.7, area("nussbaum-optiflex", 63.0, 4.5), 0.1)
        assertEquals(2290.2, area("jrg-sanipex-mt", 63.0, 4.5), 0.1)
        assertEquals(314.2, area("geberit-pushfit", 25.0, 2.5), 0.1)
        // The PE 100 table was printed with pi = 3.14 (row 20 mm reads 200.96).
        assertEquals(32870.7, area("en-12201-pe100-pn16", 250.0, 22.7), 10.0)
        assertEquals(17110.0, area("geberit-pe-hd-abwasser", 160.0, 6.2), 5.0)
    }

    @Test
    fun `water content is area per metre in litres`() {
        val copper22 = pipeTableById("din-en-1057-kupfer")!!.rows.single { it.outsideDiameterMm == 22.0 }
        assertEquals(0.314, copper22.volumeLitresPerM, 0.001)
    }

    @Test
    fun `theoretical series mass uses material density`() {
        val stainless54 = pipeTableById("press-stainless-en10312")!!.rows.single { it.outsideDiameterMm == 54.0 }
        assertEquals(51.0, stainless54.innerDiameterMm, 0.0)
        assertEquals(1.967, stainless54.massKgM!!, 0.01)
    }

    @Test
    fun `row converts to hydraulic dimensions with the printed bore`() {
        val row = pipeTableById("din-en-10220-siederohr")!!.rows.single { it.outsideDiameterMm == 42.4 }
        val dimensions = row.dimensions(0.045, SUISSETEC_TABLES_SOURCE)
        assertEquals(37.0, dimensions.outsideDiameterMm - 2 * dimensions.wallThicknessMm, 1e-9)
    }
}

package com.planruler.isometric

import com.planruler.model.Calibration
import com.planruler.model.DocPoint
import com.planruler.model.IsoPipeSystem
import com.planruler.model.LengthUnit
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.PipeRoute
import com.planruler.model.TakeoffProperties
import com.planruler.model.TradeCategory
import com.planruler.model.starterTakeoffTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanIsometricBuilderTest {
    /** 1 document unit = 10 mm. */
    private val calibration = Calibration.reference(100.0, 1.0, LengthUnit.METER)
    private var ids = 0
    private val supplyTemplate = starterTakeoffTemplates().first { it.id == "heating-supply-dn20" }

    private fun run(id: String, vararg points: DocPoint, route: PipeRoute? = null, templateId: String? = supplyTemplate.id, page: Int = 0) =
        Measurement(
            id = MeasurementId(id),
            type = MeasurementType.POLYLINE,
            points = points.toList(),
            takeoff = supplyTemplate.takeoff,
            templateId = templateId,
            createdAtEpochMs = 0,
            route = route,
            pageIndex = page,
        )

    private fun build(vararg measurements: Measurement): PlanIsometricResult =
        when (
            val result = PlanIsometricBuilder.build(
                IsometricEngine.newSheet("iso", "Plan", 0L),
                measurements.toList(),
                calibration,
                PlanIsometricOptions(pageIndex = 0),
            ) { "g${ids++}" }
        ) {
            is IsoResult.Ok -> result.value
            is IsoResult.Error -> throw AssertionError(result.error.toString())
        }

    @Test
    fun `plan runs become model runs with north up and true lengths`() {
        val result = build(run("a", DocPoint(0.0, 0.0), DocPoint(300.0, 0.0), DocPoint(300.0, -200.0)))
        val network = IsoNetwork(result.sheet)
        val lengths = result.sheet.pipes.map(network::length).sorted()
        assertEquals(listOf(2_000.0, 3_000.0), lengths)
        // Plan up (negative document Y) is model north.
        assertTrue(result.sheet.nodes.any { it.position.x == 3_000.0 && it.position.y == 2_000.0 })
        assertEquals(IsoPipeSystem.HEATING_SUPPLY, result.sheet.pipes.first().spec.system)
        assertEquals(20, result.sheet.pipes.first().spec.nominalDiameter)
        assertEquals(MeasurementId("a"), result.sheet.pipes.first().sourceMeasurementId)
    }

    @Test
    fun `risers and drops become vertical runs at their vertices`() {
        val route = PipeRoute(startElevationMm = 300.0, risesMm = listOf(0.0, 2_500.0, -400.0))
        val result = build(run("r", DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), DocPoint(100.0, 100.0), route = route))
        val network = IsoNetwork(result.sheet)
        val vertical = result.sheet.pipes.filter { pipe ->
            val a = network.position(pipe.from)!!
            val b = network.position(pipe.to)!!
            a.x == b.x && a.y == b.y
        }
        assertEquals(listOf(400.0, 2_500.0), vertical.map(network::length).sorted())
        assertEquals(setOf(300.0, 2_800.0, 2_400.0), result.sheet.nodes.map { it.position.z }.toSet())
        val bom = IsoTakeoff.of(result.sheet)
        assertEquals(1_000.0 + 1_000.0 + 2_500.0 + 400.0, bom.totalLengthMm, 1e-9)
        assertEquals(2_900.0, bom.pipes.single().verticalMm, 1e-9)
    }

    @Test
    fun `touching ends join and an end on another run becomes a tee`() {
        val main = run("main", DocPoint(0.0, 0.0), DocPoint(400.0, 0.0))
        // Ends 2 mm (0.2 units) beside the main: still the same joint within the 30 mm tolerance.
        val branch = run("branch", DocPoint(150.0, 200.0), DocPoint(150.0, 0.2))
        val continuation = run("next", DocPoint(400.0, 0.0), DocPoint(400.0, 300.0))
        val result = build(main, branch, continuation)
        assertEquals(1, result.teeJunctions)
        val network = IsoNetwork(result.sheet)
        val kinds = network.fittings().map { it.kind }
        assertEquals(1, kinds.count { it == IsoFittingKind.TEE })
        assertEquals(1, kinds.count { it == IsoFittingKind.ELBOW })
        assertEquals(5, result.sheet.nodes.size)
        assertEquals(4, result.sheet.pipes.size)
    }

    @Test
    fun `only pipe runs of the chosen page are used`() {
        val other = run("other", DocPoint(0.0, 0.0), DocPoint(10.0, 0.0), page = 1)
        val painting = run("wall", DocPoint(0.0, 0.0), DocPoint(10.0, 0.0), templateId = null)
            .copy(takeoff = TakeoffProperties(category = TradeCategory.PAINTING))
        val result = PlanIsometricBuilder.build(
            IsometricEngine.newSheet("iso", "Plan", 0L),
            listOf(other, painting),
            calibration,
            PlanIsometricOptions(pageIndex = 0),
        ) { "x" }
        assertTrue(result is IsoResult.Error)
        val uncalibrated = PlanIsometricBuilder.build(
            IsometricEngine.newSheet("iso", "Plan", 0L),
            listOf(run("a", DocPoint(0.0, 0.0), DocPoint(1.0, 0.0))),
            null,
        ) { "x" }
        assertTrue(uncalibrated is IsoResult.Error)
    }

    @Test
    fun `services are recognised from wording in every release language`() {
        fun system(material: String) = PlanIsometricBuilder.systemOf(
            run("m", DocPoint(0.0, 0.0), DocPoint(1.0, 0.0), templateId = null).copy(takeoff = TakeoffProperties(material = material)),
        )
        assertEquals(IsoPipeSystem.HEATING_RETURN, system("Труба обратки"))
        assertEquals(IsoPipeSystem.HEATING_SUPPLY, system("Vorlauf Heizung"))
        assertEquals(IsoPipeSystem.HOT_WATER_CIRCULATION, system("Циркуляция ГВС"))
        assertEquals(IsoPipeSystem.HOT_WATER, system("Rura c.w.u."))
        assertEquals(IsoPipeSystem.COLD_WATER, system("Woda zimna"))
        assertEquals(IsoPipeSystem.SEWER, system("Abwasserleitung"))
        assertEquals(IsoPipeSystem.GAS, system("Gas pipe"))
        assertEquals(IsoPipeSystem.OTHER, system("Kabel"))
        assertEquals(
            IsoPipeSystem.SEWER,
            PlanIsometricBuilder.systemOf(run("s", DocPoint(0.0, 0.0), DocPoint(1.0, 0.0), templateId = "plumbing-sewer-dn50")),
        )
    }

    @Test
    fun `nominal sizes are read from common notations`() {
        assertEquals(20, PlanIsometricBuilder.nominalDiameterOf("DN20"))
        assertEquals(25, PlanIsometricBuilder.nominalDiameterOf("Ду 25"))
        assertEquals(160, PlanIsometricBuilder.nominalDiameterOf("Ø160"))
        assertEquals(110, PlanIsometricBuilder.nominalDiameterOf("d110"))
        assertEquals(20, PlanIsometricBuilder.nominalDiameterOf("20x2.8"))
        assertEquals(32, PlanIsometricBuilder.nominalDiameterOf("32"))
        assertNull(PlanIsometricBuilder.nominalDiameterOf("3/4\""))
        assertNull(PlanIsometricBuilder.nominalDiameterOf("steel"))
        assertNull(PlanIsometricBuilder.nominalDiameterOf(null))
    }
}

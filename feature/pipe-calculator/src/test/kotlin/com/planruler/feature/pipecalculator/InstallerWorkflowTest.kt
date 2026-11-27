package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.OffsetPlacement3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.fabrication3d.getOrNull
import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.pipecalculator.CalculationException
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.tan

class InstallerWorkflowTest {
    @Test fun `large diameter right angle routes can always borrow their catalog profile`() {
        listOf(200, 250, 300).forEach { dn ->
            OFFSET_ELBOW_ANGLES.forEach { angle ->
                val input = InstallationJobInput(nominalDiameter = dn, angleDeg = angle,
                    alongMm = 4_000.0, lateralOffsetMm = 1_500.0)
                val result = workshopAssembly(job(InstallationTaskType.FLAT_OFFSET, input))
                assertEquals(dn, result.pipe.dn)
                val route = DefaultFabrication3DEngine().router.solve(
                    installerRouteRequest(job(InstallationTaskType.FLAT_OFFSET, input))
                        .toRouteRequest(result.toAssemblyProfile3D()),
                )
                if (angle == 90.0) assertNotNull("DN $dn", route.getOrNull())
            }
        }
    }

    private fun job(
        task: InstallationTaskType,
        input: InstallationJobInput = InstallationJobInput(),
    ) = InstallationJob(
        id = InstallationJobId("job"),
        name = "Test",
        taskType = task,
        input = input,
        createdAtEpochMs = 1L,
        modifiedAtEpochMs = 1L,
    )

    @Test fun `rolling offset maps installer dimensions to solver coordinates`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.ROLLING_OFFSET,
                InstallationJobInput(alongMm = 1_600.0, lateralOffsetMm = 500.0, verticalOffsetMm = 300.0),
            ),
        )

        assertEquals(InstallerCalculationPath.AUTO_ROUTE, request.calculationPath)
        // Right of the run is -Y: the world is right-handed and stands on Z.
        assertEquals(Vec3(1_600.0, -500.0, 300.0), request.target)
        assertEquals(Vec3.UNIT_X, request.endDirection)
        assertEquals(45.0, request.preferredAngleDeg ?: 0.0, 0.0)
    }

    @Test fun `left drop and back direction never expose negative coordinate fields to the fitter`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.U_TURN,
                InstallationJobInput(
                    alongMm = 2_000.0,
                    lateralOffsetMm = 900.0,
                    verticalOffsetMm = 400.0,
                    lateralDirection = InstallationLateralDirection.LEFT,
                    verticalDirection = InstallationVerticalDirection.DOWN,
                    endDirection = InstallationEndDirection.BACK,
                ),
            ),
        )

        assertEquals(Vec3(2_000.0, 900.0, -400.0), request.target)
        assertEquals(-Vec3.UNIT_X, request.endDirection)
    }

    @Test fun `straight insert ignores offsets left in an older job`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.STRAIGHT_INSERT,
                InstallationJobInput(alongMm = 1_200.0, lateralOffsetMm = 500.0, verticalOffsetMm = 300.0),
            ),
        )
        assertEquals(Vec3(1_200.0, 0.0, 0.0), request.target)
        assertEquals(InstallerCalculationPath.AUTO_ROUTE, request.calculationPath)
    }

    @Test fun `different pipe edge references apply catalog diameter correction`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.FLAT_OFFSET,
                InstallationJobInput(
                    nominalDiameter = 50,
                    lateralOffsetMm = 500.0,
                    startReference = InstallationMeasurementReference.PIPE_OUTER_EDGE,
                    endReference = InstallationMeasurementReference.PIPE_INNER_EDGE,
                ),
            ),
        )
        assertEquals(-60.3, request.referenceCorrectionMm, 1e-9)
        assertEquals(-439.7, request.target.y, 1e-9)
    }

    @Test fun `a flat offset that climbs is solved in the vertical plane with its edge correction`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.FLAT_OFFSET,
                InstallationJobInput(
                    nominalDiameter = 50,
                    alongMm = 1_600.0,
                    lateralOffsetMm = 0.0,
                    verticalOffsetMm = 400.0,
                    verticalDirection = InstallationVerticalDirection.DOWN,
                    startReference = InstallationMeasurementReference.PIPE_OUTER_EDGE,
                    endReference = InstallationMeasurementReference.PIPE_OUTER_EDGE,
                ),
            ),
        )
        assertEquals(Vec3(1_600.0, 0.0, -400.0), request.measuredTarget)

        val crossed = installerRouteRequest(
            job(
                InstallationTaskType.FLAT_OFFSET,
                InstallationJobInput(
                    nominalDiameter = 50,
                    lateralOffsetMm = 0.0,
                    verticalOffsetMm = 400.0,
                    startReference = InstallationMeasurementReference.PIPE_INNER_EDGE,
                    endReference = InstallationMeasurementReference.PIPE_OUTER_EDGE,
                ),
            ),
        )
        assertEquals(460.3, crossed.measuredTarget.z, 1e-9)
        assertEquals(0.0, crossed.measuredTarget.y, 0.0)
    }

    @Test fun `the placement of an offset reaches the solver`() {
        fun placed(placement: InstallationOffsetPlacement) = installerRouteRequest(
            job(
                InstallationTaskType.ROLLING_OFFSET,
                InstallationJobInput(offsetPlacement = placement, offsetLeadMm = 20.0, minimumStraightMm = 50.0),
            ),
        ).offsetPlacement
        assertEquals(OffsetPlacement3D.Share(0.0), placed(InstallationOffsetPlacement.START))
        assertEquals(OffsetPlacement3D.CENTERED, placed(InstallationOffsetPlacement.CENTER))
        assertEquals(OffsetPlacement3D.Share(1.0), placed(InstallationOffsetPlacement.END))
        // An exact cut shorter than the shortest allowed straight is lifted to it.
        assertEquals(OffsetPlacement3D.LeadCut(50.0), placed(InstallationOffsetPlacement.EXACT))
    }

    @Test fun `tee obstacle and reducer select manual fabrication templates`() {
        listOf(
            InstallationTaskType.OBSTACLE_BYPASS,
            InstallationTaskType.TEE_BRANCH,
            InstallationTaskType.REDUCER,
        ).forEach { task ->
            val request = installerRouteRequest(job(task, InstallationJobInput(nominalDiameter = 50, verticalOffsetMm = 300.0)))
            assertEquals(task.name, InstallerCalculationPath.MANUAL_TEMPLATE, request.calculationPath)
            assertNotNull(task.name, request.manualTemplate)
            assertTrue(task.name, request.manualTemplate!!.steps.isNotEmpty())
        }
        assertTrue(
            installerRouteRequest(job(InstallationTaskType.TEE_BRANCH)).manualTemplate!!.steps.any { it is ChainStep3D.Tee },
        )
        assertTrue(
            installerRouteRequest(job(InstallationTaskType.REDUCER)).manualTemplate!!.steps.any { it is ChainStep3D.Reducer },
        )
    }

    @Test fun `all automatic fitter tasks produce fabricable routes`() {
        val engine = DefaultFabrication3DEngine()
        val profile = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0),
        ).toAssemblyProfile3D()
        val scenarios = listOf(
            InstallationTaskType.STRAIGHT_INSERT to InstallationJobInput(alongMm = 2_000.0, lateralOffsetMm = 0.0),
            InstallationTaskType.FLAT_OFFSET to InstallationJobInput(alongMm = 4_000.0, lateralOffsetMm = 500.0),
            InstallationTaskType.ROLLING_OFFSET to InstallationJobInput(alongMm = 4_000.0, lateralOffsetMm = 500.0, verticalOffsetMm = 300.0),
            InstallationTaskType.TURN_WITH_OFFSET to InstallationJobInput(
                alongMm = 2_500.0,
                lateralOffsetMm = 900.0,
                verticalOffsetMm = 450.0,
                endDirection = InstallationEndDirection.RIGHT,
            ),
            InstallationTaskType.U_TURN to InstallationJobInput(
                alongMm = 2_000.0,
                lateralOffsetMm = 1_200.0,
                verticalOffsetMm = 300.0,
                endDirection = InstallationEndDirection.BACK,
            ),
            InstallationTaskType.FLANGED_OFFSET to InstallationJobInput(alongMm = 4_000.0, lateralOffsetMm = 500.0),
        )
        listOf(InstallationMeasurementReference.FLANGE_FACE, InstallationMeasurementReference.PIPE_FACE).forEach { reference ->
            scenarios.forEach { (task, input) ->
                val request = installerRouteRequest(job(task, input.copy(startReference = reference, endReference = reference)))
                val solution = engine.router.solve(request.toRouteRequest(profile)).getOrNull()
                assertNotNull("${task.name} $reference", solution)
                assertTrue(task.name, solution!!.targetErrorMm < 1e-4)
            }
        }
    }

    @Test fun `an offset between pipe ends is a flangeless spool short by one weld gap at each end`() {
        val input = InstallationJobInput(
            nominalDiameter = 50,
            alongMm = 1_600.0,
            lateralOffsetMm = 500.0,
            weldGapMm = 2.0,
            startReference = InstallationMeasurementReference.PIPE_FACE,
            endReference = InstallationMeasurementReference.PIPE_FACE,
            endDirection = InstallationEndDirection.RIGHT,
        )
        val request = installerRouteRequest(job(InstallationTaskType.FLAT_OFFSET, input))
        assertEquals(ChainTerminal3D.OPEN_END, request.startTerminal)
        assertEquals(ChainTerminal3D.OPEN_END, request.endTerminal)
        assertEquals(Vec3.UNIT_X, request.endDirection)
        assertEquals(Vec3(2.0, 0.0, 0.0), request.start)
        assertEquals(Vec3(1_598.0, -500.0, 0.0), request.target)
        assertEquals(Vec3(1_600.0, -500.0, 0.0), request.measuredTarget)
        assertEquals(2.0, request.siteWeldGapMm, 0.0)

        val profile = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0, weldGapMm = 2.0),
        ).toAssemblyProfile3D()
        val solution = DefaultFabrication3DEngine().router.solve(request.toRouteRequest(profile)).getOrNull()!!
        assertTrue(solution.assembly.parts.none { it.definition.kind == FabricationPartKind.FLANGE })
        val takeout = profile.elbow.centerlineRadiusMm * tan(Math.toRadians(22.5))
        assertEquals(500.0 / sin(Math.toRadians(45.0)) - 2 * takeout - 4.0, solution.pipeCuts[1].lengthMm, 1e-6)
        // Site gap, pipe, gap, elbow, sloped leg, elbow, gap, pipe, site gap: exactly the measured 1600.
        val straight = (1_600.0 - 2.0 - 2.0 - 500.0 - 2 * takeout - 4.0) / 2.0
        assertEquals(straight, solution.pipeCuts[0].lengthMm, 1e-6)
    }

    @Test fun `flange to flange stays flanged whatever the references say`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.FLANGED_OFFSET,
                InstallationJobInput(
                    startReference = InstallationMeasurementReference.PIPE_FACE,
                    endReference = InstallationMeasurementReference.PIPE_FACE,
                ),
            ),
        )
        assertEquals(ChainTerminal3D.FLANGE, request.startTerminal)
        assertEquals(ChainTerminal3D.FLANGE, request.endTerminal)
        assertEquals(Vec3.ZERO, request.start)
        assertEquals(0.0, request.siteWeldGapMm, 0.0)
    }

    @Test fun `prepared templates keep their flanges`() {
        val request = installerRouteRequest(
            job(
                InstallationTaskType.OBSTACLE_BYPASS,
                InstallationJobInput(startReference = InstallationMeasurementReference.PIPE_FACE),
            ),
        )
        assertEquals(ChainTerminal3D.FLANGE, request.startTerminal)
    }

    private fun InstallerRouteRequest.toRouteRequest(profile: com.planruler.fabrication3d.AssemblyProfile3D) = RouteRequest3D(
        profile = profile,
        start = RouteTerminal3D(start),
        target = RouteTerminal3D(target, endDirection),
        preferredElbowAngleDeg = preferredAngleDeg,
        minimumStraightMm = minimumStraightMm,
        startTerminal = startTerminal,
        endTerminal = endTerminal,
        offsetPlacement = offsetPlacement,
    )

    /**
     * The hero, cut list, stock plan and passport come from the 2D spool, the route card and the
     * set-out from the 3D solver. A fitter reads both, so they must name one set of cuts.
     */
    @Test fun `flange to flange cuts agree with the 3D route for every placement and angle`() {
        val engine = DefaultFabrication3DEngine()
        val shapes = listOf(
            InstallationJobInput(nominalDiameter = 80, alongMm = 3_000.0, lateralOffsetMm = 500.0),
            // Rolling, to the left and down: still a 500 mm offset between the axes.
            InstallationJobInput(
                nominalDiameter = 80,
                alongMm = 3_000.0,
                lateralOffsetMm = 400.0,
                verticalOffsetMm = 300.0,
                lateralDirection = InstallationLateralDirection.LEFT,
                verticalDirection = InstallationVerticalDirection.DOWN,
            ),
        )
        shapes.forEach { shape ->
            OFFSET_ELBOW_ANGLES.forEach { angle ->
                InstallationOffsetPlacement.entries.forEach { placement ->
                    val label = "rise ${shape.verticalOffsetMm}, $angle°, $placement"
                    val job = job(
                        InstallationTaskType.FLANGED_OFFSET,
                        shape.copy(angleDeg = angle, offsetPlacement = placement, offsetLeadMm = 420.0),
                    )
                    val spool = workshopAssembly(job)
                    val request = installerRouteRequest(job)
                    val profile = spool.toAssemblyProfile3D()
                    val route = engine.router.solve(request.toRouteRequest(profile)).getOrNull()
                    assertNotNull(label, route)
                    val routed = route!!.pipeCuts.map { it.lengthMm }
                    val listed = spool.cuts.map { it.lengthMm }
                    assertEquals(label, 3, routed.size)
                    listed.zip(routed).forEach { (cut, solved) -> assertEquals(label, solved, cut, AGREEMENT_MM) }
                    assertEquals(label, route.totalPipeCutMm, spool.totalNetPipeLengthMm, AGREEMENT_MM)
                    spool.stockBars.flatMap { it.cutsMm }.sorted().zip(routed.sorted()).forEach { (bar, solved) ->
                        assertEquals(label, solved, bar, AGREEMENT_MM)
                    }
                    assertEquals(label, 3, spool.cuts.size)
                    assertEquals(label, 6, spool.weldCount)

                    when (placement) {
                        InstallationOffsetPlacement.START -> assertEquals(label, 50.0, listed[0], 1e-9)
                        InstallationOffsetPlacement.CENTER -> assertEquals(label, listed[2], listed[0], 1e-9)
                        InstallationOffsetPlacement.END -> assertEquals(label, 50.0, listed[2], 1e-9)
                        InstallationOffsetPlacement.EXACT -> assertEquals(label, 420.0, listed[0], 1e-9)
                    }

                    // The passport and the set-out panel describe the same bends.
                    val setOut = requireNotNull(offsetSetOut(request, profile, angle)) { label }
                    assertEquals(label, setOut.travelMm, spool.diagonalCenterTravelMm, AGREEMENT_MM)
                    assertEquals(label, setOut.advanceMm, spool.horizontalCenterAdvanceMm, AGREEMENT_MM)
                    assertEquals(label, setOut.takeoutMm, spool.elbowTakeoutMm, AGREEMENT_MM)
                    val firstBend = requireNotNull(spool.elements.single { it.code == "E1" }.control).x
                    val secondBend = requireNotNull(spool.elements.single { it.code == "E2" }.control).x
                    assertEquals(label, firstBend, setOut.firstBendMm(routed[0]), AGREEMENT_MM)
                    assertEquals(label, secondBend, setOut.secondBendMm(routed[0]), AGREEMENT_MM)
                }
            }
        }
    }

    /** Centred is the default, so every flange-to-flange job that laid out before keeps its numbers. */
    @Test fun `a centred flange to flange spool keeps the numbers it had before placements`() {
        val default = workshopAssembly(job(InstallationTaskType.FLANGED_OFFSET))
        assertEquals(469.520, default.inletPipeCutMm, 0.001)
        assertEquals(640.146, default.diagonalPipeCutMm, 0.001)
        assertEquals(469.520, default.outletPipeCutMm, 0.001)

        listOf(
            InstallationJobInput(),
            InstallationJobInput(
                nominalDiameter = 100,
                pressureClass = 25,
                alongMm = 2_600.0,
                lateralOffsetMm = 800.0,
                angleDeg = 60.0,
                quantity = 3,
                targetOffsetMm = 800.0,
                overallFaceToFaceMm = 2_600.0,
            ),
            InstallationJobInput(
                nominalDiameter = 25,
                alongMm = 1_200.0,
                lateralOffsetMm = 300.0,
                verticalOffsetMm = 400.0,
                angleDeg = 30.0,
                targetOffsetMm = 500.0,
                overallFaceToFaceMm = 1_200.0,
            ),
        ).forEach { input ->
            // What the workshop calculated before an offset could be placed: the job's own sizes, halved.
            val before = calculateFlangedOffsetAssembly(
                FlangedOffsetAssemblyInput(
                    dn = input.nominalDiameter,
                    pn = input.pressureClass,
                    targetOffsetMm = input.targetOffsetMm,
                    overallFaceToFaceMm = input.overallFaceToFaceMm,
                    angleDeg = input.angleDeg,
                    weldGapMm = input.weldGapMm,
                    quantity = input.quantity,
                    stockLengthMm = input.stockLengthMm.toDouble(),
                    sawKerfMm = input.sawKerfMm,
                ),
            )
            val now = workshopAssembly(job(InstallationTaskType.FLANGED_OFFSET, input))
            val label = "DN ${input.nominalDiameter}"
            assertEquals(label, before.cuts.map { it.code to it.quantity }, now.cuts.map { it.code to it.quantity })
            before.cuts.zip(now.cuts).forEach { (old, new) -> assertEquals(label, old.lengthMm, new.lengthMm, 1e-9) }
            assertEquals(label, before.stockBars.map { it.cutsMm.size }, now.stockBars.map { it.cutsMm.size })
            before.stockBars.zip(now.stockBars).forEach { (old, new) ->
                old.cutsMm.zip(new.cutsMm).forEach { (a, b) -> assertEquals(label, a, b, 1e-9) }
                assertEquals(label, old.offcutMm, new.offcutMm, 1e-9)
            }
            assertEquals(label, before.totalNetPipeLengthMm, now.totalNetPipeLengthMm, 1e-9)
            assertEquals(label, before.totalPipeMassKg, now.totalPipeMassKg, 1e-9)
            assertEquals(label, before.weldCount, now.weldCount)
            assertEquals(label, before.flangeBoltCount, now.flangeBoltCount)
            assertEquals(label, before.diagonalCenterTravelMm, now.diagonalCenterTravelMm, 1e-9)
            assertEquals(label, before.horizontalCenterAdvanceMm, now.horizontalCenterAdvanceMm, 1e-9)
            assertEquals(label, before.endFace.x, now.endFace.x, 1e-9)
            assertEquals(label, before.endFace.y, now.endFace.y, 1e-9)
        }
    }

    @Test fun `any other task still borrows the catalog parts of a spool that always lays out`() {
        // Far too small for a real spool, and placed: neither matters to a borrowed profile.
        val input = InstallationJobInput(
            alongMm = 900.0,
            lateralOffsetMm = 120.0,
            targetOffsetMm = 120.0,
            overallFaceToFaceMm = 900.0,
            offsetPlacement = InstallationOffsetPlacement.START,
        )
        val borrowed = calculateFlangedOffsetAssembly(FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0))
        InstallationTaskType.entries.filter { it != InstallationTaskType.FLANGED_OFFSET }.forEach { task ->
            assertEquals(task.name, borrowed, workshopAssembly(job(task, input)))
        }
    }

    @Test fun `a long run, a large offset or a short bar never takes the borrowed spool down`() {
        // A 13 m run used to borrow a 13 m spool: its tails no longer fitted the 6 m bar, the
        // calculation failed and the flat offset lost its 3D card.
        val borrowed = calculateFlangedOffsetAssembly(FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0))
        val long = InstallationJobInput(
            alongMm = 13_000.0,
            lateralOffsetMm = 2_400.0,
            targetOffsetMm = 2_400.0,
            overallFaceToFaceMm = 13_000.0,
            stockLengthMm = 1_000,
        )
        listOf(InstallationTaskType.FLAT_OFFSET, InstallationTaskType.ROLLING_OFFSET, InstallationTaskType.STRAIGHT_INSERT)
            .forEach { task -> assertEquals(task.name, borrowed, workshopAssembly(job(task, long))) }
    }

    /** Whatever the 3D route refuses, the cut list refuses too, and the other way round. */
    @Test fun `a flange to flange spool is refused exactly where the 3D route refuses it`() {
        val engine = DefaultFabrication3DEngine()
        val profile = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0),
        ).toAssemblyProfile3D()
        fun agree(label: String, input: InstallationJobInput) {
            val job = job(InstallationTaskType.FLANGED_OFFSET, input)
            val listed = runCatching { workshopAssembly(job) }
            val routed = engine.router.solve(installerRouteRequest(job).toRouteRequest(profile)).getOrNull()
            assertEquals(label, routed != null, listed.isSuccess)
            listed.exceptionOrNull()?.let { assertTrue(label, it is CalculationException) }
            if (routed != null) {
                listed.getOrThrow().cuts.zip(routed.pipeCuts).forEach { (cut, solved) ->
                    assertEquals(label, solved.lengthMm, cut.lengthMm, AGREEMENT_MM)
                }
            }
        }
        OFFSET_ELBOW_ANGLES.forEach { angle ->
            InstallationOffsetPlacement.entries.forEach { placement ->
                val base = InstallationJobInput(
                    alongMm = 2_400.0,
                    lateralOffsetMm = 500.0,
                    angleDeg = angle,
                    offsetPlacement = placement,
                    offsetLeadMm = 300.0,
                )
                val request = installerRouteRequest(job(InstallationTaskType.FLANGED_OFFSET, base))
                val setOut = requireNotNull(offsetSetOut(request, profile, angle))
                listOf(-0.5, 0.5).forEach { step ->
                    agree("$angle° $placement run $step", base.copy(alongMm = setOut.minimumAlongMm + step))
                    agree("$angle° $placement offset $step", base.copy(lateralOffsetMm = setOut.minimumOffsetMm + step))
                    if (placement == InstallationOffsetPlacement.EXACT) {
                        agree("$angle° lead $step", base.copy(offsetLeadMm = setOut.maximumLeadMm + step))
                    }
                }
            }
        }
    }

    @Test fun `prepared fitter templates replay through the real chain engine`() {
        val engine = DefaultFabrication3DEngine()
        val profile = calculateFlangedOffsetAssembly(
            FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0),
        ).toAssemblyProfile3D()
        listOf(
            InstallationTaskType.OBSTACLE_BYPASS,
            InstallationTaskType.TEE_BRANCH,
            InstallationTaskType.REDUCER,
        ).forEach { task ->
            val request = installerRouteRequest(
                job(task, InstallationJobInput(nominalDiameter = 50, alongMm = 4_000.0, verticalOffsetMm = 300.0)),
            )
            val editor = engine.chains.fromPlan(profile, requireNotNull(request.manualTemplate)).getOrNull()
            assertNotNull(task.name, editor)
        }
    }

    private companion object {
        /** Two numbers for one cut may differ by rounding only; the screen shows tenths of a millimetre. */
        const val AGREEMENT_MM = 1e-6
    }
}

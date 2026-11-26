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
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.tan

class InstallerWorkflowTest {
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
}

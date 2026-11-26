package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteSolution3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.engine.DefaultFabrication3DEngine
import com.planruler.fabrication3d.getOrNull
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
import com.planruler.model.InstallationTaskType
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OffsetRouteGuideTest {
    private val engine = DefaultFabrication3DEngine()
    private val profile: AssemblyProfile3D = calculateFlangedOffsetAssembly(
        FlangedOffsetAssemblyInput(50, 16, 500.0, 4_000.0, weldGapMm = 2.0),
    ).toAssemblyProfile3D()

    private fun request(
        task: InstallationTaskType = InstallationTaskType.FLAT_OFFSET,
        reference: InstallationMeasurementReference = InstallationMeasurementReference.PIPE_FACE,
        input: InstallationJobInput,
    ) = installerRouteRequest(
        InstallationJob(
            id = InstallationJobId("job"),
            name = "Offset",
            taskType = task,
            input = input.copy(startReference = reference, endReference = reference, nominalDiameter = 50, weldGapMm = 2.0),
            createdAtEpochMs = 1L,
            modifiedAtEpochMs = 1L,
        ),
    )

    private fun solve(request: InstallerRouteRequest): RouteSolution3D? = engine.router.solve(
        RouteRequest3D(
            profile = profile,
            start = RouteTerminal3D(request.start),
            target = RouteTerminal3D(request.target, request.endDirection),
            preferredElbowAngleDeg = request.preferredAngleDeg,
            minimumStraightMm = request.minimumStraightMm,
            startTerminal = request.startTerminal,
            endTerminal = request.endTerminal,
            offsetPlacement = request.offsetPlacement,
        ),
    ).getOrNull()

    @Test fun `bend centres set out from the start point close on the measured run`() {
        listOf(InstallationMeasurementReference.PIPE_FACE, InstallationMeasurementReference.FLANGE_FACE).forEach { reference ->
            val request = request(reference = reference, input = InstallationJobInput(alongMm = 1_600.0, lateralOffsetMm = 500.0))
            val solution = requireNotNull(solve(request)) { reference.name }
            val setOut = offsetSetOut(request, profile, 45.0)!!
            val cuts = solution.pipeCuts.map { it.lengthMm }
            val end = setOut.secondBendMm(cuts[0]) + setOut.takeoutMm + profile.weldGapMm + cuts[2]
            val endAllowance = if (reference == InstallationMeasurementReference.FLANGE_FACE) {
                profile.flange.faceToWeldMm + profile.weldGapMm
            } else {
                2.0
            }
            assertEquals(reference.name, 1_600.0, end + endAllowance, 1e-6)
            assertEquals(500.0, setOut.advanceMm, 1e-9)
            assertEquals(707.1068, setOut.travelMm, 1e-4)
            assertNull(setOut.rollDeg)
        }
    }

    @Test fun `the shortest run and the smallest offset are the real limits of the solver`() {
        listOf(InstallationMeasurementReference.PIPE_FACE, InstallationMeasurementReference.FLANGE_FACE).forEach { reference ->
            val base = request(reference = reference, input = InstallationJobInput(alongMm = 1_600.0, lateralOffsetMm = 500.0))
            val setOut = offsetSetOut(base, profile, 45.0)!!

            fun along(mm: Double) = request(reference = reference, input = InstallationJobInput(alongMm = mm, lateralOffsetMm = 500.0))
            assertNotNull(reference.name, solve(along(setOut.minimumAlongMm + 0.5)))
            assertNull(reference.name, solve(along(setOut.minimumAlongMm - 0.5)))
            assertFalse(offsetSetOut(along(setOut.minimumAlongMm - 0.5), profile, 45.0)!!.fits)

            fun offset(mm: Double) = request(reference = reference, input = InstallationJobInput(alongMm = 1_600.0, lateralOffsetMm = mm))
            assertNotNull(reference.name, solve(offset(setOut.minimumOffsetMm + 0.5)))
            assertNull(reference.name, solve(offset(setOut.minimumOffsetMm - 0.5)))
            assertFalse(offsetSetOut(offset(setOut.minimumOffsetMm - 0.5), profile, 45.0)!!.offsetFits)
        }
    }

    @Test fun `the longest cut before the offset is the real limit too`() {
        val setOut = offsetSetOut(
            request(input = InstallationJobInput(alongMm = 1_600.0, lateralOffsetMm = 500.0)),
            profile,
            45.0,
        )!!
        fun exact(lead: Double) = request(
            input = InstallationJobInput(
                alongMm = 1_600.0,
                lateralOffsetMm = 500.0,
                offsetPlacement = InstallationOffsetPlacement.EXACT,
                offsetLeadMm = lead,
            ),
        )
        val fits = solve(exact(setOut.maximumLeadMm - 0.5))
        assertNotNull(fits)
        assertEquals(setOut.maximumLeadMm - 0.5, fits!!.pipeCuts.first().lengthMm, 1e-6)
        assertNull(solve(exact(setOut.maximumLeadMm + 0.5)))
    }

    @Test fun `a rolling offset reports its true offset and the roll of its plane`() {
        val rolling = request(
            task = InstallationTaskType.ROLLING_OFFSET,
            input = InstallationJobInput(alongMm = 2_000.0, lateralOffsetMm = 300.0, verticalOffsetMm = 400.0),
        )
        val setOut = offsetSetOut(rolling, profile, 45.0)!!
        assertEquals(500.0, setOut.offsetMm, 1e-9)
        assertEquals(36.8699, setOut.rollDeg!!, 1e-4)
        assertNull(offsetSetOut(request(task = InstallationTaskType.U_TURN, input = InstallationJobInput()), profile, 45.0))
    }

    @Test fun `only angles that fit are offered, and every one of them solves`() {
        val tight = request(input = InstallationJobInput(alongMm = 700.0, lateralOffsetMm = 400.0))
        val angles = fittingOffsetAngles(tight, profile)
        assertTrue(angles.isNotEmpty())
        assertFalse(15.0 in angles)
        OFFSET_ELBOW_ANGLES.forEach { angle ->
            val solved = solve(tight.copy(preferredAngleDeg = angle)) != null
            assertEquals("$angle°", angle in angles, solved)
        }
    }
}

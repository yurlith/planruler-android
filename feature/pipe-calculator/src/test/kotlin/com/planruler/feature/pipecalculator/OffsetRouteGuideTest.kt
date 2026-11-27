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
import com.planruler.pipecalculator.CalculationException
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.tan

class OffsetRouteGuideTest {
    @Test fun `suggested angles leave enough pipe after an exact lead`() {
        val request = request(input = InstallationJobInput(
            alongMm = 1_600.0, lateralOffsetMm = 500.0,
            offsetPlacement = InstallationOffsetPlacement.EXACT, offsetLeadMm = 1_300.0,
        ))
        val fitting = fittingOffsetAngles(request, profile)
        assertFalse(fitting.contains(45.0))
        assertTrue(fitting.contains(90.0))
        fitting.forEach { angle ->
            val setOut = requireNotNull(offsetSetOut(request, profile, angle))
            assertTrue(setOut.fits && setOut.leadFits(1_300.0))
        }
    }

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

    private fun flanged(input: InstallationJobInput) = InstallationJob(
        id = InstallationJobId("spool"),
        name = "Spool",
        taskType = InstallationTaskType.FLANGED_OFFSET,
        input = input.copy(nominalDiameter = 50, weldGapMm = 2.0),
        createdAtEpochMs = 1L,
        modifiedAtEpochMs = 1L,
    )

    private fun refused(job: InstallationJob) =
        runCatching { workshopAssembly(job) }.exceptionOrNull() is CalculationException

    @Test fun `a flange to flange spool too short for its offset gets the advice the other offsets get`() {
        // At 45° the spool needs 761 mm between the flange faces; steeper elbows need less.
        val short = flanged(InstallationJobInput(alongMm = 700.0, lateralOffsetMm = 500.0))
        assertTrue(refused(short))
        val advice = requireNotNull(offsetFitAdvice(short))
        val setOut = requireNotNull(offsetSetOut(advice.request, advice.profile, 45.0))
        assertFalse(setOut.fits)
        assertFalse(setOut.alongFits)
        assertTrue(setOut.offsetFits)
        assertEquals(760.96, setOut.minimumAlongMm, 0.01)
        assertEquals(profile, advice.profile)

        // Every angle offered for the one-tap switch really lays out, in the cut list and in 3D.
        val angles = fittingOffsetAngles(advice.request, advice.profile)
        assertEquals(listOf(60.0, 90.0), angles)
        OFFSET_ELBOW_ANGLES.forEach { angle ->
            val retried = short.copy(input = short.input.copy(angleDeg = angle))
            assertEquals("$angle°", angle !in angles, refused(retried))
            assertEquals("$angle°", angle in angles, solve(installerRouteRequest(retried)) != null)
            assertEquals("$angle°", angle !in angles, offsetFitAdvice(retried) != null)
        }
    }

    @Test fun `a flange to flange offset too small for its elbows is offered the shallow ones`() {
        val small = flanged(InstallationJobInput(alongMm = 2_000.0, lateralOffsetMm = 60.0))
        assertTrue(refused(small))
        val advice = requireNotNull(offsetFitAdvice(small))
        val setOut = requireNotNull(offsetSetOut(advice.request, advice.profile, 45.0))
        assertTrue(setOut.alongFits)
        assertFalse(setOut.offsetFits)
        // The sloped cut must be a real pipe too: 2 take-outs, 2 gaps and the 50 mm minimum.
        val takeout = 76.0 * tan(Math.toRadians(22.5))
        assertEquals((2 * takeout + 4.0 + 50.0) * sin(Math.toRadians(45.0)), setOut.minimumOffsetMm, 1e-9)
        assertEquals(listOf(15.0, 22.5, 30.0), fittingOffsetAngles(advice.request, advice.profile))
    }

    @Test fun `a flange to flange spool placed past its room is refused with the longest cut before the offset`() {
        val placed = flanged(
            InstallationJobInput(
                alongMm = 1_600.0,
                lateralOffsetMm = 500.0,
                offsetPlacement = InstallationOffsetPlacement.EXACT,
                offsetLeadMm = 1_000.0,
            ),
        )
        assertTrue(refused(placed))
        assertNull(solve(installerRouteRequest(placed)))
        val advice = requireNotNull(offsetFitAdvice(placed))
        val setOut = requireNotNull(offsetSetOut(advice.request, advice.profile, 45.0))
        assertTrue(setOut.fits)
        // 939.04 mm of straight in all, 50 of which must stay after the offset.
        assertEquals(889.04, setOut.maximumLeadMm, 0.01)
        assertFalse(setOut.leadFits(1_000.0))

        val fitting = placed.copy(input = placed.input.copy(offsetLeadMm = 889.0))
        assertFalse(refused(fitting))
        assertNull(offsetFitAdvice(fitting))
        assertEquals(889.0, workshopAssembly(fitting).inletPipeCutMm, 1e-9)
    }

    @Test fun `a refusal that has nothing to do with the fit is left to the error message`() {
        // Fits easily, but each tail is longer than a 6 m stock bar.
        val long = flanged(InstallationJobInput(alongMm = 14_000.0, lateralOffsetMm = 500.0))
        assertTrue(refused(long))
        assertNull(offsetFitAdvice(long))
        // Turns and returns are not offsets, so there is no offset advice for them at all.
        val turn = flanged(InstallationJobInput(alongMm = 300.0)).copy(taskType = InstallationTaskType.U_TURN)
        assertNull(offsetFitAdvice(turn))
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

package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.Bounds3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.ObstacleBox3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.RouteFailureCode3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.RouteTopology3D
import com.planruler.fabrication3d.Vec3
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSolverTest {

    @Test
    fun `solver closes a true YZ spatial offset with parallel flange axes`() {
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(1_600.0, 500.0, 300.0)),
                preferredElbowAngleDeg = 45.0,
            ),
        ).unwrap()

        assertEquals(RouteTopology3D.ROLLING_OFFSET, solution.topology)
        assertEquals(2, solution.elbowCount)
        assertEquals(7, solution.assembly.parts.size)
        assertEquals(3, solution.pipeCuts.size)
        assertEquals(listOf(45.0, -45.0), solution.elbowAnglesDeg)
        assertTrue(solution.targetErrorMm < 1e-6)
        assertTrue(solution.terminalDirectionErrorDeg < 1e-6)
        assertValid(solution.assembly)
        assertVec(
            Vec3(1_600.0, 500.0, 300.0),
            solution.assembly.worldPort(PortReference3D("F2", "face")).frame.position,
            2e-6,
        )
    }

    @Test
    fun `solver closes all installation diameters in a spatial plane`() {
        PIPE_INSTALLATION_SERIES.forEach { pipe ->
            val solution = engine.router.solve(
                RouteRequest3D(
                    profile = installationProfile(pipe.dn),
                    target = RouteTerminal3D(Vec3(8_000.0, 1_500.0, 700.0)),
                    preferredElbowAngleDeg = 45.0,
                    minimumStraightMm = 20.0,
                ),
            ).unwrap()

            assertTrue("DN ${pipe.dn}: ${solution.targetErrorMm}", solution.targetErrorMm < 2e-6)
            assertValid(solution.assembly)
        }
    }

    @Test
    fun `solver handles a straight route without elbows`() {
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(1_000.0, 0.0, 0.0)),
            ),
        ).unwrap()

        assertEquals(RouteTopology3D.STRAIGHT, solution.topology)
        assertEquals(0, solution.elbowCount)
        assertEquals(3, solution.assembly.parts.size)
        assertEquals(1, solution.pipeCuts.size)
        assertTrue(solution.targetErrorMm < 1e-7)
    }

    @Test
    fun `solver reports an impossible envelope without a partial model`() {
        val error = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(300),
                target = RouteTerminal3D(Vec3(400.0, 100.0, 50.0)),
                minimumStraightMm = 100.0,
            ),
        ).failure()

        assertTrue(error is Fabrication3DError.RouteNotFound)
        assertEquals(
            RouteFailureCode3D.NO_FABRICABLE_ROUTE,
            (error as Fabrication3DError.RouteNotFound).code,
        )
    }

    @Test
    fun `solver reports a target behind the start face`() {
        val error = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(-1_000.0, 0.0, 0.0)),
            ),
        ).failure()

        assertEquals(
            RouteFailureCode3D.TARGET_BEHIND_START,
            (error as Fabrication3DError.RouteNotFound).code,
        )
    }

    @Test
    fun `solver closes in an arbitrary translated coordinate frame`() {
        val start = Vec3(120.0, -80.0, 250.0)
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                start = RouteTerminal3D(start, Vec3.UNIT_Y, Vec3.UNIT_Z),
                target = RouteTerminal3D(start + Vec3(0.0, 1_700.0, 420.0), Vec3.UNIT_Y, Vec3.UNIT_Z),
                preferredElbowAngleDeg = 60.0,
            ),
        ).unwrap()

        assertTrue(solution.targetErrorMm < 2e-6)
        assertVec(
            start + Vec3(0.0, 1_700.0, 420.0),
            solution.assembly.worldPort(PortReference3D("F2", "face")).frame.position,
            2e-6,
        )
    }

    @Test
    fun `solver turns onto a perpendicular axis in the same plane`() {
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(2_000.0, 1_500.0, 0.0), Vec3.UNIT_Y),
            ),
        ).unwrap()

        assertEquals(RouteTopology3D.TURN, solution.topology)
        assertEquals(1, solution.elbowCount)
        assertEquals(2, solution.pipeCuts.size)
        assertEquals(90.0, solution.elbowAnglesDeg.single(), 1e-9)
        assertTrue(solution.targetErrorMm < 2e-6)
        assertTrue(solution.terminalDirectionErrorDeg < 1e-5)
        assertValid(solution.assembly)
    }

    @Test
    fun `solver turns onto a perpendicular axis that also sits off the turn plane`() {
        val target = Vec3(2_000.0, 1_500.0, 600.0)
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(target, Vec3.UNIT_Y),
                preferredElbowAngleDeg = 45.0,
            ),
        ).unwrap()

        assertEquals(RouteTopology3D.TURN_WITH_OFFSET, solution.topology)
        assertEquals(3, solution.elbowCount)
        assertEquals(4, solution.pipeCuts.size)
        assertTrue("closure ${solution.targetErrorMm}", solution.targetErrorMm < 2e-6)
        assertTrue(solution.terminalDirectionErrorDeg < 1e-5)
        assertValid(solution.assembly)
        assertVec(target, solution.assembly.worldPort(PortReference3D("F2", "face")).frame.position, 2e-6)
    }

    @Test
    fun `solver closes an oblique target direction`() {
        val target = Vec3(2_400.0, 900.0, 450.0)
        val direction = Vec3(1.0, 1.0, 0.5).normalized()
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(target, direction),
                minimumStraightMm = 30.0,
            ),
        ).unwrap()

        assertTrue(solution.targetErrorMm < 2e-6)
        assertTrue(solution.terminalDirectionErrorDeg < 1e-5)
        assertValid(solution.assembly)
        assertVec(target, solution.assembly.worldPort(PortReference3D("F2", "face")).frame.position, 2e-6)
    }

    @Test
    fun `a solved route can be handed to the manual editor and tuned`() {
        val profile = installationProfile(50)
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = profile,
                target = RouteTerminal3D(Vec3(1_600.0, 500.0, 300.0)),
                preferredElbowAngleDeg = 45.0,
            ),
        ).unwrap()

        val editor = engine.chains.fromPlan(profile, solution.plan).unwrap()

        // Only the metadata label differs; the fabricated geometry has to be identical.
        assertEquals(solution.assembly.parts, editor.assembly.parts)
        assertEquals(solution.assembly.connections, editor.assembly.connections)
        val pipeStep = com.planruler.fabrication3d.ChainPath3D(
            listOf(editor.plan.steps.indexOfFirst { it is ChainStep3D.Pipe }),
        )
        val tuned = engine.chains
            .execute(editor, com.planruler.fabrication3d.ChainCommand3D.Replace(pipeStep, ChainStep3D.Pipe(500.0)))
            .unwrap()
        assertValid(tuned.assembly)
    }

    @Test
    fun `a route through an obstacle is rejected`() {
        val error = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(2_000.0, 0.0, 0.0)),
                obstacles = listOf(
                    ObstacleBox3D("beam", Bounds3D(Vec3(900.0, -200.0, -200.0), Vec3(1_100.0, 200.0, 200.0))),
                ),
            ),
        ).failure()

        assertEquals(
            RouteFailureCode3D.OBSTRUCTED,
            (error as Fabrication3DError.RouteNotFound).code,
        )
    }

    @Test
    fun `solver builds a u turn between facing terminals`() {
        val target = Vec3(1_400.0, 900.0, 0.0)
        val solution = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(target, -Vec3.UNIT_X),
            ),
        ).unwrap()

        assertEquals(RouteTopology3D.U_TURN, solution.topology)
        assertEquals(2, solution.elbowCount)
        assertEquals(3, solution.pipeCuts.size)
        assertEquals(listOf(90.0, 90.0), solution.elbowAnglesDeg)
        assertTrue("closure ${solution.targetErrorMm}", solution.targetErrorMm < 2e-6)
        assertTrue(solution.terminalDirectionErrorDeg < 1e-5)
        assertValid(solution.assembly)
        assertVec(target, solution.assembly.worldPort(PortReference3D("F2", "face")).frame.position, 2e-6)
    }

    @Test
    fun `a u turn closes for every installation diameter and out of plane offset`() {
        PIPE_INSTALLATION_SERIES.forEach { pipe ->
            val target = Vec3(2_000.0, 1_200.0, 800.0)
            val solution = engine.router.solve(
                RouteRequest3D(
                    profile = installationProfile(pipe.dn),
                    target = RouteTerminal3D(target, -Vec3.UNIT_X),
                    minimumStraightMm = 30.0,
                ),
            ).unwrap()

            assertEquals("DN ${pipe.dn}", RouteTopology3D.U_TURN, solution.topology)
            assertTrue("DN ${pipe.dn}: ${solution.targetErrorMm}", solution.targetErrorMm < 2e-6)
            assertValid(solution.assembly)
        }
    }

    @Test
    fun `a u turn that would come back onto the supply is refused`() {
        val error = engine.router.solve(
            RouteRequest3D(
                profile = installationProfile(50),
                target = RouteTerminal3D(Vec3(1_500.0, 0.0, 0.0), -Vec3.UNIT_X),
            ),
        ).failure()

        assertEquals(
            RouteFailureCode3D.NO_FABRICABLE_ROUTE,
            (error as Fabrication3DError.RouteNotFound).code,
        )
    }

    @Test
    fun `a u turn needs two elbows`() {
        val profile = installationProfile(50)
        val error = engine.router.solve(
            RouteRequest3D(
                profile = profile.copy(rules = profile.rules.copy(maxElbows = 1)),
                target = RouteTerminal3D(Vec3(1_400.0, 900.0, 0.0), -Vec3.UNIT_X),
            ),
        ).failure()

        assertTrue(error is Fabrication3DError.QuotaExceeded)
    }

    @Test
    fun `solving twice returns an identical route`() {
        val request = RouteRequest3D(
            profile = installationProfile(80),
            target = RouteTerminal3D(Vec3(3_000.0, 800.0, 400.0)),
        )

        val first = engine.router.solve(request).unwrap()
        val second = engine.router.solve(request).unwrap()

        assertEquals(first.pipeCuts, second.pipeCuts)
        assertEquals(first.plan, second.plan)
    }
}

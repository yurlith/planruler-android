package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyProfileOverrides3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.ElbowRadiusMode3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileOverridesTest {
    @Test
    fun `a fitter can dimension their own flange`() {
        val profile = workshopProfile()

        val custom = engine.profiles.apply(
            profile,
            AssemblyProfileOverrides3D(
                flangeOutsideDiameterMm = 180.0,
                flangeFaceToWeldMm = 70.0,
                flangeBoltCircleDiameterMm = 145.0,
                flangeBoltHoleCount = 8,
            ),
        ).unwrap()

        assertEquals(180.0, custom.flange.outsideDiameterMm, 1e-9)
        assertEquals(70.0, custom.flange.faceToWeldMm, 1e-9)
        assertEquals(145.0, custom.flange.boltCircleDiameterMm, 1e-9)
        assertEquals(8, custom.flange.boltHoleCount)

        // The change has to reach the fabricated part, not just the profile.
        val editor = engine.chains.create(custom).unwrap()
        val geometry = editor.assembly.part("F1").definition.geometry as WeldNeckFlangeGeometry3D
        assertEquals(180.0, geometry.outsideDiameterMm, 1e-9)
        assertEquals(8, geometry.boltHoleCount)
        assertValid(editor.assembly)
    }

    @Test
    fun `a bolt circle outside the disc is refused`() {
        val error = engine.profiles.apply(
            workshopProfile(),
            AssemblyProfileOverrides3D(flangeBoltCircleDiameterMm = 5_000.0),
        ).failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            "flangeBoltCircleDiameterMm",
            (error as Fabrication3DError.InvalidParameter).parameter,
        )
    }

    @Test
    fun `bolt holes that would overlap each other are refused`() {
        val error = engine.profiles.apply(
            workshopProfile(),
            AssemblyProfileOverrides3D(flangeBoltHoleCount = 90),
        ).failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            "flangeBoltHoleCount",
            (error as Fabrication3DError.InvalidParameter).parameter,
        )
    }

    @Test
    fun `a flange thicker than its own hub is refused`() {
        val error = engine.profiles.apply(
            workshopProfile(),
            AssemblyProfileOverrides3D(flangeThicknessMm = 500.0),
        ).failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            "flangeThicknessMm",
            (error as Fabrication3DError.InvalidParameter).parameter,
        )
    }

    private val profile = installationProfile(50)

    @Test
    fun `an empty override set leaves the catalog profile untouched`() {
        val applied = engine.profiles.apply(profile, AssemblyProfileOverrides3D.NONE).unwrap()

        assertEquals(profile, applied)
    }

    @Test
    fun `the elbow radius can be switched between the standard families`() {
        val short = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(elbowRadiusMode = ElbowRadiusMode3D.SHORT_1D))
            .unwrap()
        val long = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(elbowRadiusMode = ElbowRadiusMode3D.LONG_1_5D))
            .unwrap()
        val custom = engine.profiles
            .apply(
                profile,
                AssemblyProfileOverrides3D(
                    elbowRadiusMode = ElbowRadiusMode3D.CUSTOM,
                    elbowCenterlineRadiusMm = 210.0,
                ),
            )
            .unwrap()

        assertEquals(50.0, short.elbow.centerlineRadiusMm, 1e-9)
        assertEquals(75.0, long.elbow.centerlineRadiusMm, 1e-9)
        assertEquals(210.0, custom.elbow.centerlineRadiusMm, 1e-9)
    }

    @Test
    fun `a radius smaller than the tube is refused with the rule that failed`() {
        val error = engine.profiles
            .apply(
                profile,
                AssemblyProfileOverrides3D(
                    elbowRadiusMode = ElbowRadiusMode3D.CUSTOM,
                    elbowCenterlineRadiusMm = 5.0,
                ),
            )
            .failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            ParameterRule3D.RADIUS_TOO_SMALL,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `a wall thicker than the bore is refused`() {
        val error = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(pipeWallThicknessMm = 90.0))
            .failure()

        assertEquals(
            ParameterRule3D.WALL_TOO_THICK,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `an elbow limit beyond the engine quota is refused`() {
        val error = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(maxElbows = 10_000))
            .failure()

        assertTrue(error is Fabrication3DError.QuotaExceeded)
        assertEquals(EngineQuota3D.ELBOWS, (error as Fabrication3DError.QuotaExceeded).quota)
    }

    @Test
    fun `changing the radius changes the geometry the chain builds`() {
        val tighter = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(elbowRadiusMode = ElbowRadiusMode3D.SHORT_1D))
            .unwrap()

        val catalogChain = engine.chains.create(profile).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Elbow(90.0, 0.0))
        val tightChain = engine.chains.create(tighter).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Elbow(90.0, 0.0))

        val catalogRadius = (catalogChain.assembly.part("E1").definition.geometry as ElbowGeometry3D)
            .centerlineRadiusMm
        val tightRadius = (tightChain.assembly.part("E1").definition.geometry as ElbowGeometry3D)
            .centerlineRadiusMm
        assertNotEquals(catalogRadius, tightRadius, 1e-9)
        assertEquals(50.0, tightRadius, 1e-9)
    }

    @Test
    fun `changing the weld gap changes the cut lengths the solver reports`() {
        val request = RouteRequest3D(
            profile = profile,
            target = RouteTerminal3D(Vec3(1_600.0, 500.0, 300.0)),
            preferredElbowAngleDeg = 45.0,
        )
        val widened = engine.profiles.apply(profile, AssemblyProfileOverrides3D(weldGapMm = 5.0)).unwrap()

        val standard = engine.router.solve(request).unwrap()
        val wide = engine.router.solve(request.copy(profile = widened)).unwrap()

        assertNotEquals(standard.totalPipeCutMm, wide.totalPipeCutMm, 1e-6)
        assertTrue(wide.totalPipeCutMm < standard.totalPipeCutMm)
        assertTrue(wide.targetErrorMm < 2e-6)
    }

    @Test
    fun `a weld gap wider than the shop rule is refused`() {
        val error = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(weldGapMm = 40.0))
            .failure()

        assertEquals(
            ParameterRule3D.ABOVE_MAXIMUM,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `the allowed angle set can be replaced by the fitter`() {
        val applied = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(allowedElbowAnglesDeg = listOf(22.5, 11.25)))
            .unwrap()

        assertEquals(listOf(11.25, 22.5), applied.rules.allowedElbowAnglesDeg)
    }

    @Test
    fun `an oversized angle set is refused as a candidate quota`() {
        val flood = List(10_000) { 1.0 + it % 179 }

        val error = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(allowedElbowAnglesDeg = flood))
            .failure()

        assertTrue(error is Fabrication3DError.QuotaExceeded)
        assertEquals(
            EngineQuota3D.ROUTE_CANDIDATES,
            (error as Fabrication3DError.QuotaExceeded).quota,
        )
    }

    @Test
    fun `the solver refuses an oversized angle set on the request too`() {
        val error = engine.router.solve(
            RouteRequest3D(
                profile = profile,
                target = RouteTerminal3D(Vec3(1_600.0, 500.0, 300.0)),
                allowedElbowAnglesDeg = List(10_000) { 1.0 + it % 179 },
            ),
        ).failure()

        assertTrue(error is Fabrication3DError.QuotaExceeded)
    }

    @Test
    fun `an empty angle set is refused`() {
        val error = engine.profiles
            .apply(profile, AssemblyProfileOverrides3D(allowedElbowAnglesDeg = emptyList()))
            .failure()

        assertEquals(
            ParameterRule3D.EMPTY_SET,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }
}

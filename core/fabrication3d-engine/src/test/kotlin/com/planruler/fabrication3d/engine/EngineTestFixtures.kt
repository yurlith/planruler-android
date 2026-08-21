package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.pipecalculator.FlangedOffsetAssemblyInput
import com.planruler.pipecalculator.calculateFlangedOffsetAssembly
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

internal val engine = DefaultFabrication3DEngine()

internal fun workshopProfile(
    dn: Int = 50,
    pn: Int = 16,
    targetOffsetMm: Double = 500.0,
    overallFaceToFaceMm: Double = 1_600.0,
): AssemblyProfile3D = calculateFlangedOffsetAssembly(
    FlangedOffsetAssemblyInput(
        dn = dn,
        pn = pn,
        targetOffsetMm = targetOffsetMm,
        overallFaceToFaceMm = overallFaceToFaceMm,
    ),
).toAssemblyProfile3D()

internal fun installationProfile(dn: Int): AssemblyProfile3D = calculateFlangedOffsetAssembly(
    FlangedOffsetAssemblyInput(
        dn = dn,
        pn = 16,
        targetOffsetMm = 1_500.0,
        overallFaceToFaceMm = 8_000.0,
        stockLengthMm = 12_000.0,
    ),
).toAssemblyProfile3D()

internal fun <T> Fabrication3DResult<T>.unwrap(): T = when (this) {
    is Fabrication3DResult.Ok -> value
    is Fabrication3DResult.Failure -> throw AssertionError("Expected success but failed with $error")
}

internal fun <T> Fabrication3DResult<T>.failure(): Fabrication3DError = when (this) {
    is Fabrication3DResult.Ok -> throw AssertionError("Expected a failure but got $value")
    is Fabrication3DResult.Failure -> error
}

internal fun assertVec(expected: Vec3, actual: Vec3, tolerance: Double = 1e-7) {
    assertEquals(expected.x, actual.x, tolerance)
    assertEquals(expected.y, actual.y, tolerance)
    assertEquals(expected.z, actual.z, tolerance)
}

internal fun assertValid(assembly: ParametricAssembly3D) {
    val issues = engine.graph.validate(assembly)
    assertTrue("Expected a valid assembly but found $issues", issues.isEmpty())
}

internal fun ChainEditorState3D.append(step: ChainStep3D): ChainEditorState3D =
    engine.chains.execute(this, ChainCommand3D.Append(step)).unwrap()

internal fun ChainEditorState3D.appendAll(vararg steps: ChainStep3D): ChainEditorState3D =
    steps.fold(this) { state, step -> state.append(step) }

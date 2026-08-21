package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyMetadata3D
import com.planruler.fabrication3d.Bounds3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.ObstacleBox3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.Transform3D
import com.planruler.fabrication3d.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssemblyGraphTest {
    private val pipe = engine.parts.straightPipe("pipe", null, 50, 500.0, 60.3, 2.9).unwrap()
    private val elbow = engine.parts.elbow("elbow", null, 50, 45.0, 76.0, 60.3, 2.9).unwrap()

    private val base = ParametricAssembly3D(
        metadata = AssemblyMetadata3D("test", 50, 16, "unit-test"),
        parts = listOf(PartInstance3D("P1", "P1", pipe, Transform3D.IDENTITY)),
        connections = emptyList(),
    )

    @Test
    fun `attach aligns a new elbow port and preserves its roll frame`() {
        val attached = engine.graph.attach(
            assembly = base,
            anchor = PortReference3D("P1", "end"),
            newPartId = "E1",
            newPartCode = "E1",
            definition = elbow,
            attachingPortId = "start",
            axialGapMm = 2.0,
        ).unwrap()

        assertValid(attached)
        val pipeEnd = attached.worldPort(PortReference3D("P1", "end"))
        val elbowStart = attached.worldPort(PortReference3D("E1", "start"))
        assertEquals(2.0, pipeEnd.frame.position.distanceTo(elbowStart.frame.position), 1e-8)
        assertEquals(-1.0, pipeEnd.frame.forward.dot(elbowStart.frame.forward), 1e-9)
        assertEquals(2, attached.parts.size)
        assertEquals(2, attached.freePorts().size)
    }

    @Test
    fun `equal tee exposes three independent fabrication ports`() {
        val tee = engine.parts.equalTee("tee", "catalog-tee", 50, 128.0, 64.0, 60.3, 2.9).unwrap()

        assertEquals(3, tee.ports.size)
        assertEquals(setOf("run-start", "run-end", "branch"), tee.ports.map { it.id }.toSet())
        assertEquals(Vec3(0.0, 64.0, 0.0), tee.port("branch").frame.position)
    }

    @Test
    fun `reducer and cap are now real geometry rather than empty enum entries`() {
        val reducer = engine.parts
            .reducer("reducer", null, 80, 50, 100.0, 88.9, 3.2, 60.3, 2.9, eccentric = true)
            .unwrap()
        val cap = engine.parts.cap("cap", null, 50, 30.0, 60.3, 2.9).unwrap()

        assertEquals(2, reducer.ports.size)
        assertEquals(80, reducer.port("large").nominalDiameter)
        assertEquals(50, reducer.port("small").nominalDiameter)
        assertEquals((88.9 - 60.3) / 2.0, reducer.port("small").frame.position.z, 1e-9)
        assertEquals(1, cap.ports.size)
    }

    @Test
    fun `attaching to an occupied port is refused with a typed error`() {
        val attached = engine.graph.attach(
            assembly = base,
            anchor = PortReference3D("P1", "end"),
            newPartId = "E1",
            newPartCode = "E1",
            definition = elbow,
            attachingPortId = "start",
        ).unwrap()

        val error = engine.graph.attach(
            assembly = attached,
            anchor = PortReference3D("P1", "end"),
            newPartId = "E2",
            newPartCode = "E2",
            definition = elbow,
            attachingPortId = "start",
        ).failure()

        assertEquals(Fabrication3DError.PortNotFree(PortReference3D("P1", "end")), error)
    }

    @Test
    fun `attaching a mismatched diameter is refused before geometry is built`() {
        val wrongPipe = engine.parts.straightPipe("wrong", null, 80, 500.0, 88.9, 3.2).unwrap()

        val error = engine.graph.attach(
            assembly = base,
            anchor = PortReference3D("P1", "end"),
            newPartId = "P2",
            newPartCode = "P2",
            definition = wrongPipe,
            attachingPortId = "start",
        ).failure()

        assertEquals(
            Fabrication3DError.PortsIncompatible(PortReference3D("P1", "end"), ParameterRule3D.DIAMETER_MISMATCH),
            error,
        )
    }

    @Test
    fun `removing a part drops the welds that referenced it`() {
        val attached = engine.graph.attach(
            assembly = base,
            anchor = PortReference3D("P1", "end"),
            newPartId = "E1",
            newPartCode = "E1",
            definition = elbow,
            attachingPortId = "start",
        ).unwrap()

        val reduced = engine.graph.removePart(attached, "E1").unwrap()

        assertEquals(1, reduced.parts.size)
        assertTrue(reduced.connections.isEmpty())
        assertEquals(Fabrication3DError.UnknownPart("nope"), engine.graph.removePart(reduced, "nope").failure())
    }

    @Test
    fun `a chain folded back onto itself reports a self intersection`() {
        val folded = engine.chains.create(workshopProfile()).unwrap().appendAll(
            ChainStep3D.Pipe(400.0),
            ChainStep3D.Elbow(90.0, 0.0),
            ChainStep3D.Pipe(30.0),
            ChainStep3D.Elbow(90.0, 0.0),
            ChainStep3D.Pipe(400.0),
        )

        val issues = engine.graph.selfIntersections(folded.assembly, clearanceMm = 0.0)

        assertTrue("A U-turn tighter than the pipe diameter must be reported", issues.isNotEmpty())
    }

    @Test
    fun `an obstacle in the run is reported by id`() {
        val withPipe = engine.chains.create(workshopProfile()).unwrap()
            .append(ChainStep3D.Pipe(1_000.0))
        val obstacle = ObstacleBox3D(
            id = "beam",
            bounds = Bounds3D(Vec3(400.0, -100.0, -100.0), Vec3(500.0, 100.0, 100.0)),
        )
        val clear = ObstacleBox3D(
            id = "far-wall",
            bounds = Bounds3D(Vec3(5_000.0, -100.0, -100.0), Vec3(5_100.0, 100.0, 100.0)),
        )

        val blocked = engine.graph.obstructions(withPipe.assembly, listOf(obstacle, clear))

        assertEquals(listOf("beam"), blocked)
    }
}

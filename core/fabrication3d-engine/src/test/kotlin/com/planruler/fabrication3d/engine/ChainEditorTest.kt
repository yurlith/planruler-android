package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainEditorTest {
    private val profile = workshopProfile()

    @Test
    fun `chain adds through the open end and supports undo redo`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Elbow(45.0, 90.0),
            ChainStep3D.Pipe(240.0),
        )

        assertEquals(4, editor.assembly.parts.size)
        assertEquals(1, editor.elbowCount)
        assertTrue(editor.canUndo)
        assertFalse(editor.canRedo)
        assertValid(editor.assembly)
        val elbowEnd = editor.assembly.worldPort(PortReference3D("E1", "end")).frame.position
        assertTrue("A 90-degree roll must move the bend into Z", elbowEnd.z > 1.0)
        assertEquals(0.0, elbowEnd.y, 1e-7)

        val undone = engine.chains.undo(editor).unwrap()
        assertEquals(3, undone.assembly.parts.size)
        assertTrue(undone.canRedo)
        val redone = engine.chains.redo(undone).unwrap()
        assertEquals(editor.assembly, redone.assembly)
        assertEquals(editor.plan, redone.plan)
    }

    @Test
    fun `an already welded pipe can have its length changed`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Elbow(45.0, 0.0),
            ChainStep3D.Pipe(240.0),
            ChainStep3D.Flange(),
        )
        val originalEnd = editor.assembly.worldPort(PortReference3D("F2", "face")).frame.position
        val pipePath = requireNotNull(editor.pathForPart("P1"))

        val edited = engine.chains
            .execute(editor, ChainCommand3D.Replace(pipePath, ChainStep3D.Pipe(450.0)))
            .unwrap()

        assertValid(edited.assembly)
        val geometry = edited.assembly.part("P1").definition.geometry as StraightPipeGeometry3D
        assertEquals(450.0, geometry.lengthMm, 1e-9)
        val movedEnd = edited.assembly.worldPort(PortReference3D("F2", "face")).frame.position
        assertEquals(150.0, movedEnd.x - originalEnd.x, 1e-9)
        assertEquals(originalEnd.y, movedEnd.y, 1e-9)
        assertEquals(5, edited.assembly.parts.size)
    }

    @Test
    fun `an elbow angle and roll can be changed after the fact`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Elbow(45.0, 0.0),
            ChainStep3D.Pipe(240.0),
        )
        val elbowPath = requireNotNull(editor.pathForPart("E1"))
        val before = editor.assembly.worldPort(PortReference3D("P2", "end")).frame.position

        val edited = engine.chains
            .execute(editor, ChainCommand3D.Replace(elbowPath, ChainStep3D.Elbow(30.0, 90.0)))
            .unwrap()

        assertValid(edited.assembly)
        val after = edited.assembly.worldPort(PortReference3D("P2", "end")).frame.position
        assertNotEquals(before, after)
        assertTrue("A 90 degree roll moves the outlet into Z", after.z > 1.0)
    }

    @Test
    fun `a part can be removed from the middle and another inserted`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Elbow(45.0, 0.0),
            ChainStep3D.Pipe(240.0),
            ChainStep3D.Elbow(-45.0, 0.0),
            ChainStep3D.Pipe(300.0),
        )

        val withoutElbow = engine.chains.execute(editor, ChainCommand3D.RemoveAt(ChainPath3D(listOf(1)))).unwrap()
        assertValid(withoutElbow.assembly)
        assertEquals(1, withoutElbow.elbowCount)

        val reinserted = engine.chains
            .execute(withoutElbow, ChainCommand3D.InsertAt(ChainPath3D(listOf(1)), ChainStep3D.Pipe(120.0)))
            .unwrap()

        assertValid(reinserted.assembly)
        assertEquals(4, reinserted.pipeCount)
        assertEquals(1, reinserted.elbowCount)
    }

    /**
     * The inspector edits whatever is selected, so a command must leave the selection on the
     * element it acted on. Getting this wrong is silent and dangerous: an edit aimed at a
     * branch would quietly reshape the main run instead.
     */
    @Test
    fun `selection follows the element a command acted on`() {
        val afterFirst = engine.chains.create(profile).unwrap()
            .append(ChainStep3D.Pipe(300.0))
        assertEquals("P1", afterFirst.selectedPartId)

        val afterSecond = afterFirst.append(ChainStep3D.Elbow(45.0, 0.0))
        assertEquals("E1", afterSecond.selectedPartId)

        // Replace keeps the caret where it is instead of jumping to the end of the run.
        val pipePath = requireNotNull(afterSecond.pathForPart("P1"))
        val replaced = engine.chains
            .execute(afterSecond, ChainCommand3D.Replace(pipePath, ChainStep3D.Pipe(420.0)))
            .unwrap()
        assertEquals("P1", replaced.selectedPartId)

        val inserted = engine.chains
            .execute(replaced, ChainCommand3D.InsertAt(ChainPath3D(listOf(0)), ChainStep3D.Pipe(150.0)))
            .unwrap()
        val insertedId = requireNotNull(inserted.partIdAt(ChainPath3D(listOf(0))))
        assertEquals(insertedId, inserted.selectedPartId)

        // After a removal the selection must still name a part that exists.
        val pruned = engine.chains
            .execute(inserted, ChainCommand3D.RemoveAt(ChainPath3D(listOf(0))))
            .unwrap()
        val survivor = requireNotNull(pruned.selectedPartId)
        assertTrue("Selection $survivor no longer exists", pruned.assembly.partOrNull(survivor) != null)
    }

    @Test
    fun `appending to a branch selects the branch element and not the main run`() {
        val editor = engine.chains.create(profile).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Tee())
        val teePath = requireNotNull(editor.pathForPart("T1"))

        val branched = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(180.0), teePath))
            .unwrap()

        val branchPipeId = requireNotNull(branched.partIdAt(teePath.child(0)))
        assertEquals(branchPipeId, branched.selectedPartId)
        assertNotEquals("P1", branched.selectedPartId)
        assertEquals(teePath.child(0), branched.pathForPart(branchPipeId))
    }

    @Test
    fun `a tee opens a branch that is fabricated from its own port`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Tee(),
            ChainStep3D.Pipe(250.0),
            ChainStep3D.Flange(),
        )
        val teePath = requireNotNull(editor.pathForPart("T1"))

        val branched = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(180.0), teePath))
            .unwrap()
            .let { engine.chains.execute(it, ChainCommand3D.Append(ChainStep3D.Flange(), teePath)).unwrap() }

        assertValid(branched.assembly)
        assertEquals(1, branched.teeCount)
        assertEquals(3, branched.pipeCount)
        assertEquals(listOf(teePath), branched.branchPaths())

        // The branch leaves the run: its pipe must sit along the tee branch axis.
        val branchPipeId = requireNotNull(branched.partIdAt(teePath.child(0)))
        val branchOutlet = branched.assembly.worldPort(PortReference3D(branchPipeId, "end")).frame
        assertVec(Vec3.UNIT_Y, branchOutlet.forward, 1e-9)
        assertTrue("The branch has to advance in Y", branchOutlet.position.y > 200.0)
        assertEquals(
            3,
            branched.assembly.parts.count { it.definition.kind == FabricationPartKind.FLANGE },
        )
    }

    @Test
    fun `a branch element keeps its own address for editing and removal`() {
        val editor = engine.chains.create(profile).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Tee())
        val teePath = requireNotNull(editor.pathForPart("T1"))
        val withBranch = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(180.0), teePath))
            .unwrap()
        val branchPipePath = teePath.child(0)

        val retuned = engine.chains
            .execute(withBranch, ChainCommand3D.Replace(branchPipePath, ChainStep3D.Pipe(420.0)))
            .unwrap()

        val branchPipeId = requireNotNull(retuned.partIdAt(branchPipePath))
        val geometry = retuned.assembly.part(branchPipeId).definition.geometry as StraightPipeGeometry3D
        assertEquals(420.0, geometry.lengthMm, 1e-9)
        assertValid(retuned.assembly)

        val pruned = engine.chains
            .execute(retuned, ChainCommand3D.RemoveAt(branchPipePath))
            .unwrap()
        assertEquals(1, pruned.pipeCount)
        assertEquals(1, pruned.teeCount)
        assertValid(pruned.assembly)
    }

    @Test
    fun `replacing a tee keeps the branch already welded to it`() {
        val editor = engine.chains.create(profile).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Tee())
        val teePath = requireNotNull(editor.pathForPart("T1"))
        val withBranch = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(180.0), teePath))
            .unwrap()

        val rolled = engine.chains
            .execute(withBranch, ChainCommand3D.Replace(teePath, ChainStep3D.Tee(90.0)))
            .unwrap()

        assertEquals(1, rolled.teeCount)
        assertEquals(2, rolled.pipeCount)
        val branchPipeId = requireNotNull(rolled.partIdAt(teePath.child(0)))
        val outlet = rolled.assembly.worldPort(PortReference3D(branchPipeId, "end")).frame
        assertTrue("A 90 degree tee roll swings the branch into Z", outlet.position.z > 200.0)
        assertValid(rolled.assembly)
    }

    @Test
    fun `a diameter without a catalog tee refuses the branch by rule`() {
        val withoutTee = profile.copy(tee = null)
        val editor = engine.chains.create(withoutTee).unwrap().append(ChainStep3D.Pipe(300.0))

        val error = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Tee()))
            .failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            ParameterRule3D.EMPTY_SET,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `a reducer necks the bore down for everything placed after it`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Reducer(
                lengthMm = 76.0,
                smallNominalDiameter = 40,
                smallOutsideDiameterMm = 48.3,
                smallWallThicknessMm = 2.6,
            ),
            ChainStep3D.Pipe(150.0),
        )

        assertValid(editor.assembly)
        assertEquals(1, editor.plan.steps.count { it is ChainStep3D.Reducer })
        val neckedPipe = editor.assembly.part("P2").definition.geometry as StraightPipeGeometry3D
        assertEquals(48.3, neckedPipe.outsideDiameterMm, 1e-9)
        assertEquals(2.6, neckedPipe.wallThicknessMm, 1e-9)
    }

    @Test
    fun `a cap after a reducer closes at the reduced diameter, not the original one`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Reducer(76.0, 40, 48.3, 2.6),
            ChainStep3D.Cap(),
        )

        assertValid(editor.assembly)
        val cap = editor.assembly.parts.last().definition.geometry as CapGeometry3D
        assertEquals(48.3, cap.outsideDiameterMm, 1e-9)
    }

    @Test
    fun `a flange after a reducer bores to the reduced pipe, not the original`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Reducer(76.0, 40, 48.3, 2.6),
            ChainStep3D.Flange(),
        )

        assertValid(editor.assembly)
        val flange = editor.assembly.parts.last().definition.geometry as WeldNeckFlangeGeometry3D
        assertEquals(48.3, flange.pipeOutsideDiameterMm, 1e-9)
        assertEquals(2.6, flange.pipeWallThicknessMm, 1e-9)
        // The disc itself stays whatever the profile specifies; only the bore changed.
        assertEquals(profile.flange.outsideDiameterMm, flange.outsideDiameterMm, 1e-9)
    }

    /**
     * An elbow is always built at the profile's catalog diameter, so placing one after a
     * reducer must fail as a typed port mismatch rather than silently welding a full-size
     * bend onto a necked-down pipe.
     */
    @Test
    fun `an elbow after a reducer is refused as a diameter mismatch, not built wrong`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Reducer(76.0, 40, 48.3, 2.6),
        )

        val error = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Elbow(45.0, 0.0)))
            .failure()

        assertTrue(error is Fabrication3DError.PortsIncompatible)
        assertEquals(
            ParameterRule3D.DIAMETER_MISMATCH,
            (error as Fabrication3DError.PortsIncompatible).rule,
        )
    }

    @Test
    fun `the catalog reducer step reaches the fabricated part with real dimensions`() {
        val option = requireNotNull(com.planruler.fabrication3d.catalog.reducerCatalogOption(50))

        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Reducer(
                lengthMm = option.lengthMm,
                smallNominalDiameter = option.smallNominalDiameter,
                smallOutsideDiameterMm = option.smallOutsideDiameterMm,
                smallWallThicknessMm = option.smallWallThicknessMm,
            ),
            ChainStep3D.Cap(),
        )

        assertValid(editor.assembly)
        assertEquals(40, option.smallNominalDiameter)
        val reducer = editor.assembly.parts.first { it.definition.kind == FabricationPartKind.REDUCER }
        val geometry = reducer.definition.geometry as ReducerGeometry3D
        assertEquals(option.lengthMm, geometry.lengthMm, 1e-9)
        assertEquals(option.smallOutsideDiameterMm, geometry.smallOutsideDiameterMm, 1e-9)
    }

    @Test
    fun `a reducer inside a tee branch only necks that branch`() {
        val editor = engine.chains.create(profile).unwrap()
            .appendAll(ChainStep3D.Pipe(300.0), ChainStep3D.Tee())
        val teePath = requireNotNull(editor.pathForPart("T1"))

        val branched = engine.chains
            .execute(
                editor,
                ChainCommand3D.Append(ChainStep3D.Reducer(76.0, 40, 48.3, 2.6), teePath),
            )
            .unwrap()
            .let {
                engine.chains.execute(it, ChainCommand3D.Append(ChainStep3D.Cap(), teePath)).unwrap()
            }

        assertValid(branched.assembly)
        val branchCap = branched.assembly.part(requireNotNull(branched.partIdAt(teePath.child(1))))
        assertEquals(48.3, (branchCap.definition.geometry as CapGeometry3D).outsideDiameterMm, 1e-9)

        // The trunk was never touched by the branch's reducer.
        val trunkPipe = branched.assembly.part("P1").definition.geometry as StraightPipeGeometry3D
        assertEquals(profile.pipe.outsideDiameterMm, trunkPipe.outsideDiameterMm, 1e-9)
    }

    @Test
    fun `the start frame is configurable instead of fixed to the origin`() {
        val start = ChainStart3D(
            position = Vec3(1_200.0, -400.0, 250.0),
            direction = Vec3.UNIT_Y,
            upHint = Vec3.UNIT_Z,
        )

        val editor = engine.chains.create(profile, start).unwrap().append(ChainStep3D.Pipe(500.0))

        assertValid(editor.assembly)
        assertVec(start.position, editor.assembly.worldPort(PortReference3D("F1", "face")).frame.position, 1e-9)
        val outlet = editor.assembly.worldPort(PortReference3D("P1", "end")).frame
        assertVec(Vec3.UNIT_Y, outlet.forward, 1e-9)
    }

    @Test
    fun `an open ended run places its first element without a start flange`() {
        val start = ChainStart3D(terminal = ChainTerminal3D.OPEN_END)

        val editor = engine.chains.create(profile, start).unwrap().append(ChainStep3D.Pipe(500.0))

        assertValid(editor.assembly)
        assertEquals(1, editor.assembly.parts.size)
        assertVec(Vec3.ZERO, editor.assembly.worldPort(PortReference3D("P1", "start")).frame.position, 1e-9)
    }

    @Test
    fun `the elbow limit follows the profile rules rather than a compiled in five`() {
        val generous = profile.copy(rules = profile.rules.copy(maxElbows = 12))
        var editor = engine.chains.create(generous).unwrap()
        repeat(8) { index ->
            editor = editor.appendAll(
                ChainStep3D.Pipe(150.0),
                ChainStep3D.Elbow(30.0, index * 45.0),
            )
        }

        assertEquals(8, editor.elbowCount)
        assertValid(editor.assembly)
    }

    @Test
    fun `exceeding the elbow ceiling reports a quota instead of throwing`() {
        val strict = profile.copy(rules = profile.rules.copy(maxElbows = 2))
        val editor = engine.chains.create(strict).unwrap().appendAll(
            ChainStep3D.Elbow(30.0, 0.0),
            ChainStep3D.Elbow(30.0, 0.0),
        )

        val error = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Elbow(30.0, 0.0)))
            .failure()

        assertEquals(Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, 2, 3), error)
    }

    @Test
    fun `an angle outside the configured window is refused by rule`() {
        val strict = profile.copy(
            rules = profile.rules.copy(minElbowAngleDeg = 15.0, maxElbowAngleDeg = 90.0),
        )
        val editor = engine.chains.create(strict).unwrap()

        val error = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Elbow(120.0, 0.0)))
            .failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            ParameterRule3D.ANGLE_NOT_ALLOWED,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `a wider window accepts the tight and the shallow bends the catalog now allows`() {
        val wide = profile.copy(rules = profile.rules.copy(minElbowAngleDeg = 1.0, maxElbowAngleDeg = 180.0))

        val editor = engine.chains.create(wide).unwrap().appendAll(
            ChainStep3D.Pipe(200.0),
            ChainStep3D.Elbow(11.25, 0.0),
            ChainStep3D.Pipe(200.0),
            ChainStep3D.Elbow(180.0, 0.0),
            ChainStep3D.Pipe(200.0),
        )

        assertValid(editor.assembly)
        assertEquals(2, editor.elbowCount)
    }

    @Test
    fun `a closed chain refuses further elements`() {
        val editor = engine.chains.create(profile).unwrap().appendAll(
            ChainStep3D.Pipe(300.0),
            ChainStep3D.Flange(),
        )

        assertTrue(editor.isClosed)
        assertEquals(
            2,
            editor.assembly.parts.count { it.definition.kind == FabricationPartKind.FLANGE },
        )
        val error = engine.chains
            .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(100.0)))
            .failure()
        assertTrue(error is Fabrication3DError.InvalidParameter)
    }

    @Test
    fun `a cut below the configured minimum is refused`() {
        val error = engine.chains
            .execute(
                engine.chains.create(profile).unwrap(),
                ChainCommand3D.Append(ChainStep3D.Pipe(2.0)),
            )
            .failure()

        assertTrue(error is Fabrication3DError.InvalidParameter)
        assertEquals(
            ParameterRule3D.BELOW_MINIMUM,
            (error as Fabrication3DError.InvalidParameter).rule,
        )
    }

    @Test
    fun `undo history is bounded by the engine quota`() {
        val bounded = DefaultFabrication3DEngine(
            com.planruler.fabrication3d.EngineLimits3D(maxUndoDepth = 4),
        )
        var editor = bounded.chains.create(profile).unwrap()
        repeat(10) {
            editor = bounded.chains
                .execute(editor, ChainCommand3D.Append(ChainStep3D.Pipe(100.0)))
                .unwrap()
        }

        assertEquals(4, editor.undoStack.size)
    }
}

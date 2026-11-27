package com.planruler.feature.pipecalculator

import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
import com.planruler.model.InstallationTaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallerDraftTest {
    private fun draft(input: InstallationJobInput = InstallationJobInput(), task: InstallationTaskType = InstallationTaskType.FLANGED_OFFSET) =
        InstallerDraft.from(
            InstallationJob(
                id = InstallationJobId("job"),
                name = "Test",
                taskType = task,
                input = input,
                createdAtEpochMs = 1L,
                modifiedAtEpochMs = 1L,
            ),
        )

    @Test fun `an offset picked over untouched flange faces is measured between pipe ends`() {
        val offset = draft().selectTask(InstallationTaskType.FLAT_OFFSET)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, offset.startReference)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, offset.endReference)
        val rolling = draft().selectTask(InstallationTaskType.ROLLING_OFFSET)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, rolling.startReference)
    }

    @Test fun `references the fitter chose are kept, and flange to flange is always flanged`() {
        val chosen = draft(
            InstallationJobInput(
                startReference = InstallationMeasurementReference.FLANGE_FACE,
                endReference = InstallationMeasurementReference.PIPE_OUTER_EDGE,
            ),
        ).selectTask(InstallationTaskType.FLAT_OFFSET)
        assertEquals(InstallationMeasurementReference.FLANGE_FACE, chosen.startReference)
        assertEquals(InstallationMeasurementReference.PIPE_OUTER_EDGE, chosen.endReference)

        val flanged = draft(task = InstallationTaskType.FLAT_OFFSET)
            .selectTask(InstallationTaskType.FLAT_OFFSET)
            .selectTask(InstallationTaskType.FLANGED_OFFSET)
        assertEquals(InstallationMeasurementReference.FLANGE_FACE, flanged.startReference)
        assertEquals(InstallationMeasurementReference.FLANGE_FACE, flanged.endReference)
    }

    @Test fun `flange to flange is an offset that can be placed, and it keeps its flanges`() {
        val offsets = setOf(
            InstallationTaskType.FLANGED_OFFSET,
            InstallationTaskType.FLAT_OFFSET,
            InstallationTaskType.ROLLING_OFFSET,
        )
        InstallationTaskType.entries.forEach { task -> assertEquals(task.name, task in offsets, task.isOffset()) }

        val exact = draft().copy(offsetPlacement = InstallationOffsetPlacement.EXACT, offsetLead = "250")
        val input = exact.toInputOrNull()!!
        assertEquals(InstallationOffsetPlacement.EXACT, input.offsetPlacement)
        assertEquals(250.0, input.offsetLeadMm, 0.0)

        // Where the offset sits survives a change of task; the references follow the task.
        val flanged = draft(task = InstallationTaskType.FLAT_OFFSET)
            .copy(offsetPlacement = InstallationOffsetPlacement.END)
            .selectTask(InstallationTaskType.FLANGED_OFFSET)
        assertEquals(InstallationOffsetPlacement.END, flanged.offsetPlacement)
        assertEquals(InstallationMeasurementReference.FLANGE_FACE, flanged.startReference)
        assertEquals(InstallationMeasurementReference.FLANGE_FACE, flanged.endReference)
        // Its flange faces do not make the site offsets flanged: they still start from pipe ends.
        assertEquals(InstallationMeasurementReference.PIPE_FACE, flanged.selectTask(InstallationTaskType.ROLLING_OFFSET).startReference)
        assertEquals(InstallationMeasurementReference.PIPE_FACE, flanged.selectTask(InstallationTaskType.FLAT_OFFSET).endReference)
    }

    @Test fun `each task asks only for what it reads`() {
        assertTrue(InstallationTaskType.FLAT_OFFSET.usesLateral())
        assertFalse(InstallationTaskType.FLAT_OFFSET.usesVertical())
        assertFalse(InstallationTaskType.FLAT_OFFSET.usesEndDirection())
        assertTrue(InstallationTaskType.ROLLING_OFFSET.usesVertical())
        assertFalse(InstallationTaskType.ROLLING_OFFSET.usesEndDirection())
        assertFalse(InstallationTaskType.STRAIGHT_INSERT.usesLateral())
        assertTrue(InstallationTaskType.TURN_WITH_OFFSET.usesEndDirection())
        assertTrue(InstallationTaskType.U_TURN.usesEndDirection())
    }

    @Test fun `a typed draft turns into job input and back`() {
        val typed = draft().selectTask(InstallationTaskType.ROLLING_OFFSET).copy(along = "1 600".replace(" ", ""), lateral = "500,5", vertical = "300")
        val input = typed.toInputOrNull()!!
        assertEquals(1_600.0, input.alongMm, 0.0)
        assertEquals(500.5, input.lateralOffsetMm, 1e-9)
        assertEquals(300.0, input.verticalOffsetMm, 0.0)
        assertEquals(InstallationEndDirection.FORWARD, input.endDirection)
        assertEquals("500.5", draft(input, InstallationTaskType.ROLLING_OFFSET).lateral)
    }

    @Test fun `a flat offset can climb or drop and keeps its size when turned`() {
        val sideways = draft().selectTask(InstallationTaskType.FLAT_OFFSET).withFlatOffset("450")
        assertEquals(FlatOffsetDirection.RIGHT, sideways.flatDirection)
        val up = sideways.withFlatDirection(FlatOffsetDirection.UP)
        assertEquals(FlatOffsetDirection.UP, up.flatDirection)
        assertEquals("450", up.flatOffset)
        assertEquals("0", up.lateral)
        val input = up.toInputOrNull()!!
        assertEquals(0.0, input.lateralOffsetMm, 0.0)
        assertEquals(450.0, input.verticalOffsetMm, 0.0)
        // Reopened, the job still climbs rather than stepping sideways.
        val reopened = draft(input, InstallationTaskType.FLAT_OFFSET)
        assertEquals(FlatOffsetDirection.UP, reopened.flatDirection)
        assertEquals("450", reopened.flatOffset)
        assertEquals(FlatOffsetDirection.LEFT, up.withFlatDirection(FlatOffsetDirection.LEFT).flatDirection)
        assertEquals("450", up.withFlatDirection(FlatOffsetDirection.LEFT).lateral)
    }

    @Test fun `a pure rise stays a rise when the task becomes a flat offset`() {
        val rise = draft(InstallationJobInput(lateralOffsetMm = 0.0, verticalOffsetMm = 380.0), InstallationTaskType.ROLLING_OFFSET)
        val flat = rise.selectTask(InstallationTaskType.FLAT_OFFSET)
        assertTrue(flat.flatVertical)
        assertEquals("380", flat.flatOffset)
        // Picking the task again changes nothing the fitter typed.
        assertEquals(flat, flat.selectTask(InstallationTaskType.FLAT_OFFSET))
    }

    @Test fun `where the offset sits is kept, and an exact cut must be a number`() {
        val exact = draft().selectTask(InstallationTaskType.FLAT_OFFSET)
            .copy(offsetPlacement = InstallationOffsetPlacement.EXACT, offsetLead = "250")
        val input = exact.toInputOrNull()!!
        assertEquals(InstallationOffsetPlacement.EXACT, input.offsetPlacement)
        assertEquals(250.0, input.offsetLeadMm, 0.0)
        assertEquals("250", draft(input, InstallationTaskType.FLAT_OFFSET).offsetLead)
        assertEquals(null, exact.copy(offsetLead = "").toInputOrNull())
        // A blank exact cut only matters while the exact placement is chosen.
        assertTrue(exact.copy(offsetLead = "", offsetPlacement = InstallationOffsetPlacement.START).toInputOrNull() != null)
    }
}

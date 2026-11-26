package com.planruler.feature.pipecalculator

import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationMeasurementReference
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
}

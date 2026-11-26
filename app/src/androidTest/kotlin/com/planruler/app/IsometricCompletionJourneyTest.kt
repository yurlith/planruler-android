package com.planruler.app

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.model.*
import com.planruler.project.api.ProjectResult
import com.planruler.project.local.FileProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.sqrt

class IsometricCompletionJourneyTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository get() = FileProjectRepository(context.filesDir.resolve("projects"))
    private val id = ProjectId("completion-test")

    @Before fun seedPhotoAndIsometric() {
        bringPlanRulerToForeground()
        context.getSharedPreferences("planruler-ui-state", 0).edit().clear().commit()
        context.getSharedPreferences("planruler-settings", 0).edit().clear().putString("language", "ENGLISH").commit()
        context.filesDir.resolve("projects").apply { deleteRecursively(); mkdirs() }
        val photo = File(context.filesDir, "completion-photo.png")
        val bitmap = Bitmap.createBitmap(1200, 800, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        photo.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val a = IsoNode(IsoNodeId("a"), IsoPoint(0.0, 0.0, 0.0))
        val b = IsoNode(IsoNodeId("b"), IsoPoint(2000.0, 0.0, 0.0))
        val c = IsoNode(IsoNodeId("c"), IsoPoint(2000.0, 0.0, 1500.0))
        val sheet = IsometricSheet(IsometricSheetId("iso"), "Heating riser", nodes = listOf(a, b, c),
            pipes = listOf(IsoPipe(IsoPipeId("ab"), a.id, b.id), IsoPipe(IsoPipeId("bc"), b.id, c.id)),
            viewport = ViewportState(0.15, 850.0, -1000.0), createdAtEpochMs = 1, modifiedAtEpochMs = 1)
        val project = PlanProject(id = id, name = "Completion test", createdAtEpochMs = 1, modifiedAtEpochMs = 1,
            documentUri = Uri.fromFile(photo).toString(), mimeType = "image/png",
            pages = listOf(PageMetadata(0, 1200.0, 800.0, PageMetadata.CoordinateUnit.IMAGE_PIXEL)),
            calibration = Calibration.reference(100.0, 1.0, LengthUnit.METER),
            measurements = listOf(Measurement(MeasurementId("plan-pipe"), MeasurementType.DISTANCE,
                listOf(DocPoint(200.0, 400.0), DocPoint(1000.0, 400.0)), createdAtEpochMs = 1)),
            isometrics = listOf(sheet), activeIsometricId = sheet.id)
        runBlocking { assertTrue(repository.save(project) is ProjectResult.Ok) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag(PlanRulerTestTags.navigation("PROJECTS")).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(PlanRulerTestTags.project(id.value)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag(PlanRulerTestTags.project(id.value)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(PlanRulerTestTags.WorkspaceCanvas).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun nodeDragUndoRedoCatalogAndPersistence() {
        compose.onNodeWithContentDescription("Project menu").performClick()
        compose.onNodeWithText("Isometric").performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("isometric-canvas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("isometric-canvas").performTouchInput {
            val start = Offset(center.x - 127.5f, center.y + 150f)
            swipe(start, start + Offset((-sqrt(3.0) * 75).toFloat(), 75f), 450)
        }
        compose.waitUntil(10_000) { stored().isometrics.single().nodes.first().position.x == -1000.0 }
        compose.onNodeWithContentDescription("Undo").performClick()
        compose.waitUntil(10_000) { stored().isometrics.single().nodes.first().position.x == 0.0 }
        compose.onNodeWithContentDescription("Redo").performClick()
        compose.waitUntil(10_000) { stored().isometrics.single().nodes.first().position.x == -1000.0 }
        compose.onNodeWithTag("isometric-canvas").performTouchInput {
            // Midpoint of the extended horizontal run, away from its node hit targets.
            click(Offset(center.x + (sqrt(3.0) * 37.5 - 127.5).toFloat(), center.y + 112.5f))
        }
        compose.onNodeWithText("Pipe catalog").performScrollTo().performClick()
        compose.onNodeWithTag("pipe-catalog-search").performTextInput("DN 50")
        compose.onNodeWithText("DN50 ·", substring = true).performClick()
        compose.waitUntil(10_000) { stored().isometrics.single().pipes.first().spec.nominalDiameter == 50 }
        assertTrue(stored().isometrics.single().pipes.first().spec.material!!.contains("60.3"))
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("pipe-catalog-search").fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).apply {
            waitForIdle()
            takeScreenshot(File(context.getExternalFilesDir(null), "isometric-editor.png"))
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(PlanRulerTestTags.WorkspaceCanvas).fetchSemanticsNodes().isNotEmpty() }
        assertEquals(-1000.0, stored().isometrics.single().nodes.first().position.x, 0.0)
    }

    @Test fun photoReferenceDragUsesMagnifierAndLeavesMeasurementsIntact() {
        val before = stored().measurements
        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By a known distance").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkspaceCanvas).performTouchInput {
            swipe(Offset(width * 0.25f, height * 0.45f), Offset(width * 0.75f, height * 0.45f), 450)
        }
        compose.onNodeWithText("Known length").performTextInput("5")
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performScrollTo().performClick()
        compose.onNodeWithText("Skip check").performClick()
        compose.waitUntil(10_000) { stored().calibration?.audit?.enteredLength == 5.0 }
        assertEquals(before, stored().measurements)
        assertEquals(LengthUnit.METER, stored().calibration?.audit?.enteredUnit)
    }

    private fun stored(): PlanProject = runBlocking { (repository.load(id) as ProjectResult.Ok).value }
}

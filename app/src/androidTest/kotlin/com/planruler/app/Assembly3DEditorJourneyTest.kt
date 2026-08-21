package com.planruler.app

import android.view.WindowManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.feature.pipecalculator.PipeCalculatorTags
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobId
import com.planruler.model.InstallationJobInput
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.project.local.FileProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.assertTrue
import java.util.regex.Pattern

private const val ENGINE_TIMEOUT_MS = 10_000L

/**
 * Covers what used to be impossible from the phone: changing engine parameters, editing an
 * element that is already welded into the model, branching with a tee, routing onto a
 * non-parallel axis, and reading a typed refusal instead of losing the model.
 *
 * The 3D card is one tall list item, so controls below the fold are driven through their
 * semantics click action; the outer list can only scroll item by item.
 */
@RunWith(AndroidJUnit4::class)
class Assembly3DEditorJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before
    fun resetToEnglishProjectsScreen() {
        bringPlanRulerToForeground()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("planruler-ui-state", 0).edit().clear().commit()
        context.getSharedPreferences("planruler-settings", 0).edit()
            .clear()
            .putString("language", "ENGLISH")
            .commit()
        context.filesDir.resolve("projects").apply {
            deleteRecursively()
            mkdirs()
        }
        // The production workshop is project-owned. Seed one real persisted job so these
        // editor journeys test the same repository path as an installer returning to work.
        val projectId = ProjectId("instrumentation-project")
        val jobId = InstallationJobId("instrumentation-job")
        val now = System.currentTimeMillis()
        runBlocking {
            FileProjectRepository(context.filesDir.resolve("projects")).save(
                PlanProject(
                    id = projectId,
                    name = "Instrumentation project",
                    createdAtEpochMs = now,
                    modifiedAtEpochMs = now,
                    documentUri = "",
                    mimeType = "application/pdf",
                    pages = emptyList(),
                    installationJobs = listOf(
                        InstallationJob(
                            id = jobId,
                            name = "Instrumentation job",
                            input = InstallationJobInput(inputMode = InstallationInputMode.ADVANCED),
                            createdAtEpochMs = now,
                            modifiedAtEpochMs = now,
                        ),
                    ),
                    activeInstallationJobId = jobId,
                ),
            )
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun openInstallationWorkshop() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("WORKSHOP")).performClick()
        compose.waitForIdle()
        // After a recreate the workshop reopens on the tool it was left on.
        if (exists(PlanRulerTestTags.WorkshopRoot)) {
            compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
                .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("INSTALLATION")))
            compose.onNodeWithTag(PlanRulerTestTags.workshopTool("INSTALLATION")).performClick()
        }
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3D))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3D).assertIsDisplayed()
    }

    private fun tap(tag: String) {
        compose.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    /**
     * The add-element buttons sit in a `LazyRow`, which — unlike the outer list — only
     * composes the buttons within its own horizontal viewport. A tag lookup on one that
     * has scrolled out finds nothing, so it has to be scrolled into view first.
     */
    private fun tapAction(tag: String) {
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DActionsRow)
            .performScrollToNode(hasTestTag(tag))
        tap(tag)
    }

    private fun tapText(label: String) {
        compose.onNodeWithText(label).performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    private fun exists(tag: String): Boolean =
        compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    private fun type(tag: String, value: String) {
        compose.onNodeWithTag(tag).performTextReplacement(value)
        compose.waitForIdle()
    }

    /**
     * The engine runs off the composition thread, so `waitForIdle` alone proves nothing:
     * these helpers wait for the state the background work is supposed to publish.
     */
    private fun awaitTag(tag: String, text: String) {
        compose.waitUntil(ENGINE_TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(tag) and hasText(text, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitSummary(text: String) = awaitTag(PipeCalculatorTags.Assembly3DSummary, text)

    private fun awaitText(text: String) {
        compose.waitUntil(ENGINE_TIMEOUT_MS) {
            compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitExists(tag: String) {
        compose.waitUntil(ENGINE_TIMEOUT_MS) { exists(tag) }
    }

    private fun scrollTo3DCanvas() {
        val list = compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
        // The entire 3D workspace is one tall lazy-list item. ScrollToNode can position the
        // item, but not a descendant below its first viewport, so use the same swipes as a user.
        repeat(18) {
            val bounds = compose.onNodeWithTag(PipeCalculatorTags.Assembly3DCanvas)
                .fetchSemanticsNode().boundsInRoot
            if (bounds.width > 0f && bounds.height > 0f) return
            list.performTouchInput { swipe(bottomCenter, topCenter, 220L) }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DCanvas).assertIsDisplayed()
    }

    private fun openFieldDrawing() {
        openInstallationWorkshop()
        val list = compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
        list.performScrollToNode(hasText("Drawing") and hasClickAction())
        compose.onAllNodes(hasText("Drawing") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        list.performScrollToNode(hasTestTag(PipeCalculatorTags.AssemblyDrawing))
        awaitExists(PipeCalculatorTags.AssemblyDrawing)
        awaitExists(PipeCalculatorTags.AssemblyDrawingCanvas)
    }

    private fun scrollToDrawingControl(tag: String) {
        val list = compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
        repeat(36) {
            val bounds = compose.onAllNodesWithTag(tag).fetchSemanticsNodes().firstOrNull()?.boundsInRoot
            if (bounds != null && bounds.width > 0f && bounds.height > 0f) return
            list.performSemanticsAction(SemanticsActions.ScrollBy) { scroll ->
                scroll(0f, 420f)
            }
            compose.waitForIdle()
        }
        compose.onNodeWithTag(tag).assertIsDisplayed()
    }

    private fun saveSystemDocument() {
        val documentsUi = By.pkg(Pattern.compile("com\\.(google\\.)?android\\.documentsui"))
        check(device.wait(Until.hasObject(documentsUi), 10_000)) { "System create-document picker did not open" }
        device.findObject(By.clazz("android.widget.EditText"))?.let { filename ->
            val original = filename.text.orEmpty()
            val extensionAt = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
            filename.setText(
                original.substring(0, extensionAt) + "-${System.currentTimeMillis()}" + original.substring(extensionAt),
            )
            device.waitForIdle()
        }
        val save = device.findObjects(By.clazz("android.widget.Button"))
            .firstOrNull { it.text.equals("save", ignoreCase = true) }
            ?: device.findObject(By.res("com.google.android.documentsui", "action_menu_save"))
            ?: device.findObject(By.res("com.android.documentsui", "action_menu_save"))
            ?: device.findObject(By.text(Pattern.compile("save", Pattern.CASE_INSENSITIVE)))
            ?: device.findObject(By.descContains("Save"))
        val saveButton = checkNotNull(save) { "CreateDocument Save action not found" }
        val center = saveButton.visibleCenter
        check(device.click(center.x, center.y)) { "Could not tap the system Save button" }
        if (device.wait(Until.hasObject(By.textContains("Replace")), 2_000)) {
            device.findObject(By.textContains("Replace"))?.click()
        }
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 10_000)) {
            "System create-document picker did not return to PlanRuler"
        }
    }

    private fun awaitTaggedTextChanged(tag: String, previous: String) {
        compose.waitUntil(ENGINE_TIMEOUT_MS) {
            compose.onAllNodes(hasTestTag(tag) and hasText(previous, substring = true))
                .fetchSemanticsNodes().isEmpty()
        }
    }

    /** Chips inside a lazy row: match the clickable node, not the label inside it. */
    private fun tapChip(label: String) {
        compose.waitUntil(ENGINE_TIMEOUT_MS) {
            compose.onAllNodes(hasText(label, substring = true) and hasClickAction())
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasText(label, substring = true) and hasClickAction())
            .onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
    }

    @Test
    fun engineParametersAreEditableAndReachTheModel() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)

        awaitTag(PipeCalculatorTags.Assembly3DEffectiveRadius, "76.0")

        tapText("1D")
        awaitTag(PipeCalculatorTags.Assembly3DEffectiveRadius, "50.0")

        type(PipeCalculatorTags.Assembly3DWeldGap, "4")
        tapText("Draft")
        awaitSummary("7 parts")
    }

    @Test
    fun aRefusedParameterExplainsTheRuleItBroke() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)

        type(PipeCalculatorTags.Assembly3DWeldGap, "40")

        awaitExists(PipeCalculatorTags.Assembly3DMessage)
        awaitText("weldGapMm")
    }

    @Test
    fun anElementAlreadyInTheModelCanBeEditedAndRemoved() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        awaitSummary("2 parts")

        type(PipeCalculatorTags.Assembly3DInspectorValue, "450")
        tap(PipeCalculatorTags.Assembly3DInspectorApply)

        awaitText("L 450.0 mm")

        tap(PipeCalculatorTags.Assembly3DInspectorRemove)
        awaitSummary("1 part")
    }

    @Test
    fun aTightCutIsRefusedWithAReadableMessage() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        type(PipeCalculatorTags.Assembly3DPipeLength, "1")
        tap(PipeCalculatorTags.Assembly3DAddPipe)

        awaitExists(PipeCalculatorTags.Assembly3DMessage)
        awaitSummary("1 part")
    }

    @Test
    fun theElbowLimitIsNoLongerFixedAtFive() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)
        tapText("12")

        tap(PipeCalculatorTags.Assembly3DManualMode)
        repeat(8) {
            tap(PipeCalculatorTags.Assembly3DAddPipe)
            tap(PipeCalculatorTags.Assembly3DAddElbow)
        }

        awaitSummary("17 parts")
    }

    @Test
    fun theSolverClosesANonParallelTargetAndRefusesAnImpossibleOne() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DAutoMode)

        type(PipeCalculatorTags.Assembly3DTargetX, "2000")
        type(PipeCalculatorTags.Assembly3DTargetY, "1500")
        type(PipeCalculatorTags.Assembly3DTargetZ, "600")
        type(PipeCalculatorTags.Assembly3DDirectionX, "0")
        type(PipeCalculatorTags.Assembly3DDirectionY, "1")
        tap(PipeCalculatorTags.Assembly3DSolve)

        awaitExists(PipeCalculatorTags.Assembly3DSolverResult)
        awaitText("Turn with offset")

        type(PipeCalculatorTags.Assembly3DTargetX, "-500")
        type(PipeCalculatorTags.Assembly3DDirectionX, "1")
        type(PipeCalculatorTags.Assembly3DDirectionY, "0")
        tap(PipeCalculatorTags.Assembly3DSolve)

        awaitExists(PipeCalculatorTags.Assembly3DMessage)
    }

    @Test
    fun aSolvedRouteCanBeHandedToTheEditor() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DAutoMode)
        tap(PipeCalculatorTags.Assembly3DSolve)
        awaitExists(PipeCalculatorTags.Assembly3DSolverResult)

        tap(PipeCalculatorTags.Assembly3DSendToManual)

        awaitSummary("7 parts")
        awaitExists(PipeCalculatorTags.Assembly3DInspector)
    }

    @Test
    fun theViewportOrbitsZoomsAndPansInAllThreeAxes() {
        openInstallationWorkshop()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DCanvas))
        val canvas = compose.onNodeWithTag(PipeCalculatorTags.Assembly3DCanvas)

        canvas.performTouchInput { swipe(centerLeft, centerRight, 300L) }
        compose.waitForIdle()
        canvas.performTouchInput { swipe(topCenter, bottomCenter, 300L) }
        compose.waitForIdle()
        canvas.performTouchInput {
            pinch(
                start0 = center - Offset(60f, 0f),
                end0 = center - Offset(150f, 0f),
                start1 = center + Offset(60f, 0f),
                end1 = center + Offset(150f, 0f),
            )
        }
        compose.waitForIdle()

        listOf("Top", "Right", "Front", "Isometric").forEach(::tapText)

        canvas.assertExists()
        awaitSummary("7 parts")
    }

    @Test
    fun theStartOfTheRunCanBeMovedAwayFromTheOrigin() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)

        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DStartFrame).assertExists()
        tapText("Set start")

        awaitSummary("2 parts")
    }

    /**
     * Guards the lost-update hazard. Every tap dispatches engine work onto a background
     * dispatcher, so a burst of taps races: before the editor mutations were serialised, a
     * tap that arrived while the previous one was still in flight built on the stale state
     * and silently dropped the earlier edit. Nothing here waits between taps on purpose.
     */
    @Test
    fun aBurstOfEditsAllLands() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)
        tapText("12")
        tap(PipeCalculatorTags.Assembly3DManualMode)

        repeat(6) {
            tap(PipeCalculatorTags.Assembly3DAddPipe)
            tap(PipeCalculatorTags.Assembly3DAddElbow)
        }
        awaitSummary("13 parts")

        repeat(4) { tap(PipeCalculatorTags.Assembly3DUndo) }
        awaitSummary("9 parts")

        repeat(4) { tap(PipeCalculatorTags.Assembly3DRedo) }
        awaitSummary("13 parts")
    }

    /**
     * The complaint this answers: the workshop opened on a fixed two-elbow, two-flange
     * spool with no way off it. Opening it in the editor turns every element into a
     * parameter.
     */
    @Test
    fun theVerifiedSpoolOpensInTheEditorAndBecomesChangeable() {
        openInstallationWorkshop()

        awaitExists(PipeCalculatorTags.Assembly3DEditVerified)
        tap(PipeCalculatorTags.Assembly3DEditVerified)

        awaitSummary("7 parts")
        awaitExists(PipeCalculatorTags.Assembly3DInspector)

        // The last element of the reopened spool is its closing flange; retune the run instead.
        type(PipeCalculatorTags.Assembly3DPipeLength, "275")
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        awaitExists(PipeCalculatorTags.Assembly3DMessage)

        tap(PipeCalculatorTags.Assembly3DUndo)
        awaitSummary("7 parts")
    }

    @Test
    fun aFlangeCanBeGivenItsOwnDimensions() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)

        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "165.0")

        type(PipeCalculatorTags.Assembly3DFlangeOutside, "200")
        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "200.0")

        type(PipeCalculatorTags.Assembly3DFlangeBoltCount, "8")
        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "8 ×")
    }

    @Test
    fun aFlangeIsPickedFromTheCatalogList() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)

        awaitExists(PipeCalculatorTags.Assembly3DFlangeCatalog)
        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "165.0")

        // DN 50 PN 6 is the one class with a smaller disc, so the pick is visible.
        tapChip("PN 6")
        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "140.0")

        tapChip("Catalog flange")
        awaitTag(PipeCalculatorTags.Assembly3DFlangeSummary, "165.0")
    }

    @Test
    fun anImpossibleFlangeIsRefusedByRule() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DParametersMode)

        type(PipeCalculatorTags.Assembly3DFlangeOutside, "20")

        awaitExists(PipeCalculatorTags.Assembly3DMessage)
        awaitText("flangeOutsideDiameterMm")
    }

    @Test
    fun aTeeBranchIsBuiltAndTunedFromThePhone() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        tap(PipeCalculatorTags.Assembly3DAddTee)
        awaitSummary("3 parts")

        // Switching the active chain to the tee sends the next element down the branch.
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DBranch).assertExists()
        tapText("T1")
        type(PipeCalculatorTags.Assembly3DPipeLength, "180")
        tap(PipeCalculatorTags.Assembly3DAddPipe)

        awaitSummary("4 parts")
        awaitText("L 180.0 mm")

        type(PipeCalculatorTags.Assembly3DInspectorValue, "420")
        tap(PipeCalculatorTags.Assembly3DInspectorApply)
        awaitText("L 420.0 mm")

        // The main run stays selectable and keeps taking its own elements.
        tapText("Main run")
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        awaitSummary("5 parts")
    }

    @Test
    fun theSolverTurnsTheRunBackOnItself() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DAutoMode)

        type(PipeCalculatorTags.Assembly3DTargetX, "1400")
        type(PipeCalculatorTags.Assembly3DTargetY, "900")
        type(PipeCalculatorTags.Assembly3DTargetZ, "0")
        type(PipeCalculatorTags.Assembly3DDirectionX, "-1")
        tap(PipeCalculatorTags.Assembly3DSolve)

        awaitExists(PipeCalculatorTags.Assembly3DSolverResult)
        awaitText("U-turn")
        awaitSummary("7 parts")
    }

    @Test
    fun aReducerNecksTheBoreAndAnElbowAfterItIsRefused() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        awaitSummary("2 parts")

        // DN 50 has one catalog reduction (to DN 40); the chip fills the small-end fields.
        tapChip("Catalog reducer")
        tapAction(PipeCalculatorTags.Assembly3DAddReducer)
        awaitSummary("3 parts")

        tapAction(PipeCalculatorTags.Assembly3DAddCap)
        awaitSummary("4 parts")

        tap(PipeCalculatorTags.Assembly3DInspectorRemove)
        awaitSummary("3 parts")

        // An elbow is always built at the catalog diameter, so one placed after the
        // reducer must be refused rather than silently welded on at the wrong size.
        tapAction(PipeCalculatorTags.Assembly3DAddElbow)
        awaitExists(PipeCalculatorTags.Assembly3DMessage)
        awaitSummary("3 parts")
    }

    @Test
    fun theChainSurvivesAnActivityRecreate() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        tap(PipeCalculatorTags.Assembly3DAddElbow)
        awaitSummary("3 parts")

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        awaitSummary("3 parts")
    }

    @Test
    fun pipeLengthIsDraggedOnTheModelAndOneUndoRestoresIt() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        awaitSummary("2 parts")
        scrollTo3DCanvas()

        awaitExists(PipeCalculatorTags.Assembly3DLengthHandle)
        awaitTag(PipeCalculatorTags.Assembly3DDirectValue, "L 300.0 mm")
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DLengthHandle).performTouchInput {
            swipe(center, center + Offset(120f, 0f), 700L)
        }
        awaitTaggedTextChanged(PipeCalculatorTags.Assembly3DDirectValue, "L 300.0 mm")

        tap(PipeCalculatorTags.Assembly3DUndo)
        awaitTag(PipeCalculatorTags.Assembly3DDirectValue, "L 300.0 mm")
    }

    @Test
    fun elbowAngleAndRollHaveIndependentDirectHandles() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        tap(PipeCalculatorTags.Assembly3DAddPipe)
        tap(PipeCalculatorTags.Assembly3DAddElbow)
        awaitSummary("3 parts")
        scrollTo3DCanvas()

        awaitExists(PipeCalculatorTags.Assembly3DAngleHandle)
        awaitExists(PipeCalculatorTags.Assembly3DRollHandle)
        awaitTag(PipeCalculatorTags.Assembly3DDirectValue, "∠ 45.0°")
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DAngleHandle).performTouchInput {
            swipe(center, center + Offset(100f, 35f), 700L)
        }
        awaitTaggedTextChanged(PipeCalculatorTags.Assembly3DDirectValue, "∠ 45.0°")

        tap(PipeCalculatorTags.Assembly3DUndo)
        awaitTag(PipeCalculatorTags.Assembly3DDirectValue, "∠ 45.0°")
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DRollHandle).performTouchInput {
            swipe(center, center + Offset(0f, 110f), 700L)
        }
        awaitTaggedTextChanged(PipeCalculatorTags.Assembly3DDirectValue, "↻ 0.0°")
    }

    @Test
    fun anOpenEndCanBeExtendedAndSelectedPartRemovedInsideTheScene() {
        openInstallationWorkshop()
        tap(PipeCalculatorTags.Assembly3DManualMode)
        scrollTo3DCanvas()

        tap(PipeCalculatorTags.Assembly3DSceneAdd)
        awaitSummary("2 parts")
        tap(PipeCalculatorTags.Assembly3DSceneRemove)
        awaitSummary("1 part")
    }

    @Test
    fun fieldDrawingIsCheckedAndExportsRealPngCsvAndCompletePack() {
        openFieldDrawing()

        scrollToDrawingControl(PipeCalculatorTags.FieldSunlight)
        tap(PipeCalculatorTags.FieldSunlight)
        scrollToDrawingControl(PipeCalculatorTags.FieldGloves)
        tap(PipeCalculatorTags.FieldGloves)
        scrollToDrawingControl(PipeCalculatorTags.FieldKeepAwake)
        tap(PipeCalculatorTags.FieldKeepAwake)
        compose.activityRule.scenario.onActivity { activity ->
            assertTrue(
                "Field drawing must keep the screen awake when requested",
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0,
            )
        }

        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingCheckedBy)
        type(PipeCalculatorTags.AssemblyDrawingCheckedBy, "Site foreman")
        tap(PipeCalculatorTags.AssemblyDrawingCheck)
        awaitTag(PipeCalculatorTags.AssemblyDrawingChecked, "CHECKED: Site foreman")

        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingImage)
        tap(PipeCalculatorTags.AssemblyDrawingImage)
        saveSystemDocument()
        awaitText("Image saved")

        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingCsv)
        tap(PipeCalculatorTags.AssemblyDrawingCsv)
        saveSystemDocument()
        awaitText("CSV saved")

        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingFieldPack)
        tap(PipeCalculatorTags.AssemblyDrawingFieldPack)
        saveSystemDocument()
        awaitText("Field pack saved")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val stored = runBlocking {
            (FileProjectRepository(context.filesDir.resolve("projects")).load(ProjectId("instrumentation-project"))
                as com.planruler.project.api.ProjectResult.Ok).value
        }.installationJobs.single()
        assertTrue(stored.checkedBy == "Site foreman" && stored.checkedAtEpochMs != null)
    }

    @Test
    fun installerPartCardKeepsDrawingCutAndMaterialSelectionLinked() {
        openFieldDrawing()

        val pipeCard = "${PipeCalculatorTags.AssemblyDrawingPartPrefix}P1"
        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingPartsRow)
        compose.onNodeWithTag(PipeCalculatorTags.AssemblyDrawingPartsRow)
            .performScrollToNode(hasTestTag(pipeCard))
        tap(pipeCard)

        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingSelectedPart)
        awaitTag(PipeCalculatorTags.AssemblyDrawingSelectedPart, "P1")
        awaitTag(PipeCalculatorTags.AssemblyDrawingSelectedPart, "CUT ")
        awaitTag(PipeCalculatorTags.AssemblyDrawingSelectedPart, "Selected in drawing, list and 3D")
        scrollToDrawingControl(PipeCalculatorTags.AssemblyDrawingMaterialsSummary)
        awaitTag(PipeCalculatorTags.AssemblyDrawingMaterialsSummary, "Pipe:")

        val list = compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
        list.performScrollToNode(hasText("3D model") and hasClickAction())
        compose.onAllNodes(hasText("3D model") and hasClickAction()).onFirst().performClick()
        compose.waitForIdle()
        list.performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DSelection))
        awaitText("Selected: P1")
    }
}

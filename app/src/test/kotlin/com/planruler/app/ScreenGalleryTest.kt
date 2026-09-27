package com.planruler.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.feature.pipecalculator.PipeCalculatorTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNG on the JVM so the design can be reviewed without a
 * device. CI publishes build/outputs/roborazzi next to the test APK.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h851dp-xxhdpi")
class ScreenGalleryTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun russianCleanState() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("planruler-ui-state", 0).edit().clear().commit()
        context.getSharedPreferences("planruler-settings", 0).edit()
            .clear()
            .putString("language", "RUSSIAN")
            .putStringSet("coach_seen", setOf("coach-zoom", "coach-calibration"))
            .commit()
        context.filesDir.resolve("projects").deleteRecursively()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun home() {
        shot("01-home")
    }

    @Test
    fun projects() {
        tab("PROJECTS")
        shot("02-projects")
    }

    @Test
    fun workshop() {
        tab("WORKSHOP")
        shot("03-workshop")
    }

    @Test
    fun pipeTables() {
        tab("WORKSHOP")
        tool("CATALOG")
        shot("04-pipe-tables")
    }

    @Test
    fun hydraulics() {
        tab("WORKSHOP")
        tool("HYDRAULICS")
        shot("05-hydraulics")
    }

    @Test
    fun installation3d() {
        tab("WORKSHOP")
        tool("INSTALLATION")
        shot("06-installation")
        compose.onNode(hasText("Создать проект мастерской")).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(PipeCalculatorTags.InstallationList)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        shot("07-installation-job")
        compose.onNode(hasTestTag(PipeCalculatorTags.InstallationList))
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DCanvas))
        compose.waitForIdle()
        shot("08-3d")
    }

    @Test
    fun blankDrawing() {
        compose.onNode(hasTestTag(PlanRulerTestTags.HomeRoot))
            .performScrollToNode(hasTestTag(PlanRulerTestTags.NewDrawing))
        compose.onNode(hasTestTag(PlanRulerTestTags.NewDrawing)).performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag(PlanRulerTestTags.WorkspaceCanvas)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitForIdle()
        shot("09-workspace")
    }

    @Test
    fun menu() {
        tab("MENU")
        shot("10-menu")
    }

    private fun tab(name: String) {
        compose.onNode(hasTestTag(PlanRulerTestTags.navigation(name)), useUnmergedTree = true).performClick()
        compose.waitForIdle()
    }

    private fun tool(name: String) {
        compose.onNode(hasTestTag(PlanRulerTestTags.WorkshopRoot))
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool(name)))
        compose.onNode(hasTestTag(PlanRulerTestTags.workshopTool(name))).performClick()
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }
}

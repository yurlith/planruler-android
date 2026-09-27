package com.planruler.app

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
        context.filesDir.resolve("projects").apply { deleteRecursively(); mkdirs() }
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
        settle {
            compose.onAllNodes(hasText("Создать проект мастерской")).fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodes(hasTestTag(PipeCalculatorTags.Assembly3DCanvas)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("07-installation-job")
        runCatching {
            compose.onNode(hasTestTag(PipeCalculatorTags.InstallationList))
                .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DSummary))
        }
        settle { true }
        shot("08-3d")
        runCatching {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Во весь экран"))
                .onFirst().performClick()
        }
        settle { true }
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onLast()
            .captureRoboImage("build/outputs/roborazzi/08b-3d-workbench.png")
    }

    @Test
    fun blankDrawing() {
        compose.onNode(hasTestTag(PlanRulerTestTags.HomeRoot))
            .performScrollToNode(hasTestTag(PlanRulerTestTags.NewDrawing))
        compose.onNode(hasTestTag(PlanRulerTestTags.NewDrawing)).performClick()
        settle {
            compose.onAllNodes(hasTestTag(PlanRulerTestTags.WorkspaceCanvas)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("09-workspace")
    }

    /**
     * Waits for a condition but still lets the screenshot show whatever state was reached.
     * Infinite progress animations keep Compose busy, so the main looper is drained by hand
     * to deliver results of background work.
     */
    private fun settle(condition: () -> Boolean) {
        val looper = org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper())
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            looper.idle()
            compose.mainClock.advanceTimeBy(32)
            if (runCatching(condition).getOrDefault(false)) break
        }
        repeat(10) {
            Thread.sleep(50)
            looper.idle()
            compose.mainClock.advanceTimeBy(32)
        }
    }

    @Test
    fun homeDark() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("planruler-settings", 0).edit().putString("theme", "DARK").commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        shot("11-home-dark")
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

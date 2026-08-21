package com.planruler.app

import android.graphics.Bitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.feature.pipecalculator.PipeCalculatorTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders the 3D workshop under each theme and checks the viewport actually adopts it.
 * A scene that ignores the theme is the defect this guards: the sunlight theme exists so
 * the model stays readable outdoors, and a hardcoded dark viewport silently defeated it.
 *
 * Each run also writes a PNG so the result can be looked at, not only asserted.
 */
@RunWith(AndroidJUnit4::class)
class SceneThemeScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun applyTheme(theme: String) {
        context.getSharedPreferences("planruler-ui-state", 0).edit().clear().commit()
        context.getSharedPreferences("planruler-settings", 0).edit()
            .clear()
            .putString("language", "ENGLISH")
            .putString("theme", theme)
            .commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    private fun openViewport() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("WORKSHOP")).performClick()
        compose.waitForIdle()
        if (compose.onAllNodesWithTag(PlanRulerTestTags.WorkshopRoot).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
                .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("INSTALLATION")))
            compose.onNodeWithTag(PlanRulerTestTags.workshopTool("INSTALLATION")).performClick()
        }
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DCanvas))
        // After an activity recreate the composition can settle before the new window has
        // been laid out, and capturing then asks for a zero-sized bitmap. Waiting for real
        // bounds is what makes the capture below reliable.
        compose.waitUntil(10_000L) {
            compose.onAllNodesWithTag(PipeCalculatorTags.Assembly3DCanvas)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.let { it.size.width > 0 && it.size.height > 0 } == true
        }
        // The viewport is a tall item: scrolling to it only brings it into the list, and it
        // can still sit below the window, where the capture region is empty. Keep scrolling
        // until it actually occupies screen area.
        repeat(10) {
            if (canvasIsOnScreen()) return@repeat
            compose.onNodeWithTag(PipeCalculatorTags.InstallationList).performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        compose.waitForIdle()
    }

    private fun canvasIsOnScreen(): Boolean =
        compose.onAllNodesWithTag(PipeCalculatorTags.Assembly3DCanvas)
            .fetchSemanticsNodes()
            .firstOrNull()
            ?.boundsInWindow
            ?.let { it.width > 1f && it.height > 1f } == true

    private fun captureViewport(name: String): Bitmap {
        val bitmap = compose.onNodeWithTag(PipeCalculatorTags.Assembly3DCanvas)
            .captureToImage()
            .asAndroidBitmap()
        val directory = File(context.getExternalFilesDir(null), "scene-themes").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        return bitmap
    }

    /** Mean luminance of the drawn scene, 0 for black and 1 for white. */
    private fun Bitmap.meanLuminance(): Double {
        var total = 0.0
        var samples = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val pixel = getPixel(x, y)
                val r = (pixel shr 16 and 0xFF) / 255.0
                val g = (pixel shr 8 and 0xFF) / 255.0
                val b = (pixel and 0xFF) / 255.0
                total += 0.2126 * r + 0.7152 * g + 0.0722 * b
                samples++
                x += 4
            }
            y += 4
        }
        return if (samples == 0) 0.0 else total / samples
    }

    @Test
    fun theViewportAdoptsEveryTheme() {
        applyTheme("DARK")
        openViewport()
        val dark = captureViewport("dark").meanLuminance()

        applyTheme("LIGHT")
        openViewport()
        val light = captureViewport("light").meanLuminance()

        applyTheme("SUNLIGHT")
        openViewport()
        val sunlight = captureViewport("sunlight").meanLuminance()

        applyTheme("BLUEPRINT")
        openViewport()
        val blueprint = captureViewport("blueprint").meanLuminance()

        applyTheme("HIGH_CONTRAST")
        openViewport()
        val highContrast = captureViewport("high-contrast").meanLuminance()

        assertTrue("The dark scene is not dark: $dark", dark < 0.30)
        assertTrue("The light scene is not light: $light", light > 0.55)
        assertTrue("The sunlight scene must be brighter than light: $sunlight", sunlight > light)
        assertTrue("The blueprint scene is not a dark ground: $blueprint", blueprint < 0.35)
        assertTrue("The high contrast scene is not near black: $highContrast", highContrast < 0.22)
        assertTrue(
            "Light and dark rendered the same scene, so the theme is being ignored",
            light - dark > 0.3,
        )
    }
}

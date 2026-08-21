package com.planruler.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.planruler.feature.pipecalculator.PipeCalculatorTags
import com.planruler.feature.pipecalculator.HeatCalcTags
import com.planruler.designsystem.PlanRulerTestTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipeCalculatorJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    /** The 3D engine publishes from a background dispatcher, so the summary is awaited. */
    private fun awaitSummary(text: String) {
        compose.waitUntil(10_000L) {
            compose.onAllNodes(
                hasTestTag(PipeCalculatorTags.Assembly3DSummary) and hasText(text, substring = true),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

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
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun nativeCalculatorRunsHydraulicsCatalogExpansionAndGuardsSwissGas() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("WORKSHOP")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("HYDRAULICS")).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.Root).assertIsDisplayed()
        compose.onNodeWithText("Circuit calculation").assertIsDisplayed()
        compose.onNodeWithText("DOWFROST").performClick()

        compose.onNodeWithTag(PipeCalculatorTags.HydraulicsList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.CalculateHydraulics))
        compose.onNodeWithTag(PipeCalculatorTags.CalculateHydraulics).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.HydraulicsList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.HydraulicResults))
        compose.onNodeWithTag(PipeCalculatorTags.HydraulicResults).assertIsDisplayed()
        compose.onNodeWithText("Total pressure loss").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back to workshop").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("HEATING")))
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("HEATING")).performClick()
        compose.onNodeWithTag(HeatCalcTags.DesignList)
            .performScrollToNode(hasTestTag(HeatCalcTags.CalculateDesign))
        compose.onNodeWithTag(HeatCalcTags.CalculateDesign).performClick()
        compose.onNodeWithTag(HeatCalcTags.DesignList)
            .performScrollToNode(hasTestTag(HeatCalcTags.DesignResults))
        compose.onNodeWithTag(HeatCalcTags.DesignResults).assertIsDisplayed()
        compose.onNodeWithText("Design heat loss").assertIsDisplayed()
        compose.onNodeWithText("Heat-pump target").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back to workshop").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("INSTALLATION")))
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("INSTALLATION")).performClick()
        compose.onNodeWithText("Parameters").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.CalculateOffsetAssembly))
        compose.onNodeWithTag(PipeCalculatorTags.CalculateOffsetAssembly).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasText("3D model"))
        compose.onNodeWithText("3D model").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3D))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3D).assertIsDisplayed()
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DCanvas).performTouchInput {
            swipe(centerLeft, centerRight, 350L)
        }
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DSelection))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DSelection).fetchSemanticsNode()
        awaitSummary("7 parts")

        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DManualMode))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DManualMode).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DAddPipe))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DAddPipe).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DAddElbow).performClick()
        awaitSummary("3 parts")
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DUndo))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DUndo)
            .performSemanticsAction(SemanticsActions.OnClick)
        awaitSummary("2 parts")
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DRedo))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DRedo)
            .performSemanticsAction(SemanticsActions.OnClick)
        awaitSummary("3 parts")

        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DAutoMode))
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DAutoMode).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.Assembly3DSolve))
        // The solver controls sit below the fold of the single tall card, so drive the action.
        compose.onNodeWithTag(PipeCalculatorTags.Assembly3DSolve)
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitUntil(10_000L) { compose.onAllNodesWithTag(PipeCalculatorTags.Assembly3DSolverResult).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Route closed — fabrication cuts").fetchSemanticsNode()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasText("Drawing"))
        compose.onNodeWithText("Drawing").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.OffsetDiagram))
        compose.onNodeWithTag(PipeCalculatorTags.OffsetDiagram).assertIsDisplayed()
        compose.onNodeWithText("CUT PIPE: C = 640.1 mm").fetchSemanticsNode()
        compose.onNodeWithText(
            "1 — elbow 1 inlet; F1 — its weld face; 2–3 — pipe C to cut; F2 — elbow 2 weld face; 4 — elbow 2 outlet.",
        ).fetchSemanticsNode()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.WorkshopFlange))
        compose.onNodeWithTag(PipeCalculatorTags.WorkshopFlange).assertIsDisplayed()
        compose.onNodeWithText("Flange and bolt pattern").assertIsDisplayed()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasText("Cut list"))
        compose.onNodeWithText("Cut list").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.OffsetAssemblyResults))
        compose.onNodeWithTag(PipeCalculatorTags.OffsetAssemblyResults).assertIsDisplayed()
        compose.onNodeWithText("Between weld faces F").assertIsDisplayed()
        compose.onNodeWithText("Insert cut length C").assertIsDisplayed()
        compose.onNodeWithTag(PipeCalculatorTags.InstallationList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.WorkshopStockPlan))
        compose.onNodeWithTag(PipeCalculatorTags.WorkshopStockPlan).assertIsDisplayed()
        compose.onNodeWithText("Stock cutting chart").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back to workshop").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("CATALOG")))
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("CATALOG")).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.CatalogList).assertIsDisplayed()
        compose.onNodeWithText("DN 15 · Ø 21.3 × 2.0 mm").assertIsDisplayed()
        compose.onNodeWithTag(PipeCalculatorTags.CatalogSections)
            .performScrollToNode(hasText("Elbows"))
        compose.onNodeWithText("Elbows").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.ElbowAnimation).assertIsDisplayed()
        compose.onNodeWithTag(PipeCalculatorTags.CatalogSections)
            .performScrollToNode(hasText("Flanges"))
        compose.onNodeWithText("Flanges").performClick()
        compose.onNodeWithTag(PipeCalculatorTags.FlangeAnimation).assertIsDisplayed()

        compose.onNodeWithContentDescription("Back to workshop").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("EXPANSION")))
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("EXPANSION")).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.ExpansionList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.CalculateExpansion))
        compose.onNodeWithTag(PipeCalculatorTags.CalculateExpansion).performClick()
        compose.onNodeWithTag(PipeCalculatorTags.ExpansionList)
            .performScrollToNode(hasTestTag(PipeCalculatorTags.ExpansionResults))
        compose.onNodeWithTag(PipeCalculatorTags.ExpansionResults).assertIsDisplayed()
        compose.onNodeWithText("Minimum nominal volume").assertIsDisplayed()

        compose.onNodeWithContentDescription("Back to workshop").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot)
            .performScrollToNode(hasTestTag(PlanRulerTestTags.workshopTool("GAS_CH")))
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("GAS_CH")).performClick()
        compose.onNodeWithText("Calculation locked").assertIsDisplayed()
        compose.onNodeWithText("SVGW G1:2026 · H-Gas · ≤ 5 bar").assertIsDisplayed()
    }
}

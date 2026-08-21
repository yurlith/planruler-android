package com.planruler.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.planruler.feature.crm.CrmTags
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class CrmJourneyTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun englishUi() {
        bringPlanRulerToForeground()
        InstrumentationRegistry.getInstrumentation().targetContext
            .getSharedPreferences("planruler-settings", 0)
            .edit().putString("language", "ENGLISH").commit()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun localAccountClientAndWorkOrderSurviveLockUnlock() {
        val suffix = UUID.randomUUID().toString().take(6)
        val profileName = "Local owner $suffix"
        val clientName = "Client $suffix"
        val jobName = "Heating survey $suffix"

        compose.onNodeWithText("CRM").performClick()
        compose.onNodeWithTag(CrmTags.CreateProfile).performClick()
        compose.onNodeWithTag(CrmTags.ProfileName).performTextInput(profileName)
        compose.onNodeWithTag(CrmTags.ProfileCompany).performTextInput("PlanRuler Test")
        compose.onNodeWithTag(CrmTags.Pin).performTextInput("2468")
        compose.onNodeWithTag(CrmTags.ConfirmProfile).performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag(CrmTags.Workspace), 5_000)

        compose.onNodeWithTag(CrmTags.AddClient).performClick()
        compose.onNodeWithTag(CrmTags.ClientName).performTextInput(clientName)
        compose.onNodeWithTag(CrmTags.ConfirmClient).performClick()
        compose.onNodeWithText(clientName).assertIsDisplayed()

        compose.onNodeWithTag(CrmTags.AddWorkOrder).performClick()
        compose.onNodeWithTag(CrmTags.WorkOrderTitle).performTextInput(jobName)
        compose.onNodeWithTag(CrmTags.ConfirmWorkOrder).performClick()
        compose.onNodeWithText(jobName).assertIsDisplayed()

        compose.onNodeWithTag(CrmTags.Lock).performClick()
        compose.onNodeWithText(profileName).performClick()
        compose.onNodeWithTag(CrmTags.Pin).performTextInput("2468")
        compose.onNodeWithText("Unlock").performClick()
        compose.waitUntilAtLeastOneExists(hasTestTag(CrmTags.Workspace), 5_000)
        compose.onNodeWithText(jobName).assertIsDisplayed()
    }
}

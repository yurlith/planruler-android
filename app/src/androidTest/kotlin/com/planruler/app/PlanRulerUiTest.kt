package com.planruler.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.Manifest
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern
import android.net.Uri
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.document.android.AndroidDocumentGateway
import com.planruler.document.api.DocumentResult
import kotlinx.coroutines.runBlocking

@RunWith(AndroidJUnit4::class)
class PlanRulerUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetUiState() {
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
    fun projectsScreenExposesAccessibleImportAction() {
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.ProjectsFab).assertIsDisplayed()
    }

    @Test
    fun activityRecreationRestoresProjectsDestination() {
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.ProjectsFab).assertIsDisplayed()
    }

    @Test
    fun redesignedShellExposesFiveStableDestinationsAndDedicatedMenu() {
        listOf("HOME", "PROJECTS", "WORKSHOP", "CRM", "MENU").forEach { destination ->
            compose.onNodeWithTag(PlanRulerTestTags.navigation(destination)).assertIsDisplayed()
        }
        compose.onNodeWithTag(PlanRulerTestTags.navigation("MENU")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuRoot).assertIsDisplayed()
        compose.onNodeWithText("Work settings").assertIsDisplayed()
        compose.onNodeWithText("Data and backup").assertIsDisplayed()
    }

    @Test
    fun workshopUsesVisualToolHubInsteadOfPrimaryTabStrip() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("WORKSHOP")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkshopRoot).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("INSTALLATION")).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.workshopTool("HYDRAULICS")).assertIsDisplayed()
    }

    @Test
    fun languageSelectionPersistsAcrossRecreation() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("MENU")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettingsEntry).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettings).assertIsDisplayed()
        compose.onNodeWithTag(com.planruler.feature.settings.SettingsListTag)
            .performScrollToNode(androidx.compose.ui.test.hasText("Русский"))
        compose.onNodeWithText("Русский").performClick()
        compose.onNodeWithText("Мастерская").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
    }

    @Test
    fun polishSelectionTranslatesProjectsAndPersists() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("MENU")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettingsEntry).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettings).assertIsDisplayed()
        compose.onNodeWithTag(com.planruler.feature.settings.SettingsListTag)
            .performScrollToNode(androidx.compose.ui.test.hasText("Polski"))
        compose.onNodeWithText("Polski").performClick()
        compose.onNodeWithText("Warsztat").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
    }

    @Test
    fun germanSelectionTranslatesProjectsAndPersists() {
        compose.onNodeWithTag(PlanRulerTestTags.navigation("MENU")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettingsEntry).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.MenuSettings).assertIsDisplayed()
        compose.onNodeWithTag(com.planruler.feature.settings.SettingsListTag)
            .performScrollToNode(androidx.compose.ui.test.hasText("Deutsch"))
        compose.onNodeWithText("Deutsch").performClick()
        compose.onNodeWithText("Werkstatt").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
    }

    @Test
    fun systemSafPickerImportsMeasuresAndExportsJson() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val png = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply {
                eraseColor(android.graphics.Color.rgb(240, 245, 250))
            }.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        val fileName = "pr-${System.currentTimeMillis()}.png"
        val seededUri = seedDownload(fileName, "image/png", png)
        val directOpen = runBlocking {
            AndroidDocumentGateway(InstrumentationRegistry.getInstrumentation().targetContext)
                .open(seededUri.toString())
        }
        check(directOpen is DocumentResult.Ok) { "Seeded image is not readable: $directOpen" }

        compose.onNodeWithTag(PlanRulerTestTags.ProjectsFab).performClick()
        val testFile = By.textStartsWith(fileName.substringBefore('.'))
        val testCard = By.descStartsWith(fileName)
        device.wait(Until.hasObject(testCard), 10_000)
        var file = device.findObject(testCard) ?: device.findObject(testFile)
        if (file == null) {
            device.findObject(By.descContains("Show roots"))?.click()
            device.wait(Until.hasObject(By.text("Downloads")), 3_000)
            device.findObject(By.text("Downloads"))?.click()
            device.wait(Until.hasObject(testCard), 10_000)
            file = device.findObject(testCard) ?: device.findObject(testFile)
        }
        if (file == null) {
            val search = device.findObject(By.res("com.google.android.documentsui", "action_menu_search"))
                ?: device.findObject(By.res("com.android.documentsui", "action_menu_search"))
                ?: device.findObject(By.descContains("Search"))
            search?.click()
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
            device.findObject(By.clazz("android.widget.EditText"))
                ?.setText(fileName.substringBefore('.'))
            device.waitForIdle()
            device.wait(Until.hasObject(testCard), 10_000)
            file = device.findObject(testCard) ?: device.findObject(testFile)
        }
        checkNotNull(file) { "Test image was not visible in the system document picker" }.click()
        if (!device.wait(Until.hasObject(By.pkg("com.planruler.app")), 3_000)) {
            device.findObject(By.text("Open"))?.click()
        }
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 5_000)) {
            "System picker did not return the selected document to PlanRuler"
        }
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(5_000) {
            targetContext.getSharedPreferences("planruler-ui-state", 0)
                .getString("import_uri", null) != null
        }
        val selectedUri = targetContext.getSharedPreferences("planruler-ui-state", 0)
            .getString("import_uri", null)
        val selectedDirectOpen = runBlocking {
            AndroidDocumentGateway(targetContext).open(requireNotNull(selectedUri))
        }
        check(selectedDirectOpen is DocumentResult.Ok) {
            "URI returned by system picker is not readable: $selectedDirectOpen"
        }

        val canvasReady = runCatching {
            compose.waitUntil(15_000) {
                compose.onAllNodes(
                    androidx.compose.ui.test.hasTestTag(PlanRulerTestTags.WorkspaceCanvas),
                ).fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        check(canvasReady) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val prefs = context.getSharedPreferences("planruler-ui-state", 0)
            "Imported document did not reach the workspace. project_id=${prefs.getString("project_id", null)} " +
                "import_uri=${prefs.getString("import_uri", null)} " +
                "files=${context.filesDir.resolve("projects").listFiles()?.map(File::getName)} " +
                "UI: ${compose.onRoot().printToString()}"
        }

        // A counter needs no calibration, so the measuring path is exercised on its own.
        compose.onNodeWithTag(PlanRulerTestTags.tool("COUNTER")).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.WorkspaceCanvas).performTouchInput { click(center) }
        compose.waitForIdle()
        compose.onNodeWithTag(PlanRulerTestTags.Undo).assertIsDisplayed()

        compose.onNodeWithContentDescription("Project menu").performClick()
        compose.onNodeWithText("Export").performClick()
        compose.onNodeWithText("Project JSON").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()

        val documentsUi = By.pkg(Pattern.compile("com\\.(google\\.)?android\\.documentsui"))
        check(device.wait(Until.hasObject(documentsUi), 10_000)) {
            "System create-document picker did not open"
        }
        device.findObject(By.clazz("android.widget.EditText"))?.let { filename ->
            val original = filename.text.orEmpty()
            val extensionAt = original.lastIndexOf('.').takeIf { it > 0 } ?: original.length
            val stem = original.substring(0, extensionAt)
            val extension = original.substring(extensionAt)
            filename.setText("$stem-${System.currentTimeMillis()}$extension")
            device.waitForIdle()
        }
        val saveText = By.text(Pattern.compile("save", Pattern.CASE_INSENSITIVE))
        val save = device.findObjects(By.clazz("android.widget.Button"))
            .firstOrNull { it.text.equals("save", ignoreCase = true) }
            ?: device.findObject(By.res("com.google.android.documentsui", "action_menu_save"))
            ?: device.findObject(By.res("com.android.documentsui", "action_menu_save"))
            ?: device.findObject(saveText)
            ?: device.findObject(By.text("Save"))
            ?: device.findObject(By.text("SAVE"))
            ?: device.findObject(By.textContains("Save"))
            ?: device.findObject(By.descContains("Save"))
            ?: device.findObject(By.res("com.android.documentsui", "save"))
            ?: device.findObject(By.res("com.android.documentsui", "save_button"))
        val saveButton = checkNotNull(save) { "System create-document Save action not found" }
        device.waitForIdle()
        val saveCenter = saveButton.visibleCenter
        check(device.click(saveCenter.x, saveCenter.y)) { "Could not tap the system Save button" }
        if (device.wait(Until.hasObject(By.textContains("Replace")), 2_000)) {
            device.findObject(By.textContains("Replace"))?.click()
        }
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 10_000)) {
            "System create-document picker did not return to PlanRuler. " +
                "package=${device.currentPackageName}; " +
                "text=${device.findObjects(By.clazz("android.widget.TextView")).map { it.text }}; " +
                "edits=${device.findObjects(By.clazz("android.widget.EditText")).map { "${it.text}:${it.isEnabled}" }}; " +
                "buttons=${device.findObjects(By.clazz("android.widget.Button")).map { "${it.text}:${it.isEnabled}" }}"
        }
        compose.waitForIdle()
        compose.waitUntil(15_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Export complete"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Export complete").assertIsDisplayed()
    }

    private fun seedDownload(name: String, mime: String, bytes: ByteArray): Uri {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.MediaColumns.DISPLAY_NAME}=?",
                arrayOf(name),
            )
            val uri = requireNotNull(
                resolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                ),
            )
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        } else {
            val device = UiDevice.getInstance(instrumentation)
            device.executeShellCommand("pm grant com.planruler.app ${Manifest.permission.READ_EXTERNAL_STORAGE}")
            device.executeShellCommand("pm grant com.planruler.app ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
            val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            directory.mkdirs()
            val file = File(directory, name)
            file.writeBytes(bytes)
            return Uri.fromFile(file)
        }
    }
}

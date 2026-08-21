package com.planruler.app

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.printToString
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.swipe
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.document.android.AndroidDocumentGateway
import com.planruler.document.api.DocumentResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

class PlanRulerDocumentJourneysTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)

    @Before
    fun reset() {
        bringPlanRulerToForeground()
        context.getSharedPreferences("planruler-ui-state", 0).edit().clear().commit()
        context.getSharedPreferences("planruler-settings", 0).edit()
            .clear()
            .putString("language", "ENGLISH")
            .commit()
        context.getSharedPreferences("planruler.camera.profile.statistics.v1", 0).edit().clear().commit()
        context.filesDir.resolve("projects").apply {
            deleteRecursively()
            mkdirs()
        }
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
    }

    @Test
    fun pdfJourneyCalibratesMeasuresRecreatesPagesAndExports() {
        openWithSystemSaf("journey-${System.currentTimeMillis()}.pdf", "application/pdf", twoPagePdf())
        waitForCanvas()
        compose.onNodeWithText("Page 1/2", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By the drawing scale").performClick()
        compose.onNodeWithText("1:50").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performClick()
        compose.onNodeWithText("Pick a control segment").performClick()
        canvasTap(0.25f, 0.35f)
        canvasTap(0.75f, 0.35f)
        compose.onNodeWithText("Expected length").performTextInput("1")
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationVerify).performClick()
        compose.onNodeWithText("Got it").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Calibration saved")).fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithTag(PlanRulerTestTags.TemplatesOpen).performClick()
        compose.waitUntil(5_000) {
            runCatching {
                compose.onNodeWithText("Takeoff templates").assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
        compose.onNodeWithText("Отопление — подача DN20").performClick()
        canvasTap(0.20f, 0.42f)
        canvasTap(0.45f, 0.42f)
        compose.onNodeWithTag(PlanRulerTestTags.ConfirmDraft).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.RepeatLast).performClick()

        compose.onNodeWithTag(PlanRulerTestTags.tool("DISTANCE")).performClick()
        canvasTap(0.30f, 0.52f)
        canvasTap(0.70f, 0.52f)
        compose.onNodeWithTag(PlanRulerTestTags.Undo).assertIsDisplayed().performClick()
        compose.onNodeWithTag(PlanRulerTestTags.Redo).performClick()
        compose.waitForIdle()

        compose.activityRule.scenario.recreate()
        waitForCanvas()
        compose.onNodeWithText("Page 1/2", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(PlanRulerTestTags.IndicatorPage).performClick()
        compose.onNodeWithTag("pr:page:1").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Page 2/2", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(PlanRulerTestTags.IndicatorPage).performClick()
        compose.onNodeWithTag("pr:page:0").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Page 1/2", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        exportThroughWizard("Annotated PDF")
        saveSystemDocument()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("Export complete")).fetchSemanticsNodes().isNotEmpty()
        }
        exportThroughWizard("Schedule CSV")
        saveSystemDocument()
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 10_000))
    }

    @Test
    fun rotatedJpegJourneyRendersCalibratesMeasuresAndReopens() {
        val uri = openWithSystemSaf(
            "rotated-${System.currentTimeMillis()}.jpg",
            "image/jpeg",
            rotatedJpeg(),
        )
        waitForCanvas()
        val opened = runBlocking { AndroidDocumentGateway(context).open(uri.toString()) } as DocumentResult.Ok
        assertEquals(40.0, opened.value.pages.single().width, 0.0)
        assertEquals(80.0, opened.value.pages.single().height, 0.0)

        compose.onNodeWithContentDescription("Project menu").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.PhotoMetadataMenu).assertIsDisplayed().performClick()
        compose.onNodeWithTag(PlanRulerTestTags.PhotoMetadataSheet).assertIsDisplayed()
        compose.onNodeWithText("Reference required").assertIsDisplayed()
        compose.onNodeWithText("PlanRuler Survey Cam").assertIsDisplayed()
        compose.onNodeWithText("does not provide an accepted scale", substring = true).assertIsDisplayed()
        device.pressBack()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasTestTag(PlanRulerTestTags.PhotoMetadataSheet))
                .fetchSemanticsNodes().isEmpty()
        }

        compose.onNodeWithTag(PlanRulerTestTags.tool("NAVIGATE")).performClick()
        compose.waitForIdle()
        val zoomBefore = visibleZoomPercent()
        compose.onNodeWithTag(PlanRulerTestTags.WorkspaceCanvas).performTouchInput {
            pinch(
                start0 = Offset(center.x - 45f, center.y),
                start1 = Offset(center.x + 45f, center.y),
                end0 = Offset(center.x - 155f, center.y),
                end1 = Offset(center.x + 155f, center.y),
                durationMillis = 600L,
            )
        }
        compose.waitForIdle()
        val zoomAfter = visibleZoomPercent()
        assertTrue(
            "Pinch must increase zoom without losing the gesture: before=$zoomBefore after=$zoomAfter",
            zoomAfter > zoomBefore,
        )
        compose.onNodeWithText("Fit page").performClick()

        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By a known distance").performClick()
        canvasTap(0.35f, 0.40f)
        canvasTap(0.65f, 0.40f)
        // Regression: a metre project must not silently interpret this value as mm.
        assertTrue(
            "The project's metre unit must be selected in calibration",
            compose.onAllNodes(hasTextExactly("m").and(isSelected()))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithText("Known length").performTextInput("2")
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("Skip check")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Skip check").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.tool("DISTANCE")).performClick()
        canvasTap(0.35f, 0.55f)
        canvasTap(0.65f, 0.55f)
        compose.waitForIdle()
        compose.activityRule.scenario.recreate()
        waitForCanvas()
    }

    @Test
    fun pageRevisionAlignsCarriesAndRequiresReview() {
        openWithSystemSaf(
            "revision-old-${System.currentTimeMillis()}.png",
            "image/png",
            revisionPng(Color.BLUE),
        )
        waitForCanvas()
        compose.onNodeWithTag(PlanRulerTestTags.tool("COUNTER")).performClick()
        canvasTap(0.50f, 0.50f)
        compose.waitForIdle()

        val revisionName = "revision-new-${System.currentTimeMillis()}.png"
        seedDownload(revisionName, "image/png", revisionPng(Color.GREEN))
        compose.onNodeWithTag(PlanRulerTestTags.IndicatorPage).performClick()
        compose.onNodeWithTag("pr:revision:new").performClick()
        selectFromSystemPicker(revisionName)
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Alignment")).fetchSemanticsNodes().isNotEmpty()
        }

        revisionPoint("pr:revision:previous", 0.28f, 0.35f)
        revisionPoint("pr:revision:current", 0.28f, 0.35f)
        revisionPoint("pr:revision:previous", 0.72f, 0.65f)
        revisionPoint("pr:revision:current", 0.72f, 0.65f)
        compose.onNodeWithTag("pr:revision:confirm").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("Needs review: 1", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        compose.activityRule.scenario.recreate()
        waitForCanvas()
        compose.onNodeWithText("Revision 1").assertIsDisplayed()
        compose.onNodeWithText("Needs review: 1", substring = true).assertIsDisplayed()

        compose.onNodeWithTag(PlanRulerTestTags.tool("SELECT")).performClick()
        canvasTap(0.50f, 0.50f)
        compose.onNodeWithText("Properties").performClick()
        compose.onNodeWithText("Mark as reviewed").performScrollTo().performClick()
        compose.onNodeWithText("Needs review: 0", substring = true).assertIsDisplayed()
    }

    private fun visibleZoomPercent(): Int {
        val zoomLabel = hasText("%", substring = true).and(hasClickAction().not())
        val node = compose.onAllNodes(zoomLabel).fetchSemanticsNodes()
            .first { semantics ->
                semantics.config.contains(SemanticsProperties.Text) &&
                    semantics.config[SemanticsProperties.Text]
                        .any { text -> text.text.matches(Regex("\\d+ %")) }
            }
        val label = node.config[SemanticsProperties.Text]
            .first { text -> text.text.matches(Regex("\\d+ %")) }.text
        return label.substringBefore(' ').toInt()
    }

    @Test
    fun corruptPdfJourneyShowsTypedErrorAndReturnsSafely() {
        openWithSystemSaf(
            "corrupt-${System.currentTimeMillis()}.pdf",
            "application/pdf",
            "%PDF-corrupt-planruler".toByteArray(),
            expectCanvas = false,
        )
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("damaged or unreadable", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        check(
            compose.onAllNodes(hasText("The document is damaged or unreadable", substring = true))
                .fetchSemanticsNodes().isNotEmpty(),
        )
        compose.onNodeWithTag(PlanRulerTestTags.WorkspaceBack).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.HomeRoot).assertIsDisplayed()
    }

    @Test
    fun tappingExistingVertexSelectsInsteadOfStartingNewDraft() {
        openWithSystemSaf("vertex-tap-${System.currentTimeMillis()}.pdf", "application/pdf", twoPagePdf())
        waitForCanvas()

        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By the drawing scale").performClick()
        compose.onNodeWithText("1:50").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performClick()

        compose.onNodeWithTag(PlanRulerTestTags.tool("DISTANCE")).performClick()
        canvasTap(0.30f, 0.52f)
        canvasTap(0.70f, 0.52f)
        compose.waitForIdle()

        // keepToolAfterFinish defaults to true, so DISTANCE stays the active tool after
        // the commit above. Tapping the endpoint just placed must select that same
        // measurement for editing (drag-to-stretch), not start a brand-new draft from
        // the same point - see WorkspaceScreen.onTap's `draft == null && hit != null` branch.
        canvasTap(0.70f, 0.52f)
        compose.waitForIdle()
        compose.onNodeWithText("Exact length and direction").assertIsDisplayed()
    }

    /**
     * Reported behaviour: dragging an existing ruler's endpoint is meant to stretch that
     * ruler, but a second one appears instead. Counting schedule rows before and after the
     * drag says which of the two actually happened.
     */
    @Test
    fun draggingAnEndpointStretchesTheRulerInsteadOfCreatingANewOne() {
        openWithSystemSaf("stretch-${System.currentTimeMillis()}.pdf", "application/pdf", twoPagePdf())
        waitForCanvas()

        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By the drawing scale").performClick()
        compose.onNodeWithText("1:50").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(PlanRulerTestTags.tool("DISTANCE")).performClick()
        canvasTap(0.30f, 0.52f)
        canvasTap(0.70f, 0.52f)
        compose.waitForIdle()

        val (countBefore, valueBefore) = scheduleRows()

        // Grab the endpoint just placed and pull it further right.
        canvasDrag(0.70f, 0.52f, 0.85f, 0.52f)

        val (countAfter, valueAfter) = scheduleRows()
        assertEquals(
            "Dragging the endpoint created a new ruler instead of stretching the existing one",
            countBefore,
            countAfter,
        )
        // Same ruler, longer: proves the drag edited it rather than doing nothing at all.
        assertNotEquals("The endpoint drag left the ruler unchanged", valueBefore, valueAfter)
    }

    /**
     * Ten drags in a row must still leave exactly one ruler. A single drag could pass by
     * luck; a burst catches a hit test that only works while the gesture block happens to
     * be fresh.
     */
    @Test
    fun repeatedEndpointDragsNeverAccumulateRulers() {
        openWithSystemSaf("stretch-many-${System.currentTimeMillis()}.pdf", "application/pdf", twoPagePdf())
        waitForCanvas()

        compose.onNodeWithTag(PlanRulerTestTags.IndicatorScale).performClick()
        compose.onNodeWithText("By the drawing scale").performClick()
        compose.onNodeWithText("1:50").performClick()
        compose.onNodeWithTag(PlanRulerTestTags.CalibrationApply).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(PlanRulerTestTags.tool("DISTANCE")).performClick()
        canvasTap(0.30f, 0.52f)
        canvasTap(0.60f, 0.52f)
        compose.waitForIdle()

        var grabX = 0.60f
        repeat(10) { step ->
            val nextX = if (step % 2 == 0) grabX + 0.05f else grabX - 0.05f
            canvasDrag(grabX, 0.52f, nextX, 0.52f)
            grabX = nextX
        }

        assertEquals("Repeated drags accumulated rulers", 1, scheduleRows().first)
    }

    /** Row count plus the first row's rendered value, read out of the schedule sheet. */
    private fun scheduleRows(): Pair<Int, String> {
        val rowMatcher = SemanticsMatcher("schedule row") { node ->
            node.config.contains(SemanticsProperties.TestTag) &&
                node.config[SemanticsProperties.TestTag].startsWith(PlanRulerTestTags.ScheduleRow)
        }
        compose.onNodeWithContentDescription("Project menu").performClick()
        compose.waitForIdle()
        // "Schedule" also labels a bottom-bar action, so target the dropdown entry.
        compose.onAllNodes(hasText("Schedule") and hasClickAction()).onLast().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(rowMatcher).fetchSemanticsNodes().isNotEmpty()
        }
        // Merged tree: the tagged row aggregates its child texts, including the measured value.
        val nodes = compose.onAllNodes(rowMatcher).fetchSemanticsNodes()
        val firstRowText = nodes.firstOrNull()
            ?.config
            ?.takeIf { it.contains(SemanticsProperties.Text) }
            ?.get(SemanticsProperties.Text)
            ?.joinToString(" ") { it.text }
            .orEmpty()
        device.pressBack()
        compose.waitForIdle()
        return nodes.size to firstRowText
    }

    private fun canvasDrag(fromX: Float, fromY: Float, toX: Float, toY: Float) {
        compose.onNodeWithContentDescription("Plan drawing and measurement canvas").performTouchInput {
            swipe(
                start = Offset(center.x * 2f * fromX, center.y * 2f * fromY),
                end = Offset(center.x * 2f * toX, center.y * 2f * toY),
                durationMillis = 600L,
            )
        }
        compose.waitForIdle()
    }

    /** Format card, two "Next" steps, then the export action - all on the same tagged button. */
    private fun exportThroughWizard(format: String) {
        compose.onNodeWithContentDescription("Project menu").performClick()
        compose.onNodeWithText("Export").performClick()
        compose.onNodeWithText(format).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()
        compose.onNodeWithTag(PlanRulerTestTags.ExportRun).performClick()
    }

    private fun canvasTap(x: Float, y: Float) {
        compose.onNodeWithContentDescription("Plan drawing and measurement canvas").performTouchInput {
            click(Offset(center.x * 2f * x, center.y * 2f * y))
        }
    }

    private fun waitForCanvas() {
        val ready = runCatching {
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasContentDescription("Plan drawing and measurement canvas"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }.isSuccess
        check(ready) {
            val prefs = context.getSharedPreferences("planruler-ui-state", 0)
            "Workspace canvas missing. project=${prefs.getString("project_id", null)} " +
                "uri=${prefs.getString("import_uri", null)} UI=${compose.onRoot().printToString()}"
        }
    }

    private fun openWithSystemSaf(
        name: String,
        mime: String,
        bytes: ByteArray,
        expectCanvas: Boolean = true,
    ): Uri {
        val seededUri = seedDownload(name, mime, bytes)
        compose.onNodeWithTag(PlanRulerTestTags.ProjectsFab).performClick()
        selectFromSystemPicker(name)
        if (expectCanvas) waitForCanvas()
        return seededUri
    }

    private fun selectFromSystemPicker(name: String) {
        val fileSelector = By.textStartsWith(name.substringBefore('.'))
        val cardSelector = By.descStartsWith(name)
        device.wait(Until.hasObject(cardSelector), 10_000)
        var file = device.findObject(cardSelector) ?: device.findObject(fileSelector)
        if (file == null) {
            device.findObject(By.descContains("Show roots"))?.click()
            device.wait(Until.hasObject(By.text("Downloads")), 3_000)
            device.findObject(By.text("Downloads"))?.click()
            device.wait(Until.hasObject(cardSelector), 10_000)
            file = device.findObject(cardSelector) ?: device.findObject(fileSelector)
        }
        if (file == null) {
            val search = device.findObject(By.res("com.google.android.documentsui", "action_menu_search"))
                ?: device.findObject(By.res("com.android.documentsui", "action_menu_search"))
                ?: device.findObject(By.descContains("Search"))
            search?.click()
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 3_000)
            device.findObject(By.clazz("android.widget.EditText"))
                ?.setText(name.substringBefore('.'))
            device.waitForIdle()
            device.wait(Until.hasObject(cardSelector), 10_000)
            file = device.findObject(cardSelector) ?: device.findObject(fileSelector)
        }
        checkNotNull(file) { "Fixture $name is not visible in DocumentsUI" }.click()
        if (!device.wait(Until.hasObject(By.pkg("com.planruler.app")), 3_000)) {
            device.findObject(By.text(Pattern.compile("open", Pattern.CASE_INSENSITIVE)))?.click()
        }
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 5_000))
    }

    private fun revisionPoint(tag: String, x: Float, y: Float) {
        compose.onNodeWithTag(tag).performScrollTo().performTouchInput {
            click(Offset(center.x * 2f * x, center.y * 2f * y))
        }
    }

    private fun saveSystemDocument() {
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
            ?: device.findObject(By.descContains("Save"))
        val saveButton = checkNotNull(save) { "CreateDocument Save action not found" }
        device.waitForIdle()
        val saveCenter = saveButton.visibleCenter
        check(device.click(saveCenter.x, saveCenter.y)) { "Could not tap the system Save button" }
        if (device.wait(Until.hasObject(By.textContains("Replace")), 2_000)) {
            device.findObject(By.textContains("Replace"))?.click()
        }
        check(device.wait(Until.hasObject(By.pkg("com.planruler.app")), 10_000)) {
            "System create-document picker did not return to PlanRuler"
        }
        compose.waitForIdle()
    }

    private fun twoPagePdf(): ByteArray = ByteArrayOutputStream().use { output ->
        val pdf = PdfDocument()
        repeat(2) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(612, 792, index + 1).create())
            page.canvas.drawColor(Color.WHITE)
            page.canvas.drawLine(60f, 120f, 552f, 120f, Paint().apply {
                color = if (index == 0) Color.BLUE else Color.GREEN
                strokeWidth = 8f
            })
            pdf.finishPage(page)
        }
        pdf.writeTo(output)
        pdf.close()
        output.toByteArray()
    }

    private fun revisionPng(accent: Int): ByteArray = ByteArrayOutputStream().use { output ->
        Bitmap.createBitmap(600, 800, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            val canvas = android.graphics.Canvas(bitmap)
            val paint = Paint().apply { color = accent; strokeWidth = 10f }
            canvas.drawRect(100f, 140f, 500f, 660f, paint.apply { style = Paint.Style.STROKE })
            canvas.drawLine(100f, 400f, 500f, 400f, paint)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
        }
        output.toByteArray()
    }

    private fun rotatedJpeg(): ByteArray {
        val file = File(context.cacheDir, "rotated-fixture.jpg")
        Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(Color.WHITE)
            for (y in 0 until 40) for (x in 0 until 20) bitmap.setPixel(x, y, Color.RED)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            bitmap.recycle()
        }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            setAttribute(ExifInterface.TAG_MAKE, "PlanRuler")
            setAttribute(ExifInterface.TAG_MODEL, "Survey Cam")
            setAttribute(ExifInterface.TAG_LENS_MODEL, "Main 6 mm")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "6/1")
            setAttribute(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, "26")
            setAttribute(ExifInterface.TAG_SUBJECT_DISTANCE, "5/2")
            saveAttributes()
        }
        return file.readBytes()
    }

    private fun seedDownload(name: String, mime: String, bytes: ByteArray): Uri {
        if (Build.VERSION.SDK_INT >= 29) {
            val uri = requireNotNull(
                context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                        put(MediaStore.MediaColumns.MIME_TYPE, mime)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    },
                ),
            )
            context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            return uri
        }
        device.executeShellCommand("pm grant com.planruler.app ${Manifest.permission.READ_EXTERNAL_STORAGE}")
        device.executeShellCommand("pm grant com.planruler.app ${Manifest.permission.WRITE_EXTERNAL_STORAGE}")
        val directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        directory.mkdirs()
        return Uri.fromFile(File(directory, name).apply { writeBytes(bytes) })
    }
}

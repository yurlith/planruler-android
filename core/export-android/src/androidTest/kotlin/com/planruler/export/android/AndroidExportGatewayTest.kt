package com.planruler.export.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.planruler.export.api.ExportFormat
import com.planruler.export.api.ExportPageSelection
import com.planruler.export.api.ExportRequest
import com.planruler.export.api.ExportResult
import com.planruler.model.DocPoint
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.MeasurementReviewStatus
import com.planruler.model.PageRevision
import com.planruler.model.PageMetadata
import com.planruler.model.PlanProject
import com.planruler.model.ProjectId
import com.planruler.model.RevisionAlignment
import com.planruler.model.RevisionControlPoint
import com.planruler.model.RevisionPageSource
import com.planruler.model.RevisionTransform
import com.planruler.model.TakeoffProperties
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidExportGatewayTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun exportsAllPdfPagesWithPageSpecificOverlaysAndPreservesSource() = runBlocking {
        val source = createTwoPagePdf()
        val originalBytes = source.readBytes()
        val target = File(context.cacheDir, "all-pages-export.pdf")
        val result = AndroidExportGateway(context).export(
            ExportRequest(
                project = project(source),
                targetUri = Uri.fromFile(target).toString(),
                format = ExportFormat.ANNOTATED_PDF,
                pageSelection = ExportPageSelection.ALL,
            ),
        )
        assertTrue(result is ExportResult.Success)
        assertArrayEquals(originalBytes, source.readBytes())

        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(2, renderer.pageCount)
                repeat(2) { index ->
                    renderer.openPage(index).use { page ->
                        assertEquals(100, page.width)
                        assertEquals(120, page.height)
                        val bitmap = Bitmap.createBitmap(100, 120, Bitmap.Config.ARGB_8888)
                        try {
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pixels = IntArray(100 * 120)
                            bitmap.getPixels(pixels, 0, 100, 0, 0, 100, 120)
                            assertTrue(
                                "page ${index + 1} must contain a red measurement overlay",
                                pixels.any { Color.red(it) > 150 && Color.green(it) < 100 && Color.blue(it) < 100 },
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun exportsSelectedPdfRange() = runBlocking {
        val target = File(context.cacheDir, "range-export.pdf")
        val result = AndroidExportGateway(context).export(
            ExportRequest(
                project = project(createTwoPagePdf()),
                targetUri = Uri.fromFile(target).toString(),
                format = ExportFormat.ANNOTATED_PDF,
                pageSelection = ExportPageSelection.RANGE,
                firstPage = 1,
                lastPage = 1,
            ),
        )
        assertTrue(result is ExportResult.Success)
        ParcelFileDescriptor.open(target, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(1, renderer.pageCount) }
        }
    }

    @Test
    fun csvAndJsonAreUtf8EscapedAndRoundTrip() = runBlocking {
        val project = project(createTwoPagePdf()).copy(
            measurements = listOf(
                project(createTwoPagePdf()).measurements.first().copy(
                    label = "Pipe, \"main\"",
                    takeoff = TakeoffProperties(comment = "Первая строка\n第二行"),
                ),
            ),
        )
        val gateway = AndroidExportGateway(context)
        val csv = File(context.cacheDir, "measurements.csv")
        assertTrue(
            gateway.export(ExportRequest(project, Uri.fromFile(csv).toString(), ExportFormat.CSV)) is ExportResult.Success,
        )
        val csvText = csv.readText(Charsets.UTF_8)
        assertTrue(csvText.startsWith("ID,type,template,category,material"))
        assertTrue(csvText.contains("\"Pipe, \"\"main\"\"\""))
        assertTrue(csvText.contains("\"Первая строка\n第二行\""))
        assertTrue(csvText.contains("SUMMARY,template,category,material,layer"))
        assertTrue(csvText.contains("TOTAL,"))

        val jsonFile = File(context.cacheDir, "project.json")
        assertTrue(
            gateway.export(ExportRequest(project, Uri.fromFile(jsonFile).toString(), ExportFormat.JSON)) is ExportResult.Success,
        )
        val decoded = Json { ignoreUnknownKeys = true }
            .decodeFromString(PlanProject.serializer(), jsonFile.readText(Charsets.UTF_8))
        assertEquals(project, decoded)
        assertEquals(PlanProject.CURRENT_SCHEMA, decoded.schemaVersion)
    }

    @Test
    fun revisionLogAndReviewStateAreIncludedInCsvAndJson() = runBlocking {
        val source = createTwoPagePdf()
        val original = project(source)
        val previousMeasurement = original.measurements.first()
        val carried = previousMeasurement.copy(
            id = MeasurementId("carried"),
            revisionId = "revision-1",
            reviewStatus = MeasurementReviewStatus.NEEDS_REVIEW,
            sourceMeasurementId = previousMeasurement.id,
            createdAtEpochMs = 20,
        )
        val pageSource = RevisionPageSource(
            documentUri = Uri.fromFile(source).toString(),
            mimeType = "application/pdf",
            sourcePageIndex = 0,
            metadata = original.pages[0],
        )
        val revision = PageRevision(
            id = "revision-1",
            logicalPageIndex = 0,
            revisionNumber = 1,
            createdAtEpochMs = 20,
            previousSource = pageSource,
            currentSource = pageSource,
            alignment = RevisionAlignment(
                controlPoints = listOf(
                    RevisionControlPoint(DocPoint(0.0, 0.0), DocPoint(0.0, 0.0)),
                    RevisionControlPoint(DocPoint(100.0, 0.0), DocPoint(100.0, 0.0)),
                ),
                transform = RevisionTransform(1.0, 0.0, 0.0, 1.0, 0.0, 0.0),
            ),
            archivedMeasurements = listOf(previousMeasurement),
            carriedMeasurementIds = listOf(carried.id),
            note = "Issued for construction",
        )
        val revisedProject = original.copy(
            measurements = listOf(carried, original.measurements[1]),
            pageRevisions = listOf(revision),
        )
        val gateway = AndroidExportGateway(context)
        val csv = File(context.cacheDir, "revision-log.csv")
        assertTrue(
            gateway.export(
                ExportRequest(revisedProject, Uri.fromFile(csv).toString(), ExportFormat.CSV),
            ) is ExportResult.Success,
        )
        val text = csv.readText(Charsets.UTF_8)
        assertTrue(text.contains("revision_id,review_status,source_measurement_id,reviewed_at"))
        assertTrue(text.contains("\"revision-1\",NEEDS_REVIEW"))
        assertTrue(text.contains("REVISION_LOG,revision,page,created_at"))
        assertTrue(text.contains("Issued for construction"))

        val jsonFile = File(context.cacheDir, "revision-log.json")
        assertTrue(
            gateway.export(
                ExportRequest(revisedProject, Uri.fromFile(jsonFile).toString(), ExportFormat.JSON),
            ) is ExportResult.Success,
        )
        val decoded = Json.decodeFromString(PlanProject.serializer(), jsonFile.readText(Charsets.UTF_8))
        assertEquals(revisedProject, decoded)
    }

    /**
     * Exporting over an existing longer file must replace it, not overwrite its prefix.
     * Plain "w" mode leaves the old tail behind and produces unreadable JSON/CSV/PDF.
     */
    @Test
    fun exportReplacesAnExistingLongerFile() = runBlocking {
        val project = project(createTwoPagePdf())
        val gateway = AndroidExportGateway(context)

        val jsonFile = File(context.cacheDir, "overwrite-project.json")
        jsonFile.writeText("x".repeat(64_000), Charsets.UTF_8)
        assertTrue(
            gateway.export(
                ExportRequest(project, Uri.fromFile(jsonFile).toString(), ExportFormat.JSON),
            ) is ExportResult.Success,
        )
        val jsonText = jsonFile.readText(Charsets.UTF_8)
        assertTrue(jsonText.trimEnd().endsWith("}"))
        assertEquals(
            project,
            Json { ignoreUnknownKeys = true }.decodeFromString(PlanProject.serializer(), jsonText),
        )

        val csvFile = File(context.cacheDir, "overwrite-measurements.csv")
        csvFile.writeText("stale,header\n".repeat(4_000), Charsets.UTF_8)
        assertTrue(
            gateway.export(
                ExportRequest(project, Uri.fromFile(csvFile).toString(), ExportFormat.CSV),
            ) is ExportResult.Success,
        )
        val csvLines = csvFile.readLines()
        assertTrue(csvLines.first().startsWith("ID,type,template,category,material"))
        assertEquals(project.measurements.size + 3, csvLines.count { it.isNotBlank() })

        val pdfFile = File(context.cacheDir, "overwrite-export.pdf")
        pdfFile.writeText("y".repeat(128_000), Charsets.UTF_8)
        assertTrue(
            gateway.export(
                ExportRequest(
                    project = project,
                    targetUri = Uri.fromFile(pdfFile).toString(),
                    format = ExportFormat.ANNOTATED_PDF,
                    pageSelection = ExportPageSelection.ALL,
                ),
            ) is ExportResult.Success,
        )
        ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> assertEquals(2, renderer.pageCount) }
        }
    }

    private fun createTwoPagePdf(): File {
        val file = File(context.cacheDir, "export-source-${System.nanoTime()}.pdf")
        val pdf = PdfDocument()
        repeat(2) { index ->
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(100, 120, index + 1).create())
            page.canvas.drawLine(5f, 5f, 95f, 5f, Paint().apply { color = Color.BLACK })
            pdf.finishPage(page)
        }
        FileOutputStream(file).use(pdf::writeTo)
        pdf.close()
        return file
    }

    private fun project(source: File) = PlanProject(
        id = ProjectId("export"),
        name = "Export test",
        createdAtEpochMs = 1,
        modifiedAtEpochMs = 1,
        documentUri = Uri.fromFile(source).toString(),
        mimeType = "application/pdf",
        pages = listOf(
            PageMetadata(0, 100.0, 120.0, PageMetadata.CoordinateUnit.PDF_POINT),
            PageMetadata(1, 100.0, 120.0, PageMetadata.CoordinateUnit.PDF_POINT),
        ),
        measurements = listOf(
            Measurement(
                MeasurementId("page-1"),
                MeasurementType.DISTANCE,
                listOf(DocPoint(10.0, 20.0), DocPoint(90.0, 20.0)),
                pageIndex = 0,
                createdAtEpochMs = 1,
            ),
            Measurement(
                MeasurementId("page-2"),
                MeasurementType.DISTANCE,
                listOf(DocPoint(10.0, 90.0), DocPoint(90.0, 90.0)),
                pageIndex = 1,
                createdAtEpochMs = 1,
            ),
        ),
    )
}

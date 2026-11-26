package com.planruler.export.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.planruler.export.api.ExportResult
import com.planruler.isometric.*
import com.planruler.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class IsometricPdfTest {
    @Test fun vectorPdfRendersCyrillicGeometryAndOverwritesAnExistingFile() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = IsometricEngine(IsometricEngine.newSheet("pdf", "Котельная — Т1", 0))
        val spec = IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 50)
        val run = (engine.addRun(RunStart.Point(Vec3.ZERO), Vec3.X * 3000.0, spec) as IsoResult.Ok).value
        val riser = (engine.addRun(RunStart.Node(run.endNodeId), Vec3.Z * 2700.0, spec) as IsoResult.Ok).value
        engine.addRun(RunStart.Node(riser.endNodeId), Vec3.Y * 1800.0, spec)
        engine.addComponent(IsoComponentKind.BALL_VALVE, run.pipeId, 0.5)
        val drawing = IsoPaperDrawing.build(engine.sheet)
        val file = File(context.getExternalFilesDir(null), "isometric-preview.pdf")
        file.writeBytes(ByteArray(2_000_000) { 42 })
        assertEquals(ExportResult.Success, AndroidExportGateway(context).exportIsometricPdf(Uri.fromFile(file).toString(), engine.sheet.name, drawing))
        assertTrue(file.length() < 2_000_000)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                assertEquals(1, renderer.pageCount)
                renderer.openPage(0).use { page ->
                    assertEquals(1191, page.width)
                    assertEquals(842, page.height)
                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    try {
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val pixels = IntArray(page.width * page.height)
                        bitmap.getPixels(pixels, 0, page.width, 0, 0, page.width, page.height)
                        assertTrue(pixels.count { Color.red(it) > 130 && Color.green(it) < 100 && Color.blue(it) < 100 } > 200)
                        assertTrue(pixels.count { Color.red(it) < 100 && Color.green(it) < 100 && Color.blue(it) < 100 } > 100)
                        File(context.getExternalFilesDir(null), "isometric-preview.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    } finally { bitmap.recycle() }
                }
            }
        }
        Unit
    }
}

package com.planruler.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A hand drawn line set instead of material-icons-extended: the app needs ~35 glyphs
 * and the extended artifact costs several megabytes before shrinking. Strokes are
 * tinted by [androidx.compose.material3.Icon] like any other vector.
 */
object PlanRulerIcons {

    private fun stroked(name: String, width: Float = 2f, body: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            path(
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = body,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        val k = r * 0.5523f
        moveTo(cx, cy - r)
        curveTo(cx + k, cy - r, cx + r, cy - k, cx + r, cy)
        curveTo(cx + r, cy + k, cx + k, cy + r, cx, cy + r)
        curveTo(cx - k, cy + r, cx - r, cy + k, cx - r, cy)
        curveTo(cx - r, cy - k, cx - k, cy - r, cx, cy - r)
        close()
    }

    private fun PathBuilder.dot(cx: Float, cy: Float) = circle(cx, cy, 1.5f)

    val Back = stroked("Back") { moveTo(15f, 4.5f); lineTo(8f, 12f); lineTo(15f, 19.5f) }
    val Forward = stroked("Forward") { moveTo(9f, 4.5f); lineTo(16f, 12f); lineTo(9f, 19.5f) }

    val Home = stroked("Home") {
        moveTo(3.5f, 11f); lineTo(12f, 3.5f); lineTo(20.5f, 11f)
        moveTo(5.5f, 9.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.5f)
        moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f)
    }
    val Up = stroked("Up") { moveTo(4.5f, 15f); lineTo(12f, 8f); lineTo(19.5f, 15f) }
    val Down = stroked("Down") { moveTo(4.5f, 9f); lineTo(12f, 16f); lineTo(19.5f, 9f) }

    val Hand = stroked("Hand") {
        moveTo(9f, 11f); lineTo(9f, 5.5f)
        moveTo(12f, 10.5f); lineTo(12f, 4.5f)
        moveTo(15f, 11f); lineTo(15f, 6f)
        moveTo(6f, 10.5f); lineTo(6f, 15.5f)
        curveTo(6f, 18.5f, 8.5f, 21f, 12f, 21f)
        curveTo(15.5f, 21f, 18f, 18.5f, 18f, 15.5f)
        lineTo(18f, 10.5f)
    }

    val Cursor = stroked("Cursor") {
        moveTo(6f, 3f); lineTo(6f, 19f); lineTo(10.2f, 15f); lineTo(12.8f, 20.5f)
        lineTo(15.2f, 19.4f); lineTo(12.7f, 14.2f); lineTo(18f, 14f); close()
    }

    val Ruler = stroked("Ruler") {
        moveTo(3f, 12f); lineTo(21f, 12f)
        moveTo(3f, 7.5f); lineTo(3f, 16.5f)
        moveTo(21f, 7.5f); lineTo(21f, 16.5f)
        moveTo(9f, 10f); lineTo(9f, 14f)
        moveTo(15f, 10f); lineTo(15f, 14f)
    }

    val Polyline = stroked("Polyline") {
        moveTo(4f, 18f); lineTo(9.5f, 9f); lineTo(14.5f, 14f); lineTo(20f, 5.5f)
        dot(4f, 18f); dot(9.5f, 9f); dot(14.5f, 14f); dot(20f, 5.5f)
    }

    val Area = stroked("Area") {
        moveTo(4f, 8.5f); lineTo(12f, 3.5f); lineTo(20f, 9f); lineTo(17f, 19.5f); lineTo(7f, 18.5f); close()
    }

    val Angle = stroked("Angle") {
        moveTo(4f, 19f); lineTo(21f, 19f)
        moveTo(4f, 19f); lineTo(17.5f, 5.5f)
        moveTo(11f, 19f)
        curveTo(11f, 16f, 12f, 13.5f, 13.8f, 11.5f)
    }

    val Counter = stroked("Counter") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 8f); lineTo(12f, 16f)
        moveTo(8f, 12f); lineTo(16f, 12f)
    }

    val Note = stroked("Note") {
        moveTo(4f, 20f); lineTo(4.8f, 16.2f); lineTo(16.2f, 4.8f); lineTo(19.2f, 7.8f); lineTo(7.8f, 19.2f); close()
        moveTo(14f, 7f); lineTo(17f, 10f)
    }

    val Calibrate = stroked("Calibrate") {
        moveTo(5f, 17.5f); lineTo(19f, 6.5f)
        moveTo(2.8f, 15f); lineTo(7.2f, 20f)
        moveTo(16.8f, 4f); lineTo(21.2f, 9f)
    }

    val Snap = stroked("Snap") {
        circle(12f, 12f, 3.2f)
        moveTo(12f, 2f); lineTo(12f, 6.5f)
        moveTo(12f, 17.5f); lineTo(12f, 22f)
        moveTo(2f, 12f); lineTo(6.5f, 12f)
        moveTo(17.5f, 12f); lineTo(22f, 12f)
    }

    val Target = stroked("Target") {
        circle(12f, 12f, 7f)
        dot(12f, 12f)
        moveTo(12f, 1.5f); lineTo(12f, 5f)
        moveTo(12f, 19f); lineTo(12f, 22.5f)
        moveTo(1.5f, 12f); lineTo(5f, 12f)
        moveTo(19f, 12f); lineTo(22.5f, 12f)
    }

    val Undo = stroked("Undo") {
        moveTo(8.5f, 4.5f); lineTo(4f, 9f); lineTo(8.5f, 13.5f)
        moveTo(4f, 9f); lineTo(14f, 9f)
        curveTo(17.3f, 9f, 20f, 11.7f, 20f, 15f)
        curveTo(20f, 18.3f, 17.3f, 21f, 14f, 21f)
        lineTo(9f, 21f)
    }

    val Redo = stroked("Redo") {
        moveTo(15.5f, 4.5f); lineTo(20f, 9f); lineTo(15.5f, 13.5f)
        moveTo(20f, 9f); lineTo(10f, 9f)
        curveTo(6.7f, 9f, 4f, 11.7f, 4f, 15f)
        curveTo(4f, 18.3f, 6.7f, 21f, 10f, 21f)
        lineTo(15f, 21f)
    }

    val Pages = stroked("Pages") {
        moveTo(4f, 3.5f); lineTo(14f, 3.5f); lineTo(18f, 7.5f); lineTo(18f, 17f); lineTo(4f, 17f); close()
        moveTo(14f, 3.5f); lineTo(14f, 7.5f); lineTo(18f, 7.5f)
        moveTo(7.5f, 20.5f); lineTo(20.5f, 20.5f); lineTo(20.5f, 10f)
    }

    val Schedule = stroked("Schedule") {
        moveTo(9f, 6f); lineTo(20f, 6f)
        moveTo(9f, 12f); lineTo(20f, 12f)
        moveTo(9f, 18f); lineTo(20f, 18f)
        dot(4.5f, 6f); dot(4.5f, 12f); dot(4.5f, 18f)
    }

    val Export = stroked("Export") {
        moveTo(12f, 3f); lineTo(12f, 15f)
        moveTo(7.5f, 7.5f); lineTo(12f, 3f); lineTo(16.5f, 7.5f)
        moveTo(4.5f, 14f); lineTo(4.5f, 20.5f); lineTo(19.5f, 20.5f); lineTo(19.5f, 14f)
    }

    val Eye = stroked("Eye") {
        moveTo(2.5f, 12f)
        curveTo(6f, 6.5f, 18f, 6.5f, 21.5f, 12f)
        curveTo(18f, 17.5f, 6f, 17.5f, 2.5f, 12f)
        close()
        circle(12f, 12f, 2.6f)
    }

    val Focus = stroked("Focus") {
        moveTo(3.5f, 9f); lineTo(3.5f, 3.5f); lineTo(9f, 3.5f)
        moveTo(15f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 9f)
        moveTo(20.5f, 15f); lineTo(20.5f, 20.5f); lineTo(15f, 20.5f)
        moveTo(9f, 20.5f); lineTo(3.5f, 20.5f); lineTo(3.5f, 15f)
    }

    val More = stroked("More") { dot(12f, 5f); dot(12f, 12f); dot(12f, 19f) }
    val Menu = stroked("Menu") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 17f); lineTo(20f, 17f)
    }

    val Search = stroked("Search") {
        circle(11f, 11f, 6.2f)
        moveTo(15.6f, 15.6f); lineTo(20.5f, 20.5f)
    }

    val Filter = stroked("Filter") {
        moveTo(3.5f, 5f); lineTo(20.5f, 5f); lineTo(14f, 12.5f); lineTo(14f, 19f)
        lineTo(10f, 21f); lineTo(10f, 12.5f); close()
    }

    val Sort = stroked("Sort") {
        moveTo(6.5f, 3.5f); lineTo(6.5f, 20.5f)
        moveTo(3f, 17f); lineTo(6.5f, 20.5f); lineTo(10f, 17f)
        moveTo(13f, 6f); lineTo(21f, 6f)
        moveTo(13f, 11.5f); lineTo(19f, 11.5f)
        moveTo(13f, 17f); lineTo(17f, 17f)
    }

    val Grid = stroked("Grid") {
        moveTo(4f, 4f); lineTo(10.5f, 4f); lineTo(10.5f, 10.5f); lineTo(4f, 10.5f); close()
        moveTo(13.5f, 4f); lineTo(20f, 4f); lineTo(20f, 10.5f); lineTo(13.5f, 10.5f); close()
        moveTo(4f, 13.5f); lineTo(10.5f, 13.5f); lineTo(10.5f, 20f); lineTo(4f, 20f); close()
        moveTo(13.5f, 13.5f); lineTo(20f, 13.5f); lineTo(20f, 20f); lineTo(13.5f, 20f); close()
    }

    val ListView = stroked("ListView") {
        moveTo(9f, 5.5f); lineTo(20f, 5.5f)
        moveTo(9f, 12f); lineTo(20f, 12f)
        moveTo(9f, 18.5f); lineTo(20f, 18.5f)
        moveTo(4f, 5.5f); lineTo(5.5f, 5.5f)
        moveTo(4f, 12f); lineTo(5.5f, 12f)
        moveTo(4f, 18.5f); lineTo(5.5f, 18.5f)
    }

    val Settings = stroked("Settings") {
        moveTo(3.5f, 7f); lineTo(20.5f, 7f)
        moveTo(3.5f, 17f); lineTo(20.5f, 17f)
        circle(9f, 7f, 2.4f)
        circle(15.5f, 17f, 2.4f)
    }

    val Check = stroked("Check") { moveTo(4.5f, 12.5f); lineTo(9.5f, 18f); lineTo(19.5f, 6f) }

    val Close = stroked("Close") {
        moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f)
        moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f)
    }

    val Plus = stroked("Plus") {
        moveTo(12f, 4.5f); lineTo(12f, 19.5f)
        moveTo(4.5f, 12f); lineTo(19.5f, 12f)
    }

    val Warning = stroked("Warning") {
        moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
        moveTo(12f, 9.5f); lineTo(12f, 14f)
        dot(12f, 17f)
    }

    val Alert = stroked("Alert") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7f); lineTo(12f, 13f)
        dot(12f, 16.2f)
    }

    val Delete = stroked("Delete") {
        moveTo(4f, 6.5f); lineTo(20f, 6.5f)
        moveTo(9.5f, 6.5f); lineTo(9.5f, 4f); lineTo(14.5f, 4f); lineTo(14.5f, 6.5f)
        moveTo(6f, 6.5f); lineTo(7f, 20.5f); lineTo(17f, 20.5f); lineTo(18f, 6.5f)
        moveTo(10f, 10f); lineTo(10.5f, 17f)
        moveTo(14f, 10f); lineTo(13.5f, 17f)
    }

    val Duplicate = stroked("Duplicate") {
        moveTo(8f, 3.5f); lineTo(20.5f, 3.5f); lineTo(20.5f, 16f); lineTo(8f, 16f); close()
        moveTo(16f, 19.5f); lineTo(3.5f, 19.5f); lineTo(3.5f, 7f)
    }

    val Layers = stroked("Layers") {
        moveTo(12f, 3f); lineTo(21f, 8f); lineTo(12f, 13f); lineTo(3f, 8f); close()
        moveTo(3f, 13f); lineTo(12f, 18f); lineTo(21f, 13f)
        moveTo(3f, 17.5f); lineTo(12f, 22.5f); lineTo(21f, 17.5f)
    }

    val Folder = stroked("Folder") {
        moveTo(3.5f, 6f); lineTo(9.5f, 6f); lineTo(11.5f, 8.5f); lineTo(20.5f, 8.5f)
        lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
    }

    val Clock = stroked("Clock") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f)
    }

    val Document = stroked("Document") {
        moveTo(5.5f, 3f); lineTo(14.5f, 3f); lineTo(19f, 7.5f); lineTo(19f, 21f); lineTo(5.5f, 21f); close()
        moveTo(14.5f, 3f); lineTo(14.5f, 7.5f); lineTo(19f, 7.5f)
    }

    val Image = stroked("Image") {
        moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
        moveTo(3.5f, 16f); lineTo(9f, 10.5f); lineTo(14f, 15.5f); lineTo(17f, 12.5f); lineTo(20.5f, 16f)
        circle(8f, 8.5f, 1.6f)
    }

    val Magnifier = stroked("Magnifier") {
        circle(11f, 11f, 6.2f)
        moveTo(15.6f, 15.6f); lineTo(20.5f, 20.5f)
        moveTo(8f, 11f); lineTo(14f, 11f)
        moveTo(11f, 8f); lineTo(11f, 14f)
    }
}

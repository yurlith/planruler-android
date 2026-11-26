package com.planruler.isometric

import com.planruler.model.IsometricSheet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Text exports of a drawn isometric: SVG for viewing and printing, DXF R12 for CAD. */
object IsoExport {
    private val STANDARD_SCALES = listOf(1.0, 2.0, 5.0, 10.0, 20.0, 25.0, 50.0, 100.0, 200.0, 500.0, 1_000.0)

    internal fun nextPaperScale(scale: Double): Double =
        STANDARD_SCALES.firstOrNull { it > scale } ?: (ceil(scale / 1_000.0) + 1.0) * 1_000.0

    /** Smallest standard scale 1:n at which the network fits an A3 landscape drawing field. */
    fun paperScaleFor(sheet: IsometricSheet, fieldWidthMm: Double = 380.0, fieldHeightMm: Double = 250.0): Double {
        val projection = IsoProjection.of(sheet.projection)
        val bounds = Rect2.around(sheet.nodes.map { projection.project(it.position) }) ?: return 20.0
        // Annotations need room around the pipes; a quarter of the field is a practical allowance.
        val needed = max(bounds.width / (fieldWidthMm * 0.75), bounds.height / (fieldHeightMm * 0.75))
        return STANDARD_SCALES.firstOrNull { it >= needed } ?: (ceil(needed / 1_000.0) * 1_000.0)
    }

    fun svg(scene: IsoScene, style: IsoSceneStyle, title: String? = null): String {
        val bounds = (scene.bounds ?: Rect2(0.0, 0.0, 100.0, 100.0)).expanded(style.textHeightMm * 4.0)
        fun x(value: Double) = number(value - bounds.minX)
        fun y(value: Double) = number(bounds.maxY - value)
        val out = StringBuilder()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 ${number(bounds.width)} ${number(bounds.height)}\" " +
                "width=\"${number(bounds.width)}\" height=\"${number(bounds.height)}\" " +
                "font-family=\"Arial, Helvetica, sans-serif\">\n",
        )
        title?.let { out.append("  <title>").append(escape(it)).append("</title>\n") }
        out.append("  <rect x=\"0\" y=\"0\" width=\"${number(bounds.width)}\" height=\"${number(bounds.height)}\" fill=\"#ffffff\"/>\n")
        scene.strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            val points = stroke.points.joinToString(" ") { "${x(it.x)},${y(it.y)}" }
            val tag = if (stroke.closed || stroke.filled) "polygon" else "polyline"
            val fill = if (stroke.filled) color(stroke.colorArgb) else "none"
            val dash = if (stroke.dashed) " stroke-dasharray=\"${number(style.textHeightMm * 1.6)} ${number(style.textHeightMm * 0.8)}\"" else ""
            out.append(
                "  <$tag points=\"$points\" fill=\"$fill\" stroke=\"${color(stroke.colorArgb)}\" " +
                    "stroke-width=\"${number(width(stroke.weight, style))}\" stroke-linecap=\"round\" stroke-linejoin=\"round\"$dash/>\n",
            )
        }
        scene.dots.forEach { dot ->
            val fill = if (dot.filled) color(dot.colorArgb) else "#ffffff"
            out.append(
                "  <circle cx=\"${x(dot.center.x)}\" cy=\"${y(dot.center.y)}\" r=\"${number(dot.radiusMm)}\" " +
                    "fill=\"$fill\" stroke=\"${color(dot.colorArgb)}\" stroke-width=\"${number(width(IsoWeight.THIN, style))}\"/>\n",
            )
        }
        scene.texts.forEach { text ->
            val anchor = when (text.align) {
                IsoTextAlign.START -> "start"
                IsoTextAlign.CENTER -> "middle"
                IsoTextAlign.END -> "end"
            }
            // The scene anchors text at mid cap height; viewers disagree on dominant-baseline,
            // so the baseline is computed here: half a cap height below the anchor, across the text.
            val radians = Math.toRadians(text.angleDeg)
            val baseline = text.anchor - P2(-kotlin.math.sin(radians), kotlin.math.cos(radians)) * (text.heightMm * 0.5)
            val ax = x(baseline.x)
            val ay = y(baseline.y)
            // SVG rotates clockwise with Y down, the scene counter-clockwise with Y up.
            out.append(
                "  <text x=\"$ax\" y=\"$ay\" transform=\"rotate(${number(-text.angleDeg)} $ax $ay)\" " +
                    "font-size=\"${number(text.heightMm * 1.35)}\" text-anchor=\"$anchor\" " +
                    "fill=\"${color(text.colorArgb)}\">${escape(text.text)}</text>\n",
            )
        }
        out.append("</svg>\n")
        return out.toString()
    }

    /**
     * AutoCAD R12 ASCII DXF of the drawing (2D, millimetres). Dashes are written as separate
     * lines so every viewer shows them without a line-type table; non-ASCII text uses \U+XXXX.
     */
    fun dxf(scene: IsoScene, style: IsoSceneStyle): String {
        val out = StringBuilder()
        fun pair(code: Int, value: String) {
            out.append(code).append('\n').append(value).append('\n')
        }
        val layers = IsoRole.entries.map { "PR_${it.name}" }
        pair(0, "SECTION"); pair(2, "HEADER")
        pair(9, "\$ACADVER"); pair(1, "AC1009")
        pair(9, "\$INSUNITS"); pair(70, "4")
        pair(0, "ENDSEC")
        pair(0, "SECTION"); pair(2, "TABLES")
        pair(0, "TABLE"); pair(2, "LAYER"); pair(70, layers.size.toString())
        layers.forEach { layer ->
            pair(0, "LAYER"); pair(2, layer); pair(70, "0"); pair(62, "7"); pair(6, "CONTINUOUS")
        }
        pair(0, "ENDTAB")
        pair(0, "ENDSEC")
        pair(0, "SECTION"); pair(2, "ENTITIES")
        fun line(a: P2, b: P2, layer: String, color: Int) {
            pair(0, "LINE"); pair(8, layer); pair(62, color.toString())
            pair(10, number(a.x)); pair(20, number(a.y)); pair(30, "0")
            pair(11, number(b.x)); pair(21, number(b.y)); pair(31, "0")
        }
        scene.strokes.forEach { stroke ->
            val layer = "PR_${stroke.role.name}"
            val aci = aci(stroke.colorArgb)
            val points = if (stroke.closed || stroke.filled) stroke.points + stroke.points.first() else stroke.points
            points.zipWithNext().forEach { (a, b) ->
                if (stroke.dashed) {
                    dashes(a, b, style.textHeightMm * 1.6, style.textHeightMm * 0.8).forEach { (s, e) -> line(s, e, layer, aci) }
                } else {
                    line(a, b, layer, aci)
                }
            }
        }
        scene.dots.forEach { dot ->
            pair(0, "CIRCLE"); pair(8, "PR_${dot.role.name}"); pair(62, aci(dot.colorArgb).toString())
            pair(10, number(dot.center.x)); pair(20, number(dot.center.y)); pair(30, "0")
            pair(40, number(dot.radiusMm))
        }
        scene.texts.forEach { text ->
            pair(0, "TEXT"); pair(8, "PR_${text.role.name}"); pair(62, aci(text.colorArgb).toString())
            pair(10, number(text.anchor.x)); pair(20, number(text.anchor.y)); pair(30, "0")
            pair(40, number(text.heightMm))
            pair(1, dxfText(text.text))
            pair(50, number(text.angleDeg))
            pair(72, when (text.align) { IsoTextAlign.START -> "0"; IsoTextAlign.CENTER -> "1"; IsoTextAlign.END -> "2" })
            pair(11, number(text.anchor.x)); pair(21, number(text.anchor.y)); pair(31, "0")
            pair(73, "2")
        }
        pair(0, "ENDSEC")
        pair(0, "EOF")
        return out.toString()
    }

    private fun width(weight: IsoWeight, style: IsoSceneStyle) = when (weight) {
        IsoWeight.THICK -> style.textHeightMm * 0.28
        IsoWeight.MEDIUM -> style.textHeightMm * 0.14
        IsoWeight.THIN -> style.textHeightMm * 0.08
    }

    private fun dashes(a: P2, b: P2, dash: Double, gap: Double): List<Pair<P2, P2>> {
        val length = a.distanceTo(b)
        if (length <= dash || dash <= 0.0) return listOf(a to b)
        val d = (b - a) / length
        val result = mutableListOf<Pair<P2, P2>>()
        var position = 0.0
        while (position < length) {
            val end = minOf(length, position + dash)
            result += (a + d * position) to (a + d * end)
            position = end + gap
        }
        return result
    }

    /** Nearest of the seven basic AutoCAD colours; dark ink maps to 7 (black/white by background). */
    private fun aci(argb: Long): Int {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        if (max - min < 40) return if (max < 160) 7 else 8
        val hue = when (max) {
            r -> ((g - b).toDouble() / (max - min) * 60.0 + 360.0) % 360.0
            g -> (b - r).toDouble() / (max - min) * 60.0 + 120.0
            else -> (r - g).toDouble() / (max - min) * 60.0 + 240.0
        }
        return when {
            hue < 20 || hue >= 330 -> 1
            hue < 70 -> 2
            hue < 160 -> 3
            hue < 200 -> 4
            hue < 265 -> 5
            else -> 6
        }
    }

    private fun dxfText(value: String): String = buildString {
        value.forEach { char ->
            if (char.code in 32..126) append(char) else append("\\U+").append(String.format(Locale.US, "%04X", char.code))
        }
    }

    private fun color(argb: Long) = String.format(Locale.US, "#%06X", argb and 0xFFFFFF)

    private fun number(value: Double): String {
        val rounded = if (abs(value) < 5e-5) 0.0 else value
        return String.format(Locale.US, "%.4f", rounded).trimEnd('0').trimEnd('.')
    }

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

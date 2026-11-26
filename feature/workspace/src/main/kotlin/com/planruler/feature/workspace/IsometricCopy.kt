package com.planruler.feature.workspace

import com.planruler.isometric.IsoAxis
import com.planruler.isometric.IsoBillOfMaterials
import com.planruler.isometric.IsoExport
import com.planruler.isometric.IsoFittingKind
import com.planruler.isometric.IsoLabeler
import com.planruler.isometric.IsoProjection
import com.planruler.isometric.IsoSceneBuilder
import com.planruler.isometric.IsoSceneStyle
import com.planruler.isometric.IsoTakeoff
import com.planruler.model.AppLanguage
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsometricSheet
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

/** What an isometric is written to. */
enum class IsometricExportFormat(val extension: String) { PDF("pdf"), SVG("svg"), DXF("dxf"), CSV("csv") }

private val UNSAFE_FILE_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+")

/** A sheet name made safe for the system file picker, with the format's extension. */
fun isometricFileName(sheetName: String, format: IsometricExportFormat): String {
    val base = sheetName.replace(UNSAFE_FILE_CHARS, " ").replace(Regex("\\s+"), " ").trim().take(60).trim()
    return "${base.ifEmpty { "isometric" }}.${format.extension}"
}

/**
 * Conventional system codes printed next to runs. These follow each market's drawing
 * practice (GOST 21.206/21.601 in Russian, DIN 1988 in German, PN-EN usage in Polish…)
 * rather than a translation of the English letters, so they live in one table here.
 */
fun isoSystemCode(language: AppLanguage, system: IsoPipeSystem): String? = when (system) {
    IsoPipeSystem.HEATING_SUPPLY -> when (language) {
        AppLanguage.RUSSIAN -> "Т1"
        AppLanguage.POLISH -> "Z"
        AppLanguage.GERMAN -> "VL"
        AppLanguage.FRENCH -> "D"
        AppLanguage.ITALIAN -> "M"
        AppLanguage.ENGLISH -> "F"
    }
    IsoPipeSystem.HEATING_RETURN -> when (language) {
        AppLanguage.RUSSIAN -> "Т2"
        AppLanguage.POLISH -> "P"
        AppLanguage.GERMAN -> "RL"
        AppLanguage.FRENCH, AppLanguage.ITALIAN, AppLanguage.ENGLISH -> "R"
    }
    IsoPipeSystem.COLD_WATER -> when (language) {
        AppLanguage.RUSSIAN -> "В1"
        AppLanguage.POLISH -> "ZW"
        AppLanguage.GERMAN -> "PWC"
        AppLanguage.FRENCH -> "EF"
        AppLanguage.ITALIAN -> "AF"
        AppLanguage.ENGLISH -> "CW"
    }
    IsoPipeSystem.HOT_WATER -> when (language) {
        AppLanguage.RUSSIAN -> "Т3"
        AppLanguage.POLISH -> "CWU"
        AppLanguage.GERMAN -> "PWH"
        AppLanguage.FRENCH -> "ECS"
        AppLanguage.ITALIAN -> "ACS"
        AppLanguage.ENGLISH -> "HW"
    }
    IsoPipeSystem.HOT_WATER_CIRCULATION -> when (language) {
        AppLanguage.RUSSIAN -> "Т4"
        AppLanguage.POLISH -> "CYR"
        AppLanguage.GERMAN -> "PWH-C"
        AppLanguage.FRENCH -> "BECS"
        AppLanguage.ITALIAN -> "RIC"
        AppLanguage.ENGLISH -> "HWC"
    }
    IsoPipeSystem.SEWER -> when (language) {
        AppLanguage.RUSSIAN -> "К1"
        AppLanguage.POLISH -> "K"
        AppLanguage.GERMAN -> "SW"
        AppLanguage.FRENCH -> "EU"
        AppLanguage.ITALIAN, AppLanguage.ENGLISH -> "S"
    }
    IsoPipeSystem.GAS -> if (language == AppLanguage.RUSSIAN) "Г1" else "G"
    IsoPipeSystem.OTHER -> null
}

/** Drawing wording of a sheet in the user's language: mm lengths, metre levels, system codes. */
class IsometricLabeler(private val language: AppLanguage) : IsoLabeler {
    private val comma = language != AppLanguage.ENGLISH

    override fun length(mm: Double): String = mm.roundToInt().toString()

    override fun elevation(mm: Double): String {
        val metres = String.format(Locale.US, "%.3f", abs(mm) / 1_000.0).let { if (comma) it.replace('.', ',') else it }
        // GOST 21.101: zero level is written ±0,000.
        return when {
            abs(mm) < 0.5 -> if (comma) "±0,000" else "±0.000"
            mm > 0 -> "+$metres"
            else -> "−$metres"
        }
    }

    override fun pipeTag(spec: IsoPipeSpec): String? =
        listOfNotNull(isoSystemCode(language, spec.system), spec.nominalDiameter?.let { "DN$it" })
            .joinToString(" ")
            .takeIf(String::isNotEmpty)
}

/** Arrow that points where a run along [axis] goes on this sheet, for the by-length pad. */
fun isoArrow(projection: IsoProjection, axis: IsoAxis): String {
    val screen = projection.screenOf(axis.unit)
    val angle = Math.toDegrees(atan2(screen.y, screen.x)).let { if (it < 0) it + 360.0 else it }
    val arrows = listOf("→", "↗", "↑", "↖", "←", "↙", "↓", "↘")
    return arrows[((angle + 22.5) / 45.0).toInt() % 8]
}

fun isoAxisCode(axis: IsoAxis) = when (axis) {
    IsoAxis.EAST -> "X+"
    IsoAxis.WEST -> "X−"
    IsoAxis.NORTH -> "Y+"
    IsoAxis.SOUTH -> "Y−"
    IsoAxis.UP -> "Z+"
    IsoAxis.DOWN -> "Z−"
}

/** One line of the bill of materials as the screen and the CSV show it. */
data class IsoBomLine(
    val section: String,
    val item: String,
    val system: String,
    val size: String,
    val material: String,
    val quantity: String,
    val unit: String,
)

fun isoBomLines(bom: IsoBillOfMaterials, text: Wt, language: AppLanguage): List<IsoBomLine> {
    val comma = language != AppLanguage.ENGLISH
    fun number(value: Double, decimals: Int) =
        String.format(Locale.US, "%.${decimals}f", value).let { if (comma) it.replace('.', ',') else it }
    fun system(value: IsoPipeSystem) = listOfNotNull(isoSystemCode(language, value), text.isoSystemName(value)).joinToString(" · ")
    fun size(nominal: Int?, branch: Int? = null) =
        listOfNotNull(nominal, branch).joinToString("×") { "DN$it" }.ifEmpty { "—" }
    val pieces = text.pieces
    val lines = mutableListOf<IsoBomLine>()
    bom.pipes.forEach { pipe ->
        val vertical = if (pipe.verticalMm > 0.5) " (${text.isoVertical} ${number(pipe.verticalMm / 1_000.0, 2)} m)" else ""
        lines += IsoBomLine(
            text.isoPipes,
            text.isoPipe + vertical,
            system(pipe.system),
            size(pipe.nominalDiameter),
            pipe.material.orEmpty(),
            number(pipe.lengthMm / 1_000.0, 2),
            "m",
        )
    }
    bom.fittings.forEach { fitting ->
        val angle = fitting.standardAngleDeg ?: fitting.specialAngleDeg
        val item = text.isoFitting(fitting.kind) + if (fitting.kind == IsoFittingKind.ELBOW && angle != null) " $angle°" else ""
        lines += IsoBomLine(
            text.isoFittings,
            item,
            system(fitting.system),
            size(fitting.nominalDiameter, fitting.branchDiameter),
            "",
            fitting.count.toString(),
            pieces,
        )
    }
    bom.components.forEach { component ->
        lines += IsoBomLine(
            text.isoDevices,
            text.isoComponent(component.kind),
            "",
            size(component.nominalDiameter),
            "",
            component.count.toString(),
            pieces,
        )
    }
    return lines
}

/** The file content of an isometric export; drawings are laid out on paper at a standard scale. */
fun isometricDocument(
    sheet: IsometricSheet,
    format: IsometricExportFormat,
    text: Wt,
    language: AppLanguage,
    csvDelimiter: String = ";",
): String = when (format) {
    IsometricExportFormat.PDF -> error("PDF is a binary document; use ExportGateway.exportIsometricPdf")
    IsometricExportFormat.CSV -> {
        val delimiter = csvDelimiter.ifEmpty { ";" }
        fun cell(value: String) =
            if (value.contains(delimiter) || value.contains('"') || value.contains('\n')) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
        val header = listOf(text.isoSection, text.isoItem, text.isoSystem, text.isoSize, text.material, text.isoQuantity, text.units)
        val rows = isoBomLines(IsoTakeoff.of(sheet), text, language).map {
            listOf(it.section, it.item, it.system, it.size, it.material, it.quantity, it.unit)
        }
        // A byte-order mark lets spreadsheet apps detect UTF-8 (Cyrillic, Polish diacritics).
        "\uFEFF" + (listOf(header) + rows).joinToString("\r\n") { row -> row.joinToString(delimiter, transform = ::cell) } + "\r\n"
    }
    IsometricExportFormat.SVG, IsometricExportFormat.DXF -> {
        val style = IsoSceneStyle.forPaper(IsoExport.paperScaleFor(sheet))
        val scene = IsoSceneBuilder.build(sheet, style, IsometricLabeler(language))
        if (format == IsometricExportFormat.SVG) IsoExport.svg(scene, style, sheet.name) else IsoExport.dxf(scene, style)
    }
}

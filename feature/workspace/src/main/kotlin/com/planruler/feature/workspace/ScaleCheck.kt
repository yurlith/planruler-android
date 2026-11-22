package com.planruler.feature.workspace

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MM_PER_POINT = 25.4 / 72.0

/** Paper size of a PDF page in millimetres with its ISO name when it is one. */
internal data class SheetSize(val widthMm: Double, val heightMm: Double, val isoName: String?) {
    val label: String
        get() = "${widthMm.roundToInt()}×${heightMm.roundToInt()} mm" + (isoName?.let { " ($it)" } ?: "")
}

private val isoSheets = listOf(
    "A0" to (841.0 to 1189.0),
    "A1" to (594.0 to 841.0),
    "A2" to (420.0 to 594.0),
    "A3" to (297.0 to 420.0),
    "A4" to (210.0 to 297.0),
    "Letter" to (215.9 to 279.4),
)

internal fun sheetSize(widthPt: Double, heightPt: Double): SheetSize {
    val w = widthPt * MM_PER_POINT
    val h = heightPt * MM_PER_POINT
    val short = min(w, h)
    val long = max(w, h)
    val iso = isoSheets.firstOrNull { (_, size) ->
        abs(short - size.first) <= 3.0 && abs(long - size.second) <= 3.0
    }?.first
    return SheetSize(w, h, iso)
}

/**
 * The scale a PDF really has, derived from a known real distance: a plan labelled 1:50 on
 * A1 but exported "fit to page" on A3 measures as roughly 1:100.
 */
internal fun effectivePdfScale(documentLengthPt: Double, realLengthMeters: Double): Double? {
    if (documentLengthPt <= 0.0 || realLengthMeters <= 0.0) return null
    val paperMeters = documentLengthPt * MM_PER_POINT / 1000.0
    return realLengthMeters / paperMeters
}

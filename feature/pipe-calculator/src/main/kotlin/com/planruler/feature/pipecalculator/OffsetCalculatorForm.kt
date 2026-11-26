package com.planruler.feature.pipecalculator

import com.planruler.pipecalculator.CalculationException
import com.planruler.pipecalculator.ELBOW_45_3D_CATALOG
import com.planruler.pipecalculator.PipeOffsetInput
import com.planruler.pipecalculator.PipeOffsetResult
import com.planruler.pipecalculator.bendTakeoutMm
import com.planruler.pipecalculator.nearestStandardOffsetAngle
import com.planruler.pipecalculator.offsetAngleForAdvanceDeg
import com.planruler.pipecalculator.pipeOffset

/** How the offset is given: one plane, two planes, or offset plus the room along the run. */
internal enum class OffsetMode { PLAIN, ROLLING, BY_ADVANCE }

/** Welded long-radius elbows take their take-out from the DN; anything else is typed in. */
internal enum class OffsetFittings { WELDED_3D, CUSTOM }

internal enum class OffsetField { OFFSET, SECOND, ADVANCE, ANGLE, TAKEOUT, GAP }

internal const val OFFSET_WELD_GAP_MM = "2"
internal val OFFSET_DIAMETERS: List<Int> = ELBOW_45_3D_CATALOG.map { it.dn }

/**
 * Everything the quick offset page edits, kept as typed text so a half-typed number never
 * throws. [calculate] is the single place that turns it into a result.
 */
internal data class OffsetForm(
    val mode: OffsetMode = OffsetMode.PLAIN,
    val offset: String = "500",
    val second: String = "300",
    val advance: String = "800",
    val angle: String = "45",
    val fittings: OffsetFittings = OffsetFittings.WELDED_3D,
    val dn: Int = 50,
    val takeout: String = "40",
    val gap: String = OFFSET_WELD_GAP_MM,
) {
    /** Switching the fitting kind also switches the usual joint allowance. */
    fun withFittings(kind: OffsetFittings): OffsetForm =
        if (kind == fittings) this else copy(fittings = kind, gap = if (kind == OffsetFittings.WELDED_3D) OFFSET_WELD_GAP_MM else "0")

    fun calculate(): OffsetOutcome {
        val first = number(offset) ?: return OffsetOutcome.Invalid(OffsetField.OFFSET)
        val side = if (mode == OffsetMode.ROLLING) {
            number(second) ?: return OffsetOutcome.Invalid(OffsetField.SECOND)
        } else {
            0.0
        }
        if (first < 0.0) return OffsetOutcome.Invalid(OffsetField.OFFSET)
        if (side < 0.0) return OffsetOutcome.Invalid(OffsetField.SECOND)
        if (first == 0.0 && side == 0.0) {
            return OffsetOutcome.Invalid(if (mode == OffsetMode.ROLLING) OffsetField.SECOND else OffsetField.OFFSET)
        }
        val angleDeg = when (mode) {
            OffsetMode.BY_ADVANCE -> {
                val run = number(advance)?.takeIf { it >= 0.0 } ?: return OffsetOutcome.Invalid(OffsetField.ADVANCE)
                offsetAngleForAdvanceDeg(first, run)
            }
            else -> number(angle)?.takeIf { it in 1.0..90.0 } ?: return OffsetOutcome.Invalid(OffsetField.ANGLE)
        }
        if (angleDeg < 1.0) return OffsetOutcome.Invalid(OffsetField.ADVANCE)
        val radius = if (fittings == OffsetFittings.WELDED_3D) {
            ELBOW_45_3D_CATALOG.firstOrNull { it.dn == dn }?.centerlineRadiusMm
                ?: return OffsetOutcome.Invalid(OffsetField.TAKEOUT)
        } else {
            null
        }
        val takeoutMm = if (radius != null) {
            bendTakeoutMm(radius, angleDeg)
        } else {
            number(takeout)?.takeIf { it >= 0.0 } ?: return OffsetOutcome.Invalid(OffsetField.TAKEOUT)
        }
        val gapMm = number(gap)?.takeIf { it >= 0.0 } ?: return OffsetOutcome.Invalid(OffsetField.GAP)
        val result = try {
            pipeOffset(PipeOffsetInput(first, side, angleDeg, takeoutMm, gapMm))
        } catch (_: CalculationException) {
            return OffsetOutcome.Invalid(OffsetField.OFFSET)
        }
        return OffsetOutcome.Ready(
            result = result,
            radiusMm = radius,
            nearestStockAngle = nearestStandardOffsetAngle(angleDeg).takeIf { mode == OffsetMode.BY_ADVANCE },
        )
    }

    private fun number(value: String): Double? =
        value.trim().replace(',', '.').replace(" ", "").toDoubleOrNull()?.takeIf { it.isFinite() }
}

internal sealed interface OffsetOutcome {
    data class Ready(
        val result: PipeOffsetResult,
        /** Centreline radius when the take-out came from the welded elbow catalogue. */
        val radiusMm: Double?,
        /** For an offset fitted to the room along the run: the stock angle closest to the exact one. */
        val nearestStockAngle: Double?,
    ) : OffsetOutcome

    data class Invalid(val field: OffsetField) : OffsetOutcome
}

/** Chip and form text for a stock angle: 45 and 22.5, never 45.0. */
internal fun angleText(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

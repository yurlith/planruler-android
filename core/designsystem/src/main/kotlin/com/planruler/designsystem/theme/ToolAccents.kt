package com.planruler.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.planruler.model.ThemePreference

/**
 * Brand accents for the workshop tool tiles and the heating-loop diagram. Each tile
 * draws its icon and preview in this colour as `contentColor` over a translucent tint
 * of itself, so — like the fabrication scene — it bypasses Material's own contrast
 * handling and needs its own per-theme set.
 */
@Immutable
data class PlanRulerToolAccents(
    val hydraulics: Color,
    val heating: Color,
    val drawing: Color,
    val expansion: Color,
    val catalog: Color,
    val gas: Color,
    /** Distinguishes concurrent heating circuits on the drawing diagram. */
    val loops: List<Color>,
)

val LocalToolAccents = staticCompositionLocalOf { toolAccents(ThemePreference.LIGHT, false) }

fun toolAccents(preference: ThemePreference, systemDark: Boolean): PlanRulerToolAccents {
    val effective = if (preference == ThemePreference.SYSTEM) {
        if (systemDark) ThemePreference.DARK else ThemePreference.LIGHT
    } else {
        preference
    }
    return when (effective) {
        ThemePreference.DARK -> PlanRulerToolAccents(
            hydraulics = Color(0xFF4FC3F7),
            heating = Color(0xFFFF8A75),
            drawing = Color(0xFFB39DFF),
            expansion = Color(0xFFFFB74D),
            catalog = Color(0xFF4DD0C4),
            // Distinctly yellow-green rather than amber, so it reads apart from expansion.
            gas = Color(0xFFB2D235),
            loops = listOf(Color(0xFF4FC3F7), Color(0xFF4DD0C4), Color(0xFFCE93D8), Color(0xFFFFB74D)),
        )

        // Mid-tone hues on a near-white card; this is the original palette this
        // whole set is built around.
        ThemePreference.LIGHT -> PlanRulerToolAccents(
            hydraulics = Color(0xFF047AA8),
            heating = Color(0xFFD1493F),
            drawing = Color(0xFF6D5BD0),
            expansion = Color(0xFFB55A00),
            catalog = Color(0xFF0D7D6F),
            gas = Color(0xFF4E7A00),
            loops = listOf(Color(0xFF0277BD), Color(0xFF00897B), Color(0xFF7B1FA2), Color(0xFFE65100)),
        )

        // Same white card as LIGHT, but glare demands the darkest, most saturated end
        // of each hue so the icon does not wash out.
        ThemePreference.SUNLIGHT -> PlanRulerToolAccents(
            hydraulics = Color(0xFF0D3C8B),
            heating = Color(0xFF8C2D12),
            drawing = Color(0xFF4A148C),
            expansion = Color(0xFFA66A00),
            catalog = Color(0xFF00695C),
            gas = Color(0xFF3A5200),
            loops = listOf(Color(0xFF0D3C8B), Color(0xFF00695C), Color(0xFF4A148C), Color(0xFFA66A00)),
        )

        ThemePreference.BLUEPRINT -> PlanRulerToolAccents(
            hydraulics = Color(0xFF8FC2FF),
            heating = Color(0xFFFFA08A),
            drawing = Color(0xFFC7B3FF),
            expansion = Color(0xFFFFD79A),
            catalog = Color(0xFF9FE8C0),
            gas = Color(0xFFB8DC7A),
            loops = listOf(Color(0xFF8FC2FF), Color(0xFF9FE8C0), Color(0xFFC7B3FF), Color(0xFFFFA08A)),
        )

        ThemePreference.HIGH_CONTRAST -> PlanRulerToolAccents(
            hydraulics = Color(0xFF7C9BFF),
            heating = Color(0xFFFF6E6E),
            drawing = Color(0xFFE0A0FF),
            expansion = Color(0xFFFFC400),
            catalog = Color(0xFF69F0AE),
            gas = Color(0xFFFFEB3B),
            loops = listOf(Color(0xFF7C9BFF), Color(0xFF69F0AE), Color(0xFFE0A0FF), Color(0xFFFFC400)),
        )

        else -> toolAccents(ThemePreference.LIGHT, systemDark = false)
    }
}

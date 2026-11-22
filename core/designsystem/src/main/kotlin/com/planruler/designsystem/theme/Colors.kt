package com.planruler.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.planruler.model.ThemePreference

// Brand
private val ElectricBlue = Color(0xFF2458F0)
private val ElectricBlueDeep = Color(0xFF1D4ED8)
private val Teal = Color(0xFF00957F)
private val Amber = Color(0xFFE8650F)
private val Red = Color(0xFFDC2626)
private val Green = Color(0xFF16A34A)
private val Ink = Color(0xFF0F172A)
private val Paper = Color(0xFFF1F4F9)

/** Stable engineering colours; meaning is also repeated with labels/icons in the UI. */
object EngineeringColors {
    val HeatingSupply = Color(0xFFE5484D)
    val HeatingReturn = Color(0xFF3976D2)
    val ColdWater = Color(0xFF039BE5)
    val HotWater = Color(0xFFE53935)
    val Gas = Color(0xFFF2B705)
    val Safe = Color(0xFF168A5B)
    val Warning = Color(0xFFE07A12)
    val Draft = Color(0xFF7C5CFC)
    val Neutral = Color(0xFF7A8494)
}

/**
 * Canvas needs roles Material does not model: backdrop, snap accent, label backdrop.
 * Measurement colours are never themed - the export must match the screen.
 */
@Immutable
data class PlanRulerCanvasColors(
    val backdrop: Color,
    val pageBorder: Color,
    val pageBorderWidth: Float,
    val pageShadowAlpha: Float,
    val selection: Color,
    val draft: Color,
    val snapAccent: Color,
    val guide: Color,
    val labelBackdropLight: Color,
    val labelBackdropDark: Color,
    val labelTextLight: Color,
    val labelTextDark: Color,
    val handleFill: Color,
    val success: Color,
    val warning: Color,
    val opaqueOverlays: Boolean,
)

val LocalCanvasColors = staticCompositionLocalOf { canvasColors(ThemePreference.LIGHT, false) }

/** The nine professional measurement colours plus user choice. */
val MeasurementPalette = listOf(
    0xFF2563EB, 0xFF16A34A, 0xFFEA580C, 0xFF9333EA, 0xFF0D9488,
    0xFFDC2626, 0xFFCA8A04, 0xFFFFFFFF, 0xFF111827,
).map { it.toLong() or 0xFF000000L }

fun resolveDarkTheme(preference: ThemePreference, systemDark: Boolean): Boolean = when (preference) {
    ThemePreference.SYSTEM -> systemDark
    ThemePreference.DARK, ThemePreference.BLUEPRINT -> true
    ThemePreference.LIGHT, ThemePreference.SUNLIGHT, ThemePreference.HIGH_CONTRAST -> false
}

fun planRulerColorScheme(preference: ThemePreference, systemDark: Boolean): ColorScheme =
    when (preference) {
        ThemePreference.SYSTEM -> if (systemDark) DarkScheme else LightScheme
        ThemePreference.LIGHT -> LightScheme
        ThemePreference.DARK -> DarkScheme
        ThemePreference.SUNLIGHT -> SunlightScheme
        ThemePreference.BLUEPRINT -> BlueprintScheme
        ThemePreference.HIGH_CONTRAST -> HighContrastScheme
    }

private val LightScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E8FF),
    onPrimaryContainer = Color(0xFF0B2170),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F3EB),
    onSecondaryContainer = Color(0xFF00473C),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE5D1),
    onTertiaryContainer = Color(0xFF6A2800),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8ECF3),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F9FC),
    surfaceContainer = Color(0xFFEFF2F8),
    surfaceContainerHigh = Color(0xFFE9EDF4),
    surfaceContainerHighest = Color(0xFFE2E7EF),
    outline = Color(0xFF8A94A6),
    outlineVariant = Color(0xFFD3D9E4),
    error = Red,
    onError = Color.White,
    errorContainer = Color(0xFFFBE0E0),
    onErrorContainer = Color(0xFF7F1414),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7AA2FF),
    onPrimary = Color(0xFF0B1F52),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBE6FF),
    secondary = Color(0xFF5EDBC9),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF0B5B52),
    onSecondaryContainer = Color(0xFFCFF3EC),
    tertiary = Color(0xFFF5B84E),
    onTertiary = Color(0xFF3D2600),
    background = Color(0xFF0B0F14),
    onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFF3F6FA),
    surfaceContainerLowest = Color(0xFF0B0F14),
    surfaceContainerLow = Color(0xFF12171E),
    surfaceContainer = Color(0xFF181E27),
    surfaceContainerHigh = Color(0xFF1E2530),
    surfaceContainerHighest = Color(0xFF252D39),
    surfaceVariant = Color(0xFF232C38),
    onSurfaceVariant = Color(0xFFB6C0CE),
    outline = Color(0xFF74808F),
    outlineVariant = Color(0xFF394453),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4E0002),
)

/** Outdoors: no weak greys, no translucency, maximum contrast. */
private val SunlightScheme = lightColorScheme(
    primary = ElectricBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCEDCFF),
    onPrimaryContainer = Color(0xFF06184A),
    secondary = Color(0xFF0A6E63),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7EDE6),
    onSecondaryContainer = Color(0xFF00312B),
    tertiary = Color(0xFF9A5400),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFF0F0F0),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF1F2937),
    outline = Color(0xFF3F4854),
    outlineVariant = Color(0xFF9AA3B0),
    error = Color(0xFFA80F0F),
    onError = Color.White,
)

/** Technical drawings: deep blue backdrop, cyan accents, white controls. */
private val BlueprintScheme = darkColorScheme(
    primary = Color(0xFF7DD3FC),
    onPrimary = Color(0xFF04223F),
    primaryContainer = Color(0xFF124A75),
    onPrimaryContainer = Color(0xFFDCF1FF),
    secondary = Color(0xFF6EE7D2),
    onSecondary = Color(0xFF00312B),
    tertiary = Color(0xFFFFCF7A),
    onTertiary = Color(0xFF3D2600),
    background = Color(0xFF071431),
    onBackground = Color(0xFFE8F4FF),
    surface = Color(0xFF0D2148),
    surfaceContainerLowest = Color(0xFF061029),
    surfaceContainerLow = Color(0xFF0A1A3C),
    surfaceContainer = Color(0xFF0E2249),
    surfaceContainerHigh = Color(0xFF132A56),
    surfaceContainerHighest = Color(0xFF183264),
    onSurface = Color(0xFFE8F4FF),
    surfaceVariant = Color(0xFF163166),
    onSurfaceVariant = Color(0xFFB9D6F2),
    outline = Color(0xFF5E8CC4),
    outlineVariant = Color(0xFF244A88),
    error = Color(0xFFFF9A8F),
    onError = Color(0xFF4E0002),
)

private val HighContrastScheme = lightColorScheme(
    primary = Color(0xFF0B3FCC),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBBCCFF),
    onPrimaryContainer = Color.Black,
    secondary = Color(0xFF00563F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8E6D6),
    onSecondaryContainer = Color.Black,
    tertiary = Color(0xFF7A3C00),
    onTertiary = Color.White,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFEDEDED),
    surfaceContainerHigh = Color(0xFFE3E3E3),
    surfaceContainerHighest = Color(0xFFD9D9D9),
    onSurface = Color.Black,
    surfaceVariant = Color(0xFFE6E6E6),
    onSurfaceVariant = Color.Black,
    outline = Color.Black,
    outlineVariant = Color(0xFF555555),
    error = Color(0xFF8A0000),
    onError = Color.White,
)

fun canvasColors(preference: ThemePreference, systemDark: Boolean): PlanRulerCanvasColors {
    val effective = if (preference == ThemePreference.SYSTEM) {
        if (systemDark) ThemePreference.DARK else ThemePreference.LIGHT
    } else {
        preference
    }
    return when (effective) {
        ThemePreference.DARK -> PlanRulerCanvasColors(
            backdrop = Color(0xFF0B0F14),
            pageBorder = Color(0xFF2A323D),
            pageBorderWidth = 1f,
            pageShadowAlpha = 0f,
            selection = Color(0xFF7AA2FF),
            draft = Color(0xFF7AA2FF),
            snapAccent = Color(0xFF34D399),
            guide = Color(0x8034D399),
            labelBackdropLight = Color(0xF2FFFFFF),
            labelBackdropDark = Color(0xD90B0F14),
            labelTextLight = Color(0xFF111827),
            labelTextDark = Color(0xFFF3F6FA),
            handleFill = Color(0xFFF3F6FA),
            success = Color(0xFF34D399),
            warning = Color(0xFFF5B84E),
            opaqueOverlays = false,
        )
        ThemePreference.SUNLIGHT -> PlanRulerCanvasColors(
            backdrop = Color.White,
            pageBorder = Color.Black,
            pageBorderWidth = 1.5f,
            pageShadowAlpha = 0f,
            selection = ElectricBlueDeep,
            draft = ElectricBlueDeep,
            snapAccent = Color(0xFF0F7A32),
            guide = Color(0xFF0F7A32),
            labelBackdropLight = Color.White,
            labelBackdropDark = Color.Black,
            labelTextLight = Color.Black,
            labelTextDark = Color.White,
            handleFill = Color.White,
            success = Color(0xFF0F7A32),
            warning = Color(0xFF9A5400),
            opaqueOverlays = true,
        )
        ThemePreference.BLUEPRINT -> PlanRulerCanvasColors(
            backdrop = Color(0xFF0B1B3A),
            pageBorder = Color(0xFF7DD3FC),
            pageBorderWidth = 1f,
            pageShadowAlpha = 0f,
            selection = Color(0xFF38BDF8),
            draft = Color(0xFF38BDF8),
            snapAccent = Color(0xFF34D399),
            guide = Color(0x9934D399),
            labelBackdropLight = Color(0xF2FFFFFF),
            labelBackdropDark = Color(0xE00B1B3A),
            labelTextLight = Color(0xFF071431),
            labelTextDark = Color(0xFFE8F4FF),
            handleFill = Color.White,
            success = Color(0xFF34D399),
            warning = Color(0xFFFFCF7A),
            opaqueOverlays = false,
        )
        ThemePreference.HIGH_CONTRAST -> PlanRulerCanvasColors(
            backdrop = Color.White,
            pageBorder = Color.Black,
            pageBorderWidth = 2f,
            pageShadowAlpha = 0f,
            selection = Color(0xFF0B3FCC),
            draft = Color(0xFF0B3FCC),
            snapAccent = Color(0xFF006B2C),
            guide = Color(0xFF006B2C),
            labelBackdropLight = Color.White,
            labelBackdropDark = Color.Black,
            labelTextLight = Color.Black,
            labelTextDark = Color.White,
            handleFill = Color.White,
            success = Color(0xFF006B2C),
            warning = Color(0xFF7A3C00),
            opaqueOverlays = true,
        )
        else -> PlanRulerCanvasColors(
            backdrop = Color(0xFFE7EAF0),
            pageBorder = Color(0xFFC7CED9),
            pageBorderWidth = 1f,
            pageShadowAlpha = 0.12f,
            selection = ElectricBlue,
            draft = ElectricBlue,
            snapAccent = Green,
            guide = Color(0x8016A34A),
            labelBackdropLight = Color(0xF2FFFFFF),
            labelBackdropDark = Color(0xD9111827),
            labelTextLight = Ink,
            labelTextDark = Color.White,
            handleFill = Color.White,
            success = Green,
            warning = Amber,
            opaqueOverlays = false,
        )
    }
}

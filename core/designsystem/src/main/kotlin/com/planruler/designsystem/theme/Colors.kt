package com.planruler.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.planruler.model.ThemePreference

// Brand
private val ElectricBlue = Color(0xFF2563EB)
private val ElectricBlueDeep = Color(0xFF1D4ED8)
private val Teal = Color(0xFF0D9488)
private val Amber = Color(0xFFD97706)
private val Red = Color(0xFFDC2626)
private val Green = Color(0xFF16A34A)
private val Ink = Color(0xFF111827)
private val Paper = Color(0xFFF6F8FC)

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
    primaryContainer = Color(0xFFDBE6FF),
    onPrimaryContainer = Color(0xFF10276B),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD3F1EC),
    onSecondaryContainer = Color(0xFF07564D),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFDEBC8),
    onTertiaryContainer = Color(0xFF6B3B00),
    background = Paper,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE8ECF4),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFF8C96A6),
    outlineVariant = Color(0xFFCBD3E0),
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

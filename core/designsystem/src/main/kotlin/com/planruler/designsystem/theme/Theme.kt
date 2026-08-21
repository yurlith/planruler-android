package com.planruler.designsystem.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.planruler.model.AppSettings
import com.planruler.model.ThemePreference

private val BaseTypography = Typography(
    headlineLarge = TextStyle(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium),
)

/** Tabular figures keep a measurement from jittering while a vertex is dragged. */
val TabularNumbers = TextStyle(
    fontFamily = FontFamily.Default,
    fontFeatureSettings = "tnum",
)

private val PlanRulerShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private fun Typography.scaled(factor: Float): Typography = if (factor == 1f) this else Typography(
    headlineLarge = headlineLarge.scale(factor),
    headlineMedium = headlineMedium.scale(factor),
    headlineSmall = headlineSmall.scale(factor),
    titleLarge = titleLarge.scale(factor),
    titleMedium = titleMedium.scale(factor),
    titleSmall = titleSmall.scale(factor),
    bodyLarge = bodyLarge.scale(factor),
    bodyMedium = bodyMedium.scale(factor),
    bodySmall = bodySmall.scale(factor),
    labelLarge = labelLarge.scale(factor),
    labelMedium = labelMedium.scale(factor),
    labelSmall = labelSmall.scale(factor),
)

private fun TextStyle.scale(factor: Float) = copy(
    fontSize = fontSize * factor,
    lineHeight = lineHeight * factor,
)

@Composable
fun PlanRulerTheme(
    settings: AppSettings = AppSettings(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val dark = resolveDarkTheme(settings.theme, systemDark)
    val dynamicAllowed = settings.dynamicColor &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        settings.theme in setOf(ThemePreference.SYSTEM, ThemePreference.LIGHT, ThemePreference.DARK)
    val scheme = when {
        dynamicAllowed && dark -> dynamicDarkColorScheme(context)
        dynamicAllowed -> dynamicLightColorScheme(context)
        else -> planRulerColorScheme(settings.theme, systemDark)
    }
    val reducedMotion = remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
    CompositionLocalProvider(
        LocalPlanRulerDimens provides PlanRulerDimens.of(settings.touchProfile),
        LocalCanvasColors provides canvasColors(settings.theme, systemDark),
        LocalScenePalette provides scenePalette(settings.theme, systemDark),
        LocalToolAccents provides toolAccents(settings.theme, systemDark),
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = BaseTypography.scaled(settings.uiScale),
            shapes = PlanRulerShapes,
            content = content,
        )
    }
}

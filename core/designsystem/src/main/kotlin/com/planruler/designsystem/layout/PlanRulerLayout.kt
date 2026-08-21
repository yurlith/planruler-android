package com.planruler.designsystem.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.planruler.model.AppSettings
import com.planruler.model.Handedness
import com.planruler.model.TouchProfile

enum class WidthClass { COMPACT, MEDIUM, EXPANDED }
enum class HeightClass { COMPACT, MEDIUM, EXPANDED }
enum class NavigationStyle { BOTTOM_BAR, RAIL, RAIL_WITH_DRAWER }
enum class ToolbarStyle { BOTTOM_SCROLLING, SIDE_RAIL, FLOATING_DOCK }
enum class PanelStyle { MODAL_SHEET, SIDE_SHEET, FIXED_PANEL }

/**
 * Window classes are derived from the configuration rather than from
 * material3-window-size-class: the rule set is small, and this keeps feature
 * modules free of an Activity reference.
 */
@Immutable
data class PlanRulerLayout(
    val width: WidthClass,
    val height: HeightClass,
    val widthDp: Dp,
    val heightDp: Dp,
    val handed: Handedness,
    val profile: TouchProfile,
    val focusMode: Boolean,
) {
    val phonePortrait: Boolean get() = width == WidthClass.COMPACT && height != HeightClass.COMPACT
    val phoneLandscape: Boolean get() = width == WidthClass.COMPACT && height == HeightClass.COMPACT
    val tablet: Boolean get() = width != WidthClass.COMPACT

    val navigation: NavigationStyle
        get() = when {
            width == WidthClass.EXPANDED && widthDp >= 1200.dp -> NavigationStyle.RAIL_WITH_DRAWER
            width == WidthClass.COMPACT && !phoneLandscape -> NavigationStyle.BOTTOM_BAR
            else -> NavigationStyle.RAIL
        }

    val toolbar: ToolbarStyle
        get() = when {
            phoneLandscape -> ToolbarStyle.SIDE_RAIL
            width == WidthClass.COMPACT -> ToolbarStyle.BOTTOM_SCROLLING
            else -> ToolbarStyle.FLOATING_DOCK
        }

    /** Degrades when the canvas would fall below its minimum width - see §2.2. */
    fun properties(availableWidth: Dp, panelWidth: Dp, canvasMinWidth: Dp): PanelStyle = when {
        width == WidthClass.EXPANDED && availableWidth - panelWidth >= canvasMinWidth -> PanelStyle.FIXED_PANEL
        availableWidth >= 600.dp -> PanelStyle.SIDE_SHEET
        else -> PanelStyle.MODAL_SHEET
    }
}

val LocalPlanRulerLayout = staticCompositionLocalOf {
    PlanRulerLayout(
        width = WidthClass.COMPACT,
        height = HeightClass.MEDIUM,
        widthDp = 411.dp,
        heightDp = 891.dp,
        handed = Handedness.RIGHT,
        profile = TouchProfile.FINGER,
        focusMode = false,
    )
}

@Composable
fun rememberPlanRulerLayout(settings: AppSettings, focusMode: Boolean): PlanRulerLayout {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    return remember(widthDp, heightDp, settings.handedness, settings.touchProfile, focusMode) {
        PlanRulerLayout(
            width = when {
                widthDp < 600 -> WidthClass.COMPACT
                widthDp < 840 -> WidthClass.MEDIUM
                else -> WidthClass.EXPANDED
            },
            height = when {
                heightDp < 480 -> HeightClass.COMPACT
                heightDp < 900 -> HeightClass.MEDIUM
                else -> HeightClass.EXPANDED
            },
            widthDp = widthDp.dp,
            heightDp = heightDp.dp,
            handed = settings.handedness,
            profile = settings.touchProfile,
            focusMode = focusMode,
        )
    }
}

package com.planruler.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.planruler.model.TouchProfile

/** 4 dp grid. */
object Space {
    val half = 2.dp
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
}

/**
 * Every precision dimension derives from the touch profile so that glove or stylus
 * work only changes one value. Document tolerances are expressed in dp and converted
 * with `dp * density / zoom`, which keeps them zoom independent.
 */
@Immutable
data class PlanRulerDimens(
    val profile: TouchProfile,
    val minTouchTarget: Dp,
    val toolButtonHeight: Dp,
    val vertexHandleRadius: Dp,
    val vertexHitRadius: Dp,
    val segmentHitTolerance: Dp,
    val snapSensitivity: Dp,
    val dragSlop: Dp,
    val doubleTapWindowMs: Long,
    val magnifierByDefault: Boolean,
    val inlineIconButtons: Boolean,
    val topBarHeight: Dp = 56.dp,
    val indicatorStripHeight: Dp = 40.dp,
    val indicatorChipHeight: Dp = 32.dp,
    val confirmBarHeight: Dp = 56.dp,
    val propertiesPanelWidth: Dp = 360.dp,
    val navigationRailWidth: Dp = 80.dp,
    val pageStripHeight: Dp = 96.dp,
    val pageThumbWidth: Dp = 68.dp,
    val magnifierSize: Dp = 132.dp,
    val magnifierOffset: Dp = 88.dp,
    val canvasMinWidth: Dp = 480.dp,
) {
    val toolBarHeight: Dp get() = toolButtonHeight + Space.x2 * 2

    companion object {
        fun of(profile: TouchProfile): PlanRulerDimens = when (profile) {
            TouchProfile.STYLUS -> PlanRulerDimens(
                profile = profile,
                minTouchTarget = 44.dp,
                toolButtonHeight = 48.dp,
                vertexHandleRadius = 5.dp,
                vertexHitRadius = 12.dp,
                segmentHitTolerance = 8.dp,
                snapSensitivity = 8.dp,
                dragSlop = 3.dp,
                doubleTapWindowMs = 250L,
                magnifierByDefault = false,
                inlineIconButtons = true,
            )
            TouchProfile.FINGER -> PlanRulerDimens(
                profile = profile,
                minTouchTarget = 48.dp,
                toolButtonHeight = 56.dp,
                vertexHandleRadius = 7.dp,
                vertexHitRadius = 24.dp,
                segmentHitTolerance = 16.dp,
                snapSensitivity = 12.dp,
                dragSlop = 6.dp,
                doubleTapWindowMs = 300L,
                magnifierByDefault = true,
                inlineIconButtons = true,
            )
            TouchProfile.GLOVE -> PlanRulerDimens(
                profile = profile,
                minTouchTarget = 56.dp,
                toolButtonHeight = 64.dp,
                vertexHandleRadius = 10.dp,
                vertexHitRadius = 32.dp,
                segmentHitTolerance = 22.dp,
                snapSensitivity = 16.dp,
                dragSlop = 10.dp,
                doubleTapWindowMs = 380L,
                magnifierByDefault = true,
                inlineIconButtons = false,
            )
        }
    }
}

val LocalPlanRulerDimens = staticCompositionLocalOf { PlanRulerDimens.of(TouchProfile.FINGER) }

/** Functional durations only; see UI_DESIGN_SYSTEM.md §7. */
object Motion {
    const val fast = 120
    const val standard = 200
    const val emphasized = 320
    const val page = 260
    const val centerOn = 400
}

val LocalReducedMotion = staticCompositionLocalOf { false }

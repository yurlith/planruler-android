package com.planruler.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.planruler.model.ThemePreference

/**
 * Colours for the drawn engineering scenes: the parametric 3D viewport and the flat
 * fabrication blueprint. These are painted onto a Canvas rather than composed from
 * Material surfaces, so they need their own palette the way the measurement canvas does.
 *
 * Every theme is covered on purpose. A dark scene is the wrong answer on a roof in
 * daylight, and the same fitter who picks the sunlight theme for the plan view expects
 * the pipe model to follow.
 */
@Immutable
data class PlanRulerScenePalette(
    val backdropTop: Color,
    val backdropBottom: Color,
    val grid: Color,
    val pipe: Color,
    val elbow: Color,
    val flange: Color,
    val tee: Color,
    val reducer: Color,
    val cap: Color,
    val bore: Color,
    val weld: Color,
    val selection: Color,
    val dimension: Color,
    val onScene: Color,
    val labelBackdrop: Color,
    val axisX: Color,
    val axisY: Color,
    val axisZ: Color,
    /**
     * Lowest shading factor a lit face may fall to. On pale backdrops a face that shades
     * all the way to black reads as a hole, and on dark ones it disappears entirely.
     */
    val shadeFloor: Float,
    /** Stroke width for part outlines; themes for glare and low vision draw them heavier. */
    val outlineWidth: Float,
    /** Whether every triangle gets an outline, not only the selected part. */
    val outlineEveryPart: Boolean,
) {
    /** Blueprint drawings reuse the scene ink so the two views cannot drift apart. */
    val blueprintBackground: Color get() = backdropTop
    val blueprintGrid: Color get() = grid
    val blueprintPipe: Color get() = pipe
    val blueprintFitting: Color get() = elbow
    val blueprintFlange: Color get() = flange
    val blueprintCut: Color get() = selection
    val blueprintDimension: Color get() = dimension
    val blueprintText: Color get() = onScene
}

val LocalScenePalette = staticCompositionLocalOf { scenePalette(ThemePreference.LIGHT, false) }

fun scenePalette(preference: ThemePreference, systemDark: Boolean): PlanRulerScenePalette {
    val effective = if (preference == ThemePreference.SYSTEM) {
        if (systemDark) ThemePreference.DARK else ThemePreference.LIGHT
    } else {
        preference
    }
    return when (effective) {
        ThemePreference.DARK -> PlanRulerScenePalette(
            backdropTop = Color(0xFF071722),
            backdropBottom = Color(0xFF0C2634),
            grid = Color(0xFF315364),
            pipe = Color(0xFF2FD5C4),
            elbow = Color(0xFF528DFF),
            flange = Color(0xFFFFAE4A),
            tee = Color(0xFFAD7CFF),
            reducer = Color(0xFF7CE0A0),
            cap = Color(0xFFFF8A65),
            bore = Color(0xFF102731),
            weld = Color(0xFFFFD166),
            selection = Color(0xFFFF5CA8),
            dimension = Color(0xFFFFD166),
            onScene = Color(0xFFEAF7FA),
            labelBackdrop = Color(0xE0071722),
            axisX = Color(0xFFFF6B6B),
            axisY = Color(0xFF78E08F),
            axisZ = Color(0xFF6FA8FF),
            shadeFloor = 0.42f,
            outlineWidth = 0.8f,
            outlineEveryPart = false,
        )

        // Pale studio backdrop; parts keep mid-tone fills so shading darkens them into
        // contrast with the background instead of washing them out.
        ThemePreference.LIGHT -> PlanRulerScenePalette(
            backdropTop = Color(0xFFEEF3F7),
            backdropBottom = Color(0xFFDDE6EE),
            grid = Color(0xFFB4C4D2),
            pipe = Color(0xFF0F8C80),
            elbow = Color(0xFF2A5FCC),
            flange = Color(0xFFB46A00),
            tee = Color(0xFF6A3FBF),
            // Grass rather than sea green: a teal reducer sat too close to the teal pipe.
            reducer = Color(0xFF3F7D1F),
            cap = Color(0xFFC1552F),
            bore = Color(0xFFAFBECB),
            weld = Color(0xFF6D4C00),
            selection = Color(0xFFC2185B),
            dimension = Color(0xFF8A5A00),
            onScene = Color(0xFF14212B),
            labelBackdrop = Color(0xF0FFFFFF),
            axisX = Color(0xFFC62828),
            axisY = Color(0xFF2E7D32),
            axisZ = Color(0xFF1565C0),
            shadeFloor = 0.62f,
            outlineWidth = 1f,
            outlineEveryPart = false,
        )

        // Maximum luminance separation for direct sunlight: white ground, dark saturated
        // fills, and an outline on every part so shape survives glare washout.
        ThemePreference.SUNLIGHT -> PlanRulerScenePalette(
            backdropTop = Color.White,
            backdropBottom = Color(0xFFF2F2F2),
            grid = Color(0xFF9E9E9E),
            pipe = Color(0xFF00695C),
            elbow = Color(0xFF0D3C8B),
            flange = Color(0xFF8A4B00),
            tee = Color(0xFF4A148C),
            reducer = Color(0xFF1B5E20),
            // Slate and near-black: under glare a rust cap and a bronze weld both collapsed
            // into the brown flange, so both moved off that hue entirely.
            cap = Color(0xFF37474F),
            bore = Color(0xFF757575),
            weld = Color(0xFF3E2723),
            selection = Color(0xFFAD1457),
            dimension = Color(0xFF000000),
            onScene = Color(0xFF000000),
            labelBackdrop = Color.White,
            axisX = Color(0xFFB71C1C),
            axisY = Color(0xFF1B5E20),
            axisZ = Color(0xFF0D47A1),
            shadeFloor = 0.72f,
            outlineWidth = 1.6f,
            outlineEveryPart = true,
        )

        ThemePreference.BLUEPRINT -> PlanRulerScenePalette(
            backdropTop = Color(0xFF0B1B3A),
            backdropBottom = Color(0xFF10244A),
            grid = Color(0xFF2B4C86),
            pipe = Color(0xFFCFE3FF),
            elbow = Color(0xFF8FC2FF),
            flange = Color(0xFFFFD79A),
            tee = Color(0xFFC7B3FF),
            reducer = Color(0xFF9FE8C0),
            // Blueprint tints are all pale, so the cap and the weld had to leave the amber
            // family the flange occupies or the three read as one wash.
            cap = Color(0xFFFF8A80),
            bore = Color(0xFF16305C),
            weld = Color(0xFFFFF176),
            selection = Color(0xFFFF9EC7),
            dimension = Color(0xFFFFE08A),
            onScene = Color(0xFFEAF2FF),
            labelBackdrop = Color(0xE00B1B3A),
            axisX = Color(0xFFFF8A8A),
            axisY = Color(0xFF9BE8AE),
            axisZ = Color(0xFF9EC5FF),
            shadeFloor = 0.5f,
            outlineWidth = 1f,
            outlineEveryPart = false,
        )

        ThemePreference.HIGH_CONTRAST -> PlanRulerScenePalette(
            backdropTop = Color.Black,
            backdropBottom = Color.Black,
            grid = Color(0xFF666666),
            pipe = Color(0xFF00E5FF),
            elbow = Color(0xFF7C9BFF),
            flange = Color(0xFFFFC400),
            tee = Color(0xFFE0A0FF),
            reducer = Color(0xFF69F0AE),
            cap = Color(0xFFFF8A65),
            bore = Color(0xFF1A1A1A),
            weld = Color(0xFFFFFF00),
            selection = Color(0xFFFF4081),
            dimension = Color(0xFFFFFF00),
            onScene = Color.White,
            labelBackdrop = Color(0xF0000000),
            axisX = Color(0xFFFF5252),
            axisY = Color(0xFF69F0AE),
            axisZ = Color(0xFF82B1FF),
            shadeFloor = 0.75f,
            outlineWidth = 2f,
            outlineEveryPart = true,
        )

        else -> scenePalette(ThemePreference.LIGHT, systemDark = false)
    }
}

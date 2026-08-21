package com.planruler.designsystem.theme

import androidx.compose.ui.graphics.Color
import com.planruler.model.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * The drawn scenes bypass Material's colour roles, so nothing but these checks stops a
 * theme from shipping a model that cannot be read. The sunlight theme exists for a fitter
 * standing on a roof; a dark viewport there is a defect, not a taste question.
 */
class ScenePaletteTest {
    private val themes = ThemePreference.entries

    private fun paletteOf(theme: ThemePreference) = scenePalette(theme, systemDark = false)

    /** Outer surfaces only: the bore is meant to recede, it is a hole. */
    private fun PlanRulerScenePalette.partColours() =
        bodyColours() + listOf(weld, selection)

    /** Large filled volumes a fitter has to tell apart at a glance. */
    private fun PlanRulerScenePalette.bodyColours() =
        listOf(pipe, elbow, flange, tee, reducer, cap)

    @Test
    fun `scene text is readable on its own backdrop in every theme`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            val ratio = contrastRatio(palette.onScene, palette.backdropTop)
            assertTrue(
                "$theme: scene text contrast is $ratio, below the 4.5 body-text floor",
                ratio >= 4.5,
            )
        }
    }

    @Test
    fun `every part stays visible against the backdrop even fully shaded`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            palette.partColours().forEach { colour ->
                val darkest = colour.shadedBy(palette.shadeFloor)
                val ratio = contrastRatio(darkest, palette.backdropTop)
                assertTrue(
                    "$theme: a shaded part reaches contrast $ratio against the backdrop",
                    ratio >= 1.6,
                )
            }
        }
    }

    @Test
    fun `part bodies remain distinguishable from each other`() {
        themes.forEach { theme ->
            val colours = paletteOf(theme).bodyColours()
            colours.indices.forEach { first ->
                (first + 1 until colours.size).forEach { second ->
                    val distance = colours[first].distanceTo(colours[second])
                    assertTrue(
                        "$theme: two body colours are only $distance apart",
                        distance >= 0.10,
                    )
                }
            }
        }
    }

    /** Welds and the selection are read as lines and highlights, so the bar is lower. */
    @Test
    fun `welds and the selection separate from the bodies they sit on`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            listOf(palette.weld, palette.selection).forEach { accent ->
                palette.bodyColours().forEach { body ->
                    val distance = accent.distanceTo(body)
                    assertTrue(
                        "$theme: an accent is only $distance from a body colour",
                        distance >= 0.08,
                    )
                }
            }
        }
    }

    @Test
    fun `dimension ink and grid never match the ground they are drawn on`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            assertTrue(
                "$theme: dimension ink is invisible",
                contrastRatio(palette.dimension, palette.backdropTop) >= 3.0,
            )
            assertTrue(
                "$theme: the grid is invisible",
                contrastRatio(palette.grid, palette.backdropTop) >= 1.25,
            )
        }
    }

    @Test
    fun `the glare and low vision themes carry shape in outlines`() {
        listOf(ThemePreference.SUNLIGHT, ThemePreference.HIGH_CONTRAST).forEach { theme ->
            val palette = paletteOf(theme)
            assertTrue("$theme must outline every part", palette.outlineEveryPart)
            assertTrue("$theme needs a heavier outline", palette.outlineWidth >= 1.5f)
        }
    }

    @Test
    fun `shading stays inside a usable range`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            assertTrue("$theme: shade floor out of range", palette.shadeFloor in 0.2f..0.9f)
            assertTrue("$theme: outline width must be positive", palette.outlineWidth > 0f)
        }
    }

    @Test
    fun `the system theme follows the platform setting`() {
        assertEquals(
            scenePalette(ThemePreference.DARK, systemDark = true),
            scenePalette(ThemePreference.SYSTEM, systemDark = true),
        )
        assertEquals(
            scenePalette(ThemePreference.LIGHT, systemDark = false),
            scenePalette(ThemePreference.SYSTEM, systemDark = false),
        )
    }

    @Test
    fun `the blueprint drawing reuses the scene ink`() {
        themes.forEach { theme ->
            val palette = paletteOf(theme)
            assertEquals(palette.backdropTop, palette.blueprintBackground)
            assertEquals(palette.pipe, palette.blueprintPipe)
            assertEquals(palette.onScene, palette.blueprintText)
        }
    }

    private fun Color.shadedBy(factor: Float) = Color(
        red = red * factor,
        green = green * factor,
        blue = blue * factor,
        alpha = alpha,
    )

    private fun Color.distanceTo(other: Color): Double {
        val dr = (red - other.red).toDouble()
        val dg = (green - other.green).toDouble()
        val db = (blue - other.blue).toDouble()
        return kotlin.math.sqrt(dr * dr + dg * dg + db * db) / kotlin.math.sqrt(3.0)
    }

    private fun contrastRatio(first: Color, second: Color): Double {
        val a = first.relativeLuminance()
        val b = second.relativeLuminance()
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun Color.relativeLuminance(): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
    }
}

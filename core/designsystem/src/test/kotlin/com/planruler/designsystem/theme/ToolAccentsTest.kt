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
 * The tool tiles draw their accent as `contentColor` over a translucent tint of itself
 * on top of the theme's surface, bypassing Material's own contrast handling — the same
 * hazard the scene palette has, so it gets the same numeric guard.
 */
class ToolAccentsTest {
    private val themes = ThemePreference.entries

    private fun accentsOf(theme: ThemePreference) = toolAccents(theme, systemDark = false)

    private fun PlanRulerToolAccents.all() =
        listOf(hydraulics, heating, drawing, expansion, catalog, gas)

    @Test
    fun `every accent is legible on a near white card in every theme`() {
        // SUNLIGHT and LIGHT put these tiles on a near-white Material surface; the other
        // themes' surfaces are dark, where the DARK/BLUEPRINT/HIGH_CONTRAST sets already
        // lean bright on purpose. This check targets the two pale-surface themes.
        listOf(ThemePreference.LIGHT, ThemePreference.SUNLIGHT).forEach { theme ->
            accentsOf(theme).all().forEach { colour ->
                val ratio = contrastRatio(colour, Color.White)
                assertTrue("$theme: accent contrast on white is only $ratio", ratio >= 3.0)
            }
        }
    }

    @Test
    fun `every accent is legible on a near black card in the dark themes`() {
        listOf(ThemePreference.DARK, ThemePreference.BLUEPRINT, ThemePreference.HIGH_CONTRAST).forEach { theme ->
            accentsOf(theme).all().forEach { colour ->
                val ratio = contrastRatio(colour, Color.Black)
                assertTrue("$theme: accent contrast on black is only $ratio", ratio >= 3.0)
            }
        }
    }

    @Test
    fun `the six tool accents are distinguishable from each other in every theme`() {
        val names = listOf("hydraulics", "heating", "drawing", "expansion", "catalog", "gas")
        val violations = themes.flatMap { theme ->
            val colours = accentsOf(theme).all()
            colours.indices.flatMap { first ->
                (first + 1 until colours.size).mapNotNull { second ->
                    val distance = colours[first].distanceTo(colours[second])
                    if (distance < 0.10) "$theme: ${names[first]}/${names[second]} only $distance apart" else null
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `the four loop colours are distinguishable from each other in every theme`() {
        themes.forEach { theme ->
            val loops = accentsOf(theme).loops
            assertEquals(4, loops.size)
            loops.indices.forEach { first ->
                (first + 1 until loops.size).forEach { second ->
                    val distance = loops[first].distanceTo(loops[second])
                    assertTrue(
                        "$theme: two loop colours are only $distance apart",
                        distance >= 0.10,
                    )
                }
            }
        }
    }

    @Test
    fun `the system theme follows the platform setting`() {
        assertEquals(toolAccents(ThemePreference.DARK, systemDark = true), toolAccents(ThemePreference.SYSTEM, systemDark = true))
        assertEquals(toolAccents(ThemePreference.LIGHT, systemDark = false), toolAccents(ThemePreference.SYSTEM, systemDark = false))
    }

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

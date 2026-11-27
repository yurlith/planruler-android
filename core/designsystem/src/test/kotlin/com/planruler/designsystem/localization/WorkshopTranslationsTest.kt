package com.planruler.designsystem.localization

import com.planruler.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkshopTranslationsTest {
    private val workshopMaps = mapOf(
        AppLanguage.GERMAN to workshopGermanUi,
        AppLanguage.POLISH to workshopPolishUi,
        AppLanguage.FRENCH to workshopFrenchUi,
        AppLanguage.ITALIAN to workshopItalianUi,
    )

    @Test
    fun `workshop copy covers every installer and 3D key in every language`() {
        assertEquals(154, workshopKeys.size)
        assertEquals(emptySet<String>(), missingGerman(workshopKeys))
        assertEquals(emptySet<String>(), missingPolish(workshopKeys))
        assertEquals(emptySet<String>(), missingFrench(workshopKeys))
        assertEquals(emptySet<String>(), missingItalian(workshopKeys))
        workshopMaps.forEach { (language, map) ->
            assertEquals("$language workshop keys", workshopKeys, map.keys)
            assertFalse("$language has a blank workshop translation", map.values.any(String::isBlank))
        }
    }

    @Test
    fun `workshop copy is what localizedUi returns`() {
        workshopMaps.forEach { (language, map) ->
            map.forEach { (english, translated) ->
                assertEquals("$language: $english", translated, localizedUi(language, "", english))
            }
        }
    }

    @Test
    fun `workshop copy keeps placeholders and fragment spacing`() {
        workshopMaps.forEach { (language, map) ->
            map.forEach { (english, translated) ->
                val expected = placeholders(english)
                val actual = placeholders(translated)
                // Positional arguments may move with the word order; plain ones must keep their order.
                if (expected.all { '$' in it }) {
                    assertEquals("$language: $english", expected.sorted(), actual.sorted())
                } else {
                    assertEquals("$language: $english", expected, actual)
                }
                // Fragments are glued to point names and values at the call site.
                assertEquals("$language: '$english'", english.endsWith(" "), translated.endsWith(" "))
                if (english.startsWith(" ")) {
                    assertFalse("$language: '$english'", translated.first().isLetterOrDigit())
                } else {
                    assertFalse("$language: '$english'", translated.first().isWhitespace())
                }
            }
        }
    }

    @Test
    fun `workshop copy never overrides older keys`() {
        val older = generatedGermanUi.keys + generatedPolishUi.keys + generatedFrenchUi.keys + generatedItalianUi.keys +
            baseGermanUi.keys + basePolishUi.keys + isometricGermanUi.keys + offsetGermanUi.keys
        workshopMaps.forEach { (language, map) ->
            assertEquals("$language overrides older copy", emptySet<String>(), map.keys.intersect(older))
        }
    }

    @Test
    fun `workshop trade words follow the offset copy`() {
        val offsetDoesNotFit = "The offset does not fit"
        assertEquals("Die Etage passt nicht", localizedUi(AppLanguage.GERMAN, "Этаж не помещается", offsetDoesNotFit))
        assertEquals("Odsadzka się nie mieści", localizedUi(AppLanguage.POLISH, "Этаж не помещается", offsetDoesNotFit))
        assertEquals("La baïonnette ne passe pas", localizedUi(AppLanguage.FRENCH, "Этаж не помещается", offsetDoesNotFit))
        assertEquals("Lo sfalsamento non ci sta", localizedUi(AppLanguage.ITALIAN, "Этаж не помещается", offsetDoesNotFit))
        assertEquals("Baugruppe", localizedUi(AppLanguage.GERMAN, "Монтажный узел", "Installation job"))
        assertEquals("Zespół montażowy", localizedUi(AppLanguage.POLISH, "Монтажный узел", "Installation job"))
        assertEquals("Anreißmaße ab Startpunkt", localizedUi(AppLanguage.GERMAN, "", "Set-out from the start point"))
        assertEquals("Schweißspalt", localizedUi(AppLanguage.GERMAN, "Зазор", "Weld gap"))
        assertEquals(
            "Przy 45° potrzeba co najmniej 820 mm wzdłuż trasy, teraz jest 600.0 mm.",
            localizedUi(AppLanguage.POLISH, "", "At %1\$s the run needs at least %2\$s mm, now %3\$s mm.")
                .format("45°", 820, "600.0"),
        )
        assertTrue(localizedUi(AppLanguage.FRENCH, "", "Enter the distance: from ").endsWith("entre "))
    }

    private fun placeholders(text: String): List<String> =
        Regex("%(?:\\d+\\$)?[a-zA-Z]").findAll(text).map { it.value }.toList()

    /** The English keys the pipe-calculator screens pass to t(ru, en) / localizedUi and this file covers. */
    private val workshopKeys: Set<String> = setOf(
        // AssemblyDrawingCard.kt
        "Creating PDF…",
        "PDF saved",
        "Could not save PDF",
        "Creating image…",
        "Image saved",
        "Could not save image",
        "Creating CSV…",
        "CSV saved",
        "Could not save CSV",
        "Creating field pack…",
        "Field pack saved",
        "Could not save field pack",
        "Working views and dimensions",
        "Every projection is generated from the current 3D assembly",
        "Drawing layer",
        "Building route…",
        "No route could be built for these dimensions",
        "Cuts and parts",
        "Tap a card — the same part is highlighted in the drawing and in 3D.",
        "Selected in drawing, list and 3D",
        "Clear",
        "Materials for this assembly",
        "Field mode",
        "Quick controls for glare, gloves and prolonged drawing use.",
        "Sunlight",
        "Keep screen on",
        "Check before cutting",
        "Remove check",
        "Checked by",
        "Checked — save",
        "PNG image",
        "Cuts CSV",
        "Save complete field pack ZIP",
        "ZIP contains PDF, PNG, CSV and the verification passport. Written dimensions override scale.",
        "Side",
        "End",
        "Cutting",
        "Parts",
        // FabricationWorkshop.kt
        "Unsaved calculation",
        "Project and installation job",
        "The calculation is saved automatically in the selected project.",
        "No projects yet: the calculation below works but is not saved. Create a project or import a plan to keep jobs.",
        "Recent calculations",
        "Autosave",
        "Installation job",
        "New job",
        "Calculation history",
        "Move to bin",
        "Job recycle bin",
        "Job name",
        "The calculation below is not saved yet. New job keeps it in the project with the sizes entered.",
        "Without a project this calculation is temporary. Create a project to enable autosave.",
        // HeatDesignPages.kt
        "Infiltration, 1/h",
        "Heat-recovery efficiency, 0-1",
        "Advanced parameters",
        "Hide advanced parameters",
        "Pick a construction",
        "Heat-loss breakdown",
        "Walls",
        "Roof",
        "Floor",
        "Windows",
        "Ventilation",
        "Thermal bridges",
        "Critical hydraulic circuit is linked.",
        "Pressure loss appears after a hydraulic circuit is calculated in the hydraulics tab; tab results are linked.",
        // InstallerInputWizard.kt
        "prepared template",
        "automatic route",
        "What are you assembling?",
        "Choose a diagram and the app will select the calculation method.",
        "Basic",
        "Advanced",
        "Which points did you measure between?",
        "Start: ",
        "End: ",
        "Enter the distance: from ",
        " to ",
        "Along the run",
        "Side offset",
        "Rise / drop",
        "← left",
        "right →",
        "↑ rise",
        "↓ drop",
        "Which way does the end face?",
        "Where the offset sits",
        "Cut before the offset",
        "Min. straight",
        "Weld gap",
        "Saw kerf",
        "Calculation method: ",
        "End point",
        "Start point",
        "Flange to flange",
        "Flat offset",
        "Turn with offset",
        "Obstacle bypass",
        "U-turn",
        "Tee branch",
        "Free assembly",
        "↑ up",
        "↓ down",
        "Near the start",
        "Centred",
        "Near the end",
        "Exact, mm",
        "→ forward",
        "→ right",
        "↩ back",
        "carbon steel",
        "stainless steel",
        "copper",
        "ppr",
        "other",
        "pipe axis",
        "pipe outer edge",
        "pipe inner edge",
        "pipe face",
        "fitting center",
        "fitting face",
        "flange face",
        // ParametricAssembly3DWorkspace.kt
        "Shape and cut verification without engineering coordinates",
        "Automatic calculation",
        "Prepared diagram",
        "Finding a fabricable route…",
        "Route ready",
        "Closure error",
        "Job template ready",
        "straight sections",
        "Preparing calculation…",
        "Measurement-point correction applied",
        "Set-out from the start point",
        "1st bend centre",
        "2nd bend centre",
        "Between bend centres",
        "Offset between axes",
        "The offset does not fit",
        "At %1\$s the run needs at least %2\$s mm, now %3\$s mm.",
        "At %1\$s the offset must be at least %2\$s mm, now %3\$s mm.",
        "The cut before the offset can be at most %s mm, or no pipe is left after it.",
        "No standard angle fits between these ends; check the measurements.",
        "Fits with an angle of:",
        // PipeCalculatorScreen.kt
        "DOWFROST table: 30–50 vol% and 10–120 °C. Bilinear interpolation is used between published nodes; extrapolation is rejected.",
        "These are open manufacturer catalog data associated with EN/DIN, not the full standard. Verify the actual material, execution and current product datasheet before ordering.",
        "Technical elbow contour with inner/outer arcs, weld ends, centerline and α, R, A, ØD and s dimensions.",
        "Dimensioned two-elbow diagram: L is center to center, F is weld-face to weld-face and C is the actual pipe length between cut marks 2–3.",
        "Formula C = H / sin(α) − 2A − 2g. The stock plan assumes one cut per insert; verify both actual elbow take-outs and the welding procedure before cutting.",
        "Selected open DN series for pipes and EN 10253-4/A fittings, plus PN 6–40 flange connecting dimensions according to DIN EN 1092-1.",
        "Technical flange contour: exact D, bolt circle k, hole count and d₂. The side profile is schematic.",
        "Transition flow regime: friction has increased uncertainty.",
        "Velocity exceeds 2 m/s; verify noise, erosion and project limits.",
        "Fluid properties are advisory and require project-specific verification.",
        "Preliminary result: verify against the licensed EN 12828 and SIA 384/1 editions.",
        "Select the next suitable vessel and verify its pressure/temperature limits separately.",
    )
}

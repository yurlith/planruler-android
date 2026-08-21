package com.planruler.designsystem.localization

import com.planruler.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

class LocalizationTest {
    @Test
    fun `every release language covers the complete static key set`() {
        val required = generatedEnglishKeySet()
        assertTrue(required.size >= 500)
        assertEquals(emptySet<String>(), missingPolish(required))
        assertEquals(emptySet<String>(), missingGerman(required))
        assertEquals(emptySet<String>(), missingFrench(required))
        assertEquals(emptySet<String>(), missingItalian(required))
        assertFalse(polishUi.values.any(String::isBlank))
        assertFalse(germanUi.values.any(String::isBlank))
        assertFalse(frenchUi.values.any(String::isBlank))
        assertFalse(italianUi.values.any(String::isBlank))
    }

    @Test
    fun `language selection uses requested copy`() {
        assertEquals("Projekty", localizedUi(AppLanguage.POLISH, "Проекты", "Projects"))
        assertEquals("Projects", localizedUi(AppLanguage.ENGLISH, "Проекты", "Projects"))
        assertEquals("Projekte", localizedUi(AppLanguage.GERMAN, "Проекты", "Projects"))
        assertEquals("Projets", localizedUi(AppLanguage.FRENCH, "Проекты", "Projects"))
        assertEquals("Progetti", localizedUi(AppLanguage.ITALIAN, "Проекты", "Projects"))
        assertEquals("Проекты", localizedUi(AppLanguage.RUSSIAN, "Проекты", "Projects"))
    }

    @Test
    fun `dynamic measurement copy is localized`() {
        assertEquals(
            "Bieżąca skala wskazuje: 12,5 m",
            localizedUi(AppLanguage.POLISH, "", "Current scale measures: 12,5 m"),
        )
        assertEquals(
            "Seite 3 ist nicht verfügbar.",
            localizedUi(AppLanguage.GERMAN, "", "Page 3 is unavailable."),
        )
    }

    @Test
    fun `typed shell catalogue covers every key without blanks`() {
        val catalog = typedUiCatalog()
        assertEquals(UiTextKey.entries.toSet(), catalog.keys)
        catalog.forEach { (key, translations) ->
            translations.all().forEach { value ->
                assertTrue("$key contains a blank release translation", value.isNotBlank())
            }
        }
    }

    @Test
    fun `typed shell placeholders match in all release languages`() {
        val placeholder = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]")
        typedUiCatalog().forEach { (key, translations) ->
            val expected = placeholder.matcher(translations.english).let { matcher ->
                buildList { while (matcher.find()) add(matcher.group()) }
                    .map { it.substringAfter('$', it) }
            }
            translations.all().forEach { value ->
                val actual = placeholder.matcher(value).let { matcher ->
                    buildList { while (matcher.find()) add(matcher.group()) }
                        .map { it.substringAfter('$', it) }
                }
                assertEquals("$key has incompatible formatting placeholders", expected, actual)
            }
        }
    }

    @Test
    fun `typed shell selects every requested release language`() {
        assertEquals("Warsztat", uiText(AppLanguage.POLISH, UiTextKey.NAV_WORKSHOP))
        assertEquals("Workshop", uiText(AppLanguage.ENGLISH, UiTextKey.NAV_WORKSHOP))
        assertEquals("Werkstatt", uiText(AppLanguage.GERMAN, UiTextKey.NAV_WORKSHOP))
        assertEquals("Atelier", uiText(AppLanguage.FRENCH, UiTextKey.NAV_WORKSHOP))
        assertEquals("Officina", uiText(AppLanguage.ITALIAN, UiTextKey.NAV_WORKSHOP))
        assertEquals("Мастерская", uiText(AppLanguage.RUSSIAN, UiTextKey.NAV_WORKSHOP))
    }

    @Test
    fun `reviewed engineering glossary keeps job and calculation meanings`() {
        assertEquals("Kunden und Aufträge", localizedUi(AppLanguage.GERMAN, "Клиенты и работы", "Clients and jobs"))
        assertEquals("Klienci i zlecenia", localizedUi(AppLanguage.POLISH, "Клиенты и работы", "Clients and jobs"))
        assertEquals("Clients et interventions", localizedUi(AppLanguage.FRENCH, "Клиенты и работы", "Clients and jobs"))
        assertEquals("Clienti e commesse", localizedUi(AppLanguage.ITALIAN, "Клиенты и работы", "Clients and jobs"))
        assertEquals("Heizlast und Auslegung", localizedUi(AppLanguage.GERMAN, "Теплорасчёт", "Heat design"))
        assertEquals("Obliczenia cieplne", localizedUi(AppLanguage.POLISH, "Теплорасчёт", "Heat design"))
    }

    @Test
    fun `project count follows polish and russian plural forms`() {
        assertEquals("1 projekt", formatProjectCount(AppLanguage.POLISH, 1))
        assertEquals("3 projekty", formatProjectCount(AppLanguage.POLISH, 3))
        assertEquals("14 projektów", formatProjectCount(AppLanguage.POLISH, 14))
        assertEquals("1 проект", formatProjectCount(AppLanguage.RUSSIAN, 1))
        assertEquals("22 проекта", formatProjectCount(AppLanguage.RUSSIAN, 22))
        assertEquals("25 проектов", formatProjectCount(AppLanguage.RUSSIAN, 25))
    }

    private fun generatedEnglishKeySet(): Set<String> =
        generatedGermanUi.keys + generatedPolishUi.keys + generatedFrenchUi.keys + generatedItalianUi.keys
}

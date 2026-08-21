package com.planruler.designsystem.localization

import com.planruler.model.AppLanguage

/**
 * Small, platform-independent localisation gateway used by the feature copy models.
 * English is the stable key because it is already present at every call site.
 */
fun localizedUi(language: AppLanguage, russian: String, english: String): String = when (language) {
    AppLanguage.POLISH -> polishUi[english] ?: dynamicPolish(english) ?: english
    AppLanguage.ENGLISH -> english
    AppLanguage.GERMAN -> germanUi[english] ?: dynamicGerman(english) ?: english
    AppLanguage.FRENCH -> frenchUi[english] ?: dynamicFrench(english) ?: english
    AppLanguage.ITALIAN -> italianUi[english] ?: dynamicItalian(english) ?: english
    AppLanguage.RUSSIAN -> russian
}

internal fun missingPolish(keys: Set<String>): Set<String> =
    keys.filterNotTo(linkedSetOf()) { it in polishUi || dynamicEnglishKeys.any(it::matches) }

internal fun missingGerman(keys: Set<String>): Set<String> =
    keys.filterNotTo(linkedSetOf()) { it in germanUi || dynamicEnglishKeys.any(it::matches) }

internal fun missingFrench(keys: Set<String>): Set<String> =
    keys.filterNotTo(linkedSetOf()) { it in frenchUi || dynamicEnglishKeys.any(it::matches) }

internal fun missingItalian(keys: Set<String>): Set<String> =
    keys.filterNotTo(linkedSetOf()) { it in italianUi || dynamicEnglishKeys.any(it::matches) }

private val dynamicEnglishKeys = listOf(
    Regex("Preview: 1 .+ = .+ m"),
    Regex("Current scale measures: .+ .+"),
    Regex("Difference: .+%"),
    Regex("The segment covers .+ % of the page width\\. A longer reference is more precise\\."),
    Regex("Apply properties to .+ existing measurements \\(geometry is preserved\\)"),
    Regex("Page .+ is unavailable\\."),
    Regex("“.+” and its measurements will be removed from this device\\."),
    Regex("Revision .+"),
)

private fun dynamicPolish(english: String): String? = when {
    english.startsWith("Preview: 1 PDF point = ") ->
        "Podgląd: 1 punkt PDF = ${english.substringAfter(" = ")}"
    english.startsWith("Preview: 1 ") ->
        "Podgląd: 1 ${english.substringAfter("Preview: 1 ")}"
    english.startsWith("Current scale measures: ") ->
        "Bieżąca skala wskazuje: ${english.substringAfter(": ")}"
    english.startsWith("Difference: ") ->
        "Różnica: ${english.substringAfter(": ")}"
    english.startsWith("The segment covers ") ->
        "Odcinek zajmuje ${english.substringAfter("covers ").substringBefore(" of the page width")}" +
            " szerokości strony. Dłuższy odcinek odniesienia zapewnia większą dokładność."
    english.startsWith("Apply properties to ") ->
        "Zastosuj właściwości do ${english.substringAfter("to ").substringBefore(" existing measurements")}" +
            " istniejących pomiarów (geometria zostanie zachowana)"
    english.startsWith("Page ") && english.endsWith(" is unavailable.") ->
        "Strona ${english.substringAfter("Page ").substringBefore(" is unavailable")}" + " jest niedostępna."
    english.startsWith("“") && english.endsWith("and its measurements will be removed from this device.") ->
        "${english.substringBefore(" and its measurements")} i jego pomiary zostaną usunięte z urządzenia."
    english.startsWith("Revision ") -> "Rewizja ${english.substringAfter("Revision ")}"
    else -> null
}

private fun dynamicGerman(english: String): String? = when {
    english.startsWith("Preview: 1 PDF point = ") ->
        "Vorschau: 1 PDF-Punkt = ${english.substringAfter(" = ")}"
    english.startsWith("Preview: 1 ") ->
        "Vorschau: 1 ${english.substringAfter("Preview: 1 ")}"
    english.startsWith("Current scale measures: ") ->
        "Der aktuelle Maßstab ergibt: ${english.substringAfter(": ")}"
    english.startsWith("Difference: ") ->
        "Abweichung: ${english.substringAfter(": ")}"
    english.startsWith("The segment covers ") ->
        "Die Strecke umfasst ${english.substringAfter("covers ").substringBefore(" of the page width")}" +
            " der Seitenbreite. Eine längere Referenz ist genauer."
    english.startsWith("Apply properties to ") ->
        "Eigenschaften auf ${english.substringAfter("to ").substringBefore(" existing measurements")}" +
            " vorhandene Messungen anwenden (Geometrie bleibt erhalten)"
    english.startsWith("Page ") && english.endsWith(" is unavailable.") ->
        "Seite ${english.substringAfter("Page ").substringBefore(" is unavailable")}" + " ist nicht verfügbar."
    english.startsWith("“") && english.endsWith("and its measurements will be removed from this device.") ->
        "${english.substringBefore(" and its measurements")} und die zugehörigen Messungen werden vom Gerät entfernt."
    english.startsWith("Revision ") -> "Revision ${english.substringAfter("Revision ")}"
    else -> null
}

private fun dynamicFrench(english: String): String? = when {
    english.startsWith("Preview: 1 PDF point = ") ->
        "Aperçu : 1 point PDF = ${english.substringAfter(" = ")}"
    english.startsWith("Preview: 1 ") ->
        "Aperçu : 1 ${english.substringAfter("Preview: 1 ")}"
    english.startsWith("Current scale measures: ") ->
        "L’échelle actuelle mesure : ${english.substringAfter(": ")}"
    english.startsWith("Difference: ") -> "Différence : ${english.substringAfter(": ")}"
    english.startsWith("The segment covers ") ->
        "Le segment couvre ${english.substringAfter("covers ").substringBefore(" of the page width")}" +
            " de la largeur de la page. Une référence plus longue est plus précise."
    english.startsWith("Apply properties to ") ->
        "Appliquer les propriétés à ${english.substringAfter("to ").substringBefore(" existing measurements")}" +
            " mesures existantes (géométrie conservée)"
    english.startsWith("Page ") && english.endsWith(" is unavailable.") ->
        "La page ${english.substringAfter("Page ").substringBefore(" is unavailable")} n’est pas disponible."
    english.startsWith("Revision ") -> "Révision ${english.substringAfter("Revision ")}"
    else -> null
}

private fun dynamicItalian(english: String): String? = when {
    english.startsWith("Preview: 1 PDF point = ") ->
        "Anteprima: 1 punto PDF = ${english.substringAfter(" = ")}"
    english.startsWith("Preview: 1 ") ->
        "Anteprima: 1 ${english.substringAfter("Preview: 1 ")}"
    english.startsWith("Current scale measures: ") ->
        "La scala attuale misura: ${english.substringAfter(": ")}"
    english.startsWith("Difference: ") -> "Differenza: ${english.substringAfter(": ")}"
    english.startsWith("The segment covers ") ->
        "Il segmento copre ${english.substringAfter("covers ").substringBefore(" of the page width")}" +
            " della larghezza della pagina. Un riferimento più lungo è più preciso."
    english.startsWith("Apply properties to ") ->
        "Applica le proprietà a ${english.substringAfter("to ").substringBefore(" existing measurements")}" +
            " misure esistenti (geometria conservata)"
    english.startsWith("Page ") && english.endsWith(" is unavailable.") ->
        "La pagina ${english.substringAfter("Page ").substringBefore(" is unavailable")} non è disponibile."
    english.startsWith("Revision ") -> "Revisione ${english.substringAfter("Revision ")}"
    else -> null
}

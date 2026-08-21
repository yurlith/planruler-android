package com.planruler.designsystem.localization

import com.planruler.model.AppLanguage
import java.util.Locale

/**
 * Stable identifiers for the redesigned application shell.
 *
 * Unlike [localizedUi], the English sentence is not the lookup key and a supported
 * language can never silently fall back to English. Existing screens are migrated
 * gradually; every new shell screen must use this catalogue.
 */
enum class UiTextKey {
    APP_NAME,
    NAV_HOME,
    NAV_PROJECTS,
    NAV_WORKSHOP,
    NAV_CRM,
    NAV_MENU,
    HOME_TITLE,
    HOME_SUBTITLE,
    CONTINUE_PROJECT,
    QUICK_ACTIONS,
    IMPORT_PLAN,
    OPEN_WORKSHOP,
    OPEN_CRM,
    RECENT_PROJECTS,
    NO_RECENT_PROJECTS,
    LOCAL_ONLY,
    LOCAL_ONLY_BODY,
    PROJECT_COUNT_ONE,
    PROJECT_COUNT_FEW,
    PROJECT_COUNT_MANY,
    PROJECTS_TITLE,
    PROJECTS_SUBTITLE,
    FILTER_ACTIVE,
    FILTER_RECENT,
    FILTER_TRASH,
    WORKSHOP_TITLE,
    WORKSHOP_SUBTITLE,
    TOOL_INSTALLATION,
    TOOL_INSTALLATION_BODY,
    TOOL_HYDRAULICS,
    TOOL_HYDRAULICS_BODY,
    TOOL_HEATING,
    TOOL_HEATING_BODY,
    TOOL_EXPANSION,
    TOOL_EXPANSION_BODY,
    TOOL_CATALOG,
    TOOL_CATALOG_BODY,
    TOOL_GAS,
    TOOL_GAS_BODY,
    WORKSHOP_MODEL,
    WORKSHOP_PARAMETERS,
    WORKSHOP_DRAWING,
    WORKSHOP_CUT_LIST,
    MENU_TITLE,
    MENU_SUBTITLE,
    MENU_PROFILE,
    MENU_PROFILE_BODY,
    MENU_SETTINGS,
    MENU_SETTINGS_BODY,
    MENU_BACKUP,
    MENU_BACKUP_BODY,
    MENU_NORMS,
    MENU_NORMS_BODY,
    MENU_PRIVACY,
    MENU_PRIVACY_BODY,
    BACK_TO_MENU,
    BACK_TO_WORKSHOP,
    THEME_SUNLIGHT,
    THEME_BLUEPRINT,
    OPEN,
}

internal data class ReleaseTranslations(
    val polish: String,
    val english: String,
    val german: String,
    val french: String,
    val italian: String,
    val russian: String,
) {
    operator fun get(language: AppLanguage): String = when (language) {
        AppLanguage.POLISH -> polish
        AppLanguage.ENGLISH -> english
        AppLanguage.GERMAN -> german
        AppLanguage.FRENCH -> french
        AppLanguage.ITALIAN -> italian
        AppLanguage.RUSSIAN -> russian
    }

    fun all(): List<String> = listOf(polish, english, german, french, italian, russian)
}

private fun tr(
    pl: String,
    en: String,
    de: String,
    fr: String,
    it: String,
    ru: String,
) = ReleaseTranslations(pl, en, de, fr, it, ru)

private val releaseUiText: Map<UiTextKey, ReleaseTranslations> = mapOf(
    UiTextKey.APP_NAME to tr("PlanRuler", "PlanRuler", "PlanRuler", "PlanRuler", "PlanRuler", "PlanRuler"),
    UiTextKey.NAV_HOME to tr("Start", "Home", "Start", "Accueil", "Home", "Главная"),
    UiTextKey.NAV_PROJECTS to tr("Projekty", "Projects", "Projekte", "Projets", "Progetti", "Проекты"),
    UiTextKey.NAV_WORKSHOP to tr("Warsztat", "Workshop", "Werkstatt", "Atelier", "Officina", "Мастерская"),
    UiTextKey.NAV_CRM to tr("CRM", "CRM", "CRM", "CRM", "CRM", "CRM"),
    UiTextKey.NAV_MENU to tr("Menu", "Menu", "Menü", "Menu", "Menu", "Меню"),
    UiTextKey.HOME_TITLE to tr("Centrum pracy", "Work centre", "Arbeitszentrale", "Espace de travail", "Centro di lavoro", "Рабочий центр"),
    UiTextKey.HOME_SUBTITLE to tr(
        "Projekty, pomiary i obliczenia w jednym miejscu",
        "Projects, measurements and calculations in one place",
        "Projekte, Aufmaße und Berechnungen an einem Ort",
        "Projets, mesures et calculs au même endroit",
        "Progetti, misure e calcoli in un unico posto",
        "Проекты, замеры и расчёты в одном месте",
    ),
    UiTextKey.CONTINUE_PROJECT to tr("Kontynuuj projekt", "Continue project", "Projekt fortsetzen", "Continuer le projet", "Continua il progetto", "Продолжить проект"),
    UiTextKey.QUICK_ACTIONS to tr("Szybkie działania", "Quick actions", "Schnellaktionen", "Actions rapides", "Azioni rapide", "Быстрые действия"),
    UiTextKey.IMPORT_PLAN to tr("Importuj plan", "Import plan", "Plan importieren", "Importer un plan", "Importa planimetria", "Импортировать план"),
    UiTextKey.OPEN_WORKSHOP to tr("Otwórz warsztat", "Open workshop", "Werkstatt öffnen", "Ouvrir l’atelier", "Apri l’officina", "Открыть мастерскую"),
    UiTextKey.OPEN_CRM to tr("Otwórz CRM", "Open CRM", "CRM öffnen", "Ouvrir le CRM", "Apri il CRM", "Открыть CRM"),
    UiTextKey.RECENT_PROJECTS to tr("Ostatnie projekty", "Recent projects", "Letzte Projekte", "Projets récents", "Progetti recenti", "Недавние проекты"),
    UiTextKey.NO_RECENT_PROJECTS to tr("Brak ostatnich projektów", "No recent projects", "Keine letzten Projekte", "Aucun projet récent", "Nessun progetto recente", "Недавних проектов пока нет"),
    UiTextKey.LOCAL_ONLY to tr("Tylko na urządzeniu", "On-device only", "Nur auf dem Gerät", "Uniquement sur l’appareil", "Solo sul dispositivo", "Только на устройстве"),
    UiTextKey.LOCAL_ONLY_BODY to tr(
        "Projekty, CRM i kopie zapasowe pozostają pod Twoją kontrolą.",
        "Projects, CRM and backups remain under your control.",
        "Projekte, CRM und Sicherungen bleiben unter Ihrer Kontrolle.",
        "Les projets, le CRM et les sauvegardes restent sous votre contrôle.",
        "Progetti, CRM e backup restano sotto il tuo controllo.",
        "Проекты, CRM и резервные копии остаются под вашим контролем.",
    ),
    UiTextKey.PROJECT_COUNT_ONE to tr("%1\$d projekt", "%1\$d project", "%1\$d Projekt", "%1\$d projet", "%1\$d progetto", "%1\$d проект"),
    UiTextKey.PROJECT_COUNT_FEW to tr("%1\$d projekty", "%1\$d projects", "%1\$d Projekte", "%1\$d projets", "%1\$d progetti", "%1\$d проекта"),
    UiTextKey.PROJECT_COUNT_MANY to tr("%1\$d projektów", "%1\$d projects", "%1\$d Projekte", "%1\$d projets", "%1\$d progetti", "%1\$d проектов"),
    UiTextKey.PROJECTS_TITLE to tr("Projekty", "Projects", "Projekte", "Projets", "Progetti", "Проекты"),
    UiTextKey.PROJECTS_SUBTITLE to tr("Plany, pomiary i zestawienia", "Plans, measurements and takeoffs", "Pläne, Aufmaße und Auszüge", "Plans, mesures et quantitatifs", "Planimetrie, misure e computi", "Планы, измерения и ведомости"),
    UiTextKey.FILTER_ACTIVE to tr("Aktywne", "Active", "Aktiv", "Actifs", "Attivi", "Активные"),
    UiTextKey.FILTER_RECENT to tr("Ostatnie", "Recent", "Zuletzt", "Récents", "Recenti", "Недавние"),
    UiTextKey.FILTER_TRASH to tr("Kosz", "Trash", "Papierkorb", "Corbeille", "Cestino", "Корзина"),
    UiTextKey.WORKSHOP_TITLE to tr("Warsztat inżynierski", "Engineering workshop", "Technische Werkstatt", "Atelier technique", "Officina tecnica", "Инженерная мастерская"),
    UiTextKey.WORKSHOP_SUBTITLE to tr(
        "Dobierz narzędzie, a następnie pracuj na osobnym ekranie.",
        "Choose a tool, then work in a dedicated workspace.",
        "Werkzeug wählen und anschließend im eigenen Arbeitsbereich arbeiten.",
        "Choisissez un outil, puis travaillez dans un espace dédié.",
        "Scegli uno strumento e lavora in uno spazio dedicato.",
        "Выберите инструмент и работайте в отдельной рабочей области.",
    ),
    UiTextKey.TOOL_INSTALLATION to tr("Montaż i 3D", "Fabrication and 3D", "Rohrbau und 3D", "Fabrication et 3D", "Prefabbricazione e 3D", "Монтаж и 3D"),
    UiTextKey.TOOL_INSTALLATION_BODY to tr("Kolana, kołnierze, wstawki i trasy przestrzenne", "Elbows, flanges, cut pieces and spatial routes", "Bögen, Flansche, Passstücke und räumliche Trassen", "Coudes, brides, manchettes et tracés 3D", "Curve, flange, tronchetti e percorsi 3D", "Отводы, фланцы, вставки и пространственные трассы"),
    UiTextKey.TOOL_HYDRAULICS to tr("Hydraulika", "Hydraulics", "Hydraulik", "Hydraulique", "Idraulica", "Гидравлика"),
    UiTextKey.TOOL_HYDRAULICS_BODY to tr("Przepływ, prędkość i straty ciśnienia", "Flow, velocity and pressure loss", "Volumenstrom, Geschwindigkeit und Druckverlust", "Débit, vitesse et perte de charge", "Portata, velocità e perdita di carico", "Расход, скорость и потери давления"),
    UiTextKey.TOOL_HEATING to tr("Obliczenia cieplne", "Heat design", "Heizlast und Auslegung", "Calcul thermique", "Calcolo termico", "Тепловой расчёт"),
    UiTextKey.TOOL_HEATING_BODY to tr("Straty ciepła, grzejniki i pompa ciepła", "Heat loss, emitters and heat-pump sizing", "Wärmeverlust, Heizflächen und Wärmepumpe", "Déperditions, émetteurs et pompe à chaleur", "Dispersioni, terminali e pompa di calore", "Теплопотери, приборы и тепловой насос"),
    UiTextKey.TOOL_EXPANSION to tr("Naczynie wzbiorcze", "Expansion vessel", "Ausdehnungsgefäß", "Vase d’expansion", "Vaso di espansione", "Расширительный бак"),
    UiTextKey.TOOL_EXPANSION_BODY to tr("Wstępny dobór dla zamkniętego układu", "Preliminary sizing for a closed system", "Vorauslegung für eine geschlossene Anlage", "Pré-dimensionnement d’un circuit fermé", "Predimensionamento per impianto chiuso", "Предварительный подбор для закрытой системы"),
    UiTextKey.TOOL_CATALOG to tr("Katalogi DN/PN", "DN/PN catalogues", "DN/PN-Kataloge", "Catalogues DN/PN", "Cataloghi DN/PN", "Каталоги DN/PN"),
    UiTextKey.TOOL_CATALOG_BODY to tr("Rury, kolana, trójniki, redukcje i kołnierze", "Pipes, elbows, tees, reducers and flanges", "Rohre, Bögen, T-Stücke, Reduzierungen und Flansche", "Tubes, coudes, tés, réductions et brides", "Tubi, curve, raccordi a T, riduzioni e flange", "Трубы, отводы, тройники, переходы и фланцы"),
    UiTextKey.TOOL_GAS to tr("Gaz — Szwajcaria", "Gas — Switzerland", "Gas — Schweiz", "Gaz — Suisse", "Gas — Svizzera", "Газ — Швейцария"),
    UiTextKey.TOOL_GAS_BODY to tr("Kontrolowany moduł zgodności SVGW", "Controlled SVGW compliance module", "Kontrolliertes SVGW-Regelwerksmodul", "Module contrôlé de conformité SVGW", "Modulo controllato di conformità SVGW", "Контролируемый модуль соответствия SVGW"),
    UiTextKey.WORKSHOP_MODEL to tr("Model 3D", "3D model", "3D-Modell", "Modèle 3D", "Modello 3D", "3D-модель"),
    UiTextKey.WORKSHOP_PARAMETERS to tr("Parametry", "Parameters", "Parameter", "Paramètres", "Parametri", "Параметры"),
    UiTextKey.WORKSHOP_DRAWING to tr("Rysunek", "Drawing", "Zeichnung", "Plan", "Disegno", "Чертёж"),
    UiTextKey.WORKSHOP_CUT_LIST to tr("Cięcie", "Cut list", "Zuschnitt", "Débit", "Lista di taglio", "Раскрой"),
    UiTextKey.MENU_TITLE to tr("Menu", "Menu", "Menü", "Menu", "Menu", "Меню"),
    UiTextKey.MENU_SUBTITLE to tr("Dane, ustawienia i pomoc", "Data, settings and help", "Daten, Einstellungen und Hilfe", "Données, réglages et aide", "Dati, impostazioni e assistenza", "Данные, настройки и помощь"),
    UiTextKey.MENU_PROFILE to tr("Profil lokalny", "Local profile", "Lokales Profil", "Profil local", "Profilo locale", "Локальный профиль"),
    UiTextKey.MENU_PROFILE_BODY to tr("Konto właściciela i blokada PIN bez serwera", "Owner profile and PIN lock without a server", "Eigentümerprofil und PIN-Sperre ohne Server", "Profil propriétaire et verrouillage PIN sans serveur", "Profilo proprietario e blocco PIN senza server", "Профиль владельца и PIN-блокировка без сервера"),
    UiTextKey.MENU_SETTINGS to tr("Ustawienia pracy", "Work settings", "Arbeitseinstellungen", "Réglages de travail", "Impostazioni di lavoro", "Рабочие настройки"),
    UiTextKey.MENU_SETTINGS_BODY to tr("Język, jednostki, sterowanie i wygląd", "Language, units, controls and appearance", "Sprache, Einheiten, Bedienung und Darstellung", "Langue, unités, commandes et apparence", "Lingua, unità, comandi e aspetto", "Язык, единицы, управление и оформление"),
    UiTextKey.MENU_BACKUP to tr("Dane i kopia zapasowa", "Data and backup", "Daten und Sicherung", "Données et sauvegarde", "Dati e backup", "Данные и резервная копия"),
    UiTextKey.MENU_BACKUP_BODY to tr("Szyfrowany plik lokalny, bez chmury", "Encrypted local file, with no cloud", "Verschlüsselte lokale Datei, ohne Cloud", "Fichier local chiffré, sans cloud", "File locale cifrato, senza cloud", "Зашифрованный локальный файл без облака"),
    UiTextKey.MENU_NORMS to tr("Katalogi i normy", "Catalogues and standards", "Kataloge und Normen", "Catalogues et normes", "Cataloghi e norme", "Каталоги и нормативы"),
    UiTextKey.MENU_NORMS_BODY to tr("Źródła danych, wydania i ograniczenia", "Data sources, editions and limitations", "Datenquellen, Ausgaben und Einschränkungen", "Sources, éditions et limites", "Fonti, edizioni e limitazioni", "Источники данных, редакции и ограничения"),
    UiTextKey.MENU_PRIVACY to tr("Prywatność", "Privacy", "Datenschutz", "Confidentialité", "Privacy", "Конфиденциальность"),
    UiTextKey.MENU_PRIVACY_BODY to tr("Przetwarzanie lokalne i brak wysyłania danych", "Local processing with no data upload", "Lokale Verarbeitung ohne Datenübertragung", "Traitement local sans envoi de données", "Elaborazione locale senza invio di dati", "Локальная обработка без отправки данных"),
    UiTextKey.BACK_TO_MENU to tr("Wróć do menu", "Back to menu", "Zurück zum Menü", "Retour au menu", "Torna al menu", "Вернуться в меню"),
    UiTextKey.BACK_TO_WORKSHOP to tr("Wróć do warsztatu", "Back to workshop", "Zurück zur Werkstatt", "Retour à l’atelier", "Torna all’officina", "Вернуться в мастерскую"),
    UiTextKey.THEME_SUNLIGHT to tr("Światło dzienne", "Sunlight", "Sonnenlicht", "Lumière du jour", "Luce diurna", "Дневной свет"),
    UiTextKey.THEME_BLUEPRINT to tr("Plan techniczny", "Blueprint", "Technische Zeichnung", "Plan technique", "Disegno tecnico", "Чертёж"),
    UiTextKey.OPEN to tr("Otwórz", "Open", "Öffnen", "Ouvrir", "Apri", "Открыть"),
)

fun uiText(language: AppLanguage, key: UiTextKey): String =
    releaseUiText.getValue(key)[language]

fun formatUiText(language: AppLanguage, key: UiTextKey, vararg arguments: Any): String =
    String.format(uiLocale(language), uiText(language, key), *arguments)

fun formatProjectCount(language: AppLanguage, count: Int): String {
    val key = when {
        count == 1 -> UiTextKey.PROJECT_COUNT_ONE
        language in setOf(AppLanguage.POLISH, AppLanguage.RUSSIAN) &&
            count % 10 in 2..4 && count % 100 !in 12..14 -> UiTextKey.PROJECT_COUNT_FEW
        else -> UiTextKey.PROJECT_COUNT_MANY
    }
    return formatUiText(language, key, count)
}

fun uiLocale(language: AppLanguage): Locale = when (language) {
    AppLanguage.POLISH -> Locale.forLanguageTag("pl-PL")
    AppLanguage.ENGLISH -> Locale.forLanguageTag("en-GB")
    AppLanguage.GERMAN -> Locale.forLanguageTag("de-CH")
    AppLanguage.FRENCH -> Locale.forLanguageTag("fr-CH")
    AppLanguage.ITALIAN -> Locale.forLanguageTag("it-CH")
    AppLanguage.RUSSIAN -> Locale.forLanguageTag("ru-RU")
}

internal fun typedUiCatalog(): Map<UiTextKey, ReleaseTranslations> = releaseUiText

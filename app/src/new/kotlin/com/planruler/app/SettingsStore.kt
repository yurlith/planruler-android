package com.planruler.app

import android.content.Context
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.Handedness
import com.planruler.model.LengthUnit
import com.planruler.model.MagnifierPlacement
import com.planruler.model.ProjectSort
import com.planruler.model.ProjectView
import com.planruler.model.SnapMode
import com.planruler.model.ThemePreference
import com.planruler.model.TouchProfile
import java.util.Locale

/**
 * Preferences live in the composition root: feature modules receive [AppSettings]
 * and a callback, never a storage API.
 */
class SettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("planruler-settings", Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        language = enum(KEY_LANGUAGE, AppLanguage.entries, defaultLanguage()),
        theme = enum(KEY_THEME, ThemePreference.entries, ThemePreference.SYSTEM),
        dynamicColor = preferences.getBoolean(KEY_DYNAMIC, false),
        touchProfile = enum(KEY_TOUCH, TouchProfile.entries, TouchProfile.FINGER),
        handedness = enum(KEY_HAND, Handedness.entries, Handedness.RIGHT),
        defaultUnit = enum(KEY_UNIT, LengthUnit.entries, LengthUnit.METER),
        decimals = preferences.getInt(KEY_DECIMALS, 2),
        showUnits = preferences.getBoolean(KEY_SHOW_UNITS, true),
        showSegments = preferences.getBoolean(KEY_SHOW_SEGMENTS, false),
        snapMode = enum(KEY_SNAP, SnapMode.entries, SnapMode.AUTO),
        magnifier = preferences.getBoolean(KEY_MAGNIFIER, true),
        magnifierPlacement = enum(KEY_MAGNIFIER_PLACE, MagnifierPlacement.entries, MagnifierPlacement.AUTO),
        haptics = preferences.getBoolean(KEY_HAPTICS, true),
        labelScale = preferences.getFloat(KEY_LABEL_SCALE, 1f),
        uiScale = preferences.getFloat(KEY_UI_SCALE, 1f),
        defaultStrokeWidth = preferences.getFloat(KEY_STROKE, 2f),
        autosaveDelayMs = preferences.getLong(KEY_AUTOSAVE, 700L),
        confirmDelete = preferences.getBoolean(KEY_CONFIRM_DELETE, false),
        keepToolAfterFinish = preferences.getBoolean(KEY_KEEP_TOOL, true),
        keepScreenAwakeInField = preferences.getBoolean(KEY_KEEP_SCREEN_AWAKE_FIELD, false),
        warnUncalibrated = preferences.getBoolean(KEY_WARN_SCALE, true),
        exportIncludeLegend = preferences.getBoolean(KEY_EXPORT_LEGEND, true),
        exportIncludeScale = preferences.getBoolean(KEY_EXPORT_SCALE, true),
        csvDelimiter = preferences.getString(KEY_CSV, ",") ?: ",",
        projectView = enum(KEY_PROJECT_VIEW, ProjectView.entries, ProjectView.GRID),
        projectSort = enum(KEY_PROJECT_SORT, ProjectSort.entries, ProjectSort.MODIFIED),
        onboardingSeen = preferences.getBoolean(KEY_ONBOARDING, false),
        coachSeen = preferences.getStringSet(KEY_COACH, emptySet())?.toSet() ?: emptySet(),
    )

    fun save(settings: AppSettings) {
        preferences.edit()
            .putString(KEY_LANGUAGE, settings.language.name)
            .putString(KEY_THEME, settings.theme.name)
            .putBoolean(KEY_DYNAMIC, settings.dynamicColor)
            .putString(KEY_TOUCH, settings.touchProfile.name)
            .putString(KEY_HAND, settings.handedness.name)
            .putString(KEY_UNIT, settings.defaultUnit.name)
            .putInt(KEY_DECIMALS, settings.decimals)
            .putBoolean(KEY_SHOW_UNITS, settings.showUnits)
            .putBoolean(KEY_SHOW_SEGMENTS, settings.showSegments)
            .putString(KEY_SNAP, settings.snapMode.name)
            .putBoolean(KEY_MAGNIFIER, settings.magnifier)
            .putString(KEY_MAGNIFIER_PLACE, settings.magnifierPlacement.name)
            .putBoolean(KEY_HAPTICS, settings.haptics)
            .putFloat(KEY_LABEL_SCALE, settings.labelScale)
            .putFloat(KEY_UI_SCALE, settings.uiScale)
            .putFloat(KEY_STROKE, settings.defaultStrokeWidth)
            .putLong(KEY_AUTOSAVE, settings.autosaveDelayMs)
            .putBoolean(KEY_CONFIRM_DELETE, settings.confirmDelete)
            .putBoolean(KEY_KEEP_TOOL, settings.keepToolAfterFinish)
            .putBoolean(KEY_KEEP_SCREEN_AWAKE_FIELD, settings.keepScreenAwakeInField)
            .putBoolean(KEY_WARN_SCALE, settings.warnUncalibrated)
            .putBoolean(KEY_EXPORT_LEGEND, settings.exportIncludeLegend)
            .putBoolean(KEY_EXPORT_SCALE, settings.exportIncludeScale)
            .putString(KEY_CSV, settings.csvDelimiter)
            .putString(KEY_PROJECT_VIEW, settings.projectView.name)
            .putString(KEY_PROJECT_SORT, settings.projectSort.name)
            .putBoolean(KEY_ONBOARDING, settings.onboardingSeen)
            .putStringSet(KEY_COACH, settings.coachSeen)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enum(key: String, values: List<T>, fallback: T): T {
        val stored = preferences.getString(key, null) ?: return fallback
        return values.firstOrNull { it.name == stored } ?: fallback
    }

    private fun defaultLanguage(): AppLanguage = when (Locale.getDefault().language.lowercase(Locale.ROOT)) {
        "pl" -> AppLanguage.POLISH
        "de" -> AppLanguage.GERMAN
        "fr" -> AppLanguage.FRENCH
        "it" -> AppLanguage.ITALIAN
        "ru" -> AppLanguage.RUSSIAN
        else -> AppLanguage.ENGLISH
    }

    private companion object {
        const val KEY_LANGUAGE = "language"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_TOUCH = "touch_profile"
        const val KEY_HAND = "handedness"
        const val KEY_UNIT = "default_unit"
        const val KEY_DECIMALS = "decimals"
        const val KEY_SHOW_UNITS = "show_units"
        const val KEY_SHOW_SEGMENTS = "show_segments"
        const val KEY_SNAP = "snap_mode"
        const val KEY_MAGNIFIER = "magnifier"
        const val KEY_MAGNIFIER_PLACE = "magnifier_placement"
        const val KEY_HAPTICS = "haptics"
        const val KEY_LABEL_SCALE = "label_scale"
        const val KEY_UI_SCALE = "ui_scale"
        const val KEY_STROKE = "stroke_width"
        const val KEY_AUTOSAVE = "autosave_delay"
        const val KEY_CONFIRM_DELETE = "confirm_delete"
        const val KEY_KEEP_TOOL = "keep_tool"
        const val KEY_KEEP_SCREEN_AWAKE_FIELD = "keep_screen_awake_field"
        const val KEY_WARN_SCALE = "warn_scale"
        const val KEY_EXPORT_LEGEND = "export_legend"
        const val KEY_EXPORT_SCALE = "export_scale"
        const val KEY_CSV = "csv_delimiter"
        const val KEY_PROJECT_VIEW = "project_view"
        const val KEY_PROJECT_SORT = "project_sort"
        const val KEY_ONBOARDING = "onboarding_seen"
        const val KEY_COACH = "coach_seen"
    }
}

package com.planruler.model

/** UI preferences that survive process death. Persisted by the composition root. */
enum class ThemePreference { SYSTEM, LIGHT, DARK, SUNLIGHT, BLUEPRINT, HIGH_CONTRAST }

/** Drives every precision-related dimension: targets, handles, tolerances, slop. */
enum class TouchProfile { STYLUS, FINGER, GLOVE }

enum class Handedness { RIGHT, LEFT }

enum class MagnifierPlacement { AUTO, LEFT, RIGHT, TOP }

/** Snapping strategy chosen by the user; maps onto the snap types the engine may return. */
enum class SnapMode { AUTO, VERTEX, AXIS, EDGE, OFF }

enum class ProjectView { GRID, LIST }

enum class ProjectSort { MODIFIED, CREATED, NAME, MEASUREMENTS }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val theme: ThemePreference = ThemePreference.SYSTEM,
    val dynamicColor: Boolean = false,
    val touchProfile: TouchProfile = TouchProfile.FINGER,
    val handedness: Handedness = Handedness.RIGHT,
    val defaultUnit: LengthUnit = LengthUnit.METER,
    val decimals: Int = 2,
    val showUnits: Boolean = true,
    val showSegments: Boolean = false,
    val snapMode: SnapMode = SnapMode.AUTO,
    val magnifier: Boolean = true,
    val magnifierPlacement: MagnifierPlacement = MagnifierPlacement.AUTO,
    val haptics: Boolean = true,
    val labelScale: Float = 1.0f,
    val uiScale: Float = 1.0f,
    val defaultStrokeWidth: Float = 2f,
    val autosaveDelayMs: Long = 700L,
    val confirmDelete: Boolean = false,
    val keepToolAfterFinish: Boolean = true,
    /** Applied only while a field drawing/measurement screen is visible. */
    val keepScreenAwakeInField: Boolean = false,
    val warnUncalibrated: Boolean = true,
    val exportIncludeLegend: Boolean = true,
    val exportIncludeScale: Boolean = true,
    val csvDelimiter: String = ",",
    val projectView: ProjectView = ProjectView.GRID,
    val projectSort: ProjectSort = ProjectSort.MODIFIED,
    val onboardingSeen: Boolean = false,
    val coachSeen: Set<String> = emptySet(),
) {
    val russian: Boolean get() = language == AppLanguage.RUSSIAN
}

package com.planruler.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.planruler.designsystem.component.SectionHeader
import com.planruler.designsystem.localization.UiTextKey
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.localization.uiText
import com.planruler.designsystem.theme.Space
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.model.Handedness
import com.planruler.model.LengthUnit
import com.planruler.model.MagnifierPlacement
import com.planruler.model.SnapMode
import com.planruler.model.ThemePreference
import com.planruler.model.TouchProfile
import kotlin.math.roundToInt

const val SettingsListTag = "pr:settings:list"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettings: (AppSettings) -> Unit,
    modifier: Modifier = Modifier,
    backupStatus: String? = null,
    onExportBackup: (String) -> Unit = {},
    onImportBackup: (String) -> Unit = {},
) {
    val text = St(settings.language)
    var backupDialog by remember { mutableStateOf<BackupDialog?>(null) }
    LazyColumn(modifier.fillMaxWidth().navigationBarsPadding().testTag(SettingsListTag)) {
        item {
            SectionHeader(text.measurement)
            ChipsRow(text.defaultUnit, LengthUnit.entries, settings.defaultUnit, { it.symbol }) {
                onSettings(settings.copy(defaultUnit = it))
            }
            ChipsRow(text.decimals, listOf(0, 1, 2, 3), settings.decimals, { it.toString() }) {
                onSettings(settings.copy(decimals = it))
            }
            SwitchRow(text.showUnits, settings.showUnits) { onSettings(settings.copy(showUnits = it)) }
            SwitchRow(text.showSegments, settings.showSegments) { onSettings(settings.copy(showSegments = it)) }
            SwitchRow(text.warnUncalibrated, settings.warnUncalibrated) {
                onSettings(settings.copy(warnUncalibrated = it))
            }
            SwitchRow(text.keepTool, settings.keepToolAfterFinish) {
                onSettings(settings.copy(keepToolAfterFinish = it))
            }
            HorizontalDivider()
        }
        item {
            SectionHeader(text.control)
            ChipsRow(text.touchProfile, TouchProfile.entries, settings.touchProfile, text::touchProfileLabel) {
                onSettings(settings.copy(touchProfile = it))
            }
            ChipsRow(text.handedness, Handedness.entries, settings.handedness, text::handednessLabel) {
                onSettings(settings.copy(handedness = it))
            }
            ChipsRow(text.snapping, SnapMode.entries, settings.snapMode, text::snapLabel) {
                onSettings(settings.copy(snapMode = it))
            }
            SwitchRow(text.magnifier, settings.magnifier) { onSettings(settings.copy(magnifier = it)) }
            ChipsRow(
                text.magnifierPlacement,
                MagnifierPlacement.entries,
                settings.magnifierPlacement,
                text::placementLabel,
            ) { onSettings(settings.copy(magnifierPlacement = it)) }
            SwitchRow(text.haptics, settings.haptics) { onSettings(settings.copy(haptics = it)) }
            SwitchRow(text.confirmDelete, settings.confirmDelete) { onSettings(settings.copy(confirmDelete = it)) }
            HorizontalDivider()
        }
        item {
            SectionHeader(text.appearance)
            ChipsRow(text.theme, ThemePreference.entries, settings.theme, text::themeLabel) {
                onSettings(settings.copy(theme = it))
            }
            SwitchRow(text.dynamicColor, settings.dynamicColor) { onSettings(settings.copy(dynamicColor = it)) }
            ChipsRow(text.language, AppLanguage.entries, settings.language, text::languageLabel) {
                onSettings(settings.copy(language = it))
            }
            SliderRow(text.uiScale, settings.uiScale, 0.85f..1.3f) { onSettings(settings.copy(uiScale = it)) }
            SliderRow(text.labelScale, settings.labelScale, 0.85f..1.35f) {
                onSettings(settings.copy(labelScale = it))
            }
            SliderRow(text.strokeWidth, settings.defaultStrokeWidth, 1f..8f) {
                onSettings(settings.copy(defaultStrokeWidth = it))
            }
            HorizontalDivider()
        }
        item {
            SectionHeader(text.projects)
            ChipsRow(
                text.autosaveDelay,
                listOf(300L, 700L, 1500L, 3000L),
                settings.autosaveDelayMs,
                { "${it / 1000.0} ${text.seconds}" },
            ) { onSettings(settings.copy(autosaveDelayMs = it)) }
            HorizontalDivider()
        }
        item {
            SectionHeader(text.export)
            SwitchRow(text.exportLegend, settings.exportIncludeLegend) {
                onSettings(settings.copy(exportIncludeLegend = it))
            }
            SwitchRow(text.exportScale, settings.exportIncludeScale) {
                onSettings(settings.copy(exportIncludeScale = it))
            }
            ChipsRow(text.csvDelimiter, listOf(",", ";", "\t"), settings.csvDelimiter, {
                if (it == "\t") "TAB" else it
            }) { onSettings(settings.copy(csvDelimiter = it)) }
            HorizontalDivider()
        }
        item {
            SectionHeader(text.localBackup)
            Text(
                text.localBackupBody,
                Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2),
                horizontalArrangement = Arrangement.spacedBy(Space.x2),
            ) {
                Button({ backupDialog = BackupDialog.EXPORT }, Modifier.weight(1f)) { Text(text.createBackup) }
                OutlinedButton({ backupDialog = BackupDialog.IMPORT }, Modifier.weight(1f)) { Text(text.restoreBackup) }
            }
            backupStatus?.let {
                Text(
                    it,
                    Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text.backupDocumentWarning,
                Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
                color = MaterialTheme.colorScheme.tertiary,
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
        }
        item {
            SectionHeader(text.privacy)
            Text(
                text.privacyBody,
                Modifier.padding(horizontal = Space.x4, vertical = Space.x2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text.version,
                Modifier.padding(horizontal = Space.x4, vertical = Space.x4),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    backupDialog?.let { mode ->
        BackupPasswordDialog(
            export = mode == BackupDialog.EXPORT,
            text = text,
            onDismiss = { backupDialog = null },
            onConfirm = { password ->
                backupDialog = null
                if (mode == BackupDialog.EXPORT) onExportBackup(password) else onImportBackup(password)
            },
        )
    }
}

private enum class BackupDialog { EXPORT, IMPORT }

@Composable
private fun BackupPasswordDialog(
    export: Boolean,
    text: St,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val valid = password.length >= 8 && (!export || password == repeat)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (export) text.createBackup else text.restoreBackup) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                Text(if (export) text.backupPasswordBody else text.restorePasswordBody)
                OutlinedTextField(
                    password,
                    { password = it.take(128) },
                    label = { Text(text.backupPassword) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (export) {
                    OutlinedTextField(
                        repeat,
                        { repeat = it.take(128) },
                        label = { Text(text.repeatPassword) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = { Button({ onConfirm(password) }, enabled = valid) { Text(if (export) text.continueLabel else text.restoreBackup) } },
        dismissButton = { TextButton(onDismiss) { Text(text.cancel) } },
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, Modifier.padding(end = Space.x3), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipsRow(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(label(option)) },
                )
            }
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.x4, vertical = Space.x2)) {
        Text("$title: ${(value * 100).roundToInt()} %", style = MaterialTheme.typography.bodyLarge)
        Slider(value, onChange, valueRange = range)
    }
}

class St(private val currentLanguage: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(currentLanguage, russian, english)
    val measurement get() = t("Измерения", "Measurements")
    val control get() = t("Управление", "Control")
    val appearance get() = t("Внешний вид", "Appearance")
    val projects get() = t("Проекты", "Projects")
    val export get() = t("Экспорт", "Export")
    val privacy get() = t("Конфиденциальность", "Privacy")
    val localBackup get() = t("Локальная резервная копия", "Local backup")
    val localBackupBody get() = t("Создаётся зашифрованный файл, который вы сами сохраняете на телефоне, SD-карте или USB. Серверы не используются.", "Creates an encrypted file that you save to the phone, SD card, or USB. No server is used.")
    val createBackup get() = t("Создать backup", "Create backup")
    val restoreBackup get() = t("Восстановить", "Restore")
    val backupPassword get() = t("Пароль backup", "Backup password")
    val repeatPassword get() = t("Повторите пароль", "Repeat password")
    val backupPasswordBody get() = t("Минимум 8 символов. Этот пароль потребуется после переустановки или на другом телефоне; приложение его не сохраняет.", "Use at least 8 characters. This password is required after reinstalling or on another phone; the app does not store it.")
    val restorePasswordBody get() = t("Данные из архива будут объединены с локальными данными. Введите пароль, заданный при создании backup.", "Backup data will be merged with local data. Enter the password used when the backup was created.")
    val backupDocumentWarning get() = t("Backup содержит проекты, измерения, CRM и корзину. Исходные PDF/изображения по внешним URI нужно хранить отдельно.", "The backup contains projects, measurements, CRM, and trash. Source PDFs/images referenced by external URI must be kept separately.")
    val continueLabel get() = t("Продолжить", "Continue")
    val repeatMismatch get() = t("Пароли должны совпадать", "Passwords must match")
    val cancel get() = t("Отмена", "Cancel")
    val defaultUnit get() = t("Единицы по умолчанию", "Default units")
    val decimals get() = t("Знаков после запятой", "Decimal places")
    val showUnits get() = t("Показывать единицы у значений", "Show units next to values")
    val showSegments get() = t("Показывать длины сегментов", "Show segment lengths")
    val warnUncalibrated get() = t("Предупреждать о неподтверждённой калибровке", "Warn about unverified calibration")
    val keepTool get() = t("Не выходить из инструмента после завершения", "Keep the tool after finishing")
    val touchProfile get() = t("Профиль касания", "Touch profile")
    val handedness get() = t("Рука", "Handedness")
    val snapping get() = t("Привязка", "Snapping")
    val magnifier get() = t("Показывать лупу", "Show magnifier")
    val magnifierPlacement get() = t("Положение лупы", "Magnifier position")
    val haptics get() = t("Виброотклик", "Haptic feedback")
    val confirmDelete get() = t("Подтверждать удаление", "Confirm deletion")
    val theme get() = t("Тема", "Theme")
    val dynamicColor get() = t("Цвета устройства (Android 12+)", "Device colours (Android 12+)")
    val language get() = t("Язык", "Language")
    val uiScale get() = t("Размер текста интерфейса", "Interface text size")
    val labelScale get() = t("Размер подписей измерений", "Measurement label size")
    val strokeWidth get() = t("Толщина линий", "Line thickness")
    val autosaveDelay get() = t("Задержка автосохранения", "Autosave delay")
    val seconds get() = t("с", "s")
    val exportLegend get() = t("Включать легенду", "Include legend")
    val exportScale get() = t("Включать масштаб", "Include scale")
    val csvDelimiter get() = t("Разделитель CSV", "CSV delimiter")
    val privacyBody
        get() = t(
            "Все документы обрабатываются локально. Приложение не загружает проекты на сервер и не требует аккаунта.",
            "Everything is processed locally. The app uploads nothing and needs no account.",
        )
    val version get() = "PlanRuler 1.5.1"

    fun themeLabel(theme: ThemePreference) = when (theme) {
        ThemePreference.SYSTEM -> t("Системная", "System")
        ThemePreference.LIGHT -> t("Светлая", "Light")
        ThemePreference.DARK -> t("Тёмная", "Dark")
        ThemePreference.SUNLIGHT -> uiText(currentLanguage, UiTextKey.THEME_SUNLIGHT)
        ThemePreference.BLUEPRINT -> uiText(currentLanguage, UiTextKey.THEME_BLUEPRINT)
        ThemePreference.HIGH_CONTRAST -> t("Контрастная", "High contrast")
    }

    fun touchProfileLabel(profile: TouchProfile) = when (profile) {
        TouchProfile.STYLUS -> t("Стилус", "Stylus")
        TouchProfile.FINGER -> t("Палец", "Finger")
        TouchProfile.GLOVE -> t("Перчатки", "Gloves")
    }

    fun handednessLabel(handedness: Handedness) = when (handedness) {
        Handedness.RIGHT -> t("Правая", "Right")
        Handedness.LEFT -> t("Левая", "Left")
    }

    fun snapLabel(mode: SnapMode) = when (mode) {
        SnapMode.AUTO -> t("Авто", "Auto")
        SnapMode.VERTEX -> t("Точки", "Vertex")
        SnapMode.AXIS -> t("Оси", "Axis")
        SnapMode.EDGE -> t("Рёбра", "Edge")
        SnapMode.OFF -> t("Выкл.", "Off")
    }

    fun placementLabel(placement: MagnifierPlacement) = when (placement) {
        MagnifierPlacement.AUTO -> t("Авто", "Auto")
        MagnifierPlacement.LEFT -> t("Слева", "Left")
        MagnifierPlacement.RIGHT -> t("Справа", "Right")
        MagnifierPlacement.TOP -> t("Сверху", "Top")
    }

    fun languageLabel(language: AppLanguage) = when (language) {
        AppLanguage.POLISH -> "Polski"
        AppLanguage.ENGLISH -> "English"
        AppLanguage.GERMAN -> "Deutsch"
        AppLanguage.FRENCH -> "Français"
        AppLanguage.ITALIAN -> "Italiano"
        AppLanguage.RUSSIAN -> "Русский"
    }
}

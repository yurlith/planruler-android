package com.planruler.feature.pipecalculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.localization.localizedUi
import com.planruler.model.AppLanguage
import com.planruler.model.InstallationEndDirection
import com.planruler.model.InstallationInputMode
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationOffsetPlacement
import com.planruler.model.InstallationPipeMaterial
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES

@Composable
internal fun InstallerInputWizard(
    language: AppLanguage,
    draft: InstallerDraft,
    onDraft: (InstallerDraft) -> Unit,
) {
    fun t(russian: String, english: String) = localizedUi(language, russian, english)
    var referenceEnd by remember { mutableStateOf<Boolean?>(null) }
    val tasks = InstallationTaskType.entries.filter {
        it != InstallationTaskType.MANUAL || draft.inputMode == InstallationInputMode.ADVANCED
    }
    val path = if (draft.taskType in setOf(
            InstallationTaskType.OBSTACLE_BYPASS,
            InstallationTaskType.TEE_BRANCH,
            InstallationTaskType.REDUCER,
            InstallationTaskType.MANUAL,
        )
    ) t("готовый шаблон", "prepared template") else t("автоматический маршрут", "automatic route")

    ElevatedCard(
        Modifier.fillMaxWidth().testTag(PipeCalculatorTags.InstallerWizard),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(t("Что нужно собрать?", "What are you assembling?"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                Text(
                    t("Выберите схему — приложение само подберёт способ расчёта.", "Choose a diagram and the app will select the calculation method."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InstallationInputMode.entries.forEach { mode ->
                    FilterChip(
                        selected = draft.inputMode == mode,
                        onClick = { onDraft(draft.copy(inputMode = mode)) },
                        label = { Text(if (mode == InstallationInputMode.BASIC) t("Обычный", "Basic") else t("Расширенный", "Advanced")) },
                        modifier = Modifier.testTag(
                            if (mode == InstallationInputMode.BASIC) PipeCalculatorTags.InstallerBasicMode
                            else PipeCalculatorTags.InstallerAdvancedMode,
                        ),
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tasks, key = { it.name }) { task ->
                    InstallerTaskCard(
                        task = task,
                        selected = draft.taskType == task,
                        language = language,
                        onClick = { onDraft(draft.selectTask(task)) },
                    )
                }
            }

            Text(t("Труба", "Pipe"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(PIPE_INSTALLATION_SERIES, key = { it.dn }) { pipe ->
                    FilterChip(
                        selected = draft.nominalDiameter == pipe.dn,
                        enabled = draft.taskType != InstallationTaskType.REDUCER || pipe.dn > 15,
                        onClick = { onDraft(draft.copy(nominalDiameter = pipe.dn)) },
                        label = { Text("DN${pipe.dn}") },
                    )
                }
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(InstallationPipeMaterial.entries) { material ->
                    FilterChip(
                        selected = draft.material == material,
                        onClick = { onDraft(draft.copy(material = material, materialName = null)) },
                        label = {
                            Text(
                                if (
                                    material == InstallationPipeMaterial.OTHER &&
                                    draft.material == material && !draft.materialName.isNullOrBlank()
                                ) draft.materialName.take(20) else material.label(language),
                            )
                        },
                    )
                }
            }

            InstallerMeasurementDiagram(draft.taskType, language)
            Text(t("Между какими точками измеряли?", "Which points did you measure between?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { referenceEnd = false },
                    modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.InstallerStartReference),
                ) { Text(t("Начало: ", "Start: ") + draft.startReference.label(language)) }
                OutlinedButton(
                    onClick = { referenceEnd = true },
                    modifier = Modifier.weight(1f).testTag(PipeCalculatorTags.InstallerEndReference),
                ) { Text(t("Конец: ", "End: ") + draft.endReference.label(language)) }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        t("Введите расстояние: от ", "Enter the distance: from ") +
                            draft.startReference.label(language).lowercase() + t(" до ", " to ") +
                            draft.endReference.label(language).lowercase() + ".",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val siteWelds = listOf(draft.startReference, draft.endReference)
                        .count { it != InstallationMeasurementReference.FLANGE_FACE }
                    if (siteWelds > 0 && draft.taskType != InstallationTaskType.FLANGED_OFFSET && draft.taskType.usesAutoRoute()) {
                        Text(
                            t(
                                "Без фланца конец приваривается на месте: на каждый такой стык заложен зазор %s мм.",
                                "An end without a flange is welded on site: each such joint keeps a %s mm gap.",
                            ).format(draft.weldGap.ifBlank { "0" }),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag(PipeCalculatorTags.InstallerSiteWeldHint),
                        )
                    }
                }
            }

            InstallerDimensionField(
                value = draft.along,
                onValue = { onDraft(draft.copy(along = it)) },
                label = t("Вдоль трассы", "Along the run"),
                tag = PipeCalculatorTags.InstallerAlong,
            )
            // Only the fields the chosen task reads: offsets keep the end facing forward, so
            // asking for that only invites numbers that are then ignored.
            val flat = draft.taskType == InstallationTaskType.FLAT_OFFSET
            val lateralShown = draft.taskType.usesLateral() && !flat
            val verticalShown = draft.taskType.usesVertical()
            if (flat) {
                // One size and one of four ways: a flat offset steps aside or climbs, never both.
                InstallerDimensionField(
                    value = draft.flatOffset,
                    onValue = { onDraft(draft.withFlatOffset(it)) },
                    label = t("Смещение этажа", "Offset distance"),
                    tag = PipeCalculatorTags.InstallerLateral,
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(FlatOffsetDirection.entries) { direction ->
                        FilterChip(
                            selected = draft.flatDirection == direction,
                            onClick = { onDraft(draft.withFlatDirection(direction)) },
                            label = { Text(direction.label(language)) },
                            modifier = Modifier.testTag(PipeCalculatorTags.installerFlatDirection(direction.name)),
                        )
                    }
                }
            }
            if (lateralShown || verticalShown) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lateralShown) {
                        InstallerDimensionField(
                            value = draft.lateral,
                            onValue = { onDraft(draft.copy(lateral = it)) },
                            label = t("В сторону", "Side offset"),
                            tag = PipeCalculatorTags.InstallerLateral,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (verticalShown) {
                        InstallerDimensionField(
                            value = draft.vertical,
                            onValue = { onDraft(draft.copy(vertical = it)) },
                            label = t("По высоте", "Rise / drop"),
                            tag = PipeCalculatorTags.InstallerVertical,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            if (lateralShown) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    InstallationLateralDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = draft.lateralDirection == direction,
                            onClick = { onDraft(draft.copy(lateralDirection = direction)) },
                            label = { Text(if (direction == InstallationLateralDirection.LEFT) t("← влево", "← left") else t("вправо →", "right →")) },
                        )
                    }
                }
            }
            if (verticalShown) {
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    InstallationVerticalDirection.entries.forEach { direction ->
                        FilterChip(
                            selected = draft.verticalDirection == direction,
                            onClick = { onDraft(draft.copy(verticalDirection = direction)) },
                            label = { Text(if (direction == InstallationVerticalDirection.UP) t("↑ подъём", "↑ rise") else t("↓ опуск", "↓ drop")) },
                        )
                    }
                }
            }

            if (draft.taskType.usesEndDirection()) {
                Text(t("Куда смотрит конец?", "Which way does the end face?"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    modifier = Modifier.testTag(PipeCalculatorTags.InstallerEndDirection),
                ) {
                    items(InstallationEndDirection.entries) { direction ->
                        FilterChip(
                            selected = draft.endDirection == direction,
                            onClick = { onDraft(draft.copy(endDirection = direction)) },
                            label = { Text(direction.label(language)) },
                        )
                    }
                }
            }
            if (draft.taskType.isOffset()) {
                Text(t("Где этаж", "Where the offset sits"), style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(InstallationOffsetPlacement.entries) { placement ->
                        FilterChip(
                            selected = draft.offsetPlacement == placement,
                            onClick = { onDraft(draft.copy(offsetPlacement = placement)) },
                            label = { Text(placement.label(language)) },
                            modifier = Modifier.testTag(PipeCalculatorTags.installerPlacement(placement.name)),
                        )
                    }
                }
                if (draft.offsetPlacement == InstallationOffsetPlacement.EXACT) {
                    InstallerDimensionField(
                        value = draft.offsetLead,
                        onValue = { onDraft(draft.copy(offsetLead = it)) },
                        label = t("Отрезок до этажа", "Cut before the offset"),
                        tag = PipeCalculatorTags.InstallerOffsetLead,
                    )
                }
            }
            Text(t("Угол отвода", "Elbow angle"), style = MaterialTheme.typography.labelLarge)
            // Offsets are often set out with shallow elbows; turns and returns keep the usual four.
            val angles = if (draft.taskType.isOffset()) OFFSET_ELBOW_ANGLES else listOf(30.0, 45.0, 60.0, 90.0)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                items(angles) { angle ->
                    val value = editableInstallerNumber(angle)
                    FilterChip(
                        selected = draft.angle.replace(',', '.').toDoubleOrNull() == angle,
                        onClick = { onDraft(draft.copy(angle = value)) },
                        label = { Text("$value°") },
                        modifier = Modifier.testTag(PipeCalculatorTags.installerAngle(value)),
                    )
                }
            }

            if (draft.inputMode == InstallationInputMode.ADVANCED) {
                Text(t("Расширенные параметры", "Advanced parameters"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstallerDimensionField(draft.minimumStraight, { onDraft(draft.copy(minimumStraight = it)) }, t("Мин. прямой", "Min. straight"), "", Modifier.weight(1f))
                    InstallerDimensionField(draft.weldGap, { onDraft(draft.copy(weldGap = it)) }, t("Зазор", "Weld gap"), "", Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InstallerDimensionField(draft.quantity, { onDraft(draft.copy(quantity = it)) }, t("Количество", "Quantity"), "", Modifier.weight(1f))
                    InstallerDimensionField(draft.sawKerf, { onDraft(draft.copy(sawKerf = it)) }, t("Пропил", "Saw kerf"), "", Modifier.weight(1f))
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(listOf(6_000, 12_000)) { length ->
                        FilterChip(
                            selected = draft.stockLengthMm == length,
                            onClick = { onDraft(draft.copy(stockLengthMm = length)) },
                            label = { Text("${length / 1_000} m") },
                        )
                    }
                }
            }

            Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(
                    t("Способ расчёта: ", "Calculation method: ") + path,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }

    referenceEnd?.let { isEnd ->
        AlertDialog(
            onDismissRequest = { referenceEnd = null },
            title = { Text(if (isEnd) t("Точка конца", "End point") else t("Точка начала", "Start point")) },
            text = {
                LazyColumn {
                    items(InstallationMeasurementReference.entries) { reference ->
                        TextButton(
                            onClick = {
                                onDraft(
                                    if (isEnd) draft.copy(endReference = reference)
                                    else draft.copy(startReference = reference),
                                )
                                referenceEnd = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(reference.label(language)) }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

@Composable
private fun InstallerTaskCard(
    task: InstallationTaskType,
    selected: Boolean,
    language: AppLanguage,
    onClick: () -> Unit,
) {
    val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(
        Modifier
            .width(148.dp)
            .background(background, RoundedCornerShape(20.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag(PipeCalculatorTags.InstallerTaskPrefix + task.name)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        TaskDiagram(task, border)
        Text(task.label(language), style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold)
    }
}

@Composable
private fun TaskDiagram(task: InstallationTaskType, color: Color) {
    Canvas(Modifier.fillMaxWidth().height(58.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * .08f, h * .72f)
            when (task) {
                InstallationTaskType.STRAIGHT_INSERT, InstallationTaskType.REDUCER -> lineTo(w * .92f, h * .72f)
                InstallationTaskType.U_TURN -> {
                    lineTo(w * .72f, h * .72f); quadraticTo(w * .92f, h * .72f, w * .92f, h * .42f)
                    quadraticTo(w * .92f, h * .12f, w * .72f, h * .12f); lineTo(w * .24f, h * .12f)
                }
                InstallationTaskType.TEE_BRANCH -> {
                    lineTo(w * .92f, h * .72f); moveTo(w * .52f, h * .72f); lineTo(w * .52f, h * .12f)
                }
                InstallationTaskType.TURN_WITH_OFFSET -> {
                    lineTo(w * .55f, h * .72f); lineTo(w * .55f, h * .25f); lineTo(w * .92f, h * .25f)
                }
                else -> {
                    lineTo(w * .36f, h * .72f); lineTo(w * .65f, h * .22f); lineTo(w * .92f, h * .22f)
                }
            }
        }
        drawPath(path, color, style = Stroke(width = 7f, cap = StrokeCap.Round))
        if (task == InstallationTaskType.REDUCER) drawCircle(color, radius = 8f, center = Offset(w * .52f, h * .72f))
        if (task == InstallationTaskType.OBSTACLE_BYPASS) {
            drawRect(color.copy(alpha = .22f), topLeft = Offset(w * .44f, h * .35f), size = androidx.compose.ui.geometry.Size(w * .18f, h * .37f))
        }
    }
}

@Composable
private fun InstallerMeasurementDiagram(task: InstallationTaskType, language: AppLanguage) {
    val color = MaterialTheme.colorScheme.primary
    val terminalColor = MaterialTheme.colorScheme.tertiary
    val description = task.label(language)
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Canvas(
            Modifier.fillMaxWidth().height(150.dp).padding(16.dp).semantics { contentDescription = description },
        ) {
            val path = Path().apply {
                moveTo(size.width * .08f, size.height * .72f)
                when (task) {
                    InstallationTaskType.STRAIGHT_INSERT, InstallationTaskType.REDUCER -> lineTo(size.width * .92f, size.height * .72f)
                    InstallationTaskType.U_TURN -> {
                        lineTo(size.width * .76f, size.height * .72f)
                        quadraticTo(size.width * .92f, size.height * .72f, size.width * .92f, size.height * .45f)
                        quadraticTo(size.width * .92f, size.height * .18f, size.width * .76f, size.height * .18f)
                        lineTo(size.width * .25f, size.height * .18f)
                    }
                    InstallationTaskType.TEE_BRANCH -> {
                        lineTo(size.width * .92f, size.height * .72f); moveTo(size.width * .52f, size.height * .72f); lineTo(size.width * .52f, size.height * .12f)
                    }
                    InstallationTaskType.TURN_WITH_OFFSET -> {
                        lineTo(size.width * .55f, size.height * .72f); lineTo(size.width * .55f, size.height * .24f); lineTo(size.width * .92f, size.height * .24f)
                    }
                    else -> {
                        lineTo(size.width * .34f, size.height * .72f); lineTo(size.width * .66f, size.height * .22f); lineTo(size.width * .92f, size.height * .22f)
                    }
                }
            }
            drawPath(path, color, style = Stroke(width = 10f, cap = StrokeCap.Round))
            drawCircle(terminalColor, 11f, Offset(size.width * .08f, size.height * .72f))
            val end = if (task == InstallationTaskType.U_TURN) Offset(size.width * .25f, size.height * .18f)
            else if (task in listOf(InstallationTaskType.STRAIGHT_INSERT, InstallationTaskType.REDUCER, InstallationTaskType.TEE_BRANCH)) Offset(size.width * .92f, size.height * .72f)
            else Offset(size.width * .92f, size.height * .22f)
            drawCircle(terminalColor, 11f, end)
        }
    }
}

@Composable
private fun InstallerDimensionField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { char -> char.isDigit() || char == ',' || char == '.' }) },
        label = { Text(label) },
        suffix = { Text("mm") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier.fillMaxWidth().then(if (tag.isNotEmpty()) Modifier.testTag(tag) else Modifier),
    )
}

private fun InstallationTaskType.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        InstallationTaskType.FLANGED_OFFSET -> "Фланец–фланец"
        InstallationTaskType.STRAIGHT_INSERT -> "Прямая вставка"
        InstallationTaskType.FLAT_OFFSET -> "Плоское смещение"
        InstallationTaskType.ROLLING_OFFSET -> "Пространственное смещение"
        InstallationTaskType.TURN_WITH_OFFSET -> "Поворот с выносом"
        InstallationTaskType.OBSTACLE_BYPASS -> "Обход препятствия"
        InstallationTaskType.U_TURN -> "Разворот"
        InstallationTaskType.TEE_BRANCH -> "Ответвление тройником"
        InstallationTaskType.REDUCER -> "Переход диаметра"
        InstallationTaskType.MANUAL -> "Свободная сборка"
    },
    when (this) {
        InstallationTaskType.FLANGED_OFFSET -> "Flange to flange"
        InstallationTaskType.STRAIGHT_INSERT -> "Straight insert"
        InstallationTaskType.FLAT_OFFSET -> "Flat offset"
        InstallationTaskType.ROLLING_OFFSET -> "Rolling offset"
        InstallationTaskType.TURN_WITH_OFFSET -> "Turn with offset"
        InstallationTaskType.OBSTACLE_BYPASS -> "Obstacle bypass"
        InstallationTaskType.U_TURN -> "U-turn"
        InstallationTaskType.TEE_BRANCH -> "Tee branch"
        InstallationTaskType.REDUCER -> "Reducer"
        InstallationTaskType.MANUAL -> "Free assembly"
    },
)

private fun InstallationPipeMaterial.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        InstallationPipeMaterial.CARBON_STEEL -> "Сталь"
        InstallationPipeMaterial.STAINLESS_STEEL -> "Нержавейка"
        InstallationPipeMaterial.COPPER -> "Медь"
        InstallationPipeMaterial.PPR -> "PPR"
        InstallationPipeMaterial.OTHER -> "Другое"
    },
    name.lowercase().replace('_', ' '),
)

private fun InstallationMeasurementReference.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        InstallationMeasurementReference.PIPE_AXIS -> "ось трубы"
        InstallationMeasurementReference.PIPE_OUTER_EDGE -> "наружный край трубы"
        InstallationMeasurementReference.PIPE_INNER_EDGE -> "внутренний край трубы"
        InstallationMeasurementReference.PIPE_FACE -> "торец трубы"
        InstallationMeasurementReference.FITTING_CENTER -> "центр фитинга"
        InstallationMeasurementReference.FITTING_FACE -> "торец фитинга"
        InstallationMeasurementReference.FLANGE_FACE -> "плоскость фланца"
    },
    name.lowercase().replace('_', ' '),
)

private fun FlatOffsetDirection.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        FlatOffsetDirection.LEFT -> "← влево"
        FlatOffsetDirection.RIGHT -> "вправо →"
        FlatOffsetDirection.UP -> "↑ вверх"
        FlatOffsetDirection.DOWN -> "↓ вниз"
    },
    when (this) {
        FlatOffsetDirection.LEFT -> "← left"
        FlatOffsetDirection.RIGHT -> "right →"
        FlatOffsetDirection.UP -> "↑ up"
        FlatOffsetDirection.DOWN -> "↓ down"
    },
)

private fun InstallationOffsetPlacement.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        InstallationOffsetPlacement.START -> "У начала"
        InstallationOffsetPlacement.CENTER -> "По центру"
        InstallationOffsetPlacement.END -> "У конца"
        InstallationOffsetPlacement.EXACT -> "Точно, мм"
    },
    when (this) {
        InstallationOffsetPlacement.START -> "Near the start"
        InstallationOffsetPlacement.CENTER -> "Centred"
        InstallationOffsetPlacement.END -> "Near the end"
        InstallationOffsetPlacement.EXACT -> "Exact, mm"
    },
)

private fun InstallationEndDirection.label(language: AppLanguage): String = localizedUi(
    language,
    when (this) {
        InstallationEndDirection.FORWARD -> "→ прямо"
        InstallationEndDirection.LEFT -> "← влево"
        InstallationEndDirection.RIGHT -> "→ вправо"
        InstallationEndDirection.UP -> "↑ вверх"
        InstallationEndDirection.DOWN -> "↓ вниз"
        InstallationEndDirection.BACK -> "↩ назад"
    },
    when (this) {
        InstallationEndDirection.FORWARD -> "→ forward"
        InstallationEndDirection.LEFT -> "← left"
        InstallationEndDirection.RIGHT -> "→ right"
        InstallationEndDirection.UP -> "↑ up"
        InstallationEndDirection.DOWN -> "↓ down"
        InstallationEndDirection.BACK -> "↩ back"
    },
)

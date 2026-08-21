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
import com.planruler.model.InstallationJob
import com.planruler.model.InstallationJobInput
import com.planruler.model.InstallationLateralDirection
import com.planruler.model.InstallationMeasurementReference
import com.planruler.model.InstallationPipeMaterial
import com.planruler.model.InstallationTaskType
import com.planruler.model.InstallationVerticalDirection
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import kotlin.math.hypot

internal data class InstallerDraft(
    val taskType: InstallationTaskType,
    val inputMode: InstallationInputMode,
    val nominalDiameter: Int,
    val pressureClass: Int,
    val material: InstallationPipeMaterial,
    val materialName: String?,
    val startReference: InstallationMeasurementReference,
    val endReference: InstallationMeasurementReference,
    val along: String,
    val lateral: String,
    val vertical: String,
    val lateralDirection: InstallationLateralDirection,
    val verticalDirection: InstallationVerticalDirection,
    val endDirection: InstallationEndDirection,
    val angle: String,
    val minimumStraight: String,
    val weldGap: String,
    val quantity: String,
    val sawKerf: String,
    val stockLengthMm: Int,
) {
    fun toInputOrNull(): InstallationJobInput? = runCatching {
        val alongMm = along.installerNumber().also { require(it > 0.0) }
        val lateralMm = lateral.installerNumber().also { require(it >= 0.0) }
        val verticalMm = vertical.installerNumber().also { require(it >= 0.0) }
        InstallationJobInput(
            nominalDiameter = nominalDiameter,
            pressureClass = pressureClass,
            material = material,
            materialName = materialName,
            inputMode = inputMode,
            startReference = startReference,
            endReference = endReference,
            alongMm = alongMm,
            lateralOffsetMm = lateralMm,
            verticalOffsetMm = verticalMm,
            lateralDirection = lateralDirection,
            verticalDirection = verticalDirection,
            endDirection = endDirection,
            angleDeg = angle.installerNumber().also { require(it > 0.0 && it < 180.0) },
            targetOffsetMm = hypot(lateralMm, verticalMm),
            overallFaceToFaceMm = alongMm,
            minimumStraightMm = minimumStraight.installerNumber().also { require(it >= 0.0) },
            weldGapMm = weldGap.installerNumber().also { require(it >= 0.0) },
            quantity = quantity.installerWholeNumber().also { require(it > 0) },
            sawKerfMm = sawKerf.installerNumber().also { require(it >= 0.0) },
            stockLengthMm = stockLengthMm,
        )
    }.getOrNull()

    fun selectTask(task: InstallationTaskType): InstallerDraft = copy(
        taskType = task,
        nominalDiameter = if (task == InstallationTaskType.REDUCER && nominalDiameter == 15) 20 else nominalDiameter,
        lateral = when (task) {
            InstallationTaskType.STRAIGHT_INSERT, InstallationTaskType.REDUCER -> "0"
            else -> lateral.takeUnless { it == "0" } ?: "500"
        },
        vertical = when (task) {
            InstallationTaskType.ROLLING_OFFSET,
            InstallationTaskType.TURN_WITH_OFFSET,
            InstallationTaskType.OBSTACLE_BYPASS,
            InstallationTaskType.TEE_BRANCH,
            -> vertical.takeUnless { it == "0" } ?: "300"
            else -> "0"
        },
        endDirection = when (task) {
            InstallationTaskType.TURN_WITH_OFFSET -> InstallationEndDirection.RIGHT
            InstallationTaskType.U_TURN -> InstallationEndDirection.BACK
            else -> InstallationEndDirection.FORWARD
        },
    )

    companion object {
        fun from(job: InstallationJob): InstallerDraft = InstallerDraft(
            taskType = job.taskType,
            inputMode = job.input.inputMode,
            nominalDiameter = job.input.nominalDiameter,
            pressureClass = job.input.pressureClass,
            material = job.input.material,
            materialName = job.input.materialName,
            startReference = job.input.startReference,
            endReference = job.input.endReference,
            along = editableInstallerNumber(job.input.alongMm),
            lateral = editableInstallerNumber(job.input.lateralOffsetMm),
            vertical = editableInstallerNumber(job.input.verticalOffsetMm),
            lateralDirection = job.input.lateralDirection,
            verticalDirection = job.input.verticalDirection,
            endDirection = job.input.endDirection,
            angle = editableInstallerNumber(job.input.angleDeg),
            minimumStraight = editableInstallerNumber(job.input.minimumStraightMm),
            weldGap = editableInstallerNumber(job.input.weldGapMm),
            quantity = job.input.quantity.toString(),
            sawKerf = editableInstallerNumber(job.input.sawKerfMm),
            stockLengthMm = job.input.stockLengthMm,
        )
    }
}

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
                Text(
                    t("Введите расстояние: от ", "Enter the distance: from ") +
                        draft.startReference.label(language).lowercase() + t(" до ", " to ") +
                        draft.endReference.label(language).lowercase() + ".",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            InstallerDimensionField(
                value = draft.along,
                onValue = { onDraft(draft.copy(along = it)) },
                label = t("Вдоль трассы", "Along the run"),
                tag = PipeCalculatorTags.InstallerAlong,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InstallerDimensionField(
                    value = draft.lateral,
                    onValue = { onDraft(draft.copy(lateral = it)) },
                    label = t("В сторону", "Side offset"),
                    tag = PipeCalculatorTags.InstallerLateral,
                    modifier = Modifier.weight(1f),
                )
                InstallerDimensionField(
                    value = draft.vertical,
                    onValue = { onDraft(draft.copy(vertical = it)) },
                    label = t("По высоте", "Rise / drop"),
                    tag = PipeCalculatorTags.InstallerVertical,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                InstallationLateralDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = draft.lateralDirection == direction,
                        onClick = { onDraft(draft.copy(lateralDirection = direction)) },
                        label = { Text(if (direction == InstallationLateralDirection.LEFT) t("← влево", "← left") else t("вправо →", "right →")) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                InstallationVerticalDirection.entries.forEach { direction ->
                    FilterChip(
                        selected = draft.verticalDirection == direction,
                        onClick = { onDraft(draft.copy(verticalDirection = direction)) },
                        label = { Text(if (direction == InstallationVerticalDirection.UP) t("↑ подъём", "↑ rise") else t("↓ опуск", "↓ drop")) },
                    )
                }
            }

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
            Text(t("Угол отвода", "Elbow angle"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("30", "45", "60", "90").forEach { angle ->
                    FilterChip(
                        selected = draft.angle == angle,
                        onClick = { onDraft(draft.copy(angle = angle)) },
                        label = { Text("$angle°") },
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

private fun String.installerNumber(): Double = trim().replace(',', '.').toDouble()

private fun String.installerWholeNumber(): Int {
    val value = installerNumber()
    require(value % 1.0 == 0.0)
    return value.toInt()
}

private fun editableInstallerNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

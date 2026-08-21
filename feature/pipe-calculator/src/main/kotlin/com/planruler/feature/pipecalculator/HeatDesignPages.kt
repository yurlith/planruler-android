package com.planruler.feature.pipecalculator

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.component.IndicatorChip
import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.theme.EngineeringColors
import com.planruler.model.AppLanguage
import com.planruler.pipecalculator.EnvelopeCatalog
import com.planruler.pipecalculator.EnvelopeElement
import com.planruler.pipecalculator.HeatLossBreakdown
import com.planruler.pipecalculator.HeatLossInput
import com.planruler.pipecalculator.HeatingDesignInput
import com.planruler.pipecalculator.HeatingDesignResult
import com.planruler.pipecalculator.HydraulicInput
import com.planruler.pipecalculator.calculateHeatingDesign
import java.util.Locale

object HeatCalcTags {
    const val DesignList = "heatcalc_design_list"
    const val CalculateDesign = "heatcalc_calculate_design"
    const val DesignResults = "heatcalc_design_results"
    const val AdvancedToggle = "heatcalc_advanced_toggle"
    const val Breakdown = "heatcalc_breakdown"
}

@Composable
internal fun HeatDesignPage(
    language: AppLanguage,
    criticalCircuit: HydraulicInput?,
) {
    val text = remember(language) { HeatText(language) }
    var heatedArea by rememberSaveable { mutableStateOf("120") }
    var wallsArea by rememberSaveable { mutableStateOf("145") }
    var roofArea by rememberSaveable { mutableStateOf("120") }
    var floorArea by rememberSaveable { mutableStateOf("120") }
    var windowsArea by rememberSaveable { mutableStateOf("24") }
    var floorHeight by rememberSaveable { mutableStateOf("2.7") }
    var floors by rememberSaveable { mutableStateOf("1") }
    var uWall by rememberSaveable { mutableStateOf("0.30") }
    var uRoof by rememberSaveable { mutableStateOf("0.20") }
    var uFloor by rememberSaveable { mutableStateOf("0.25") }
    var uWindow by rememberSaveable { mutableStateOf("1.10") }
    var indoor by rememberSaveable { mutableStateOf("21") }
    var outdoor by rememberSaveable { mutableStateOf("-8") }
    var airChanges by rememberSaveable { mutableStateOf("0.50") }
    var bridges by rememberSaveable { mutableStateOf("0.05") }
    var infiltration by rememberSaveable { mutableStateOf("0.0") }
    var heatRecovery by rememberSaveable { mutableStateOf("0.0") }
    var hydronicDelta by rememberSaveable { mutableStateOf("10") }
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<HeatingDesignResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().testTag(HeatCalcTags.DesignList),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeatHeading(text.designTitle, text.designBody) }
        item { HeatSection(text.geometry) }
        item { HeatNumberField(heatedArea, { heatedArea = it }, text.heatedArea) }
        item { HeatNumberField(wallsArea, { wallsArea = it }, text.wallsArea) }
        item { HeatNumberField(roofArea, { roofArea = it }, text.roofArea) }
        item { HeatNumberField(floorArea, { floorArea = it }, text.floorArea) }
        item { HeatNumberField(windowsArea, { windowsArea = it }, text.windowsArea) }
        item { HeatNumberField(floorHeight, { floorHeight = it }, text.floorHeight) }
        item { HeatNumberField(floors, { floors = it }, text.floors) }
        item { HeatSection(text.thermalEnvelope) }
        item { UValueField(uWall, { uWall = it }, text.uWall, EnvelopeElement.WALL, text) }
        item { UValueField(uRoof, { uRoof = it }, text.uRoof, EnvelopeElement.ROOF, text) }
        item { UValueField(uFloor, { uFloor = it }, text.uFloor, EnvelopeElement.FLOOR, text) }
        item { UValueField(uWindow, { uWindow = it }, text.uWindow, EnvelopeElement.WINDOW, text) }
        item { HeatNumberField(indoor, { indoor = it }, text.indoor) }
        item { HeatNumberField(outdoor, { outdoor = it }, text.outdoor) }
        item {
            TextButton(
                onClick = { advancedOpen = !advancedOpen },
                modifier = Modifier.testTag(HeatCalcTags.AdvancedToggle),
            ) { Text(if (advancedOpen) text.advancedHide else text.advancedShow) }
        }
        item {
            AnimatedVisibility(advancedOpen) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    HeatNumberField(airChanges, { airChanges = it }, text.airChanges)
                    HeatNumberField(infiltration, { infiltration = it }, text.infiltration)
                    HeatNumberField(heatRecovery, { heatRecovery = it }, text.heatRecovery)
                    HeatNumberField(bridges, { bridges = it }, text.bridges)
                }
            }
        }
        item { HeatSection(text.systemSizing) }
        item { HeatNumberField(hydronicDelta, { hydronicDelta = it }, text.hydronicDelta) }
        item {
            Button(
                onClick = {
                    runCatching {
                        calculateHeatingDesign(
                            HeatingDesignInput(
                                building = HeatLossInput(
                                    heatedAreaM2 = heatNumber(heatedArea),
                                    wallsAreaM2 = heatNumber(wallsArea),
                                    roofAreaM2 = heatNumber(roofArea),
                                    floorAreaM2 = heatNumber(floorArea),
                                    windowsAreaM2 = heatNumber(windowsArea),
                                    floorHeightM = heatNumber(floorHeight),
                                    floors = heatWhole(floors),
                                    wallUValueWm2K = heatNumber(uWall),
                                    roofUValueWm2K = heatNumber(uRoof),
                                    floorUValueWm2K = heatNumber(uFloor),
                                    windowUValueWm2K = heatNumber(uWindow),
                                    indoorTemperatureC = heatNumber(indoor),
                                    designOutdoorTemperatureC = heatNumber(outdoor),
                                    airChangesPerHour = heatNumber(airChanges),
                                    thermalBridgeWm2K = heatNumber(bridges),
                                    infiltrationAirChangesPerHour = heatNumber(infiltration),
                                    heatRecoveryEfficiency = heatNumber(heatRecovery),
                                ),
                                hydronicDeltaTK = heatNumber(hydronicDelta),
                                criticalCircuit = listOfNotNull(criticalCircuit),
                            ),
                        )
                    }.onSuccess { result = it; error = null }
                        .onFailure { result = null; error = text.invalidInput(it.message) }
                },
                modifier = Modifier.fillMaxWidth().testTag(HeatCalcTags.CalculateDesign),
            ) { Text(text.calculate) }
        }
        error?.let { item { HeatMessage(it, error = true) } }
        item { HeatMessage(text.linkedInputs(hasCircuit = criticalCircuit != null)) }
        result?.let { design ->
            item {
                OutlinedCard(Modifier.fillMaxWidth().testTag(HeatCalcTags.DesignResults)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(text.results, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            IndicatorChip(
                                PlanRulerIcons.Warning,
                                text.preliminary,
                                IndicatorStatus.WARNING,
                                {},
                            )
                        }
                        HeatMetric(text.designHeatLoss, heatFormat(design.heatLoss.totalHeatLossKw, 2) + " kW", EngineeringColors.HeatingSupply)
                        HeatMetric(text.specificHeatLoss, heatFormat(design.heatLoss.specificHeatLossWm2, 1) + " W/m²", EngineeringColors.Warning)
                        HeatMetric(text.heatedVolume, heatFormat(design.heatLoss.heatedVolumeM3, 1) + " m³", EngineeringColors.Draft)
                        HeatMetric(text.designFlow, heatFormat(design.designFlowLitresPerHour, 0) + " l/h", EngineeringColors.HeatingReturn)
                        HeatMetric(
                            text.pumpHead,
                            design.recommendedPumpHeadKpa?.let { heatFormat(it, 1) + " kPa" } ?: text.requiresCircuit,
                            if (design.recommendedPumpHeadKpa == null) EngineeringColors.Warning else EngineeringColors.Safe,
                        )
                        HeatMetric(
                            text.heatPump,
                            design.targetHeatPumpCapacityKw?.let { heatFormat(it, 2) + " kW" } ?: text.notCalculated,
                            EngineeringColors.HeatingSupply,
                        )
                        HeatMetric(
                            text.cooling,
                            design.estimatedCoolingCapacityKw?.let { heatFormat(it, 2) + " kW" } ?: text.notCalculated,
                            EngineeringColors.ColdWater,
                        )
                        HeatMetric(text.ventilation, heatFormat(design.freshAirFlowLs, 1) + " l/s", EngineeringColors.Safe)
                        HeatMetric(
                            text.dhw,
                            design.suggestedDomesticHotWaterStorageLitres?.let { heatFormat(it, 0) + " l" }
                                ?: text.notCalculated,
                            EngineeringColors.HotWater,
                        )
                        HeatMetric(text.zones, design.controlZoneCount?.toString() ?: text.notCalculated, EngineeringColors.Neutral)
                        HeatBreakdownChart(design.heatLoss.breakdown, text)
                    }
                }
            }
        }
        item { HeatMessage(text.calculationWarning) }
    }
}

@Composable
private fun HeatNumberField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter { it.isDigit() || it in ".,-" }.replace(',', '.')) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun UValueField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    element: EnvelopeElement,
    text: HeatText,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HeatNumberField(value, onValueChange, label)
        androidx.compose.foundation.layout.Box {
            TextButton(onClick = { menuOpen = true }) { Text(text.pickConstruction) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                EnvelopeCatalog.forElement(element).forEach { construction ->
                    DropdownMenuItem(
                        text = { Text("${construction.label} · ${heatFormat(construction.uValueWm2K, 2)}") },
                        onClick = {
                            onValueChange(heatFormat(construction.uValueWm2K, 2))
                            menuOpen = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeatBreakdownChart(breakdown: HeatLossBreakdown, text: HeatText) {
    val segments = listOf(
        Triple(text.breakdownWalls, breakdown.wallsW, EngineeringColors.HeatingSupply),
        Triple(text.breakdownRoof, breakdown.roofW, EngineeringColors.Draft),
        Triple(text.breakdownFloor, breakdown.floorW, EngineeringColors.HeatingReturn),
        Triple(text.breakdownWindows, breakdown.windowsW, EngineeringColors.ColdWater),
        Triple(text.breakdownVentilation, breakdown.ventilationW, EngineeringColors.Safe),
        Triple(text.breakdownBridges, breakdown.areaThermalBridgeAllowanceW + breakdown.linearThermalBridgesW, EngineeringColors.Warning),
    ).filter { it.second > 0.0 }
    val total = segments.sumOf { it.second }
    if (total <= 0.0) return
    Column(Modifier.testTag(HeatCalcTags.Breakdown), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text.breakdownTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().height(20.dp)) {
            segments.forEach { (_, value, color) ->
                androidx.compose.foundation.layout.Box(Modifier.weight((value / total).toFloat()).fillMaxSize().background(color))
            }
        }
        segments.forEach { (label, value, color) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(
                    Modifier.padding(end = 8.dp).size(8.dp).background(color, androidx.compose.foundation.shape.CircleShape),
                )
                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Text(
                    heatFormat(value / total * 100, 0) + " %",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun HeatHeading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatSection(title: String) = Text(
    title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    modifier = Modifier.padding(top = 8.dp),
)

@Composable
private fun HeatMetric(label: String, value: String, accent: Color = EngineeringColors.Neutral) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            androidx.compose.foundation.layout.Box(
                Modifier.padding(end = 10.dp).size(8.dp).background(accent, androidx.compose.foundation.shape.CircleShape),
            )
            Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HeatMessage(message: String, error: Boolean = false) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(
            message,
            Modifier.padding(12.dp),
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private fun heatNumber(value: String): Double = value.replace(',', '.').toDoubleOrNull()
    ?: throw IllegalArgumentException("Invalid number")

private fun heatWhole(value: String): Int {
    val number = heatNumber(value)
    if (number <= 0 || number % 1.0 != 0.0) throw IllegalArgumentException("Positive whole number required")
    return number.toInt()
}

private fun heatFormat(value: Double, decimals: Int): String = String.format(Locale.getDefault(), "%.${decimals}f", value)

private class HeatText(private val language: AppLanguage) {
    private fun t(ru: String, en: String) = localizedUi(language, ru, en)
    val designTitle get() = t("Предварительный тепловой расчёт", "Preliminary heating calculation")
    val designBody get() = t("Теплопотери и расход теплоносителя с прозрачной трассировкой. Непроверенные подборы скрыты до ввода инженерных данных.", "Traceable heat loss and fluid flow. Unverified sizing results stay hidden until engineering inputs are supplied.")
    val geometry get() = t("Геометрия здания", "Building geometry")
    val heatedArea get() = t("Отапливаемая площадь, m²", "Heated area, m²")
    val wallsArea get() = t("Площадь наружных стен, m²", "External wall area, m²")
    val roofArea get() = t("Площадь кровли, m²", "Roof area, m²")
    val floorArea get() = t("Площадь пола, m²", "Floor area, m²")
    val windowsArea get() = t("Площадь окон/дверей, m²", "Window/door area, m²")
    val floorHeight get() = t("Высота этажа, m", "Floor height, m")
    val floors get() = t("Количество этажей", "Number of floors")
    val thermalEnvelope get() = t("Ограждения и климат", "Envelope and climate")
    val uWall get() = t("U стен, W/(m²·K)", "Wall U-value, W/(m²·K)")
    val uRoof get() = t("U кровли, W/(m²·K)", "Roof U-value, W/(m²·K)")
    val uFloor get() = t("U пола, W/(m²·K)", "Floor U-value, W/(m²·K)")
    val uWindow get() = t("U окон, W/(m²·K)", "Window U-value, W/(m²·K)")
    val indoor get() = t("Температура внутри, °C", "Indoor temperature, °C")
    val outdoor get() = t("Расчётная наружная температура, °C", "Design outdoor temperature, °C")
    val airChanges get() = t("Воздухообмен, 1/h", "Air changes, 1/h")
    val bridges get() = t("Добавка мостиков холода, W/(m²·K)", "Thermal bridge allowance, W/(m²·K)")
    val infiltration get() = t("Инфильтрация, 1/h", "Infiltration, 1/h")
    val heatRecovery get() = t("КПД рекуперации тепла, 0–1", "Heat-recovery efficiency, 0-1")
    val advancedShow get() = t("Дополнительные параметры", "Advanced parameters")
    val advancedHide get() = t("Скрыть дополнительные параметры", "Hide advanced parameters")
    val pickConstruction get() = t("Выбрать конструкцию", "Pick a construction")
    val systemSizing get() = t("Подбор системы", "System sizing")
    val hydronicDelta get() = t("Перепад подачи/обратки, K", "Supply/return difference, K")
    val calculate get() = t("Рассчитать систему", "Calculate system")
    val results get() = t("Итоговый подбор", "Design results")
    val preliminary get() = t("Предварительно", "Preliminary")
    val designHeatLoss get() = t("Расчётные теплопотери", "Design heat loss")
    val specificHeatLoss get() = t("Удельные теплопотери", "Specific heat loss")
    val heatedVolume get() = t("Отапливаемый объём", "Heated volume")
    val designFlow get() = t("Расчётный расход", "Design flow")
    val pumpHead get() = t("Потери критического контура", "Critical-circuit pressure loss")
    val heatPump get() = t("Целевая мощность теплового насоса", "Heat-pump target")
    val cooling get() = t("Оценка охлаждения", "Estimated cooling")
    val ventilation get() = t("Свежий воздух", "Fresh air")
    val dhw get() = t("Накопитель ГВС", "DHW storage")
    val zones get() = t("Зоны управления", "Control zones")
    val breakdownTitle get() = t("Структура теплопотерь", "Heat-loss breakdown")
    val breakdownWalls get() = t("Стены", "Walls")
    val breakdownRoof get() = t("Кровля", "Roof")
    val breakdownFloor get() = t("Пол", "Floor")
    val breakdownWindows get() = t("Окна", "Windows")
    val breakdownVentilation get() = t("Вентиляция", "Ventilation")
    val breakdownBridges get() = t("Мостики холода", "Thermal bridges")
    val calculationWarning get() = t("Предварительная оценка — не расчёт соответствия SIA 384/2 или EN 12831. До лицензированной проверки эталонных примеров результат нельзя использовать как единственное основание для заказа оборудования или монтажа.", "Preliminary estimate — not an SIA 384/2 or EN 12831 compliance calculation. Until licensed reference cases are validated, do not use it as the sole basis for equipment orders or installation.")
    val requiresCircuit get() = t("нужен критический контур", "critical circuit required")
    val notCalculated get() = t("не рассчитывается по коэффициенту", "not calculated by a ratio")
    fun linkedInputs(hasCircuit: Boolean): String = if (hasCircuit) {
        t("Подключён критический гидравлический контур.", "Critical hydraulic circuit is linked.")
    } else {
        t("Напор появится после расчёта гидравлического контура во вкладке гидравлики; значения между вкладками связаны.", "Pressure loss appears after a hydraulic circuit is calculated in the hydraulics tab; tab results are linked.")
    }
    fun invalidInput(message: String?) = t("Проверьте введённые размеры", "Check the entered dimensions") + message?.let { ": $it" }.orEmpty()
}

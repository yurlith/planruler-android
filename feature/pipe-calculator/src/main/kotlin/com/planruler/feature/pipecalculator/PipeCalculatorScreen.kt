package com.planruler.feature.pipecalculator

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.localization.localizedUi
import com.planruler.designsystem.localization.UiTextKey
import com.planruler.designsystem.localization.uiText
import com.planruler.designsystem.component.PlanRulerIconButton
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.theme.Space
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.model.AppLanguage
import com.planruler.model.AppSettings
import com.planruler.project.api.ProjectRepository
import com.planruler.pipecalculator.ExpansionVesselInput
import com.planruler.pipecalculator.ExpansionVesselResult
import com.planruler.pipecalculator.ECCENTRIC_REDUCER_CATALOG
import com.planruler.pipecalculator.ELBOW_45_3D_CATALOG
import com.planruler.pipecalculator.EQUAL_TEE_CATALOG
import com.planruler.pipecalculator.FLANGE_CONNECTING_DIMENSIONS
import com.planruler.pipecalculator.HydraulicInput
import com.planruler.pipecalculator.HydraulicResult
import com.planruler.pipecalculator.MANUAL_INPUT_SOURCE
import com.planruler.pipecalculator.PIPE_INSTALLATION_SERIES
import com.planruler.pipecalculator.PIPE_REFERENCE_TABLES
import com.planruler.pipecalculator.PipeTable
import com.planruler.pipecalculator.PipeTableRow
import com.planruler.pipecalculator.pipeTableById
import com.planruler.pipecalculator.PipeDimensions
import com.planruler.pipecalculator.calculateExpansionVessel
import com.planruler.pipecalculator.calculateHydraulics
import com.planruler.pipecalculator.dowfrostAt
import com.planruler.pipecalculator.manualFluid
import com.planruler.pipecalculator.waterAt
import java.util.Locale

object PipeCalculatorTags {
    const val Root = "pipe_calculator_root"
    const val HydraulicsList = "pipe_hydraulics_list"
    const val ExpansionList = "pipe_expansion_list"
    const val CalculateHydraulics = "pipe_calculate_hydraulics"
    const val HydraulicResults = "pipe_hydraulic_results"
    const val CalculateExpansion = "pipe_calculate_expansion"
    const val ExpansionResults = "pipe_expansion_results"
    const val CatalogList = "pipe_catalog_list"
    const val CatalogSections = "pipe_catalog_sections"
    const val ElbowAnimation = "pipe_elbow_animation"
    const val FlangeAnimation = "pipe_flange_animation"
    const val InstallationList = "pipe_installation_list"
    const val InstallerWizard = "pipe_installer_wizard"
    const val InstallerTaskPrefix = "pipe_installer_task_"
    const val InstallerBasicMode = "pipe_installer_basic_mode"
    const val InstallerAdvancedMode = "pipe_installer_advanced_mode"
    const val InstallerStartReference = "pipe_installer_start_reference"
    const val InstallerEndReference = "pipe_installer_end_reference"
    const val InstallerAlong = "pipe_installer_along"
    const val InstallerLateral = "pipe_installer_lateral"
    const val InstallerVertical = "pipe_installer_vertical"
    const val InstallerEndDirection = "pipe_installer_end_direction"
    const val CalculateOffsetAssembly = "pipe_calculate_offset_assembly"
    const val Assembly3D = "pipe_assembly_3d"
    const val Assembly3DCanvas = "pipe_assembly_3d_canvas"
    const val AssemblyDrawing = "pipe_assembly_drawing"
    const val AssemblyDrawingCanvas = "pipe_assembly_drawing_canvas"
    const val AssemblyDrawingPdf = "pipe_assembly_drawing_pdf"
    const val AssemblyDrawingImage = "pipe_assembly_drawing_image"
    const val AssemblyDrawingCsv = "pipe_assembly_drawing_csv"
    const val AssemblyDrawingFieldPack = "pipe_assembly_drawing_field_pack"
    const val AssemblyDrawingCheckedBy = "pipe_assembly_drawing_checked_by"
    const val AssemblyDrawingCheck = "pipe_assembly_drawing_check"
    const val AssemblyDrawingChecked = "pipe_assembly_drawing_checked"
    const val AssemblyDrawingViewPrefix = "pipe_assembly_drawing_view_"
    const val AssemblyDrawingPartsSchedule = "pipe_assembly_drawing_parts_schedule"
    const val AssemblyDrawingPartsRow = "pipe_assembly_drawing_parts_row"
    const val AssemblyDrawingPartPrefix = "pipe_assembly_drawing_part_"
    const val AssemblyDrawingSelectedPart = "pipe_assembly_drawing_selected_part"
    const val AssemblyDrawingMaterialsSummary = "pipe_assembly_drawing_materials_summary"
    const val FieldSunlight = "pipe_field_sunlight"
    const val FieldGloves = "pipe_field_gloves"
    const val FieldKeepAwake = "pipe_field_keep_awake"
    const val Assembly3DSelection = "pipe_assembly_3d_selection"
    const val Assembly3DSummary = "pipe_assembly_3d_summary"
    const val Assembly3DLengthHandle = "pipe_assembly_3d_length_handle"
    const val Assembly3DAngleHandle = "pipe_assembly_3d_angle_handle"
    const val Assembly3DRollHandle = "pipe_assembly_3d_roll_handle"
    const val Assembly3DStartXHandle = "pipe_assembly_3d_start_x_handle"
    const val Assembly3DStartYHandle = "pipe_assembly_3d_start_y_handle"
    const val Assembly3DStartZHandle = "pipe_assembly_3d_start_z_handle"
    const val Assembly3DDirectValue = "pipe_assembly_3d_direct_value"
    const val Assembly3DControlHint = "pipe_assembly_3d_control_hint"
    const val Assembly3DSceneAdd = "pipe_assembly_3d_scene_add"
    const val Assembly3DSceneRemove = "pipe_assembly_3d_scene_remove"
    const val Assembly3DManualMode = "pipe_assembly_3d_manual_mode"
    const val Assembly3DAutoMode = "pipe_assembly_3d_auto_mode"
    const val Assembly3DAddPipe = "pipe_assembly_3d_add_pipe"
    const val Assembly3DAddElbow = "pipe_assembly_3d_add_elbow"
    const val Assembly3DUndo = "pipe_assembly_3d_undo"
    const val Assembly3DRedo = "pipe_assembly_3d_redo"
    const val Assembly3DSolve = "pipe_assembly_3d_solve"
    const val Assembly3DSolverResult = "pipe_assembly_3d_solver_result"
    const val Assembly3DParametersMode = "pipe_assembly_3d_parameters_mode"
    const val Assembly3DInspector = "pipe_assembly_3d_inspector"
    const val Assembly3DInspectorValue = "pipe_assembly_3d_inspector_value"
    const val Assembly3DInspectorApply = "pipe_assembly_3d_inspector_apply"
    const val Assembly3DInspectorRemove = "pipe_assembly_3d_inspector_remove"
    const val Assembly3DElbowLimit = "pipe_assembly_3d_elbow_limit"
    const val Assembly3DRadiusMode = "pipe_assembly_3d_radius_mode"
    const val Assembly3DWeldGap = "pipe_assembly_3d_weld_gap"
    const val Assembly3DQuality = "pipe_assembly_3d_quality"
    const val Assembly3DStartFrame = "pipe_assembly_3d_start_frame"
    const val Assembly3DTargetDirection = "pipe_assembly_3d_target_direction"
    const val Assembly3DSendToManual = "pipe_assembly_3d_send_to_manual"
    const val Assembly3DMessage = "pipe_assembly_3d_message"
    const val Assembly3DCollisionWarning = "pipe_assembly_3d_collision_warning"
    const val Assembly3DMassSummary = "pipe_assembly_3d_mass_summary"
    const val Assembly3DTargetX = "pipe_assembly_3d_target_x"
    const val Assembly3DTargetY = "pipe_assembly_3d_target_y"
    const val Assembly3DTargetZ = "pipe_assembly_3d_target_z"
    const val Assembly3DDirectionX = "pipe_assembly_3d_direction_x"
    const val Assembly3DDirectionY = "pipe_assembly_3d_direction_y"
    const val Assembly3DDirectionZ = "pipe_assembly_3d_direction_z"
    const val Assembly3DPipeLength = "pipe_assembly_3d_pipe_length"
    const val Assembly3DAngleField = "pipe_assembly_3d_angle_field"
    const val Assembly3DEffectiveRadius = "pipe_assembly_3d_effective_radius"
    const val Assembly3DVerifiedMode = "pipe_assembly_3d_verified_mode"
    const val Assembly3DFlangeSummary = "pipe_assembly_3d_flange_summary"
    const val Assembly3DFlangeCatalog = "pipe_assembly_3d_flange_catalog"
    const val Assembly3DFlangeOutside = "pipe_assembly_3d_flange_outside"
    const val Assembly3DFlangeBoltCount = "pipe_assembly_3d_flange_bolt_count"
    const val Assembly3DEditVerified = "pipe_assembly_3d_edit_verified"
    const val Assembly3DAddTee = "pipe_assembly_3d_add_tee"
    const val Assembly3DAddReducer = "pipe_assembly_3d_add_reducer"
    const val Assembly3DAddCap = "pipe_assembly_3d_add_cap"
    const val Assembly3DActionsRow = "pipe_assembly_3d_actions_row"
    const val Assembly3DReducerFields = "pipe_assembly_3d_reducer_fields"
    const val Assembly3DReducerSmallDn = "pipe_assembly_3d_reducer_small_dn"
    const val Assembly3DBranch = "pipe_assembly_3d_branch"
    const val OffsetAssemblyResults = "pipe_offset_assembly_results"
    const val WorkshopStockPlan = "pipe_workshop_stock_plan"
}

internal enum class CalculatorTool { HYDRAULICS, HEATING, INSTALLATION, EXPANSION, CATALOG, GAS_CH }

private enum class FluidMode { WATER, DOWFROST, MANUAL }

private enum class CatalogSection { PIPE_TABLES, PIPES, ELBOWS, TEES, REDUCERS, FLANGES }

private enum class PipeInputMode { SERIES, TABLE, MANUAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PipeCalculatorScreen(
    language: AppLanguage,
    fabrication3d: Fabrication3DEngine,
    modifier: Modifier = Modifier,
    projectRepository: ProjectRepository? = null,
    onProjectsChanged: () -> Unit = {},
    settings: AppSettings = AppSettings(language = language),
    onSettings: (AppSettings) -> Unit = {},
) {
    val text = CalculatorText(language)
    var selected by rememberSaveable { mutableStateOf<CalculatorTool?>(null) }
    var sharedCircuit by remember { mutableStateOf<HydraulicInput?>(null) }
    BackHandler(enabled = selected != null) { selected = null }
    Column(modifier.fillMaxSize().testTag(PipeCalculatorTags.Root)) {
        val active = selected
        if (active == null) {
            WorkshopHome(language = language, onSelect = { selected = it })
        } else {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlanRulerIconButton(
                        icon = PlanRulerIcons.Back,
                        description = uiText(language, UiTextKey.BACK_TO_WORKSHOP),
                        onClick = { selected = null },
                    )
                    Text(text.tab(active), style = MaterialTheme.typography.titleLarge)
                }
            }
            when (active) {
                CalculatorTool.HYDRAULICS -> HydraulicsPage(text) { sharedCircuit = it }
                CalculatorTool.HEATING -> HeatDesignPage(language, sharedCircuit)
                CalculatorTool.INSTALLATION -> FabricationWorkshop(
                    language = language,
                    fabrication3d = fabrication3d,
                    projectRepository = projectRepository,
                    onProjectsChanged = onProjectsChanged,
                    settings = settings,
                    onSettings = onSettings,
                )
                CalculatorTool.EXPANSION -> ExpansionPage(text)
                CalculatorTool.CATALOG -> CatalogPage(text)
                CalculatorTool.GAS_CH -> GasGuardPage(text)
            }
        }
    }
}

@Composable
private fun HydraulicsPage(text: CalculatorText, onCriticalCircuit: (HydraulicInput) -> Unit) {
    var power by rememberSaveable { mutableStateOf("50") }
    var deltaT by rememberSaveable { mutableStateOf("20") }
    var outside by rememberSaveable { mutableStateOf("60.3") }
    var wall by rememberSaveable { mutableStateOf("2.9") }
    var roughness by rememberSaveable { mutableStateOf("0.05") }
    var length by rememberSaveable { mutableStateOf("25") }
    var localLoss by rememberSaveable { mutableStateOf("4") }
    var temperature by rememberSaveable { mutableStateOf("60") }
    var fluidMode by rememberSaveable { mutableStateOf(FluidMode.WATER) }
    var concentration by rememberSaveable { mutableStateOf("40") }
    var density by rememberSaveable { mutableStateOf("1035") }
    var heatCapacity by rememberSaveable { mutableStateOf("3.75") }
    var viscosity by rememberSaveable { mutableStateOf("0.0018") }
    var pipeMode by rememberSaveable { mutableStateOf(PipeInputMode.SERIES) }
    var catalogPipeId by rememberSaveable { mutableStateOf("en10220-dn50-60.3x2.9") }
    var tableId by rememberSaveable { mutableStateOf(PIPE_REFERENCE_TABLES.first().id) }
    var tableRowIndex by rememberSaveable { mutableStateOf(0) }
    val table = pipeTableById(tableId) ?: PIPE_REFERENCE_TABLES.first()
    val tableRow = table.rows.getOrElse(tableRowIndex) { table.rows.first() }
    var result by remember { mutableStateOf<HydraulicResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().testTag(PipeCalculatorTags.HydraulicsList),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading(text.hydraulicsTitle, text.hydraulicsBody) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = fluidMode == FluidMode.WATER,
                    onClick = { fluidMode = FluidMode.WATER },
                    label = { Text(text.water) },
                )
                FilterChip(
                    selected = fluidMode == FluidMode.DOWFROST,
                    onClick = { fluidMode = FluidMode.DOWFROST },
                    label = { Text("DOWFROST") },
                )
                FilterChip(
                    selected = fluidMode == FluidMode.MANUAL,
                    onClick = { fluidMode = FluidMode.MANUAL },
                    label = { Text(text.manual) },
                )
            }
        }
        item { NumericField(power, { power = it }, text.power) }
        item { NumericField(deltaT, { deltaT = it }, text.deltaT) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = pipeMode == PipeInputMode.SERIES,
                    onClick = { pipeMode = PipeInputMode.SERIES },
                    label = { Text(text.pipeCatalog) },
                )
                FilterChip(
                    selected = pipeMode == PipeInputMode.TABLE,
                    onClick = {
                        pipeMode = PipeInputMode.TABLE
                        outside = format(tableRow.outsideDiameterMm, 2)
                        wall = format((tableRow.outsideDiameterMm - tableRow.innerDiameterMm) / 2.0, 3)
                        roughness = format(table.material.roughnessMm, 4)
                    },
                    label = { Text(text.pipeTables) },
                )
                FilterChip(
                    selected = pipeMode == PipeInputMode.MANUAL,
                    onClick = { pipeMode = PipeInputMode.MANUAL },
                    label = { Text(text.manual) },
                )
            }
        }
        if (pipeMode == PipeInputMode.TABLE) {
            item {
                PipeTablePicker(
                    selectedTableId = table.id,
                    selectedRowIndex = table.rows.indexOf(tableRow),
                    onSelect = { nextTable, index ->
                        tableId = nextTable.id
                        tableRowIndex = index
                        val row = nextTable.rows[index]
                        outside = format(row.outsideDiameterMm, 2)
                        wall = format((row.outsideDiameterMm - row.innerDiameterMm) / 2.0, 3)
                        roughness = format(nextTable.material.roughnessMm, 4)
                    },
                )
            }
            item { Advisory(tableRowSummary(text, table, tableRow)) }
        } else if (pipeMode == PipeInputMode.SERIES) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PIPE_INSTALLATION_SERIES, key = { it.id }) { pipe ->
                        FilterChip(
                            selected = catalogPipeId == pipe.id,
                            onClick = {
                                catalogPipeId = pipe.id
                                outside = format(pipe.outsideDiameterMm, 1)
                                wall = format(pipe.wallThicknessMm, 1)
                            },
                            label = { Text("DN ${pipe.dn}") },
                        )
                    }
                }
            }
            item {
                val selectedPipe = PIPE_INSTALLATION_SERIES.single { it.id == catalogPipeId }
                Advisory(
                    "DN ${selectedPipe.dn}: Ø ${format(selectedPipe.outsideDiameterMm, 1)} × " +
                        "${format(selectedPipe.wallThicknessMm, 1)} mm · ${text.massPerMeter} " +
                        "${format(selectedPipe.theoreticalMassKgM, 3)} kg/m",
                )
            }
        } else {
            item { NumericField(outside, { outside = it }, text.outsideDiameter) }
            item { NumericField(wall, { wall = it }, text.wallThickness) }
        }
        item { NumericField(roughness, { roughness = it }, text.roughness) }
        item { NumericField(length, { length = it }, text.circuitLength) }
        item { NumericField(localLoss, { localLoss = it }, text.localCoefficient) }
        item { NumericField(temperature, { temperature = it }, text.fluidTemperature) }
        if (fluidMode != FluidMode.WATER) {
            item { NumericField(concentration, { concentration = it }, text.glycolConcentration) }
        }
        if (fluidMode == FluidMode.MANUAL) {
            item { NumericField(density, { density = it }, text.density) }
            item { NumericField(heatCapacity, { heatCapacity = it }, text.heatCapacity) }
            item { NumericField(viscosity, { viscosity = it }, text.viscosity) }
            item { Advisory(text.glycolWarning) }
        } else if (fluidMode == FluidMode.DOWFROST) {
            item { Advisory(text.dowfrostWarning) }
        }
        item {
            Button(
                onClick = {
                    runCatching {
                        val fluid = when (fluidMode) {
                            FluidMode.WATER -> waterAt(number(temperature))
                            FluidMode.DOWFROST -> dowfrostAt(
                                number(temperature),
                                number(concentration),
                            )
                            FluidMode.MANUAL -> manualFluid(
                                name = "Glycol ${number(concentration)} %",
                                temperatureC = number(temperature),
                                densityKgM3 = number(density),
                                specificHeatKjKgK = number(heatCapacity),
                                dynamicViscosityPaS = number(viscosity),
                            )
                        }
                        val pipeSource = when (pipeMode) {
                            PipeInputMode.SERIES -> PIPE_INSTALLATION_SERIES.single { it.id == catalogPipeId }.source
                            PipeInputMode.TABLE -> table.source
                            PipeInputMode.MANUAL -> MANUAL_INPUT_SOURCE
                        }
                        val input = HydraulicInput(
                                powerKw = number(power),
                                deltaTK = number(deltaT),
                                pipe = PipeDimensions(
                                    number(outside),
                                    number(wall),
                                    number(roughness),
                                    pipeSource,
                                ),
                                lengthM = number(length),
                                localLossCoefficient = number(localLoss),
                                fluid = fluid,
                            )
                        calculateHydraulics(input) to input
                    }.onSuccess {
                        result = it.first
                        onCriticalCircuit(it.second)
                        error = null
                    }
                        .onFailure { result = null; error = text.invalidInput(it.message) }
                },
                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.CalculateHydraulics),
            ) { Text(text.calculateCircuit) }
        }
        error?.let { item { ErrorCard(it) } }
        result?.let { value -> item { HydraulicResultCard(value, text) } }
        item {
            Advisory(if (pipeMode == PipeInputMode.MANUAL) text.manualDimensionsWarning else text.manufacturerCatalogWarning)
        }
    }
}

@Composable
private fun HydraulicResultCard(result: HydraulicResult, text: CalculatorText) {
    ResultCard(text.results, Modifier.testTag(PipeCalculatorTags.HydraulicResults)) {
        Metric(text.volumeFlow, format(result.volumeFlowM3H, 3) + " m³/h")
        Metric(text.velocity, format(result.velocityMS, 3) + " m/s")
        Metric(text.innerDiameter, format(result.innerDiameterMm, 2) + " mm")
        Metric("Reynolds", format(result.reynolds, 0))
        Metric(text.totalPressureLoss, format(result.totalLossPa, 0) + " Pa")
        Metric(text.pipeVolume, format(result.pipeVolumeLitres, 2) + " l")
        if (result.trace.warnings.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            result.trace.warnings.forEach { Text("• ${text.warning(it)}", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CatalogPage(text: CalculatorText) {
    var section by rememberSaveable { mutableStateOf(CatalogSection.PIPE_TABLES) }
    var referenceTableId by rememberSaveable { mutableStateOf(PIPE_REFERENCE_TABLES.first().id) }
    var runLength by rememberSaveable { mutableStateOf("10") }
    var selectedPn by rememberSaveable { mutableStateOf(16) }
    var selectedElbowId by rememberSaveable { mutableStateOf("heco-nb45-dn50-60.3x2.9") }
    var selectedFlangeDn by rememberSaveable { mutableStateOf(50) }

    LazyColumn(
        Modifier.fillMaxSize().testTag(PipeCalculatorTags.CatalogList),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PageHeading(text.catalogTitle, text.catalogBody) }
        item {
            LazyRow(
                Modifier.testTag(PipeCalculatorTags.CatalogSections),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(CatalogSection.entries) { item ->
                    FilterChip(
                        selected = section == item,
                        onClick = { section = item },
                        label = { Text(text.catalogSection(item)) },
                    )
                }
            }
        }
        if (section == CatalogSection.ELBOWS) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ELBOW_45_3D_CATALOG, key = { it.id }) { elbow ->
                        FilterChip(
                            selected = selectedElbowId == elbow.id,
                            onClick = { selectedElbowId = elbow.id },
                            label = { Text("DN ${elbow.dn}") },
                        )
                    }
                }
            }
        }
        if (section == CatalogSection.FLANGES) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(6, 10, 16, 25, 40)) { pn ->
                        FilterChip(
                            selected = selectedPn == pn,
                            onClick = { selectedPn = pn },
                            label = { Text("PN $pn") },
                        )
                    }
                }
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FLANGE_CONNECTING_DIMENSIONS.map { it.dn }.distinct()) { dn ->
                        FilterChip(
                            selected = selectedFlangeDn == dn,
                            onClick = { selectedFlangeDn = dn },
                            label = { Text("DN $dn") },
                        )
                    }
                }
            }
        }
        when (section) {
            CatalogSection.PIPE_TABLES -> {
                val table = pipeTableById(referenceTableId) ?: PIPE_REFERENCE_TABLES.first()
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(PIPE_REFERENCE_TABLES, key = { it.id }) { item ->
                            FilterChip(
                                selected = item.id == table.id,
                                onClick = { referenceTableId = item.id },
                                label = { Text(item.standard) },
                            )
                        }
                    }
                }
                item {
                    CatalogRow(
                        "[${table.reference}] ${table.title}",
                        listOfNotNull(table.standard, table.note).joinToString(" · "),
                    )
                }
                item { NumericField(runLength, { runLength = it }, text.runLengthForVolume) }
                val metres = runLength.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                items(table.rows) { row ->
                    CatalogRow(
                        row.label + " mm",
                        buildString {
                            append("ID ${format(row.innerDiameterMm, 1)} mm · A ${format(row.flowAreaMm2, 1)} mm²")
                            append(" · ${format(row.volumeLitresPerM, 3)} l/m")
                            row.massKgM?.let { append(" · ${format(it, 3)} kg/m") }
                            if (metres != null) {
                                append("\n${format(metres, 1)} m: ${format(row.volumeLitresPerM * metres, 2)} l")
                                row.massKgM?.let { append(" · ${format(it * metres, 2)} kg") }
                            }
                        },
                    )
                }
                item { CatalogSource(text, table.source.organisation, table.source.document) }
            }
            CatalogSection.PIPES -> {
                items(PIPE_INSTALLATION_SERIES, key = { it.id }) { pipe ->
                    CatalogRow(
                        "DN ${pipe.dn} · Ø ${format(pipe.outsideDiameterMm, 1)} × ${format(pipe.wallThicknessMm, 1)} mm",
                        "ID ${format(pipe.innerDiameterMm, 1)} mm · ${format(pipe.theoreticalMassKgM, 3)} kg/m",
                    )
                }
                item { CatalogSource(text, PIPE_INSTALLATION_SERIES.first().source.organisation, PIPE_INSTALLATION_SERIES.first().source.document) }
            }
            CatalogSection.ELBOWS -> {
                item {
                    AnimatedElbowPreview(
                        ELBOW_45_3D_CATALOG.single { it.id == selectedElbowId },
                        text.elbowAnimationDescription,
                    )
                }
                items(ELBOW_45_3D_CATALOG, key = { it.id }) { elbow ->
                    CatalogRow(
                        "DN ${elbow.dn} · Ø ${format(elbow.outsideDiameterMm, 1)} × ${format(elbow.wallThicknessMm, 1)} mm",
                        "45° · R ${format(elbow.centerlineRadiusMm, 1)} ± ${format(elbow.radiusToleranceMm, 1)} mm · A ${format(elbow.centerToEndMm, 1)} mm",
                    )
                }
                item { CatalogSource(text, ELBOW_45_3D_CATALOG.first().source.organisation, ELBOW_45_3D_CATALOG.first().source.document) }
            }
            CatalogSection.TEES -> {
                items(EQUAL_TEE_CATALOG, key = { it.id }) { tee ->
                    CatalogRow(
                        "DN ${tee.dn} · Ø ${format(tee.outsideDiameterMm, 1)} × ${format(tee.wallThicknessMm, 1)} mm",
                        "L ${format(tee.overallRunMm, 0)} mm · A ${format(tee.centerToEndMm, 0)} mm",
                    )
                }
                item { CatalogSource(text, EQUAL_TEE_CATALOG.first().source.organisation, EQUAL_TEE_CATALOG.first().source.document) }
            }
            CatalogSection.REDUCERS -> {
                items(ECCENTRIC_REDUCER_CATALOG, key = { it.id }) { reducer ->
                    CatalogRow(
                        "DN ${reducer.largeDn} → DN ${reducer.smallDn}",
                        "Ø ${format(reducer.largeOutsideDiameterMm, 1)} → ${format(reducer.smallOutsideDiameterMm, 1)} mm · L ${format(reducer.lengthMm, 0)} mm",
                    )
                }
                item { CatalogSource(text, ECCENTRIC_REDUCER_CATALOG.first().source.organisation, ECCENTRIC_REDUCER_CATALOG.first().source.document) }
            }
            CatalogSection.FLANGES -> {
                item {
                    AnimatedFlangePreview(
                        FLANGE_CONNECTING_DIMENSIONS.single { it.pn == selectedPn && it.dn == selectedFlangeDn },
                        text.flangeAnimationDescription,
                        text.frontView,
                        text.profileByType,
                    )
                }
                items(
                    FLANGE_CONNECTING_DIMENSIONS.filter { it.pn == selectedPn },
                    key = { it.id },
                ) { flange ->
                    CatalogRow(
                        "DN ${flange.dn} · PN ${flange.pn}",
                        "D ${format(flange.outsideDiameterMm, 0)} · k ${format(flange.boltCircleDiameterMm, 0)} mm · ${flange.boltHoleCount} × Ø ${format(flange.boltHoleDiameterMm, 0)} mm",
                    )
                }
                item { CatalogSource(text, FLANGE_CONNECTING_DIMENSIONS.first().source.organisation, FLANGE_CONNECTING_DIMENSIONS.first().source.document) }
            }
        }
    }
}

@Composable
private fun PipeTablePicker(
    selectedTableId: String,
    selectedRowIndex: Int,
    onSelect: (PipeTable, Int) -> Unit,
) {
    val table = pipeTableById(selectedTableId) ?: PIPE_REFERENCE_TABLES.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PIPE_REFERENCE_TABLES, key = { it.id }) { item ->
                FilterChip(
                    selected = item.id == table.id,
                    onClick = { onSelect(item, 0) },
                    label = { Text(item.standard) },
                )
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(table.rows.size) { index ->
                val row = table.rows[index]
                FilterChip(
                    selected = index == selectedRowIndex,
                    onClick = { onSelect(table, index) },
                    label = { Text(row.label) },
                )
            }
        }
    }
}

private fun tableRowSummary(text: CalculatorText, table: PipeTable, row: PipeTableRow): String = buildString {
    append("${table.standard} · ${row.label} mm · ID ${format(row.innerDiameterMm, 1)} mm")
    append(" · ${text.waterContent} ${format(row.volumeLitresPerM, 3)} l/m")
    row.massKgM?.let { append(" · ${text.massPerMeter} ${format(it, 3)} kg/m") }
}

@Composable
private fun CatalogRow(title: String, details: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CatalogSource(text: CalculatorText, organisation: String, document: String) =
    Advisory("${text.source}: $organisation · $document. ${text.manufacturerCatalogWarning}")

@Composable
private fun ExpansionPage(text: CalculatorText) {
    var systemVolume by rememberSaveable { mutableStateOf("500") }
    var densityMin by rememberSaveable { mutableStateOf("998.2") }
    var densityMax by rememberSaveable { mutableStateOf("958.4") }
    var reserve by rememberSaveable { mutableStateOf("5") }
    var precharge by rememberSaveable { mutableStateOf("1.0") }
    var finalPressure by rememberSaveable { mutableStateOf("2.5") }
    var safetyPressure by rememberSaveable { mutableStateOf("3.0") }
    var result by remember { mutableStateOf<ExpansionVesselResult?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().testTag(PipeCalculatorTags.ExpansionList),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading(text.expansionTitle, text.expansionBody) }
        item { NumericField(systemVolume, { systemVolume = it }, text.systemVolume) }
        item { NumericField(densityMin, { densityMin = it }, text.densityMinimum) }
        item { NumericField(densityMax, { densityMax = it }, text.densityMaximum) }
        item { NumericField(reserve, { reserve = it }, text.reserveVolume) }
        item { NumericField(precharge, { precharge = it }, text.precharge) }
        item { NumericField(finalPressure, { finalPressure = it }, text.finalPressure) }
        item { NumericField(safetyPressure, { safetyPressure = it }, text.safetyValve) }
        item {
            Button(
                onClick = {
                    runCatching {
                        calculateExpansionVessel(
                            ExpansionVesselInput(
                                number(systemVolume),
                                number(densityMin),
                                number(densityMax),
                                number(reserve),
                                number(precharge),
                                number(finalPressure),
                                number(safetyPressure),
                            ),
                        )
                    }.onSuccess { result = it; error = null }
                        .onFailure { result = null; error = text.invalidInput(it.message) }
                },
                modifier = Modifier.fillMaxWidth().testTag(PipeCalculatorTags.CalculateExpansion),
            ) { Text(text.calculateVessel) }
        }
        error?.let { item { ErrorCard(it) } }
        result?.let {
            item {
                ResultCard(text.results, Modifier.testTag(PipeCalculatorTags.ExpansionResults)) {
                    Metric(text.expansionVolume, format(it.expansionVolumeLitres, 2) + " l")
                    Metric(text.acceptanceVolume, format(it.requiredAcceptanceLitres, 2) + " l")
                    Metric(text.minimumNominalVolume, format(it.minimumNominalVesselLitres, 2) + " l")
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    it.trace.warnings.forEach { warning ->
                        Text("• ${text.warning(warning)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        item { Advisory(text.expansionWarning) }
    }
}

@Composable
private fun GasGuardPage(text: CalculatorText) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { PageHeading(text.gasTitle, text.gasBody) }
        item {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text.locked, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    Text(text.gasLicense)
                    Text("SVGW G1:2026 · H-Gas · ≤ 5 bar", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun NumericField(value: String, onValueChange: (String) -> Unit, label: String) {
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
private fun PageHeading(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorCard(message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun Advisory(message: String) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private fun number(value: String): Double = value.replace(',', '.').toDoubleOrNull()
    ?: throw IllegalArgumentException("Invalid number")

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.getDefault(), "%.${decimals}f", value)

private class CalculatorText(private val language: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(language, russian, english)
    fun tab(tab: CalculatorTool) = when (tab) {
        CalculatorTool.HYDRAULICS -> t("Гидравлика", "Hydraulics")
        CalculatorTool.HEATING -> t("Теплорасчёт", "Heat design")
        CalculatorTool.INSTALLATION -> t("Монтаж", "Installation")
        CalculatorTool.EXPANSION -> t("Расширительный бак", "Expansion vessel")
        CalculatorTool.CATALOG -> t("Таблицы DN/PN", "DN/PN tables")
        CalculatorTool.GAS_CH -> t("Газ CH", "Gas CH")
    }
    val hydraulicsTitle get() = t("Расчёт контура", "Circuit calculation")
    val hydraulicsBody get() = t("Расход, скорость, Reynolds, потери и объём трубы.", "Flow, velocity, Reynolds number, pressure loss and pipe volume.")
    val water get() = t("Вода", "Water")
    val manual get() = t("Вручную", "Manual")
    val pipeCatalog get() = t("Каталог DN", "DN catalog")
    val massPerMeter get() = t("масса", "mass")
    val power get() = t("Тепловая мощность, kW", "Thermal power, kW")
    val deltaT get() = t("Перепад температуры, K", "Temperature difference, K")
    val outsideDiameter get() = t("Наружный диаметр, mm", "Outside diameter, mm")
    val wallThickness get() = t("Толщина стенки, mm", "Wall thickness, mm")
    val roughness get() = t("Шероховатость, mm", "Roughness, mm")
    val circuitLength get() = t("Длина контура, m", "Circuit length, m")
    val localCoefficient get() = t("Сумма местных коэффициентов ζ", "Sum of local coefficients ζ")
    val fluidTemperature get() = t("Температура жидкости, °C", "Fluid temperature, °C")
    val glycolConcentration get() = t("Концентрация гликоля, %", "Glycol concentration, %")
    val density get() = t("Плотность, kg/m³", "Density, kg/m³")
    val heatCapacity get() = t("Теплоёмкость, kJ/(kg·K)", "Specific heat, kJ/(kg·K)")
    val viscosity get() = t("Динамическая вязкость, Pa·s", "Dynamic viscosity, Pa·s")
    val calculateCircuit get() = t("Рассчитать контур", "Calculate circuit")
    val results get() = t("Результаты", "Results")
    val volumeFlow get() = t("Объёмный расход", "Volume flow")
    val velocity get() = t("Скорость", "Velocity")
    val innerDiameter get() = t("Внутренний диаметр", "Inner diameter")
    val totalPressureLoss get() = t("Общие потери давления", "Total pressure loss")
    val pipeVolume get() = t("Объём жидкости в трубе", "Fluid volume in pipe")
    val glycolWarning get() = t("Введите свойства из актуального паспорта производителя для концентрации и температуры.", "Enter properties from the current manufacturer datasheet for the actual concentration and temperature.")
    val dowfrostWarning get() = t(
        "Таблица DOWFROST: 30–50 об.% и 10–120 °C. Между опубликованными точками применяется билинейная интерполяция; экстраполяция запрещена.",
        "DOWFROST table: 30–50 vol% and 10–120 °C. Bilinear interpolation is used between published nodes; extrapolation is rejected.",
    )
    val manualDimensionsWarning get() = t("Размеры вводятся вручную: защищённые таблицы EN/DIN не вшиты без лицензии.", "Dimensions are manual: protected EN/DIN tables are not bundled without a distribution licence.")
    val manufacturerCatalogWarning get() = t(
        "Это открытые каталожные данные производителя, относящиеся к EN/DIN, а не полный текст стандарта. Перед заказом проверьте фактический материал, исполнение и актуальный паспорт изделия.",
        "These are open manufacturer catalog data associated with EN/DIN, not the full standard. Verify the actual material, execution and current product datasheet before ordering.",
    )
    val elbowAnimationDescription get() = t(
        "Технический контур отвода с внутренней и наружной дугой, сварными торцами, осевой линией и размерами α, R, A, ØD и s.",
        "Technical elbow contour with inner/outer arcs, weld ends, centerline and α, R, A, ØD and s dimensions.",
    )
    val takeoutA get() = t("Монтажный размер A, mm", "Take-out A, mm")
    val takeoutB get() = t("Монтажный размер B, mm", "Take-out B, mm")
    val calculate get() = t("Рассчитать", "Calculate")
    val offset get() = t("Смещение, mm", "Offset, mm")
    val targetHeight get() = t("Требуемая высота H, mm", "Required height H, mm")
    val angle get() = t("Угол, градусы", "Angle, degrees")
    val elbowTakeout get() = t("Монтажный размер отвода, mm", "Elbow take-out, mm")
    val stockLength get() = t("Длина исходного хлыста", "Source stock length")
    val sawKerf get() = t("Ширина реза, mm", "Saw kerf, mm")
    val insertCutLength get() = t("Длина вставки C для резки", "Insert cut length C")
    val piecesPerStock get() = t("Вставок из одного хлыста", "Inserts per stock")
    val stockBars get() = t("Требуется хлыстов", "Stock bars required")
    val centerTravel get() = t("Между центрами отводов L", "Elbow center travel L")
    val advance get() = t("Продвижение", "Advance")
    val lengthM get() = t("Длина, m", "Length, m")
    val catalogTitle get() = t("Таблицы сварных трубных элементов", "Welded pipe-element tables")
    val catalogBody get() = t(
        "Выборочные открытые ряды DN для труб и EN 10253-4/A, а также присоединительные размеры фланцев PN 6–40 по DIN EN 1092-1.",
        "Selected open DN series for pipes and EN 10253-4/A fittings, plus PN 6–40 flange connecting dimensions according to DIN EN 1092-1.",
    )
    val source get() = t("Источник", "Source")
    val flangeAnimationDescription get() = t(
        "Технический контур фланца: точные D, болтовой круг k, число и диаметр отверстий d₂. Боковой профиль показан схематически.",
        "Technical flange contour: exact D, bolt circle k, hole count and d₂. The side profile is schematic.",
    )
    val frontView get() = t("вид спереди", "front view")
    val pipeTables get() = t("Таблицы труб", "Pipe tables")
    val runLengthForVolume get() = t("Длина трассы для объёма и массы, m", "Run length for volume and mass, m")
    val waterContent get() = t("Объём воды", "Water content")
    val profileByType get() = t("профиль — по типу", "profile — by flange type")
    fun catalogSection(section: CatalogSection) = when (section) {
        CatalogSection.PIPE_TABLES -> t("Таблицы труб", "Pipe tables")
        CatalogSection.PIPES -> t("Трубы под фитинги", "Fitting pipe series")
        CatalogSection.ELBOWS -> t("Отводы", "Elbows")
        CatalogSection.TEES -> t("Тройники", "Tees")
        CatalogSection.REDUCERS -> t("Переходы", "Reducers")
        CatalogSection.FLANGES -> t("Фланцы", "Flanges")
    }
    val expansionTitle get() = t("Расширительный бак", "Expansion vessel")
    val expansionBody get() = t("Предварительный подбор для закрытой системы отопления.", "Preliminary sizing for a closed heating system.")
    val systemVolume get() = t("Объём системы, l", "System volume, l")
    val densityMinimum get() = t("Плотность при Tmin, kg/m³", "Density at Tmin, kg/m³")
    val densityMaximum get() = t("Плотность при Tmax, kg/m³", "Density at Tmax, kg/m³")
    val reserveVolume get() = t("Резерв, l", "Reserve volume, l")
    val precharge get() = t("Предварительное давление, bar(g)", "Pre-charge pressure, bar(g)")
    val finalPressure get() = t("Конечное давление, bar(g)", "Final pressure, bar(g)")
    val safetyValve get() = t("Клапан безопасности, bar(g)", "Safety valve, bar(g)")
    val calculateVessel get() = t("Рассчитать бак", "Calculate vessel")
    val expansionVolume get() = t("Объём расширения", "Expansion volume")
    val acceptanceVolume get() = t("Нужный принимаемый объём", "Required acceptance volume")
    val minimumNominalVolume get() = t("Минимальный номинальный объём", "Minimum nominal volume")
    val expansionWarning get() = t("Результат предварительный: нужна проверка по лицензированным EN 12828 и SIA 384/1.", "This is a preliminary result; verify against licensed EN 12828 and SIA 384/1.")
    val gasTitle get() = t("Газовые установки — Швейцария", "Gas installations — Switzerland")
    val gasBody get() = t("Модуль не выдаёт расчёты до лицензирования и экспертной приёмки.", "The module does not produce results until licensing and expert acceptance are complete.")
    val locked get() = t("Расчёт заблокирован", "Calculation locked")
    val gasLicense get() = t("Таблицы и алгоритмы SVGW нельзя переносить в сторонний IT-инструмент без отдельного разрешения.", "SVGW tables and algorithms require separate permission before implementation in a third-party IT tool.")
    fun invalidInput(message: String?) = t("Проверьте введённые данные", "Check the entered values") + message?.let { ": $it" }.orEmpty()
    fun warning(english: String) = when (english) {
        "Transition flow regime: friction has increased uncertainty." -> t("Переходный режим: коэффициент трения имеет повышенную неопределённость.", english)
        "Velocity exceeds 2 m/s; verify noise, erosion and project limits." -> t("Скорость выше 2 m/s: проверьте шум, эрозию и проектные ограничения.", english)
        "Fluid properties are advisory and require project-specific verification." -> t("Свойства теплоносителя имеют справочный статус и требуют проектной проверки.", english)
        "Preliminary result: verify against the licensed EN 12828 and SIA 384/1 editions." -> t("Проверьте результат по лицензированным EN 12828 и SIA 384/1.", english)
        "Select the next suitable vessel and verify its pressure/temperature limits separately." -> t("Выберите следующий подходящий бак и отдельно проверьте давление/температуру.", english)
        else -> english
    }
}

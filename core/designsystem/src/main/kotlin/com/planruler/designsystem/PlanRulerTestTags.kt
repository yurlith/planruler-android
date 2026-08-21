package com.planruler.designsystem

/**
 * Stable handles for instrumentation. Tests navigate by tag and assert on text only
 * where the text itself is the subject (values, error messages).
 */
object PlanRulerTestTags {
    const val ProjectsList = "pr:projects:list"
    const val ProjectsFab = "pr:projects:fab"
    const val ProjectsSearch = "pr:projects:search"
    const val ProjectCard = "pr:projects:card"
    const val HomeRoot = "pr:home:root"
    const val WorkshopRoot = "pr:workshop:root"
    const val MenuRoot = "pr:menu:root"
    const val MenuSettings = "pr:menu:settings"
    const val MenuSettingsEntry = "pr:menu:settings-entry"

    const val WorkspaceCanvas = "pr:workspace:canvas"
    const val WorkspaceBack = "pr:workspace:back"
    const val ToolButton = "pr:tool"
    const val ToolMore = "pr:tool:more"
    const val FieldSummary = "pr:field:summary"
    const val LastMeasurement = "pr:field:last-measurement"
    const val MaterialTotal = "pr:field:material-total"
    const val CalculateAssembly = "pr:field:calculate-assembly"

    const val IndicatorPage = "pr:indicator:page"
    const val IndicatorScale = "pr:indicator:scale"
    const val IndicatorUnits = "pr:indicator:units"
    const val IndicatorSnap = "pr:indicator:snap"

    const val ConfirmDraft = "pr:draft:confirm"
    const val CancelDraft = "pr:draft:cancel"
    const val BackPoint = "pr:draft:back"

    const val Undo = "pr:action:undo"
    const val Redo = "pr:action:redo"
    const val SaveStatus = "pr:status:save"
    const val TextEntrySave = "text-entry-save"

    const val PropertiesSheet = "pr:properties:sheet"
    const val LayersSheet = "pr:layers:sheet"
    const val CalibrationSheet = "pr:calibration:sheet"
    const val CalibrationApply = "pr:calibration:apply"
    const val CalibrationVerify = "pr:calibration:verify"
    const val TemplatesOpen = "pr:templates:open"
    const val TemplatesSheet = "pr:templates:sheet"
    const val TemplateSave = "pr:templates:save"
    const val RepeatLast = "pr:takeoff:repeat"
    const val ExactLengthApply = "pr:takeoff:exact-apply"
    const val PhotoMetadataMenu = "pr:photo-metadata:menu"
    const val PhotoMetadataSheet = "pr:photo-metadata:sheet"
    const val ScheduleRow = "pr:schedule:row"
    const val ExportStep = "pr:export:step"
    const val ExportRun = "pr:export:run"

    fun tool(name: String) = "$ToolButton:$name"
    fun project(id: String) = "$ProjectCard:$id"
    fun navigation(destination: String) = "pr:navigation:${destination.lowercase()}"
    fun workshopTool(tool: String) = "pr:workshop:tool:${tool.lowercase()}"
    fun scheduleRow(id: String) = "$ScheduleRow:$id"
}

package com.planruler.export.api

import com.planruler.model.PlanProject
import com.planruler.model.TradeCategory

enum class ExportFormat { ANNOTATED_PDF, CSV, JSON }
enum class ExportPageSelection { CURRENT, ALL, RANGE }

/**
 * User visible strings the exported document needs. The gateway is an adapter and
 * owns no copy, so the caller supplies the already localised words.
 */
data class ExportLabels(
    val project: String = "Project",
    val exportedAt: String = "Exported",
    val scale: String = "Scale",
    val notCalibrated: String = "Not calibrated",
    val units: String = "Units",
    val legend: String = "Legend",
    val items: String = "pcs",
    val calibratedAt: String = "Calibrated",
    val calibratedBy: String = "Calibrated by",
    val calibrationMethod: String = "Method",
    val verification: String = "Verification",
    val localUser: String = "Device user",
    val referenceMethod: String = "Known distance",
    val printRatioMethod: String = "Drawing scale",
    val page: String = "page",
    val expected: String = "expected",
    val measured: String = "measured",
    val template: String = "Template",
    val material: String = "Material",
    val layer: String = "Layer",
    val baseQuantity: String = "Base",
    val withWaste: String = "With waste",
    val revisionLog: String = "Revision log",
    val revision: String = "Revision",
    val needsReview: String = "Needs review",
    val reviewed: String = "Reviewed",
    val previousPlan: String = "Previous plan",
    val newPlan: String = "New plan",
    val controlPoints: String = "Control points",
    val note: String = "Note",
    val categories: Map<TradeCategory, String> = TradeCategory.entries.associateWith { it.name },
) {
    fun category(category: TradeCategory) = categories[category] ?: category.name
}

data class ExportRequest(
    val project: PlanProject,
    val targetUri: String,
    val format: ExportFormat,
    val pageArgb: IntArray? = null,
    val pagePixelWidth: Int = 0,
    val pagePixelHeight: Int = 0,
    val pageSelection: ExportPageSelection = ExportPageSelection.CURRENT,
    val firstPage: Int = project.selectedPage,
    val lastPage: Int = project.selectedPage,
    val includeLegend: Boolean = true,
    val includeScale: Boolean = true,
    val csvDelimiter: String = ",",
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val labels: ExportLabels = ExportLabels(),
)
sealed interface ExportError {
    data object PermissionDenied : ExportError
    data class Io(val message: String) : ExportError
    data object PageRequired : ExportError
}
sealed interface ExportResult {
    data object Success : ExportResult
    data class Error(val error: ExportError) : ExportResult
}
interface ExportGateway { suspend fun export(request: ExportRequest): ExportResult }

package com.planruler.pipecalculator

const val PIPE_CALCULATOR_ENGINE_VERSION = "0.3.0-pre-normative-audit"

enum class SourceKind { NORMATIVE, MANUFACTURER, SUPPLIER, USER, SECONDARY }

enum class ValidationStatus {
    VERIFIED,
    ADVISORY,
    PRELIMINARY,
    DRAFT_STANDARD,
    REQUIRES_LICENSED_VALIDATION,
}

/** A calculation may only be presented as normative after licensed-reference validation. */
enum class CalculationStatus {
    VERIFIED,
    PRELIMINARY,
    INCOMPLETE_INPUT,
    REQUIRES_LICENSED_VALIDATION,
}

data class CalculationStep(
    val id: String,
    val formula: String,
    val value: Double,
    val unit: String,
)

data class DataSource(
    val id: String,
    val organisation: String,
    val document: String,
    val edition: String,
    val kind: SourceKind,
    val validationStatus: ValidationStatus,
    val checkedAt: String,
    val url: String? = null,
)

data class CalculationTrace(
    val engineVersion: String = PIPE_CALCULATOR_ENGINE_VERSION,
    val formula: String,
    val sourceIds: List<String>,
    val warnings: List<String> = emptyList(),
    val methodId: String = "planruler-preliminary",
    val status: CalculationStatus = CalculationStatus.PRELIMINARY,
    val steps: List<CalculationStep> = emptyList(),
)

data class FluidProperties(
    val name: String,
    val temperatureC: Double,
    val densityKgM3: Double,
    val specificHeatKjKgK: Double,
    val dynamicViscosityPaS: Double,
    val source: DataSource,
    val concentrationPercent: Double? = null,
)

data class PipeDimensions(
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val roughnessMm: Double,
    val source: DataSource,
)

data class HydraulicInput(
    val powerKw: Double,
    val deltaTK: Double,
    val pipe: PipeDimensions,
    val lengthM: Double,
    val localLossCoefficient: Double,
    val fluid: FluidProperties,
)

data class ExpansionVesselInput(
    val systemVolumeLitres: Double,
    val densityAtMinimumKgM3: Double,
    val densityAtMaximumKgM3: Double,
    val reserveLitres: Double,
    val prechargeBarGauge: Double,
    val finalPressureBarGauge: Double,
    val safetyValveBarGauge: Double,
)

class CalculationException(message: String) : IllegalArgumentException(message)

internal fun positive(value: Double, label: String): Double {
    if (!value.isFinite() || value <= 0.0) throw CalculationException("$label must be greater than zero")
    return value
}

internal fun nonNegative(value: Double, label: String): Double {
    if (!value.isFinite() || value < 0.0) throw CalculationException("$label must not be negative")
    return value
}

internal fun within(value: Double, min: Double, max: Double, label: String): Double {
    if (!value.isFinite() || value < min || value > max) {
        throw CalculationException("$label must be between $min and $max")
    }
    return value
}

val MANUAL_INPUT_SOURCE = DataSource(
    id = "user-input",
    organisation = "PlanRuler user",
    document = "Manual dimensional input",
    edition = "current-input",
    kind = SourceKind.USER,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "runtime",
)

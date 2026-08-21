package com.planruler.pipecalculator

data class FluidGridPoint(
    val temperatureC: Double,
    val concentrationPercent: Double,
    val densityKgM3: Double,
    val specificHeatKjKgK: Double,
    val dynamicViscosityPaS: Double,
)

data class FluidDataset(
    val id: String,
    val name: String,
    val points: List<FluidGridPoint>,
    val source: DataSource,
)

private fun bounds(values: List<Double>, target: Double, label: String): Pair<Double, Double> {
    val sorted = values.distinct().sorted()
    if (sorted.size < 2) throw CalculationException("At least two $label points are required")
    within(target, sorted.first(), sorted.last(), label)
    return sorted.last { it <= target } to sorted.first { it >= target }
}

private fun linear(x: Double, x0: Double, x1: Double, y0: Double, y1: Double): Double =
    if (x0 == x1) y0 else y0 + (y1 - y0) * (x - x0) / (x1 - x0)

private fun FluidDataset.point(t: Double, c: Double): FluidGridPoint =
    points.firstOrNull { it.temperatureC == t && it.concentrationPercent == c }
        ?: throw CalculationException("Fluid table has no node for $t °C / $c %")

private fun FluidDataset.interpolateProperty(
    temperatureC: Double,
    concentrationPercent: Double,
    selector: (FluidGridPoint) -> Double,
): Double {
    val (t0, t1) = bounds(points.map { it.temperatureC }, temperatureC, "temperature")
    val (c0, c1) = bounds(points.map { it.concentrationPercent }, concentrationPercent, "concentration")
    val atC0 = linear(temperatureC, t0, t1, selector(point(t0, c0)), selector(point(t1, c0)))
    val atC1 = linear(temperatureC, t0, t1, selector(point(t0, c1)), selector(point(t1, c1)))
    return linear(concentrationPercent, c0, c1, atC0, atC1)
}

fun interpolateFluid(
    dataset: FluidDataset,
    temperatureC: Double,
    concentrationPercent: Double,
): FluidProperties {
    if (dataset.points.size < 4) throw CalculationException("Fluid table contains too few points")
    return FluidProperties(
        name = dataset.name,
        temperatureC = temperatureC,
        concentrationPercent = concentrationPercent,
        densityKgM3 = positive(
            dataset.interpolateProperty(temperatureC, concentrationPercent) { it.densityKgM3 },
            "Interpolated density",
        ),
        specificHeatKjKgK = positive(
            dataset.interpolateProperty(temperatureC, concentrationPercent) { it.specificHeatKjKgK },
            "Interpolated heat capacity",
        ),
        dynamicViscosityPaS = positive(
            dataset.interpolateProperty(temperatureC, concentrationPercent) { it.dynamicViscosityPaS },
            "Interpolated viscosity",
        ),
        source = dataset.source,
    )
}

private val waterSource = DataSource(
    id = "water-advisory-v1",
    organisation = "PlanRuler",
    document = "Engineering water checkpoints; normative verification required",
    edition = "1",
    kind = SourceKind.SECONDARY,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "2026-08-13",
)

private val waterBase = listOf(
    FluidGridPoint(0.0, 0.0, 999.84, 4.219, 0.001792),
    FluidGridPoint(20.0, 0.0, 998.2, 4.182, 0.001002),
    FluidGridPoint(60.0, 0.0, 983.2, 4.185, 0.000467),
    FluidGridPoint(100.0, 0.0, 958.4, 4.216, 0.000282),
)

val WATER_DATASET = FluidDataset(
    id = "water-advisory-v1",
    name = "Water",
    points = waterBase + waterBase.map { it.copy(concentrationPercent = 1.0) },
    source = waterSource,
)

fun waterAt(temperatureC: Double): FluidProperties = interpolateFluid(WATER_DATASET, temperatureC, 0.0)

val DOWFROST_SOURCE = DataSource(
    id = "dowfrost-tds-180-01587-11",
    organisation = "Dow Chemical Company",
    document = "DOWFROST Heat Transfer Fluid — Technical Data Sheet",
    edition = "Form No. 180-01587-11",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://www.dow.com/content/dam/internal/documents/180/180-01587-11-dowfrost-technical-data-sheet.pdf?iframe=true",
)

private fun dowfrostPoint(
    temperatureC: Double,
    concentrationPercent: Double,
    densityKgM3: Double,
    specificHeatKjKgK: Double,
    dynamicViscosityMpaS: Double,
) = FluidGridPoint(
    temperatureC = temperatureC,
    concentrationPercent = concentrationPercent,
    densityKgM3 = densityKgM3,
    specificHeatKjKgK = specificHeatKjKgK,
    dynamicViscosityPaS = dynamicViscosityMpaS / 1_000.0,
)

/**
 * Published DOWFROST propylene-glycol solution checkpoints.
 * Concentration is volume percent. Values between nodes are bilinearly interpolated;
 * extrapolation beyond 30–50 vol% and 10–120 °C is intentionally rejected.
 */
val DOWFROST_DATASET = FluidDataset(
    id = "dowfrost-30-50vol-10-120c",
    name = "DOWFROST propylene glycol",
    points = listOf(
        dowfrostPoint(10.0, 30.0, 1_033.71, 3.821, 4.5068),
        dowfrostPoint(40.0, 30.0, 1_019.56, 3.903, 1.6295),
        dowfrostPoint(65.0, 30.0, 1_004.26, 3.972, 0.9144),
        dowfrostPoint(90.0, 30.0, 985.77, 4.041, 0.6040),
        dowfrostPoint(120.0, 30.0, 959.35, 4.123, 0.4246),
        dowfrostPoint(10.0, 40.0, 1_042.14, 3.668, 7.2173),
        dowfrostPoint(40.0, 40.0, 1_026.49, 3.768, 2.2389),
        dowfrostPoint(65.0, 40.0, 1_009.90, 3.850, 1.1762),
        dowfrostPoint(90.0, 40.0, 990.10, 3.933, 0.7462),
        dowfrostPoint(120.0, 40.0, 962.08, 4.032, 0.5084),
        dowfrostPoint(10.0, 50.0, 1_049.25, 3.493, 10.6481),
        dowfrostPoint(40.0, 50.0, 1_032.17, 3.609, 3.1103),
        dowfrostPoint(65.0, 50.0, 1_014.40, 3.706, 1.5483),
        dowfrostPoint(90.0, 50.0, 993.42, 3.802, 0.9339),
        dowfrostPoint(120.0, 50.0, 964.00, 3.918, 0.6029),
    ),
    source = DOWFROST_SOURCE,
)

fun dowfrostAt(temperatureC: Double, concentrationPercent: Double): FluidProperties =
    interpolateFluid(DOWFROST_DATASET, temperatureC, concentrationPercent)

fun manualFluid(
    name: String,
    temperatureC: Double,
    densityKgM3: Double,
    specificHeatKjKgK: Double,
    dynamicViscosityPaS: Double,
): FluidProperties = FluidProperties(
    name = name,
    temperatureC = temperatureC,
    densityKgM3 = positive(densityKgM3, "Density"),
    specificHeatKjKgK = positive(specificHeatKjKgK, "Specific heat"),
    dynamicViscosityPaS = positive(dynamicViscosityPaS, "Dynamic viscosity"),
    source = MANUAL_INPUT_SOURCE,
)

package com.planruler.pipecalculator

enum class EnvelopeElement { WALL, ROOF, FLOOR, WINDOW }

data class EnvelopeConstruction(
    val id: String,
    val element: EnvelopeElement,
    val label: String,
    val uValueWm2K: Double,
    val source: DataSource,
)

/**
 * Typical construction U-values for picking a starting point in [HeatLossInput], not a
 * certified assembly library. Ranges follow commonly published planning figures (SIA 380/1
 * planning tables, EN ISO 6946 worked assemblies, Passive House Institute criteria); a
 * project-specific value from an actual assembly calculation always takes precedence, which
 * is why every entry stays editable rather than locking the field once picked.
 */
object EnvelopeCatalog {
    private val typicalSource = DataSource(
        id = "planruler-typical-envelope-u-values-v1",
        organisation = "PlanRuler",
        document = "Typical construction U-value planning ranges (SIA 380/1 planning tables, " +
            "EN ISO 6946 worked assemblies, Passive House Institute criteria)",
        edition = "v1",
        kind = SourceKind.SECONDARY,
        validationStatus = ValidationStatus.ADVISORY,
        checkedAt = "2026-08-19",
    )

    val walls = listOf(
        EnvelopeConstruction("wall-existing-uninsulated", EnvelopeElement.WALL, "Existing masonry, uninsulated", 1.50, typicalSource),
        EnvelopeConstruction("wall-existing-retrofit", EnvelopeElement.WALL, "Existing masonry, retrofit insulation", 0.35, typicalSource),
        EnvelopeConstruction("wall-new-code", EnvelopeElement.WALL, "New build, current code level", 0.22, typicalSource),
        EnvelopeConstruction("wall-passive-house", EnvelopeElement.WALL, "High-performance / Passive House", 0.13, typicalSource),
    )

    val roofs = listOf(
        EnvelopeConstruction("roof-existing-uninsulated", EnvelopeElement.ROOF, "Existing roof, uninsulated", 1.80, typicalSource),
        EnvelopeConstruction("roof-existing-retrofit", EnvelopeElement.ROOF, "Existing roof, retrofit insulation", 0.25, typicalSource),
        EnvelopeConstruction("roof-new-code", EnvelopeElement.ROOF, "New build, current code level", 0.18, typicalSource),
        EnvelopeConstruction("roof-passive-house", EnvelopeElement.ROOF, "High-performance / Passive House", 0.11, typicalSource),
    )

    val floors = listOf(
        EnvelopeConstruction("floor-existing-uninsulated", EnvelopeElement.FLOOR, "Existing ground floor, uninsulated", 1.20, typicalSource),
        EnvelopeConstruction("floor-existing-retrofit", EnvelopeElement.FLOOR, "Existing ground floor, retrofit insulation", 0.35, typicalSource),
        EnvelopeConstruction("floor-new-code", EnvelopeElement.FLOOR, "New build, current code level", 0.25, typicalSource),
        EnvelopeConstruction("floor-passive-house", EnvelopeElement.FLOOR, "High-performance / Passive House", 0.15, typicalSource),
    )

    val windows = listOf(
        EnvelopeConstruction("window-single", EnvelopeElement.WINDOW, "Single glazing", 5.50, typicalSource),
        EnvelopeConstruction("window-double-old", EnvelopeElement.WINDOW, "Older double glazing", 2.80, typicalSource),
        EnvelopeConstruction("window-double-lowe", EnvelopeElement.WINDOW, "Modern double glazing, low-E argon", 1.10, typicalSource),
        EnvelopeConstruction("window-triple", EnvelopeElement.WINDOW, "Triple glazing", 0.70, typicalSource),
    )

    fun forElement(element: EnvelopeElement): List<EnvelopeConstruction> = when (element) {
        EnvelopeElement.WALL -> walls
        EnvelopeElement.ROOF -> roofs
        EnvelopeElement.FLOOR -> floors
        EnvelopeElement.WINDOW -> windows
    }
}

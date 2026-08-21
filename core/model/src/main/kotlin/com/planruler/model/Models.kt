package com.planruler.model

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.pow

@Serializable
@JvmInline value class ProjectId(val value: String)
@Serializable
@JvmInline value class DocumentId(val value: String)
@Serializable
@JvmInline value class MeasurementId(val value: String)
@Serializable
@JvmInline value class LayerId(val value: String)
@Serializable
@JvmInline value class InstallationJobId(val value: String)

@Serializable
data class DocPoint(val x: Double, val y: Double) {
    fun distanceTo(other: DocPoint): Double = hypot(x - other.x, y - other.y)
}

data class ScreenPoint(val x: Double, val y: Double)
data class ViewportPoint(val x: Double, val y: Double)

@Serializable
data class ViewportState(
    val zoom: Double = 1.0,
    val centerX: Double = 0.0,
    val centerY: Double = 0.0,
)

/** Stable power-of-two scale used by the deep-zoom tile cache. */
fun quantizedRenderScale(zoom: Double): Double {
    val safeZoom = zoom.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
    return 2.0.pow(ceil(log2(safeZoom))).coerceIn(0.01, 64.0)
}

/**
 * Reversible transform whose stored center is in document coordinates.
 * Resizing the screen therefore does not move the viewed document center.
 */
data class ViewportTransform(
    val viewportWidth: Double,
    val viewportHeight: Double,
    val state: ViewportState,
    val minZoom: Double = 0.1,
    val maxZoom: Double = 32.0,
) {
    private val zoom = state.zoom.coerceIn(minZoom, maxZoom)
    fun screenToDocument(point: ScreenPoint) = DocPoint(
        (point.x - viewportWidth / 2.0) / zoom + state.centerX,
        (point.y - viewportHeight / 2.0) / zoom + state.centerY,
    )
    fun documentToScreen(point: DocPoint) = ScreenPoint(
        (point.x - state.centerX) * zoom + viewportWidth / 2.0,
        (point.y - state.centerY) * zoom + viewportHeight / 2.0,
    )
    fun zoomAt(factor: Double, focus: ScreenPoint): ViewportState {
        val safeFactor = factor.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        val safeFocus = focus.takeIf { it.x.isFinite() && it.y.isFinite() }
            ?: ScreenPoint(viewportWidth / 2.0, viewportHeight / 2.0)
        val anchor = screenToDocument(safeFocus)
        val nextZoom = (zoom * safeFactor).coerceIn(minZoom, maxZoom)
        return ViewportState(
            zoom = nextZoom,
            centerX = anchor.x - (safeFocus.x - viewportWidth / 2.0) / nextZoom,
            centerY = anchor.y - (safeFocus.y - viewportHeight / 2.0) / nextZoom,
        )
    }
    fun panBy(screenDx: Double, screenDy: Double) = state.copy(
        zoom = zoom,
        centerX = state.centerX - (screenDx.takeIf { it.isFinite() } ?: 0.0) / zoom,
        centerY = state.centerY - (screenDy.takeIf { it.isFinite() } ?: 0.0) / zoom,
    )
}

@Serializable
enum class LengthUnit(val metersPerUnit: Double, val symbol: String) {
    MILLIMETER(0.001, "mm"), CENTIMETER(0.01, "cm"), METER(1.0, "m"),
    INCH(0.0254, "in"), FOOT(0.3048, "ft");
    fun toMeters(value: Double) = value * metersPerUnit
    fun fromMeters(value: Double) = value / metersPerUnit
}

@Serializable
/**
 * Languages supported by the user interface.
 *
 * Russian remains in the model so installations that already persisted it keep working.
 * The first three entries are the product languages selected for the international release.
 */
enum class AppLanguage { POLISH, ENGLISH, GERMAN, FRENCH, ITALIAN, RUSSIAN }

@Serializable
data class Calibration(
    val metersPerDocumentUnit: Double,
    val method: Method,
    val description: String,
    /** Optional for projects created before schema 3. */
    val audit: CalibrationAudit? = null,
) {
    @Serializable enum class Method { REFERENCE, PRINT_RATIO }
    init { require(metersPerDocumentUnit.isFinite() && metersPerDocumentUnit > 0.0) }

    companion object {
        fun reference(
            documentLength: Double,
            realLength: Double,
            unit: LengthUnit,
            audit: CalibrationAudit? = null,
        ): Calibration {
            require(documentLength > 0.0) { "Reference segment must not be zero" }
            require(realLength > 0.0) { "Known length must be positive" }
            return Calibration(
                unit.toMeters(realLength) / documentLength,
                Method.REFERENCE,
                "Manual reference",
                audit,
            )
        }
        /** PDF coordinates are points: 72 points = one printed inch. */
        fun pdfRatio(ratio: Double, audit: CalibrationAudit? = null): Calibration {
            require(ratio > 0.0)
            return Calibration(
                0.0254 / 72.0 * ratio,
                Method.PRINT_RATIO,
                "1:${ratio.toInt()} from PDF points",
                audit,
            )
        }
    }
}

/** Persisted provenance for decisions that affect every calculated quantity. */
@Serializable
data class CalibrationAudit(
    val calibratedAtEpochMs: Long,
    val calibratedBy: String,
    val pageIndex: Int,
    val referenceDocumentLength: Double? = null,
    val enteredLength: Double? = null,
    val enteredUnit: LengthUnit? = null,
    val printRatio: Double? = null,
    val printSizeConfirmed: Boolean? = null,
    val verification: CalibrationVerification? = null,
)

/** Independent control segment measured after the scale has been set. */
@Serializable
data class CalibrationVerification(
    val verifiedAtEpochMs: Long,
    val pageIndex: Int,
    val documentLength: Double,
    val expectedLength: Double,
    val measuredLength: Double,
    val unit: LengthUnit,
) {
    val relativeError: Double
        get() = if (expectedLength > 0.0) kotlin.math.abs(measuredLength - expectedLength) / expectedLength else 0.0
}

@Serializable enum class MeasurementType { DISTANCE, POLYLINE, AREA, ANGLE, ANNOTATION, COUNTER }
@Serializable enum class TradeCategory { HEATING, PLUMBING, ELECTRICAL, HVAC, PAINTING, FLOORING, GENERAL }
@Serializable enum class DistanceConstraint { FREE, HORIZONTAL, VERTICAL }
@Serializable enum class MeasurementReviewStatus { VERIFIED, NEEDS_REVIEW }

@Serializable
data class MeasurementStyle(
    val colorArgb: Long = 0xFFFF3B30,
    val strokeWidth: Float = 2f,
    val textSize: Float = 14f,
)

@Serializable
data class TakeoffProperties(
    val category: TradeCategory = TradeCategory.GENERAL,
    val subcategory: String? = null,
    val material: String? = null,
    val diameter: String? = null,
    val size: String? = null,
    val quantity: Double = 1.0,
    val wasteFactor: Double = 1.0,
    val comment: String? = null,
)

@Serializable
data class TakeoffTemplate(
    val id: String,
    val name: String,
    val measurementType: MeasurementType,
    val style: MeasurementStyle,
    val displayUnit: LengthUnit? = null,
    val layerId: LayerId = LayerId("default"),
    val takeoff: TakeoffProperties,
)

/** Starter packs are project data after creation: users may edit them without app-global side effects. */
fun starterTakeoffTemplates(): List<TakeoffTemplate> = listOf(
    TakeoffTemplate(
        "plumbing-cold-dn20", "ХВС DN20", MeasurementType.POLYLINE,
        MeasurementStyle(0xFF1976D2, 2.5f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.PLUMBING, material = "Труба ХВС", diameter = "DN20", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "plumbing-hot-dn20", "ГВС DN20", MeasurementType.POLYLINE,
        MeasurementStyle(0xFFE53935, 2.5f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.PLUMBING, material = "Труба ГВС", diameter = "DN20", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "plumbing-sewer-dn50", "Канализация DN50", MeasurementType.POLYLINE,
        MeasurementStyle(0xFF6D4C41, 3f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.PLUMBING, material = "Канализационная труба", diameter = "DN50", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "heating-supply-dn20", "Отопление — подача DN20", MeasurementType.POLYLINE,
        MeasurementStyle(0xFFD32F2F, 2.5f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.HEATING, material = "Труба подачи", diameter = "DN20", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "heating-return-dn20", "Отопление — обратка DN20", MeasurementType.POLYLINE,
        MeasurementStyle(0xFF7B1FA2, 2.5f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.HEATING, material = "Труба обратки", diameter = "DN20", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "heating-radiator", "Радиатор", MeasurementType.COUNTER,
        MeasurementStyle(0xFFC62828, 3f), takeoff = TakeoffProperties(
            category = TradeCategory.HEATING, material = "Радиатор",
        ),
    ),
    TakeoffTemplate(
        "electrical-cable", "Кабельная трасса", MeasurementType.POLYLINE,
        MeasurementStyle(0xFFF9A825, 2.5f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.ELECTRICAL, material = "Кабель", wasteFactor = 1.1,
        ),
    ),
    TakeoffTemplate(
        "electrical-socket", "Розетка", MeasurementType.COUNTER,
        MeasurementStyle(0xFFFF8F00, 3f), takeoff = TakeoffProperties(
            category = TradeCategory.ELECTRICAL, material = "Розетка",
        ),
    ),
    TakeoffTemplate(
        "electrical-light", "Светильник", MeasurementType.COUNTER,
        MeasurementStyle(0xFFFDD835, 3f), takeoff = TakeoffProperties(
            category = TradeCategory.ELECTRICAL, material = "Светильник",
        ),
    ),
    TakeoffTemplate(
        "hvac-duct-160", "Воздуховод Ø160", MeasurementType.POLYLINE,
        MeasurementStyle(0xFF00838F, 3f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.HVAC, material = "Круглый воздуховод", diameter = "Ø160", wasteFactor = 1.05,
        ),
    ),
    TakeoffTemplate(
        "hvac-grille", "Вентрешётка", MeasurementType.COUNTER,
        MeasurementStyle(0xFF006064, 3f), takeoff = TakeoffProperties(
            category = TradeCategory.HVAC, material = "Вентиляционная решётка",
        ),
    ),
    TakeoffTemplate(
        "painting-wall", "Покраска стен", MeasurementType.AREA,
        MeasurementStyle(0xFF8E24AA, 2f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.PAINTING, material = "Краска для стен", wasteFactor = 1.1,
        ),
    ),
    TakeoffTemplate(
        "painting-ceiling", "Покраска потолка", MeasurementType.AREA,
        MeasurementStyle(0xFF5E35B1, 2f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.PAINTING, material = "Краска для потолка", wasteFactor = 1.1,
        ),
    ),
    TakeoffTemplate(
        "flooring-tile", "Плитка", MeasurementType.AREA,
        MeasurementStyle(0xFF2E7D32, 2f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.FLOORING, material = "Плитка", wasteFactor = 1.1,
        ),
    ),
    TakeoffTemplate(
        "flooring-laminate", "Ламинат", MeasurementType.AREA,
        MeasurementStyle(0xFF558B2F, 2f), LengthUnit.METER, takeoff = TakeoffProperties(
            category = TradeCategory.FLOORING, material = "Ламинат", wasteFactor = 1.08,
        ),
    ),
)

@Serializable
data class Measurement(
    val id: MeasurementId,
    val type: MeasurementType,
    val points: List<DocPoint>,
    val style: MeasurementStyle = MeasurementStyle(),
    val label: String? = null,
    val layerId: LayerId = LayerId("default"),
    val takeoff: TakeoffProperties = TakeoffProperties(),
    val templateId: String? = null,
    val displayUnit: LengthUnit? = null,
    val showLabel: Boolean = true,
    val pageIndex: Int = 0,
    val createdAtEpochMs: Long,
    /** Revision that created or carried this geometry; absent for pre-revision measurements. */
    val revisionId: String? = null,
    /** A carried measurement is never silently accepted as correct. */
    val reviewStatus: MeasurementReviewStatus = MeasurementReviewStatus.VERIFIED,
    val sourceMeasurementId: MeasurementId? = null,
    val reviewedAtEpochMs: Long? = null,
)

@Serializable
data class PageMetadata(
    val index: Int,
    val width: Double,
    val height: Double,
    val coordinateUnit: CoordinateUnit,
    val rotationDegrees: Int = 0,
) {
    @Serializable enum class CoordinateUnit { PDF_POINT, IMAGE_PIXEL }
}

/** A persisted document page used by one logical project page. */
@Serializable
data class RevisionPageSource(
    val documentUri: String,
    val mimeType: String,
    val sourcePageIndex: Int,
    val metadata: PageMetadata,
)

@Serializable
data class RevisionControlPoint(
    val previous: DocPoint,
    val current: DocPoint,
)

/** Affine mapping from coordinates of the previous page to the current revision. */
@Serializable
data class RevisionTransform(
    val m00: Double,
    val m01: Double,
    val m10: Double,
    val m11: Double,
    val tx: Double,
    val ty: Double,
) {
    fun map(point: DocPoint): DocPoint = DocPoint(
        m00 * point.x + m01 * point.y + tx,
        m10 * point.x + m11 * point.y + ty,
    )

    val determinant: Double get() = m00 * m11 - m01 * m10
}

@Serializable
data class RevisionAlignment(
    val controlPoints: List<RevisionControlPoint>,
    val transform: RevisionTransform,
) {
    init { require(controlPoints.size in 2..3) }

    companion object {
        /** Two pairs produce a similarity transform; three pairs produce a full affine transform. */
        fun calculate(points: List<RevisionControlPoint>): RevisionAlignment? {
            if (points.size !in 2..3) return null
            val transform = if (points.size == 2) similarity(points[0], points[1]) else affine(points)
            if (transform == null || !transform.determinant.isFinite() || kotlin.math.abs(transform.determinant) < 1e-12) {
                return null
            }
            return RevisionAlignment(points.toList(), transform)
        }

        private fun similarity(first: RevisionControlPoint, second: RevisionControlPoint): RevisionTransform? {
            val px = second.previous.x - first.previous.x
            val py = second.previous.y - first.previous.y
            val qx = second.current.x - first.current.x
            val qy = second.current.y - first.current.y
            val lengthSquared = px * px + py * py
            if (!lengthSquared.isFinite() || lengthSquared < 1e-12) return null
            val real = (px * qx + py * qy) / lengthSquared
            val imaginary = (px * qy - py * qx) / lengthSquared
            val m00 = real
            val m01 = -imaginary
            val m10 = imaginary
            val m11 = real
            return RevisionTransform(
                m00,
                m01,
                m10,
                m11,
                first.current.x - m00 * first.previous.x - m01 * first.previous.y,
                first.current.y - m10 * first.previous.x - m11 * first.previous.y,
            )
        }

        private fun affine(points: List<RevisionControlPoint>): RevisionTransform? {
            val p0 = points[0].previous
            val p1 = points[1].previous
            val p2 = points[2].previous
            val q0 = points[0].current
            val q1 = points[1].current
            val q2 = points[2].current
            val p10x = p1.x - p0.x
            val p10y = p1.y - p0.y
            val p20x = p2.x - p0.x
            val p20y = p2.y - p0.y
            val determinant = p10x * p20y - p20x * p10y
            if (!determinant.isFinite() || kotlin.math.abs(determinant) < 1e-12) return null
            val q10x = q1.x - q0.x
            val q10y = q1.y - q0.y
            val q20x = q2.x - q0.x
            val q20y = q2.y - q0.y
            val m00 = (q10x * p20y - q20x * p10y) / determinant
            val m01 = (-q10x * p20x + q20x * p10x) / determinant
            val m10 = (q10y * p20y - q20y * p10y) / determinant
            val m11 = (-q10y * p20x + q20y * p10x) / determinant
            return RevisionTransform(
                m00,
                m01,
                m10,
                m11,
                q0.x - m00 * p0.x - m01 * p0.y,
                q0.y - m10 * p0.x - m11 * p0.y,
            )
        }
    }
}

@Serializable
data class PageRevision(
    val id: String,
    val logicalPageIndex: Int,
    val revisionNumber: Int,
    val createdAtEpochMs: Long,
    val previousSource: RevisionPageSource,
    val currentSource: RevisionPageSource,
    val alignment: RevisionAlignment,
    /** Exact snapshot retained before measurements are copied to the new page. */
    val archivedMeasurements: List<Measurement>,
    val carriedMeasurementIds: List<MeasurementId>,
    val note: String? = null,
    val scaleNeedsVerification: Boolean = true,
)

@Serializable
data class Layer(val id: LayerId, val name: String, val visible: Boolean = true, val locked: Boolean = false)

/** Installer-facing task types. More specialised templates can be added without changing saved jobs. */
@Serializable
enum class InstallationTaskType {
    FLANGED_OFFSET,
    STRAIGHT_INSERT,
    FLAT_OFFSET,
    ROLLING_OFFSET,
    TURN_WITH_OFFSET,
    OBSTACLE_BYPASS,
    U_TURN,
    TEE_BRANCH,
    REDUCER,
    MANUAL,
}

@Serializable
enum class InstallationJobStatus { DRAFT, CHECKED, FABRICATED }

@Serializable
enum class InstallationWorkspaceSection { MODEL, PARAMETERS, DRAWING, CUT_LIST }

@Serializable
enum class InstallationInputMode { BASIC, ADVANCED }

@Serializable
enum class InstallationPipeMaterial { CARBON_STEEL, STAINLESS_STEEL, COPPER, PPR, OTHER }

/** The physical feature touched by a tape or laser at either end of the site measurement. */
@Serializable
enum class InstallationMeasurementReference {
    PIPE_AXIS,
    PIPE_OUTER_EDGE,
    PIPE_INNER_EDGE,
    PIPE_FACE,
    FITTING_CENTER,
    FITTING_FACE,
    FLANGE_FACE,
}

@Serializable
enum class InstallationLateralDirection { LEFT, RIGHT }

@Serializable
enum class InstallationVerticalDirection { UP, DOWN }

@Serializable
enum class InstallationEndDirection { FORWARD, LEFT, RIGHT, UP, DOWN, BACK }

/**
 * Stable input owned by the project model rather than a Compose screen.
 * Values use millimetres/degrees so a job can be recalculated after an app restart.
 */
@Serializable
data class InstallationJobInput(
    val nominalDiameter: Int = 50,
    val pressureClass: Int = 16,
    val material: InstallationPipeMaterial = InstallationPipeMaterial.CARBON_STEEL,
    /** Original takeoff material name when it does not map to a catalogue material. */
    val materialName: String? = null,
    val inputMode: InstallationInputMode = InstallationInputMode.BASIC,
    val startReference: InstallationMeasurementReference = InstallationMeasurementReference.FLANGE_FACE,
    val endReference: InstallationMeasurementReference = InstallationMeasurementReference.FLANGE_FACE,
    val alongMm: Double = 1_600.0,
    val lateralOffsetMm: Double = 500.0,
    val verticalOffsetMm: Double = 0.0,
    val lateralDirection: InstallationLateralDirection = InstallationLateralDirection.RIGHT,
    val verticalDirection: InstallationVerticalDirection = InstallationVerticalDirection.UP,
    val endDirection: InstallationEndDirection = InstallationEndDirection.FORWARD,
    val angleDeg: Double = 45.0,
    val targetOffsetMm: Double = 500.0,
    val overallFaceToFaceMm: Double = 1_600.0,
    val minimumStraightMm: Double = 50.0,
    val weldGapMm: Double = 2.0,
    val quantity: Int = 1,
    val sawKerfMm: Double = 3.0,
    val stockLengthMm: Int = 6_000,
)

/** Exact 2D provenance retained so the workshop never has to reverse-engineer the plan. */
@Serializable
data class InstallationJobSource2D(
    val measurementId: MeasurementId,
    val pageIndex: Int,
    val points: List<DocPoint>,
    val millimetersPerDocumentUnit: Double,
    val material: String? = null,
    val diameter: String? = null,
)

/** Opaque, versioned engine recipe. Meshes and derived geometry are deliberately not persisted. */
@Serializable
data class InstallationChainRecipe(
    val format: String = "planruler-chain-v1",
    val encodedPlan: String,
    val taskType: InstallationTaskType? = null,
)

/** A bounded audit trail of previously autosaved calculation states. */
@Serializable
data class InstallationJobRevision(
    val savedAtEpochMs: Long,
    val taskType: InstallationTaskType,
    val input: InstallationJobInput,
    val chainRecipe: InstallationChainRecipe? = null,
)

@Serializable
data class InstallationJob(
    val id: InstallationJobId,
    val name: String,
    val location: String? = null,
    val taskType: InstallationTaskType = InstallationTaskType.FLANGED_OFFSET,
    val sourceMeasurementIds: List<MeasurementId> = emptyList(),
    val source2D: InstallationJobSource2D? = null,
    val input: InstallationJobInput = InstallationJobInput(),
    val chainRecipe: InstallationChainRecipe? = null,
    val activeSection: InstallationWorkspaceSection = InstallationWorkspaceSection.MODEL,
    val status: InstallationJobStatus = InstallationJobStatus.DRAFT,
    /** Installer/supervisor who accepted the current calculation for fabrication. */
    val checkedBy: String? = null,
    /** Acceptance time for the current calculation. Cleared when its geometry changes. */
    val checkedAtEpochMs: Long? = null,
    val notes: String? = null,
    val engineVersion: String? = null,
    val catalogVersion: String? = null,
    val resultChecksum: String? = null,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val lastOpenedAtEpochMs: Long = modifiedAtEpochMs,
    /** Non-null means that the job is in the recoverable project-local recycle bin. */
    val deletedAtEpochMs: Long? = null,
    val history: List<InstallationJobRevision> = emptyList(),
)

@Serializable
data class PlanProject(
    val schemaVersion: Int = CURRENT_SCHEMA,
    val id: ProjectId,
    val name: String,
    val createdAtEpochMs: Long,
    val modifiedAtEpochMs: Long,
    val documentUri: String,
    val mimeType: String,
    val pages: List<PageMetadata>,
    val selectedPage: Int = 0,
    val calibration: Calibration? = null,
    val displayUnit: LengthUnit = LengthUnit.METER,
    val measurements: List<Measurement> = emptyList(),
    val layers: List<Layer> = listOf(Layer(LayerId("default"), "Measurements")),
    val categories: List<TradeCategory> = TradeCategory.entries,
    val viewport: ViewportState = ViewportState(),
    val takeoffTemplates: List<TakeoffTemplate> = starterTakeoffTemplates(),
    val activeTakeoffTemplateId: String? = null,
    val pageRevisions: List<PageRevision> = emptyList(),
    val installationJobs: List<InstallationJob> = emptyList(),
    val activeInstallationJobId: InstallationJobId? = null,
) {
    companion object { const val CURRENT_SCHEMA = 9 }
}

fun PlanProject.latestRevision(pageIndex: Int): PageRevision? =
    pageRevisions.lastOrNull { it.logicalPageIndex == pageIndex }

fun PlanProject.currentPageSource(pageIndex: Int): RevisionPageSource? {
    if (pageIndex !in pages.indices) return null
    return latestRevision(pageIndex)?.currentSource ?: RevisionPageSource(
        documentUri = documentUri,
        mimeType = mimeType,
        sourcePageIndex = pageIndex,
        metadata = pages[pageIndex],
    )
}

fun carryMeasurementsToRevision(
    measurements: List<Measurement>,
    pageIndex: Int,
    revisionId: String,
    transform: RevisionTransform,
    createdAtEpochMs: Long,
    idGenerator: () -> MeasurementId,
): List<Measurement> = measurements.map { measurement ->
    if (measurement.pageIndex != pageIndex) {
        measurement
    } else {
        measurement.copy(
            id = idGenerator(),
            points = measurement.points.map(transform::map),
            createdAtEpochMs = createdAtEpochMs,
            revisionId = revisionId,
            reviewStatus = MeasurementReviewStatus.NEEDS_REVIEW,
            sourceMeasurementId = measurement.id,
            reviewedAtEpochMs = null,
        )
    }
}

data class TakeoffTotals(
    val itemCount: Int = 0,
    val baseLengthMeters: Double = 0.0,
    val adjustedLengthMeters: Double = 0.0,
    val baseAreaSquareMeters: Double = 0.0,
    val adjustedAreaSquareMeters: Double = 0.0,
    val baseCount: Double = 0.0,
    val adjustedCount: Double = 0.0,
) {
    operator fun plus(other: TakeoffTotals) = TakeoffTotals(
        itemCount + other.itemCount,
        baseLengthMeters + other.baseLengthMeters,
        adjustedLengthMeters + other.adjustedLengthMeters,
        baseAreaSquareMeters + other.baseAreaSquareMeters,
        adjustedAreaSquareMeters + other.adjustedAreaSquareMeters,
        baseCount + other.baseCount,
        adjustedCount + other.adjustedCount,
    )
}

/** One calculation shared by the on-screen schedule and every export adapter. */
fun calculateTakeoffTotals(measurements: Iterable<Measurement>, calibration: Calibration?): TakeoffTotals {
    val factor = calibration?.metersPerDocumentUnit ?: 0.0
    return measurements.fold(TakeoffTotals()) { total, measurement ->
        val multiplier = measurement.takeoff.quantity.coerceAtLeast(0.0)
        val adjustedMultiplier = multiplier * measurement.takeoff.wasteFactor.coerceAtLeast(0.0)
        val segmentLength = measurement.points.zipWithNext()
            .sumOf { (start, end) -> start.distanceTo(end) } * factor
        val area = if (measurement.type == MeasurementType.AREA && measurement.points.size >= 3) {
            kotlin.math.abs(
                measurement.points.indices.sumOf { index ->
                    val start = measurement.points[index]
                    val end = measurement.points[(index + 1) % measurement.points.size]
                    start.x * end.y - end.x * start.y
                },
            ) / 2.0 * factor * factor
        } else {
            0.0
        }
        total + when (measurement.type) {
            MeasurementType.DISTANCE, MeasurementType.POLYLINE -> TakeoffTotals(
                itemCount = 1,
                baseLengthMeters = segmentLength * multiplier,
                adjustedLengthMeters = segmentLength * adjustedMultiplier,
            )
            MeasurementType.AREA -> TakeoffTotals(
                itemCount = 1,
                baseAreaSquareMeters = area * multiplier,
                adjustedAreaSquareMeters = area * adjustedMultiplier,
            )
            MeasurementType.COUNTER -> TakeoffTotals(
                itemCount = 1,
                baseCount = multiplier,
                adjustedCount = adjustedMultiplier,
            )
            MeasurementType.ANGLE, MeasurementType.ANNOTATION -> TakeoffTotals(itemCount = 1)
        }
    }
}

sealed interface MeasureValue {
    data class Length(val meters: Double, val segmentsMeters: List<Double> = emptyList()) : MeasureValue
    data class Area(val squareMeters: Double, val perimeterMeters: Double) : MeasureValue
    data class Angle(val degrees: Double) : MeasureValue
    data class Count(val count: Int) : MeasureValue
    data object None : MeasureValue
}

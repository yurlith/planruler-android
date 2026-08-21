package com.planruler.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.planruler.document.api.*
import com.planruler.engine.api.*
import com.planruler.export.api.*
import com.planruler.model.*
import com.planruler.project.api.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class PageThumbnail(val width: Int, val height: Int, val argb: IntArray) {
    override fun equals(other: Any?) = this === other
    override fun hashCode() = System.identityHashCode(this)
}

enum class RevisionMeasurementFilter { ALL, REVISION, NEEDS_REVIEW }

fun Measurement.matches(filter: RevisionMeasurementFilter): Boolean = when (filter) {
    RevisionMeasurementFilter.ALL -> true
    RevisionMeasurementFilter.REVISION -> revisionId != null
    RevisionMeasurementFilter.NEEDS_REVIEW -> reviewStatus == MeasurementReviewStatus.NEEDS_REVIEW
}

data class PendingPageRevision(
    val documentUri: String,
    val mimeType: String,
    val document: OpenedDocument,
    val sourcePageIndex: Int,
    val renderedPage: RenderedPage,
    val rendering: Boolean = false,
)

data class WorkspaceUiState(
    val loading: Boolean = true,
    val project: PlanProject? = null,
    val document: OpenedDocument? = null,
    val renderedPage: RenderedPage? = null,
    val message: UiMessage? = null,
    val fatal: UiMessage? = null,
    val saveBadge: SaveBadge = SaveBadge.SAVED,
    val thumbnails: Map<Int, PageThumbnail> = emptyMap(),
    /** Sharp re-renders of the visible rectangle; empty until the zoom needs them. */
    val tiles: List<RenderedTile> = emptyList(),
    val revisionOverlay: RenderedPage? = null,
    val revisionOverlayOpacity: Float = 0.45f,
    val revisionFilter: RevisionMeasurementFilter = RevisionMeasurementFilter.ALL,
    val pendingRevision: PendingPageRevision? = null,
)

class WorkspaceViewModel(
    private val engine: MeasurementEngineApi,
    private val snapEngine: SnapEngine,
    private val documents: DocumentGateway,
    private val projects: ProjectRepository,
    private val exports: ExportGateway,
    private val projectId: ProjectId?,
    private val importUri: String?,
    private val mimeType: String?,
    initialSettings: AppSettings = AppSettings(),
) : ViewModel() {
    private val mutableUi = MutableStateFlow(WorkspaceUiState())
    val ui = mutableUi.asStateFlow()
    val engineState = engine.state
    private var autosave: Job? = null
    private var tileJob: Job? = null
    private val saveMutex = Mutex()
    private val openDocuments = mutableMapOf<String, OpenedDocument>()
    private var settings = initialSettings

    init {
        viewModelScope.launch {
            if (projectId != null) loadProject(projectId)
            else if (importUri != null) createProject(importUri, mimeType.orEmpty())
            else mutableUi.value = WorkspaceUiState(false, message = UiMessage.ProjectMissing, fatal = UiMessage.ProjectMissing)
        }
        viewModelScope.launch {
            engine.state.drop(1).collect { scheduleSave() }
        }
    }

    /** Settings are owned by the composition root; the workspace only reacts to them. */
    fun applySettings(settings: AppSettings) {
        this.settings = settings
        engine.setDisplayFormat(settings.decimals, settings.showUnits)
    }

    private suspend fun createProject(uri: String, mime: String) {
        when (val opened = documents.open(uri)) {
            is DocumentResult.Error -> mutableUi.value = WorkspaceUiState(
                false,
                message = UiMessage.Document(opened.error),
                fatal = UiMessage.Document(opened.error),
            )
            is DocumentResult.Ok -> {
                openDocuments[uri] = opened.value
                val now = System.currentTimeMillis()
                val blankCalibration = Calibration.pdfRatio(1.0).takeIf { BlankDocument.isBlankUri(uri) }
                val project = PlanProject(
                    id = ProjectId(UUID.randomUUID().toString()),
                    name = if (BlankDocument.isBlankUri(uri)) {
                        blankDrawingTitle(settings.language)
                    } else {
                        opened.value.title.substringBeforeLast(".")
                    },
                    createdAtEpochMs = now,
                    modifiedAtEpochMs = now,
                    documentUri = uri,
                    mimeType = mime.ifBlank { opened.value.mimeType },
                    pages = opened.value.pages,
                    displayUnit = settings.defaultUnit,
                    calibration = blankCalibration,
                )
                when (projects.save(project)) {
                    is ProjectResult.Ok -> {
                        // restore rather than setDisplayUnit: a fresh project must not start with an undo step.
                        engine.restore(blankCalibration, settings.defaultUnit, emptyList())
                        mutableUi.value = WorkspaceUiState(project = project, document = opened.value)
                        render(0)
                    }
                    is ProjectResult.Error -> {
                        documents.close(opened.value.id)
                        mutableUi.value = WorkspaceUiState(
                            false,
                            message = UiMessage.ProjectNotCreated,
                            fatal = UiMessage.ProjectNotCreated,
                        )
                    }
                }
            }
        }
    }

    private fun blankDrawingTitle(language: AppLanguage): String = when (language) {
        AppLanguage.POLISH -> "Nowy rysunek"
        AppLanguage.ENGLISH -> "Untitled drawing"
        AppLanguage.GERMAN -> "Neue Zeichnung"
        AppLanguage.FRENCH -> "Nouveau dessin"
        AppLanguage.ITALIAN -> "Nuovo disegno"
        AppLanguage.RUSSIAN -> "Новый чертёж"
    }

    private suspend fun loadProject(id: ProjectId) {
        when (val loaded = projects.load(id)) {
            is ProjectResult.Error -> mutableUi.value = WorkspaceUiState(
                false,
                message = UiMessage.ProjectUnreadable,
                fatal = UiMessage.ProjectUnreadable,
            )
            is ProjectResult.Ok -> {
                engine.restore(loaded.value.calibration, loaded.value.displayUnit, loaded.value.measurements)
                mutableUi.value = WorkspaceUiState(project = loaded.value)
                render(loaded.value.selectedPage)
            }
        }
    }

    val layers: List<Layer> get() = ui.value.project?.layers.orEmpty()
    val templates: List<TakeoffTemplate> get() = ui.value.project?.takeoffTemplates.orEmpty()
    val activeTemplate: TakeoffTemplate?
        get() = ui.value.project?.let { project ->
            project.takeoffTemplates.firstOrNull { it.id == project.activeTakeoffTemplateId }
        }
    val hiddenLayers: Set<LayerId>
        get() = layers.filterNot { it.visible }.mapTo(mutableSetOf()) { it.id }
    val lockedLayers: Set<LayerId>
        get() = layers.filter { it.locked }.mapTo(mutableSetOf()) { it.id }

    fun addLayer(name: String) {
        val project = ui.value.project ?: return
        val trimmed = name.trim().take(60).ifEmpty { return }
        val layer = Layer(LayerId(UUID.randomUUID().toString()), trimmed)
        mutableUi.value = ui.value.copy(project = project.copy(layers = project.layers + layer))
        persistCriticalChange()
    }

    fun updateLayer(id: LayerId, name: String? = null, visible: Boolean? = null, locked: Boolean? = null) {
        val project = ui.value.project ?: return
        val updated = project.layers.map { layer ->
            if (layer.id != id) {
                layer
            } else {
                layer.copy(
                    name = name?.trim()?.take(60)?.takeIf(String::isNotEmpty) ?: layer.name,
                    visible = visible ?: layer.visible,
                    locked = locked ?: layer.locked,
                )
            }
        }
        mutableUi.value = ui.value.copy(project = project.copy(layers = updated))
        persistCriticalChange()
    }

    /** Refuses to delete a layer that still holds measurements: silent data loss is worse. */
    fun deleteLayer(id: LayerId): Boolean {
        val project = ui.value.project ?: return false
        if (project.layers.size <= 1) return false
        if (engine.state.value.measurements.any { it.layerId == id }) return false
        if (project.takeoffTemplates.any { it.layerId == id }) return false
        mutableUi.value = ui.value.copy(
            project = project.copy(layers = project.layers.filterNot { it.id == id }),
        )
        persistCriticalChange()
        return true
    }

    fun selectTemplate(id: String?) {
        val project = ui.value.project ?: return
        val valid = id?.takeIf { candidate -> project.takeoffTemplates.any { it.id == candidate } }
        mutableUi.value = ui.value.copy(project = project.copy(activeTakeoffTemplateId = valid))
        persistCriticalChange()
    }

    fun saveTemplate(template: TakeoffTemplate, updateExisting: Boolean) {
        val project = ui.value.project ?: return
        val normalized = template.copy(
            name = template.name.trim().take(120).ifEmpty { return },
            style = template.style.copy(strokeWidth = template.style.strokeWidth.coerceIn(0.5f, 24f)),
            takeoff = template.takeoff.copy(
                quantity = template.takeoff.quantity.coerceAtLeast(0.0),
                wasteFactor = template.takeoff.wasteFactor.coerceAtLeast(0.0),
            ),
        )
        val exists = project.takeoffTemplates.any { it.id == normalized.id }
        val templates = if (exists) {
            project.takeoffTemplates.map { if (it.id == normalized.id) normalized else it }
        } else {
            project.takeoffTemplates + normalized
        }
        mutableUi.value = ui.value.copy(
            project = project.copy(
                takeoffTemplates = templates,
                activeTakeoffTemplateId = normalized.id,
            ),
        )
        if (updateExisting) report(engine.applyTemplateToMeasurements(normalized))
        persistCriticalChange()
    }

    fun deleteTemplate(id: String): Boolean {
        val project = ui.value.project ?: return false
        if (engine.state.value.measurements.any { it.templateId == id }) return false
        mutableUi.value = ui.value.copy(
            project = project.copy(
                takeoffTemplates = project.takeoffTemplates.filterNot { it.id == id },
                activeTakeoffTemplateId = project.activeTakeoffTemplateId.takeUnless { it == id },
            ),
        )
        persistCriticalChange()
        return true
    }

    fun changePage(delta: Int) {
        val project = ui.value.project ?: return
        goToPage(project.selectedPage + delta)
    }

    fun goToPage(index: Int) {
        val project = ui.value.project ?: return
        val target = index.coerceIn(0, project.pages.lastIndex)
        if (target == project.selectedPage && ui.value.renderedPage != null) return
        mutableUi.value = ui.value.copy(project = project.copy(selectedPage = target))
        scheduleSave()
        viewModelScope.launch { render(target) }
    }

    fun updateViewport(viewport: ViewportState) {
        val project = ui.value.project ?: return
        mutableUi.value = ui.value.copy(project = project.copy(viewport = viewport))
        scheduleSave()
    }

    /**
     * Deep zoom is served by re-rendering the visible rectangle rather than by holding a
     * larger page bitmap: the page render is capped, tiles are not.
     */
    fun requestTiles(viewport: ViewportState, canvasWidth: Int, canvasHeight: Int) {
        tileJob?.cancel()
        val gateway = documents as? TileDocumentGateway ?: return
        val document = ui.value.document ?: return
        val page = ui.value.renderedPage ?: return
        if (canvasWidth <= 0 || canvasHeight <= 0 || page.source.width <= 0.0) return
        val baseScale = page.pixelWidth / page.source.width
        if (viewport.zoom <= baseScale * TILE_TRIGGER) {
            if (ui.value.tiles.isNotEmpty()) mutableUi.value = ui.value.copy(tiles = emptyList())
            return
        }
        tileJob = viewModelScope.launch {
            delay(TILE_DEBOUNCE_MS)
            val transform = ViewportTransform(canvasWidth.toDouble(), canvasHeight.toDouble(), viewport)
            val topLeft = transform.screenToDocument(ScreenPoint(0.0, 0.0))
            val bottomRight = transform.screenToDocument(
                ScreenPoint(canvasWidth.toDouble(), canvasHeight.toDouble()),
            )
            // A stable power-of-two render scale makes adjacent pinch frames reuse the
            // same tile boundaries and LRU-cache keys. It is never below display zoom.
            val renderScale = quantizedRenderScale(viewport.zoom)
            val step = TILE_PIXELS / renderScale
            if (step <= 0.0 || !step.isFinite()) return@launch
            val firstColumn = floor(max(0.0, topLeft.x) / step).toInt()
            val lastColumn = floor(min(page.source.width, bottomRight.x) / step).toInt()
            val firstRow = floor(max(0.0, topLeft.y) / step).toInt()
            val lastRow = floor(min(page.source.height, bottomRight.y) / step).toInt()
            if (lastColumn < firstColumn || lastRow < firstRow) return@launch

            val rendered = mutableListOf<RenderedTile>()
            outer@ for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    if (rendered.size >= TILE_LIMIT) break@outer
                    val result = gateway.renderTile(
                        document.id,
                        TileRequest(
                            pageIndex = page.pageIndex,
                            left = column * step,
                            top = row * step,
                            right = min((column + 1) * step, page.source.width),
                            bottom = min((row + 1) * step, page.source.height),
                            scale = renderScale,
                        ),
                    )
                    if (result is DocumentResult.Ok) {
                        rendered += result.value
                        // Publish as they arrive: a partially sharpened page beats a blank wait.
                        mutableUi.value = ui.value.copy(tiles = rendered.toList())
                    }
                }
            }
            mutableUi.value = ui.value.copy(tiles = rendered.toList())
        }
    }

    private suspend fun documentFor(source: RevisionPageSource): DocumentResult<OpenedDocument> {
        openDocuments[source.documentUri]?.let { return DocumentResult.Ok(it) }
        return when (val opened = documents.open(source.documentUri)) {
            is DocumentResult.Ok -> {
                openDocuments[source.documentUri] = opened.value
                opened
            }
            is DocumentResult.Error -> opened
        }
    }

    private suspend fun render(index: Int) {
        val project = ui.value.project ?: return
        val source = project.currentPageSource(index) ?: return
        tileJob?.cancel()
        mutableUi.value = ui.value.copy(
            loading = true,
            renderedPage = null,
            revisionOverlay = null,
            tiles = emptyList(),
        )
        when (val opened = documentFor(source)) {
            is DocumentResult.Error -> mutableUi.value = ui.value.copy(
                loading = false,
                message = UiMessage.Document(opened.error),
                fatal = UiMessage.Document(opened.error),
            )
            is DocumentResult.Ok -> when (
                val result = documents.renderPage(opened.value.id, source.sourcePageIndex, RenderRequest())
            ) {
                is DocumentResult.Error -> mutableUi.value =
                    ui.value.copy(loading = false, message = UiMessage.Document(result.error))
                is DocumentResult.Ok -> {
                    val revision = project.latestRevision(index)
                    val overlay = revision?.previousSource?.let { previous ->
                        when (val previousDocument = documentFor(previous)) {
                            is DocumentResult.Error -> null
                            is DocumentResult.Ok -> when (
                                val previousPage = documents.renderPage(
                                    previousDocument.value.id,
                                    previous.sourcePageIndex,
                                    RenderRequest(),
                                )
                            ) {
                                is DocumentResult.Ok -> previousPage.value
                                is DocumentResult.Error -> null
                            }
                        }
                    }
                    mutableUi.value = ui.value.copy(
                        loading = false,
                        document = opened.value,
                        renderedPage = result.value,
                        revisionOverlay = overlay,
                        fatal = null,
                    )
                }
            }
        }
    }

    /** Page strip previews: small renders reuse the same gateway, no extra decoding path. */
    fun loadThumbnails() {
        val state = ui.value
        val project = state.project ?: return
        if (state.thumbnails.isNotEmpty()) return
        viewModelScope.launch {
            val result = mutableMapOf<Int, PageThumbnail>()
            project.pages.take(THUMBNAIL_LIMIT).forEach { page ->
                val source = project.currentPageSource(page.index) ?: return@forEach
                val opened = documentFor(source) as? DocumentResult.Ok ?: return@forEach
                when (
                    val rendered = documents.renderPage(
                        opened.value.id,
                        source.sourcePageIndex,
                        RenderRequest(maxEdgePixels = 180),
                    )
                ) {
                    is DocumentResult.Ok -> {
                        result[page.index] = PageThumbnail(
                            rendered.value.pixelWidth,
                            rendered.value.pixelHeight,
                            rendered.value.argb,
                        )
                        mutableUi.value = ui.value.copy(thumbnails = result.toMap())
                    }
                    is DocumentResult.Error -> Unit
                }
            }
        }
    }

    fun preparePageRevision(uri: String, mimeType: String) {
        val project = ui.value.project ?: return
        if (ui.value.renderedPage == null || ui.value.pendingRevision != null) return
        viewModelScope.launch {
            val sourceDocument = when (val opened = documents.open(uri)) {
                is DocumentResult.Error -> {
                    mutableUi.value = ui.value.copy(message = UiMessage.Document(opened.error))
                    return@launch
                }
                is DocumentResult.Ok -> opened.value.also { openDocuments[uri] = it }
            }
            when (val rendered = documents.renderPage(sourceDocument.id, 0, RenderRequest())) {
                is DocumentResult.Error -> mutableUi.value = ui.value.copy(message = UiMessage.Document(rendered.error))
                is DocumentResult.Ok -> mutableUi.value = ui.value.copy(
                    pendingRevision = PendingPageRevision(
                        documentUri = uri,
                        mimeType = mimeType.ifBlank { sourceDocument.mimeType },
                        document = sourceDocument,
                        sourcePageIndex = 0,
                        renderedPage = rendered.value,
                    ),
                )
            }
        }
    }

    fun selectPendingRevisionPage(index: Int) {
        val pending = ui.value.pendingRevision ?: return
        val target = index.coerceIn(0, pending.document.pages.lastIndex)
        if (target == pending.sourcePageIndex || pending.rendering) return
        mutableUi.value = ui.value.copy(pendingRevision = pending.copy(rendering = true))
        viewModelScope.launch {
            when (val rendered = documents.renderPage(pending.document.id, target, RenderRequest())) {
                is DocumentResult.Error -> mutableUi.value = ui.value.copy(
                    pendingRevision = pending.copy(rendering = false),
                    message = UiMessage.Document(rendered.error),
                )
                is DocumentResult.Ok -> mutableUi.value = ui.value.copy(
                    pendingRevision = pending.copy(
                        sourcePageIndex = target,
                        renderedPage = rendered.value,
                        rendering = false,
                    ),
                )
            }
        }
    }

    fun cancelPendingRevision() {
        mutableUi.value = ui.value.copy(pendingRevision = null)
    }

    /**
     * Archives the previous geometry, then carries transformed copies. The copies have new
     * identities and remain unverified until the user explicitly reviews them.
     */
    fun confirmPageRevision(controlPoints: List<RevisionControlPoint>, note: String?): Boolean {
        val alignment = RevisionAlignment.calculate(controlPoints) ?: return false
        val state = ui.value
        val pending = state.pendingRevision ?: return false
        val project = state.project ?: return false
        val logicalPage = project.selectedPage
        val previousSource = project.currentPageSource(logicalPage) ?: return false
        val now = System.currentTimeMillis()
        val revisionId = UUID.randomUUID().toString()
        val archived = engine.state.value.measurements.filter { it.pageIndex == logicalPage }
        val carried = carryMeasurementsToRevision(
            archived,
            logicalPage,
            revisionId,
            alignment.transform,
            now,
        ) { MeasurementId(UUID.randomUUID().toString()) }
        val currentMetadata = pending.document.pages[pending.sourcePageIndex]
        val currentSource = RevisionPageSource(
            documentUri = pending.documentUri,
            mimeType = pending.mimeType,
            sourcePageIndex = pending.sourcePageIndex,
            metadata = currentMetadata,
        )
        val revision = PageRevision(
            id = revisionId,
            logicalPageIndex = logicalPage,
            revisionNumber = project.pageRevisions.count { it.logicalPageIndex == logicalPage } + 1,
            createdAtEpochMs = now,
            previousSource = previousSource,
            currentSource = currentSource,
            alignment = alignment,
            archivedMeasurements = archived,
            carriedMeasurementIds = carried.map { it.id },
            note = note?.trim()?.take(240)?.takeIf(String::isNotEmpty),
        )
        val updatedMeasurements = engine.state.value.measurements.filterNot { it.pageIndex == logicalPage } + carried
        val coordinateScale = sqrt(kotlin.math.abs(alignment.transform.determinant))
        val sourceCalibration = engine.state.value.calibration
        val adjustedCalibration = sourceCalibration?.copy(
            metersPerDocumentUnit = sourceCalibration.metersPerDocumentUnit / coordinateScale,
            audit = sourceCalibration.audit?.copy(verification = null),
        )
        val updatedPages = project.pages.mapIndexed { index, metadata ->
            if (index == logicalPage) currentMetadata.copy(index = logicalPage) else metadata
        }
        val updatedProject = project.copy(
            pages = updatedPages,
            pageRevisions = project.pageRevisions + revision,
            measurements = updatedMeasurements,
            viewport = ViewportState(),
            calibration = adjustedCalibration,
        )
        engine.restore(adjustedCalibration, engine.state.value.displayUnit, updatedMeasurements)
        mutableUi.value = state.copy(
            project = updatedProject,
            document = pending.document,
            pendingRevision = null,
            thumbnails = emptyMap(),
            revisionFilter = RevisionMeasurementFilter.NEEDS_REVIEW,
            message = UiMessage.RevisionSaved,
        )
        persistCriticalChange()
        viewModelScope.launch { render(logicalPage) }
        return true
    }

    fun setRevisionOverlayOpacity(value: Float) {
        mutableUi.value = ui.value.copy(revisionOverlayOpacity = value.coerceIn(0f, 1f))
    }

    fun setRevisionFilter(filter: RevisionMeasurementFilter) {
        mutableUi.value = ui.value.copy(revisionFilter = filter)
    }

    fun markMeasurementReviewed(id: MeasurementId) {
        reportAndPersist(
            engine.setReviewStatus(
                id,
                MeasurementReviewStatus.VERIFIED,
                System.currentTimeMillis(),
            ),
        )
    }

    fun calibrate(
        start: DocPoint,
        end: DocPoint,
        value: Double,
        unit: LengthUnit,
        calibratedBy: String,
    ): Boolean {
        val audit = CalibrationAudit(
            calibratedAtEpochMs = System.currentTimeMillis(),
            calibratedBy = calibratedBy.trim().take(60).ifEmpty { "Local user" },
            pageIndex = ui.value.project?.selectedPage ?: 0,
            referenceDocumentLength = start.distanceTo(end),
            enteredLength = value,
            enteredUnit = unit,
        )
        val saved = report(engine.calibrateByReference(start, end, value, unit, audit))
        if (saved) {
            markCurrentRevisionScaleChecked()
            mutableUi.value = ui.value.copy(message = UiMessage.CalibrationSaved)
            persistCriticalChange()
        }
        return saved
    }

    fun calibrateRatio(ratio: Double, printSizeConfirmed: Boolean, calibratedBy: String): Boolean {
        val audit = CalibrationAudit(
            calibratedAtEpochMs = System.currentTimeMillis(),
            calibratedBy = calibratedBy.trim().take(60).ifEmpty { "Local user" },
            pageIndex = ui.value.project?.selectedPage ?: 0,
            printRatio = ratio,
            printSizeConfirmed = printSizeConfirmed,
        )
        val saved = report(engine.calibratePdfRatio(ratio, audit))
        if (saved) {
            markCurrentRevisionScaleChecked()
            mutableUi.value = ui.value.copy(message = UiMessage.CalibrationSaved)
            persistCriticalChange()
        }
        return saved
    }

    fun verifyCalibration(start: DocPoint, end: DocPoint, expected: Double, unit: LengthUnit): Boolean {
        val verified = report(
            engine.verifyCalibration(
                start,
                end,
                expected,
                unit,
                ui.value.project?.selectedPage ?: 0,
            ),
        )
        if (verified) {
            markCurrentRevisionScaleChecked()
            persistCriticalChange()
        }
        return verified
    }

    private fun markCurrentRevisionScaleChecked() {
        val project = ui.value.project ?: return
        val revision = project.latestRevision(project.selectedPage) ?: return
        mutableUi.value = ui.value.copy(
            project = project.copy(
                pageRevisions = project.pageRevisions.map {
                    if (it.id == revision.id) it.copy(scaleNeedsVerification = false) else it
                },
            ),
        )
    }

    fun begin(type: MeasurementType, point: DocPoint, label: String? = null) {
        val template = activeTemplate?.takeIf { it.measurementType == type }
        report(
            engine.beginMeasurement(
                type = type,
                first = point,
                label = label ?: template?.name,
                pageIndex = ui.value.project?.selectedPage ?: 0,
                style = template?.style ?: MeasurementStyle(strokeWidth = settings.defaultStrokeWidth),
                layerId = template?.layerId ?: LayerId("default"),
                takeoff = template?.takeoff ?: TakeoffProperties(),
                displayUnit = template?.displayUnit,
                templateId = template?.id,
                revisionId = ui.value.project?.latestRevision(ui.value.project?.selectedPage ?: 0)?.id,
            ),
        )
    }

    /** Per-segment lengths for polylines; empty unless the measurement has segments. */
    fun segmentValues(measurement: Measurement): List<String> =
        (rawValue(measurement) as? MeasureValue.Length)
            ?.segmentsMeters
            ?.takeIf { it.size > 1 }
            ?.map { engine.format(MeasureValue.Length(it), measurement.displayUnit) }
            .orEmpty()
    fun add(point: DocPoint) { report(engine.addPoint(point)) }
    fun updateLast(point: DocPoint) { report(engine.updateLastPoint(point)) }
    fun removeLastPoint() { report(engine.removeLastPoint()) }
    fun commit() { reportAndPersist(engine.commitMeasurement()) }
    fun cancel() = engine.cancelMeasurement()
    fun updateAnnotation(id: MeasurementId, text: String) { reportAndPersist(engine.updateAnnotation(id, text)) }
    fun beginEdit(id: MeasurementId) { report(engine.beginEdit(id)) }
    fun previewVertex(index: Int, point: DocPoint) { report(engine.previewVertex(index, point)) }
    fun previewMove(delta: DocPoint) { report(engine.previewMove(delta)) }
    fun commitEdit() { reportAndPersist(engine.commitEdit()) }
    fun cancelEdit() = engine.cancelEdit()
    fun duplicate(id: MeasurementId) { reportAndPersist(engine.duplicateMeasurement(id)) }
    fun repeatLast(): Measurement? {
        val last = engine.state.value.measurements.lastOrNull() ?: return null
        val zoom = ui.value.project?.viewport?.zoom?.coerceAtLeast(0.1) ?: 1.0
        val result = engine.duplicateMeasurement(last.id, DocPoint(24.0 / zoom, 24.0 / zoom))
        reportAndPersist(result)
        return (result as? EngineResult.Ok)?.value
    }
    fun setExactLength(
        id: MeasurementId,
        length: Double,
        unit: LengthUnit,
        constraint: DistanceConstraint,
    ): Boolean = reportAndPersist(engine.setExactLength(id, length, unit, constraint))
    fun delete(id: MeasurementId) { reportAndPersist(engine.deleteMeasurement(id)) }
    fun insertVertex(id: MeasurementId, afterIndex: Int, point: DocPoint) {
        reportAndPersist(engine.insertVertex(id, afterIndex, point))
    }
    fun removeVertex(id: MeasurementId, index: Int) { reportAndPersist(engine.removeVertex(id, index)) }
    fun updateProperties(id: MeasurementId, update: MeasurementPropertiesUpdate) {
        reportAndPersist(engine.updateProperties(id, update))
    }

    fun snap(
        point: DocPoint,
        anchor: DocPoint?,
        zoom: Double,
        density: Double,
        mode: SnapMode,
        sensitivityDp: Double,
        excludedId: MeasurementId? = null,
    ): SnapResult = snapEngine.resolve(
        point,
        SnapContext(
            measurements = engine.state.value.measurements.filter {
                it.pageIndex == (ui.value.project?.selectedPage ?: 0) &&
                    it.layerId !in hiddenLayers &&
                    it.matches(ui.value.revisionFilter)
            },
            anchor = anchor,
            sensitivityDocumentUnits = SnapSensitivity.documentUnits(
                sensitivityDp,
                density,
                zoom.coerceAtLeast(0.1),
            ),
            enabled = mode != SnapMode.OFF,
            excludedMeasurementId = excludedId,
            allowed = mode.allowedTypes(),
        ),
    )

    fun undo() { reportAndPersist(engine.undo()) }
    fun redo() { reportAndPersist(engine.redo()) }
    fun setUnit(unit: LengthUnit) { engine.setDisplayUnit(unit); persistCriticalChange() }
    fun value(measurement: Measurement): String =
        (engine.evaluate(measurement) as? EngineResult.Ok)
            ?.let { engine.format(it.value, measurement.displayUnit) }
            .orEmpty()
    fun rawValue(measurement: Measurement): MeasureValue? =
        (engine.evaluate(measurement) as? EngineResult.Ok)?.value
    fun clearMessage() { mutableUi.value = ui.value.copy(message = null) }

    fun save() { viewModelScope.launch { saveNow(showFeedback = true) } }

    /** Creates a workshop draft from one measured route and persists both in one project snapshot. */
    fun createInstallationJob(measurement: Measurement, onCreated: () -> Unit): Boolean {
        val calibration = engine.state.value.calibration
        val job = measurementToInstallationJob(
            measurement = measurement,
            calibration = calibration,
            now = System.currentTimeMillis(),
            id = InstallationJobId(UUID.randomUUID().toString()),
        ) ?: return false
        autosave?.cancel()
        viewModelScope.launch {
            saveMutex.withLock {
                val project = ui.value.project ?: return@withLock
                val snapshot = engine.state.value
                val updated = project.copy(
                    modifiedAtEpochMs = job.modifiedAtEpochMs,
                    calibration = snapshot.calibration,
                    displayUnit = snapshot.displayUnit,
                    measurements = snapshot.measurements,
                    installationJobs = project.installationJobs + job,
                    activeInstallationJobId = job.id,
                )
                mutableUi.value = ui.value.copy(project = updated, saveBadge = SaveBadge.SAVING)
                when (projects.save(updated)) {
                    is ProjectResult.Ok -> {
                        mutableUi.value = ui.value.copy(saveBadge = SaveBadge.SAVED)
                        onCreated()
                    }
                    is ProjectResult.Error -> mutableUi.value = ui.value.copy(
                        saveBadge = SaveBadge.FAILED,
                        message = UiMessage.SaveFailed,
                    )
                }
            }
        }
        return true
    }

    private fun scheduleSave() {
        autosave?.cancel()
        autosave = viewModelScope.launch { delay(settings.autosaveDelayMs); saveNow() }
    }

    /** Committed measurements and scale changes must survive an immediate process stop. */
    private fun persistCriticalChange() {
        autosave?.cancel()
        // With an unlocked mutex the local repository has no suspension point, so this
        // reaches the atomic file move before the UI action returns.
        autosave = viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { saveNow() }
    }

    private suspend fun saveNow(showFeedback: Boolean = false) {
        saveMutex.withLock {
            val project = ui.value.project ?: return@withLock
            val snapshot = engine.state.value
            val updated = project.copy(
                modifiedAtEpochMs = System.currentTimeMillis(),
                calibration = snapshot.calibration,
                displayUnit = snapshot.displayUnit,
                measurements = snapshot.measurements,
            )
            mutableUi.value = ui.value.copy(project = updated, saveBadge = SaveBadge.SAVING)
            when (projects.save(updated)) {
                is ProjectResult.Ok -> mutableUi.value = ui.value.copy(
                    saveBadge = SaveBadge.SAVED,
                    message = if (showFeedback) UiMessage.ProjectSaved else ui.value.message,
                )
                is ProjectResult.Error -> mutableUi.value = ui.value.copy(
                    saveBadge = SaveBadge.FAILED,
                    message = UiMessage.SaveFailed,
                )
            }
        }
    }

    fun export(
        uri: String,
        format: ExportFormat,
        selection: ExportPageSelection = ExportPageSelection.CURRENT,
        firstPage: Int = ui.value.project?.selectedPage ?: 0,
        lastPage: Int = firstPage,
    ) {
        viewModelScope.launch {
            saveNow()
            val project = ui.value.project ?: return@launch
            val page = ui.value.renderedPage
            val result = exports.export(
                ExportRequest(
                    project = project,
                    targetUri = uri,
                    format = format,
                    pageArgb = page?.argb,
                    pagePixelWidth = page?.pixelWidth ?: 0,
                    pagePixelHeight = page?.pixelHeight ?: 0,
                    pageSelection = selection,
                    firstPage = firstPage,
                    lastPage = lastPage,
                    includeLegend = settings.exportIncludeLegend,
                    includeScale = settings.exportIncludeScale,
                    csvDelimiter = settings.csvDelimiter,
                    labels = exportLabels(Wt(settings.language)),
                ),
            )
            mutableUi.value = ui.value.copy(
                message = if (result is ExportResult.Success) UiMessage.ExportDone else UiMessage.ExportFailed,
            )
        }
    }

    private fun report(result: EngineResult<*>): Boolean {
        if (result is EngineResult.Error) {
            mutableUi.value = ui.value.copy(message = UiMessage.Measurement(result.error))
            return false
        }
        return true
    }

    private fun reportAndPersist(result: EngineResult<*>): Boolean {
        val success = report(result)
        if (success) persistCriticalChange()
        return success
    }

    override fun onCleared() {
        autosave?.cancel()
        kotlinx.coroutines.runBlocking { saveNow() }
        openDocuments.values.map { it.id }.distinct().forEach { id ->
            kotlinx.coroutines.runBlocking { documents.close(id) }
        }
        openDocuments.clear()
    }

    private companion object {
        const val THUMBNAIL_LIMIT = 24
        const val TILE_PIXELS = 512.0
        const val TILE_LIMIT = 12
        const val TILE_DEBOUNCE_MS = 180L
        /** Below this the page render is already sharper than the screen asks for. */
        const val TILE_TRIGGER = 1.2
    }
}

package com.planruler.feature.workspace

import com.planruler.designsystem.component.IndicatorStatus
import com.planruler.designsystem.localization.localizedUi
import com.planruler.document.api.DocumentError
import com.planruler.document.api.CaptureReadiness
import com.planruler.document.api.CaptureWarning
import com.planruler.document.api.DepthDecodeStatus
import com.planruler.engine.api.MeasurementError
import com.planruler.engine.api.SnapType
import com.planruler.export.api.ExportLabels
import com.planruler.model.Calibration
import com.planruler.model.AppLanguage
import com.planruler.model.MeasurementType
import com.planruler.model.SnapMode
import com.planruler.model.TradeCategory

/**
 * Navigation and selection are modes of the same picker as the measuring tools, so the
 * UI cannot end up in a "select + draw" combination the way two booleans allowed.
 */
enum class WorkspaceTool {
    NAVIGATE, SELECT, DISTANCE, POLYLINE, AREA, ANGLE, COUNTER, ANNOTATION, CALIBRATE;

    val measurement: MeasurementType?
        get() = when (this) {
            DISTANCE -> MeasurementType.DISTANCE
            POLYLINE -> MeasurementType.POLYLINE
            AREA -> MeasurementType.AREA
            ANGLE -> MeasurementType.ANGLE
            COUNTER -> MeasurementType.COUNTER
            ANNOTATION -> MeasurementType.ANNOTATION
            else -> null
        }

    val draws: Boolean get() = measurement != null
}

enum class WorkspaceMode { EDIT, VIEW }

enum class SaveBadge { SAVED, SAVING, FAILED }

/** Answers "can I trust this scale?" without the user opening the calibration screen. */
enum class ScaleConfidence { CONFIRMED, LIKELY, NEEDS_CHECK, MISSING;

    val status: IndicatorStatus
        get() = when (this) {
            CONFIRMED -> IndicatorStatus.OK
            LIKELY -> IndicatorStatus.WARNING
            NEEDS_CHECK -> IndicatorStatus.WARNING
            MISSING -> IndicatorStatus.ERROR
        }
}

fun scaleConfidence(calibration: Calibration?, vectorPage: Boolean): ScaleConfidence {
    if (calibration == null) return ScaleConfidence.MISSING
    val audit = calibration.audit
    val verification = audit?.verification
    return when {
        verification != null ->
            if (verification.relativeError <= 0.01) ScaleConfidence.CONFIRMED else ScaleConfidence.NEEDS_CHECK
        calibration.method == Calibration.Method.REFERENCE -> ScaleConfidence.CONFIRMED
        audit?.printSizeConfirmed == false -> ScaleConfidence.NEEDS_CHECK
        vectorPage -> ScaleConfidence.LIKELY
        else -> ScaleConfidence.NEEDS_CHECK
    }
}

fun SnapMode.allowedTypes(): Set<SnapType> = when (this) {
    SnapMode.AUTO -> setOf(SnapType.VERTEX, SnapType.HORIZONTAL, SnapType.VERTICAL, SnapType.SEGMENT)
    SnapMode.VERTEX -> setOf(SnapType.VERTEX)
    SnapMode.AXIS -> setOf(SnapType.HORIZONTAL, SnapType.VERTICAL)
    SnapMode.EDGE -> setOf(SnapType.SEGMENT)
    SnapMode.OFF -> emptySet()
}

enum class WorkspaceOverlay {
    CALIBRATION, PROPERTIES, PAGES, SCHEDULE, EXPORT, MORE_TOOLS, TOOL_SETTINGS, LAYERS, TEMPLATES,
    PHOTO_METADATA,
}

/** The export adapter owns no copy, so the localised words travel with the request. */
fun exportLabels(text: Wt) = ExportLabels(
    project = text.projectWord,
    exportedAt = text.exportedAt,
    scale = text.scaleWord,
    notCalibrated = text.notCalibrated,
    units = text.units,
    legend = text.includeLegend,
    items = text.pieces,
    calibratedAt = text.calibratedAt,
    calibratedBy = text.calibratedByExport,
    calibrationMethod = text.calibrationMethod,
    verification = text.verification,
    localUser = text.localUser,
    referenceMethod = text.calibrationByLength,
    printRatioMethod = text.calibrationByRatio,
    page = text.page.lowercase(),
    expected = text.expected.lowercase(),
    measured = text.measured.lowercase(),
    template = text.template,
    material = text.material,
    layer = text.layer,
    baseQuantity = text.baseQuantity,
    withWaste = text.withWaste,
    revisionLog = text.revisionLog,
    revision = text.revisionWord,
    needsReview = text.needsReview,
    reviewed = text.reviewed,
    previousPlan = text.previousPlan,
    newPlan = text.newPlan,
    controlPoints = text.controlPoints,
    note = text.note,
    categories = TradeCategory.entries.associateWith { categoryLabel(it, text) },
)

/** Typed so the view layer can localise; the view model never builds user facing text. */
sealed interface UiMessage {
    data object ProjectSaved : UiMessage
    data object SaveFailed : UiMessage
    data object ProjectMissing : UiMessage
    data object ProjectUnreadable : UiMessage
    data object ProjectNotCreated : UiMessage
    data object ExportDone : UiMessage
    data object ExportFailed : UiMessage
    data object CalibrationSaved : UiMessage
    data object RevisionSaved : UiMessage
    data class Measurement(val error: MeasurementError) : UiMessage
    data class Document(val error: DocumentError) : UiMessage
}

/** Localised copy. Two static maps beat resource plumbing across five modules here. */
class Wt(private val language: AppLanguage) {
    private fun t(russian: String, english: String) = localizedUi(language, russian, english)

    val workspace get() = t("Рабочая область", "Workspace")
    val projects get() = t("Проекты", "Projects")
    val page get() = t("Страница", "Page")
    val pages get() = t("Страницы", "Pages")
    val back get() = t("Назад", "Back")
    val undo get() = t("Отменить", "Undo")
    val redo get() = t("Повторить", "Redo")
    val save get() = t("Сохранить", "Save")
    val saved get() = t("Сохранено", "Saved")
    val saving get() = t("Сохранение…", "Saving…")
    val saveFailed get() = t("Не удалось сохранить", "Could not save")
    val retry get() = t("Повторить", "Retry")
    val export get() = t("Экспорт", "Export")
    val schedule get() = t("Ведомость", "Schedule")
    val properties get() = t("Свойства", "Properties")
    val calibrate get() = t("Калибровка", "Calibration")
    val cancel get() = t("Отмена", "Cancel")
    val apply get() = t("Применить", "Apply")
    val close get() = t("Закрыть", "Close")
    val ok get() = t("Понятно", "Got it")
    val neverShow get() = t("Больше не показывать", "Don't show again")
    val delete get() = t("Удалить", "Delete")
    val duplicate get() = t("Дублировать", "Duplicate")
    val finish get() = t("Подтвердить", "Confirm")
    val backPoint get() = t("Назад", "Back")
    val more get() = t("Ещё", "More")
    val viewMode get() = t("Просмотр", "View")
    val editMode get() = t("Редактирование", "Editing")
    val focusMode get() = t("На весь экран", "Focus mode")
    val projectMenu get() = t("Меню проекта", "Project menu")
    val zoomLabel get() = t("Масштаб экрана", "Zoom")
    val fitPage get() = t("Вписать страницу", "Fit page")
    val actualSize get() = t("100 %", "100 %")
    val toSelection get() = t("К выбранному", "Go to selection")

    fun tool(tool: WorkspaceTool) = when (tool) {
        WorkspaceTool.NAVIGATE -> t("Навигация", "Navigate")
        WorkspaceTool.SELECT -> t("Выбор", "Select")
        WorkspaceTool.DISTANCE -> t("Длина", "Length")
        WorkspaceTool.POLYLINE -> t("Полилиния", "Polyline")
        WorkspaceTool.AREA -> t("Площадь", "Area")
        WorkspaceTool.ANGLE -> t("Угол", "Angle")
        WorkspaceTool.COUNTER -> t("Счётчик", "Counter")
        WorkspaceTool.ANNOTATION -> t("Заметка", "Note")
        WorkspaceTool.CALIBRATE -> t("Калибровка", "Calibrate")
    }

    fun measurement(type: MeasurementType) = when (type) {
        MeasurementType.DISTANCE -> t("Длина", "Length")
        MeasurementType.POLYLINE -> t("Полилиния", "Polyline")
        MeasurementType.AREA -> t("Площадь", "Area")
        MeasurementType.ANGLE -> t("Угол", "Angle")
        MeasurementType.ANNOTATION -> t("Заметка", "Note")
        MeasurementType.COUNTER -> t("Счётчик", "Counter")
    }

    val selectedState get() = t("Выбран", "Selected")
    val activeTool get() = t("Активный инструмент", "Active tool")
    val canvasDescription get() = t("План и измерения", "Plan drawing and measurement canvas")
    val noPage get() = t("Страница недоступна", "Page unavailable")
    val notCalibrated get() = t("Не откалибровано", "Not calibrated")
    val calibrated get() = t("Калибровано", "Calibrated")
    val units get() = t("Единицы", "Units")
    val automatic get() = t("авто", "auto")

    // Plan revisions
    val revisionWord get() = t("Ревизия", "Revision")
    val revisions get() = t("Ревизии", "Revisions")
    val newRevision get() = t("Новая ревизия", "New revision")
    val replaceCurrentPage get() = t("Заменить текущую страницу", "Replace current page")
    val previousPlan get() = t("Предыдущий план", "Previous plan")
    val newPlan get() = t("Новый план", "New plan")
    val alignment get() = t("Совмещение", "Alignment")
    val alignmentHint get() = t(
        "Отметьте одинаковые точки сначала на старом, затем на новом плане. Нужны 2 точки; третья исправляет перекос.",
        "Mark matching points on the old plan and then on the new plan. Two pairs are required; a third corrects skew.",
    )
    val pickPreviousPoint get() = t("Укажите точку на старом плане", "Pick a point on the old plan")
    val pickCurrentPoint get() = t("Укажите ту же точку на новом плане", "Pick the same point on the new plan")
    val controlPoints get() = t("Контрольные точки", "Control points")
    val undoPoint get() = t("Убрать точку", "Undo point")
    val invalidAlignment get() = t(
        "Точки слишком близки или лежат на одной прямой. Выберите другие контрольные точки.",
        "The points are too close or collinear. Choose different control points.",
    )
    val selectRevisionPage get() = t("Страница нового документа", "Page in the new document")
    val revisionNote get() = t("Комментарий к ревизии", "Revision note")
    val note get() = t("Примечание", "Note")
    val optional get() = t("необязательно", "optional")
    val overlay get() = t("Наложение", "Overlay")
    val previousOpacity get() = t("Прозрачность старого плана", "Old plan opacity")
    val allMeasurements get() = t("Все", "All")
    val changedMeasurements get() = t("Изменённые", "Changed")
    val needsReview get() = t("Требует проверки", "Needs review")
    val reviewed get() = t("Проверено", "Reviewed")
    val markReviewed get() = t("Отметить проверенным", "Mark as reviewed")
    val carriedReviewWarning get() = t(
        "Перенесённые измерения являются копиями и не считаются правильными, пока вы их не проверите.",
        "Carried measurements are copies and are not considered correct until you review them.",
    )
    val noRevisions get() = t("Для этой страницы ревизий пока нет", "This page has no revisions yet")
    fun revisionNumber(number: Int) = t("Ревизия $number", "Revision $number")
    val revisionSaved get() = t("Ревизия сохранена — измерения требуют проверки", "Revision saved — measurements need review")
    val revisionLog get() = t("Журнал ревизий", "Revision log")

    fun category(category: TradeCategory): String = when (category) {
        TradeCategory.HEATING -> t("Отопление", "Heating")
        TradeCategory.PLUMBING -> t("Сантехника", "Plumbing")
        TradeCategory.ELECTRICAL -> t("Электрика", "Electrical")
        TradeCategory.HVAC -> t("Вентиляция", "HVAC")
        TradeCategory.PAINTING -> t("Покраска", "Painting")
        TradeCategory.FLOORING -> t("Полы", "Flooring")
        TradeCategory.GENERAL -> t("Общее", "General")
    }

    fun snap(mode: SnapMode) = when (mode) {
        SnapMode.AUTO -> t("Привязка: авто", "Snap: auto")
        SnapMode.VERTEX -> t("Привязка: точки", "Snap: vertex")
        SnapMode.AXIS -> t("Привязка: оси", "Snap: axis")
        SnapMode.EDGE -> t("Привязка: рёбра", "Snap: edge")
        SnapMode.OFF -> t("Привязка: выкл.", "Snap: off")
    }

    // Calibration
    val calibrationHow get() = t("Как установить масштаб?", "How should the scale be set?")
    val calibrationByLength get() = t("По известному расстоянию", "By a known distance")
    val calibrationByLengthHint
        get() = t(
            "Проведите отрезок по чертежу и введите его реальную длину",
            "Draw a segment on the plan and enter its real length",
        )
    val calibrationByRatio get() = t("По масштабу чертежа", "By the drawing scale")
    val calibrationByRatioHint
        get() = t("Для векторных PDF с известным печатным масштабом", "For vector PDFs with a known printed scale")
    val calibrationDrawHint
        get() = t("Коснитесь двух точек известного расстояния", "Tap the two ends of a known distance")
    val verificationDrawHint
        get() = t("Коснитесь концов другого известного отрезка", "Tap the ends of another known segment")
    val knownLength get() = t("Известная длина", "Known length")
    val referenceSegment get() = t("Опорный отрезок", "Reference segment")
    val precision get() = t("Точность калибровки", "Calibration precision")
    val precisionHigh get() = t("Высокая", "High")
    val precisionMedium get() = t("Средняя", "Medium")
    val precisionLow get() = t("Низкая", "Low")
    val redrawPoints get() = t("Выбрать точки заново", "Pick the points again")
    val calibrationSaved get() = t("Калибровка сохранена", "Calibration saved")
    val imageCalibration
        get() = t("Для изображения доступна только ручная калибровка.", "Images support manual calibration only.")
    val printedScale get() = t("Печатный масштаб", "Printed scale")
    val printedScaleSure get() = t("Печаталось без изменения размера?", "Printed without resizing?")
    val yes get() = t("Да", "Yes")
    val notSure get() = t("Не уверен", "Not sure")
    val scaleConfirmed get() = t("Масштаб подтверждён", "Scale confirmed")
    val scaleLikely get() = t("Масштаб вероятно точен", "Scale is probably right")
    val scaleNeedsCheck get() = t("Масштаб требует проверки", "Scale needs a check")
    val calibratedBy get() = t("Кто калибрует (необязательно)", "Calibrated by (optional)")
    val localUser get() = t("Пользователь устройства", "Device user")
    val calibratedAt get() = t("Калибровано", "Calibrated")
    val calibratedByExport get() = t("Калибровал", "Calibrated by")
    val calibrationMethod get() = t("Метод", "Method")
    val verification get() = t("Контроль", "Verification")
    val shortReferenceWarning
        get() = t(
            "Опорный отрезок короче 10 % ширины страницы. Выберите более длинный — ошибка точки заметно влияет на масштаб.",
            "The reference is shorter than 10% of the page. Use a longer one because point placement strongly affects scale.",
        )
    val scanQualityWarning
        get() = t(
            "Фото и сканы могут быть растянуты или сняты под углом. Используйте длинный отрезок рядом с областью измерений.",
            "Photos and scans may be stretched or skewed. Use a long reference near the area you will measure.",
        )
    val scanQualityHint
        get() = t(
            "Если страница PDF является сканом, проверьте отсутствие растяжения и используйте длинный опорный отрезок.",
            "If this PDF page is a scan, check it for stretching and use a long reference segment.",
        )
    fun calibrationPreview(value: String, documentUnit: String) = t(
        "Предварительный результат: 1 $documentUnit = $value м",
        "Preview: 1 $documentUnit = $value m",
    )
    fun ratioPreview(value: String) = t(
        "Предварительный результат: 1 точка PDF = $value м",
        "Preview: 1 PDF point = $value m",
    )
    val pdfPoint get() = t("точка PDF", "PDF point")
    val imagePixel get() = t("пиксель", "pixel")
    val documentUnit get() = t("единица чертежа", "document unit")
    val verificationHint
        get() = t(
            "Необязательно: проверьте масштаб по другому известному отрезку. Он не изменит калибровку, а покажет расхождение.",
            "Optional: check the scale against another known segment. It will not change calibration and will show the difference.",
        )
    val pickVerificationSegment get() = t("Выбрать контрольный отрезок", "Pick a control segment")
    val expectedLength get() = t("Ожидаемая длина", "Expected length")
    val expected get() = t("Ожидается", "Expected")
    val measured get() = t("Измерено", "Measured")
    fun measuredControl(value: String, unit: String) = t(
        "По текущему масштабу: $value $unit",
        "Current scale measures: $value $unit",
    )
    fun controlDifference(value: String) = t("Расхождение: $value %", "Difference: $value%")
    val saveVerification get() = t("Сохранить проверку", "Save check")
    val skipVerification get() = t("Пропустить проверку", "Skip check")

    fun referenceShare(percent: Int) = t(
        "Отрезок занимает $percent % ширины страницы. Чем он длиннее, тем выше точность.",
        "The segment covers $percent % of the page width. A longer reference is more precise.",
    )

    // Properties
    val label get() = t("Название", "Name")
    val material get() = t("Материал", "Material")
    val comment get() = t("Комментарий", "Comment")
    val quantity get() = t("Количество", "Quantity")
    val waste get() = t("Запас, %", "Waste, %")
    val strokeWidth get() = t("Толщина линии", "Stroke width")
    val showLabel get() = t("Показывать подпись", "Show label")
    val color get() = t("Цвет", "Colour")
    val category get() = t("Категория", "Category")
    val result get() = t("Результат", "Result")
    val copyValue get() = t("Копировать значение", "Copy value")
    val copied get() = t("Скопировано", "Copied")
    val diameter get() = t("Диаметр", "Diameter")
    val subcategory get() = t("Подкатегория", "Subcategory")
    val size get() = t("Размер", "Size")
    val insertVertex get() = t("Добавить вершину", "Insert vertex")
    val edit get() = t("Изменить", "Edit")
    val active get() = t("Активен", "Active")
    val templates get() = t("Шаблоны работ", "Takeoff templates")
    val template get() = t("Шаблон", "Template")
    val addTemplate get() = t("Новый шаблон", "New template")
    val editTemplate get() = t("Изменить шаблон", "Edit template")
    val templateName get() = t("Название шаблона", "Template name")
    val measurementKind get() = t("Инструмент", "Measurement tool")
    val noActiveTemplate get() = t("Без шаблона", "No template")
    val repeatLast get() = t("Повторить", "Repeat")
    val lastMeasurement get() = t("Последний размер", "Last measurement")
    val materialTotal get() = t("Итого материала", "Material total")
    val calculateAssembly get() = t("Рассчитать узел", "Calculate assembly")
    val quantityMultiplier get() = t("Коэффициент количества", "Quantity multiplier")
    fun updateExisting(count: Int) = t(
        "Применить свойства к существующим измерениям: $count (геометрия сохранится)",
        "Apply properties to $count existing measurements (geometry is preserved)",
    )
    val templateInUse get() = t(
        "Шаблон используется измерениями. Сначала переназначьте или удалите их.",
        "The template is used by measurements. Reassign or delete them first.",
    )
    val exactLength get() = t("Точная длина и направление", "Exact length and direction")
    val lengthValue get() = t("Длина", "Length")
    val keepDirection get() = t("По направлению", "Keep direction")
    val horizontal get() = t("Горизонталь", "Horizontal")
    val vertical get() = t("Вертикаль", "Vertical")
    val exactLengthHint get() = t(
        "Первая точка остаётся на месте; вторая перестраивается по заданной длине.",
        "The first point stays fixed; the second is rebuilt to the requested length.",
    )

    // Layers
    val layers get() = t("Слои", "Layers")
    val layer get() = t("Слой", "Layer")
    val layerName get() = t("Название слоя", "Layer name")
    val addLayer get() = t("Добавить слой", "Add layer")
    val layerVisible get() = t("Видимый", "Visible")
    val layerLocked get() = t("Заблокирован", "Locked")
    val moveToLayer get() = t("Слой измерения", "Measurement layer")
    val layerLockedHint
        get() = t(
            "Слой заблокирован: разблокируйте его, чтобы редактировать измерения.",
            "The layer is locked: unlock it to edit its measurements.",
        )
    val layerInUse
        get() = t("Нельзя удалить слой с измерениями.", "A layer that still holds measurements cannot be deleted.")
    val removeVertex get() = t("Удалить вершину", "Remove vertex")
    val deleteMeasurement get() = t("Удалить измерение?", "Delete measurement?")
    val deleteMeasurementBody
        get() = t("Измерение будет удалено из проекта.", "The measurement will be removed from the project.")

    // Annotation
    val addAnnotation get() = t("Добавить заметку", "Add note")
    val editAnnotation get() = t("Изменить заметку", "Edit note")
    val annotationText get() = t("Текст заметки", "Note text")

    // Schedule
    val searchHint get() = t("Поиск", "Search")
    val grouping get() = t("Группировка", "Grouping")
    val groupByPage get() = t("По странице", "By page")
    val groupByCategory get() = t("По категории", "By category")
    val groupByType get() = t("По типу", "By type")
    val groupByTemplate get() = t("По шаблону", "By template")
    val groupByMaterial get() = t("По материалу", "By material")
    val groupByLayer get() = t("По слою", "By layer")
    val projectTotal get() = t("Весь проект", "Whole project")
    val groupNone get() = t("Без группировки", "No grouping")
    val total get() = t("Итого", "Total")
    val noTemplate get() = t("Без шаблона", "No template")
    val noMaterial get() = t("Без материала", "No material")
    val baseQuantity get() = t("Без запаса", "Base")
    val withWaste get() = t("С запасом", "With waste")
    val lengthTotal get() = t("Длина", "Length")
    val areaTotal get() = t("Площадь", "Area")
    val countTotal get() = t("Количество", "Count")
    val formula get() = t("Формула", "Formula")
    val scheduleEmpty get() = t("Измерений пока нет", "No measurements yet")
    val showOnPlan get() = t("Показать на плане", "Show on plan")

    // Export
    val exportFormat get() = t("Формат", "Format")
    val exportContent get() = t("Содержимое", "Content")
    val exportLook get() = t("Внешний вид", "Appearance")
    val exportPreview get() = t("Предпросмотр", "Preview")
    val exportPdf get() = t("Размеченный PDF", "Annotated PDF")
    val exportCsv get() = t("Ведомость CSV", "Schedule CSV")
    val exportJson get() = t("Проект JSON", "Project JSON")
    val exportCurrentPage get() = t("Текущая страница", "Current page")
    val exportAllPages get() = t("Все страницы", "All pages")
    val exportRange get() = t("Диапазон страниц", "Page range")
    val firstPage get() = t("Первая", "First")
    val lastPage get() = t("Последняя", "Last")
    val includeLegend get() = t("Легенда", "Legend")
    val includeScale get() = t("Информация о масштабе", "Scale information")
    val next get() = t("Далее", "Next")
    val projectWord get() = t("Проект", "Project")
    val exportedAt get() = t("Экспортировано", "Exported")
    val scaleWord get() = t("Масштаб", "Scale")
    val pieces get() = t("шт.", "pcs")
    val exportDone get() = t("Экспорт завершён", "Export complete")
    val exportFailed get() = t("Не удалось выполнить экспорт", "Export failed")
    val exportSummary get() = t("Проверьте параметры и выполните экспорт", "Check the options and export")

    // Coach tips
    val coachZoom
        get() = t(
            "Двумя пальцами — масштаб и перемещение. Одним пальцем — постановка точек.",
            "Two fingers zoom and pan. One finger places points.",
        )
    val coachCalibration
        get() = t(
            "Сначала укажите известное расстояние — от него зависят все измерения.",
            "Start with a known distance: every measurement depends on it.",
        )
    val coachMagnifier
        get() = t(
            "Удерживайте точку, чтобы открыть лупу и поставить её точно.",
            "Hold a point to open the magnifier and place it precisely.",
        )
    val coachAutosave
        get() = t("Проект сохраняется автоматически.", "The project saves itself automatically.")

    // Photo metadata research
    val photoData get() = t("Данные фото", "Photo data")
    val photoDataInspector get() = t("Инспектор данных фото", "Photo data inspector")
    val noPhotoMetadata get() = t("Метаданные камеры не найдены.", "No camera metadata was found.")
    val readiness get() = t("Готовность", "Readiness")
    fun photoReadiness(value: CaptureReadiness) = when (value) {
        CaptureReadiness.REFERENCE_REQUIRED -> t("Нужен опорный размер", "Reference required")
        CaptureReadiness.APPROXIMATE_FOCUS_PLANE -> t("Приближённая плоскость фокуса", "Approximate focus plane")
        CaptureReadiness.METRIC_DEPTH_AVAILABLE -> t("Доступна метрическая глубина", "Metric depth available")
        CaptureReadiness.AR_SURVEY_AVAILABLE -> t("Доступна AR-съёмка", "AR survey available")
    }
    val photoScaleCaution get() = t(
        "Метаданные камеры сами по себе не задают подтверждённый масштаб. Используйте известный размер, пока метрическая глубина не подтверждена.",
        "Camera metadata does not provide an accepted scale. Use a known reference unless metric depth is confirmed.",
    )
    val cameraAndOptics get() = t("Камера и оптика", "Camera and optics")
    val camera get() = t("Камера", "Camera")
    val lens get() = t("Объектив", "Lens")
    val imageResolution get() = t("Разрешение изображения", "Image resolution")
    val orientation get() = t("Ориентация", "Orientation")
    val focalLength get() = t("Фокусное расстояние", "Focal length")
    val equivalent35mm get() = t("Эквивалент 35 мм", "35 mm equivalent")
    val fieldOfView get() = t("Поле зрения", "Field of view")
    val sensorDiagonal get() = t("Диагональ сенсора", "Sensor diagonal")
    val focusDistance get() = t("Дистанция фокусировки", "Focus distance")
    val focusPlanePixelSize get() = t("Размер пикселя в плоскости фокуса", "Focus-plane pixel size")
    val containerSignals get() = t("Сигналы контейнера", "Container signals")
    val depthStandard get() = t("Стандарт глубины", "Depth standard")
    val depthUnits get() = t("Единицы глубины", "Depth units")
    val depthPayload get() = t("Карта глубины", "Depth payload")
    val depthDecoding get() = t("Декодирование глубины", "Depth decoding")
    val depthDecoder get() = t("Декодер", "Decoder")
    val sourceFormat get() = t("Формат источника", "Source format")
    val bitDepth get() = t("Разрядность", "Bit depth")
    val validDepthSamples get() = t("Достоверные точки", "Valid depth samples")
    val minimumDepth get() = t("Минимальная глубина", "Minimum depth")
    val medianDepth get() = t("Медианная глубина", "Median depth")
    val maximumDepth get() = t("Максимальная глубина", "Maximum depth")
    fun depthDecodeStatus(value: DepthDecodeStatus) = when (value) {
        DepthDecodeStatus.NOT_PRESENT -> t("Нет данных", "Not present")
        DepthDecodeStatus.DECODED_METRIC -> t("Декодировано в метры", "Decoded to metres")
        DepthDecodeStatus.DECODED_RELATIVE -> t("Только относительная глубина", "Relative depth only")
        DepthDecodeStatus.MALFORMED_METADATA -> t("Повреждённые метаданные", "Malformed metadata")
        DepthDecodeStatus.UNSUPPORTED_PAYLOAD -> t("Неподдерживаемая нагрузка", "Unsupported payload")
        DepthDecodeStatus.RESOURCE_LIMIT_EXCEEDED -> t("Превышен безопасный лимит", "Safety limit exceeded")
    }
    val metricDepthDecoded get() = t(
        "Карта декодирована в единый формат метров и готова для геометрических расчётов.",
        "The map is decoded to a format-independent metre representation and is ready for geometry calculations.",
    )
    val confidenceMap get() = t("Карта достоверности", "Confidence map")
    val cameraPose get() = t("Положение камеры", "Camera pose")
    val worldPlanes get() = t("Плоскости пространства", "World planes")
    val motionPhotoVideo get() = t("Видео Motion Photo", "Motion Photo video")
    val localCameraProfile get() = t("Локальный профиль камеры", "Local camera profile")
    val samples get() = t("Снимки", "Samples")
    val stable get() = t("Устойчив", "Stable")
    val collectingData get() = t("Сбор данных", "Collecting data")
    val medianNormalizedFocal get() = t("Медиана нормализованного фокуса", "Median normalized focal")
    val relativeMad get() = t("Относительный MAD", "Relative MAD")
    val formulaCandidate get() = t("Кандидат единой формулы", "Formula candidate")
    val formulaNeedsMetricAnchor get() = t(
        "Для метрического результата всё ещё нужна подтверждённая дистанция, карта глубины или известный размер.",
        "A metric distance, depth map or known reference is still required.",
    )
    val warnings get() = t("Предупреждения", "Warnings")
    val fileFingerprint get() = t("Отпечаток файла", "File fingerprint")
    val fileSize get() = t("Размер файла", "File size")
    val available get() = t("Есть", "Available")
    val notFound get() = t("Не найдено", "Not found")
    fun photoWarning(value: CaptureWarning) = when (value) {
        CaptureWarning.NO_CAMERA_IDENTITY, CaptureWarning.NO_OPTICAL_METADATA -> t(
            "Не найдены идентификатор камеры или оптические параметры.",
            "Camera identity or optical metadata is missing.",
        )
        CaptureWarning.SUBJECT_DISTANCE_IS_ONLY_A_HINT -> t(
            "Дистанция фокусировки — только приблизительная подсказка плоскости.",
            "Focus distance is only an approximate plane hint.",
        )
        CaptureWarning.FOCAL_PLANE_AND_35MM_ESTIMATES_DISAGREE -> t(
            "Независимые оценки фокусного расстояния расходятся.",
            "Independent focal estimates disagree.",
        )
        CaptureWarning.DEPTH_UNITS_MISSING, CaptureWarning.DEPTH_PAYLOAD_NOT_CONFIRMED -> t(
            "Метаданные глубины неполные.",
            "Depth metadata is incomplete.",
        )
        CaptureWarning.DEPTH_DECODE_FAILED -> t(
            "Карту глубины не удалось безопасно декодировать.",
            "The depth payload could not be decoded safely.",
        )
        CaptureWarning.MOTION_PHOTO_NEEDS_METRIC_ANCHOR -> t(
            "Для Motion Photo всё ещё нужна метрическая опора.",
            "Motion Photo still needs a metric anchor.",
        )
        CaptureWarning.AUXILIARY_DEPTH_REQUIRES_DECODER -> t(
            "Для вспомогательной глубины Apple нужен системный декодер.",
            "Apple auxiliary depth needs a platform decoder.",
        )
        CaptureWarning.CAMERA_PROFILE_HAS_TOO_FEW_SAMPLES -> t(
            "Нужно минимум три разных снимка.",
            "At least three different photos are required.",
        )
        CaptureWarning.CAMERA_PROFILE_IS_UNSTABLE -> t(
            "Профиль этой камеры пока статистически неустойчив.",
            "This camera profile is not statistically stable.",
        )
    }

    // Errors
    fun message(message: UiMessage): String = when (message) {
        UiMessage.ProjectSaved -> t("Проект сохранён", "Project saved")
        UiMessage.SaveFailed -> t("Не удалось сохранить проект", "Could not save the project")
        UiMessage.ProjectMissing -> t("Проект не выбран", "No project selected")
        UiMessage.ProjectUnreadable -> t("Не удалось открыть проект", "The project could not be opened")
        UiMessage.ProjectNotCreated -> t("Не удалось создать проект", "Could not create the project")
        UiMessage.ExportDone -> exportDone
        UiMessage.ExportFailed -> exportFailed
        UiMessage.CalibrationSaved -> calibrationSaved
        UiMessage.RevisionSaved -> revisionSaved
        is UiMessage.Measurement -> measurementError(message.error)
        is UiMessage.Document -> documentError(message.error)
    }

    fun measurementError(error: MeasurementError): String = when (error) {
        MeasurementError.NotCalibrated -> t(
            "Сначала откалибруйте план: без масштаба длину вычислить нельзя.",
            "Calibrate the plan first: without a scale there is no length.",
        )
        is MeasurementError.InvalidGeometry -> geometry(error.reason)
        MeasurementError.NoActiveDraft -> t("Сначала начните измерение.", "Start a measurement first.")
        MeasurementError.NothingToUndo -> t("Отменять нечего.", "There is nothing to undo.")
        is MeasurementError.NotFound -> t("Измерение больше не существует.", "The measurement no longer exists.")
    }

    /** Engine reasons are technical; the user needs the fix, not the diagnosis. */
    private fun geometry(reason: String): String = when {
        reason.contains("self-intersecting", true) -> t(
            "Контур пересекает сам себя. Переместите выделенную вершину.",
            "The outline crosses itself. Move the highlighted vertex.",
        )
        reason.contains("Reference segment", true) -> t(
            "Точки калибровки слишком близко друг к другу. Возьмите более длинный отрезок.",
            "The calibration points are too close together. Use a longer reference.",
        )
        reason.contains("Known length", true) -> t(
            "Введите положительную известную длину.",
            "Enter a positive known length.",
        )
        reason.contains("At least", true) -> t(
            "Точек недостаточно, чтобы завершить фигуру.",
            "There are not enough points to finish the shape.",
        )
        reason.contains("single point", true) -> t(
            "Это первая точка: отмените измерение целиком.",
            "This is the first point: cancel the whole measurement instead.",
        )
        reason.contains("at least", true) -> t(
            "В фигуре не может остаться меньше точек.",
            "The shape cannot lose any more points.",
        )
        else -> reason
    }

    fun documentError(error: DocumentError): String = when (error) {
        DocumentError.UnsupportedFormat -> t("Выберите файл PDF, PNG или JPEG.", "Choose a PDF, PNG or JPEG file.")
        DocumentError.AccessLost -> t(
            "Доступ к документу потерян. Импортируйте файл заново.",
            "Document access was lost. Import the file again.",
        )
        DocumentError.CorruptDocument -> t("Документ повреждён и не читается.", "The document is damaged or unreadable.")
        DocumentError.OutOfMemory -> t(
            "Страница слишком большая для безопасного рендеринга.",
            "The page is too large to render safely.",
        )
        is DocumentError.PageUnavailable -> t(
            "Страница ${error.index + 1} недоступна.",
            "Page ${error.index + 1} is unavailable.",
        )
        is DocumentError.Io -> error.message
    }
}

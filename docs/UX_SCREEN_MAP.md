# PlanRuler — экранная карта

Дата: 2026-07-25. Статус: реализована основная часть P0 — см. [§14](#14-статус-реализации).

Перевод UX/UI-концепции в Compose-контракты: граф экранов, сигнатуры, состояния,
точные размеры и адаптивные правила. Токены (цвета, размеры, движение, тактильность,
профили касания) — в [UI_DESIGN_SYSTEM.md](UI_DESIGN_SYSTEM.md); ниже они используются
по именам (`dimens.toolButtonHeight`, `canvasColors.snapAccent`, `TouchProfile.GLOVE`).

Легенда: `✔` есть в коде · `~` есть, переработать · `+` новое ·
`[P0]`/`[P1]`/`[P2]` — приоритет из §41 концепции.

---

## 1. Граф экранов

```text
Welcome (S0, только первый запуск)
   ├─ Открыть PDF/изображение ──> SAF ──> NewProject (S2) ──> Workspace (S3)
   └─ Пустой проект ─────────────────────> NewProject (S2) ──> Workspace (S3)

ProjectsHost (S1)                       [нижняя навигация / rail]
   ├─ tab: Проекты   ──> ProjectCard ──> Workspace (S3)
   ├─ tab: Недавние
   ├─ tab: Шаблоны                                     [P1]
   └─ tab: Настройки ──> Settings (S6) ──> Info (S7)

Workspace (S3)                          [глобальная навигация скрыта]
   ├─ overlay: Calibration (O1)         ├─ overlay: Properties (O2)
   ├─ overlay: Pages (O3)               ├─ overlay: Layers (O4)      [P1]
   ├─ overlay: Annotation (O5)          ├─ overlay: RadialMenu (O6)  [P1]
   ├─ overlay: Magnifier (O7)           ├─ overlay: CoachTip (O8)
   ├─ Schedule (S4)   ── прицел ──> возврат на S3 с центрированием
   └─ Export (S5, мастер 4 шага) ──> SAF CreateDocument ──> результат
```

```kotlin
sealed interface Route {
    data object Welcome : Route
    data class Projects(val tab: ProjectsTab = ProjectsTab.ALL) : Route
    data class Workspace(
        val projectId: String? = null,
        val importUri: String? = null,
        val mimeType: String? = null,
    ) : Route
    data class Schedule(val projectId: String) : Route
    data class Export(val projectId: String) : Route
    data class Settings(val section: SettingsSection = SettingsSection.MEASUREMENT) : Route
    data class Info(val page: InfoPage) : Route
}

enum class ProjectsTab { ALL, RECENT, TEMPLATES, SETTINGS }
enum class SettingsSection { MEASUREMENT, CONTROL, APPEARANCE, PROJECTS, EXPORT, PRIVACY }
enum class InfoPage { HELP, PRIVACY, ABOUT }
```

Навигация остаётся в composition root `:app` как типизированный стек
`List<Route>` в `rememberSaveable(stateSaver = RouteSaver)`. `navigation-compose`
не подключаем: граф маленький (8 узлов), а лишняя библиотека нарушила бы принцип
минимальных зависимостей из требований. Текущая реализация — три `var` в
`PlanRulerRoot` (`MainActivity.kt:54`) ~ — заменяется стеком.

### Правила возврата

| Экран | Системный «назад» | Результат |
| --- | --- | --- |
| S0 Welcome | выход | — |
| S1 Projects, вкладка ≠ ALL | переход на ALL | стек не меняется |
| S1 Projects, ALL | выход | подтверждение не требуется |
| S1, активен множественный выбор | сброс выбора | — |
| S3 Workspace, активен черновик | диалог «Сохранить черновик / Отменить / Остаться» | §7 концепции |
| S3 Workspace, есть выделение | снять выделение | стек не меняется |
| S3 Workspace, Focus Mode | выйти из Focus Mode | стек не меняется |
| S3 Workspace, открыт оверлей | закрыть верхний оверлей | по одному |
| S3 Workspace, чисто | `save()` + возврат на S1 | ✔ поведение сохраняется |
| S4/S5/S6/S7 | возврат на родителя | S5 внутри мастера — на предыдущий шаг |

Predictive back (API 34+): `PredictiveBackHandler` на оверлеях S3 — прогрессивное
затухание sheet-а. На шагах мастера экспорта — сдвиг предыдущего шага на 24 dp.

### Восстановление после смерти процесса

Сейчас — `SharedPreferences` c тремя ключами ✔ (`MainActivity.kt:53`). Переходим на
`DataStore` + `SavedStateHandle`, сохраняем: стек маршрутов, `projectId`,
`selectedPage`, `viewport` (уже в `PlanProject` ✔), активный инструмент, режим
(edit/view/focus), профиль касания, `themeMode`, состояние черновика — **не**
сохраняем (черновик отменяется, как требует §9 требований).

---

## 2. Адаптивные правила

### 2.1 Классы окна

```kotlin
@Immutable
data class PlanRulerLayout(
    val width: WindowWidthSizeClass,     // Compact <600dp · Medium 600–839 · Expanded ≥840
    val height: WindowHeightSizeClass,   // Compact <480dp · Medium 480–899 · Expanded ≥900
    val handed: Handedness,              // LEFT / RIGHT / AUTO
    val touch: TouchProfile,             // STYLUS / FINGER / GLOVE
    val focusMode: Boolean,
) {
    val navigation: NavigationStyle
    val toolbar: ToolbarStyle
    val properties: PanelStyle
    val canvasMinWidth: Dp get() = 480.dp
}

enum class NavigationStyle { BOTTOM_BAR, RAIL, RAIL_WITH_DRAWER }
enum class ToolbarStyle { BOTTOM_SCROLLING, SIDE_RAIL, FLOATING_DOCK }
enum class PanelStyle { MODAL_SHEET, SIDE_SHEET, FIXED_PANEL }
```

Источник — `calculateWindowSizeClass(activity)` в `:app`, дальше `PlanRulerLayout`
раздаётся через `LocalPlanRulerLayout`. Feature-модули **не** обращаются к `Activity`.

### 2.2 Матрица раскладок

| Ширина × высота | Пример | Навигация (S1) | Тулбар (S3) | Свойства (S3) | Ведомость (S4) |
| --- | --- | --- | --- | --- | --- |
| Compact × Medium/Expanded | телефон, портрет | `NavigationBar` 80 dp | `BOTTOM_SCROLLING` | `MODAL_SHEET` | отдельный экран |
| Compact × Compact | телефон, ландшафт | `RAIL` 80 dp | `SIDE_RAIL` 72 dp | `SIDE_SHEET` 360 dp | отдельный экран |
| Medium × любая | планшет 7–9″, складной | `RAIL` 80 dp | `FLOATING_DOCK` | `SIDE_SHEET` 360 dp | отдельный экран |
| Expanded × Medium/Expanded | планшет 10″+, Chromebook | `RAIL_WITH_DRAWER` | `FLOATING_DOCK` | `FIXED_PANEL` 360 dp | правая панель, вкладка |

Правило приоритета канвы: если `доступная ширина − панели < canvasMinWidth (480 dp)`,
`FIXED_PANEL` автоматически деградирует в `SIDE_SHEET`, а `SIDE_SHEET` — в `MODAL_SHEET`.
Проверка выполняется в `BoxWithConstraints` рабочей области, а не по классу окна —
это корректно работает в многооконном режиме и на складных.

### 2.3 Каркасы

**Телефон, портрет (Compact × Medium):**

```text
┌───────────────────────────────┐  status inset
│ ‹  Дом Берн — отопление   ✓ ↶↷│  56 dp  WorkspaceTopBar
├───────────────────────────────┤
│ 1/4 │ 1:50 │ м │ Snap: Auto   │  40 dp  IndicatorStrip
├───────────────────────────────┤
│                               │
│           КАНВА               │  675 dp (см. бюджет высоты в design system §4)
│                               │
├───────────────────────────────┤
│ [Отмена] [Назад] [Подтвердить]│  56 dp  ConfirmBar (только при черновике)
├───────────────────────────────┤
│ ✋ ↖ 📏 ⌁ ▱ ＋ ⋯               │  72 dp  ToolBar (горизонтальный скролл)
└───────────────────────────────┘  navigation inset
```

**Планшет (Expanded):**

```text
┌────┬──────────────────────────────────┬──────────────┐
│    │ ‹ Проект              ✓ ↶ ↷ 👁 ⋮ │              │
│Rail├──────────────────────────────────┤  Свойства    │
│ 80 │ 1/4 │ 1:50 │ м │ Snap            │  360 dp      │
│ dp ├──────────────────────────────────┤  FIXED_PANEL │
│    │                                  │              │
│    │             КАНВА                │              │
│    │        (≥ 480 dp ширины)         │              │
│    │   ┌────────────────────────┐     │              │
│    │   │ ✋ ↖ 📏 ⌁ ▱ ∠ ＋ ✎ ⋯   │     │  FLOATING_DOCK
│    │   └────────────────────────┘     │  72 dp, 16 dp от низа
└────┴──────────────────────────────────┴──────────────┘
```

Панели на планшете находятся **в потоке layout** (`Row`), а не поверх канвы —
требование §18 «панели не перекрывают документ». При открытии/закрытии панели
центр документа сохраняется автоматически: `ViewportTransform` хранит центр в
координатах документа ✔ (`Models.kt:34`), поэтому изменение ширины вьюпорта не сдвигает план.
Ширина панели анимируется `standard` (200 ms).

**Телефон, ландшафт (Compact × Compact):** верхняя панель схлопывается в плавающую
строку 40 dp над канвой (полупрозрачная, `surface` α .92), индикаторы переезжают
в неё чипами по 28 dp, тулбар становится вертикальным rail 72 dp со стороны
ведущей руки, `ConfirmBar` — вертикальный столбец кнопок с противоположной стороны.

### 2.4 Режим одной руки, Glove, Focus

| Режим | Что меняется | Приоритет |
| --- | --- | --- |
| Одна рука (LEFT/RIGHT) | `SIDE_RAIL` и плавающие меню прижимаются к стороне; Undo/Redo дублируются в нижнюю зону; `MagnifierPlacement` по умолчанию — противоположная сторона; круговое меню открывается в точке касания | P0 |
| Glove | `TouchProfile.GLOVE` (design system §3); inline-иконки скрываются в меню; двойные касания < 380 ms фильтруются; подтверждения дублируются вибрацией | P1 |
| Focus | скрываются top bar, индикаторная строка, подписи инструментов, боковые панели; остаются канва, активный инструмент, Undo, подтверждение, текущий результат; тап по свободному месту возвращает панели | P1 |

`AUTO` для рукости: определяется по стороне экрана, где чаще происходит первое
касание (скользящее среднее по 50 жестам, порог 65 %), пересчитывается раз в сессию.

---

## 3. S0 — Первый запуск `[P0]` `+`

```kotlin
@Composable
fun WelcomeScreen(state: WelcomeUiState, onEvent: (WelcomeEvent) -> Unit)

data class WelcomeUiState(val busy: Boolean = false, val error: String? = null)
sealed interface WelcomeEvent {
    data object OpenDocument : WelcomeEvent
    data object CreateEmpty : WelcomeEvent
    data object Skip : WelcomeEvent
}
```

| Зона | Размер | Содержимое |
| --- | --- | --- |
| Логотип | 74 dp круг | `primaryContainer`, глиф 32 sp |
| Заголовок | `headlineLarge` | «PlanRuler» |
| Подзаголовок | `bodyLarge`, `onSurfaceVariant` | «Измеряйте PDF и планы с реальным масштабом» |
| Основная кнопка | `fillMaxWidth`, высота 56 dp | «Открыть PDF или изображение» |
| Вторичная кнопка | `OutlinedButton` 48 dp | «Создать пустой проект» |
| Три преимущества | `labelMedium`, иконка 20 dp, строки по 32 dp | калибровка · длины/площади/количество · PDF, CSV, JSON |
| Сноска | `labelMedium`, `secondary` | «Работает офлайн. Документы не покидают устройство» |

Показывается один раз (`DataStore: onboarding_seen`). Обучающих шагов нет —
контекстные подсказки `CoachTip` (O8) появляются по факту первой встречи с функцией.

---

## 4. S1 — Проекты `[P0]` `~`

```kotlin
@Composable
fun ProjectsScreen(
    state: ProjectsUiState,
    layout: PlanRulerLayout,
    onEvent: (ProjectsEvent) -> Unit,
)

data class ProjectsUiState(
    val loading: Boolean = true,
    val projects: List<ProjectCardModel> = emptyList(),
    val tab: ProjectsTab = ProjectsTab.ALL,
    val query: String = "",
    val filters: Set<ProjectFilter> = emptySet(),
    val sort: ProjectSort = ProjectSort.MODIFIED,
    val view: ProjectView = ProjectView.GRID,
    val selection: Set<ProjectId> = emptySet(),
    val error: String? = null,
)

data class ProjectCardModel(               // проекция PlanProject, не сам агрегат
    val id: ProjectId,
    val name: String,
    val documentName: String,
    val thumbnail: ImageBitmap?,           // кеш первой страницы, 68×88 dp
    val pageCount: Int,
    val measurementCount: Int,
    val modifiedAtEpochMs: Long,
    val calibration: CalibrationBadge,     // CONFIRMED / LIKELY / MISSING
    val category: TradeCategory,           // ✔ уже есть в модели
    val saveState: SaveBadge,              // SAVED / PENDING / FAILED
)
```

`ProjectCardModel` строится в state holder-е; сейчас экран получает список
`PlanProject` целиком ✔ (`ProjectsScreen.kt:32`) ~ — тяжёлый агрегат с полными
`measurements` в списке не нужен и мешает пагинации.

### Анатомия

| Элемент | Размер | Состояния |
| --- | --- | --- |
| Top bar | 64 dp | обычный / поиск (заголовок → `TextField`) / выбор (N выбрано) |
| Кнопка меню `☰` | 48 dp | открывает `ModalNavigationDrawer` 300 dp |
| Действия: поиск, фильтр, сортировка, вид | по 48 dp | фильтр с бейджем-счётчиком |
| Сетка | `LazyVerticalGrid(GridCells.Adaptive(168.dp))`, gap 12 dp, padding 16 dp | — |
| Список | `LazyColumn`, строка 88 dp | — |
| Карточка | `medium` 18 dp, elevation 3 dp ✔ | обычная / нажатая (8 dp) ✔ / выбранная (контур 2 dp + галочка 24 dp) |
| FAB | Extended 56 dp, отступ 16 dp | скрывается при скролле вниз, возвращается при вверх |
| Меню карточки `⋮` | 48 dp | открыть, переименовать, дублировать, экспортировать, информация, архив, удалить |

Контент карточки (пример из концепции):

```text
[миниатюра]  Дом Берн — отопление                       ⋮
   56×72     Plan_EG.pdf · 4 страницы
             32 измерения · 1:50 ✔    Изменён сегодня, 08:42
```

Бейдж калибровки — `IndicatorStatus`: `CONFIRMED` зелёный со сплошной заливкой,
`LIKELY` янтарный с иконкой треугольника, `MISSING` красный контур с `!`.

### Состояния экрана

| Состояние | UI |
| --- | --- |
| Загрузка | 6 shimmer-карточек (высота как у контента, без «прыжка» layout) |
| Пусто (нет проектов) | `EmptyState` + кнопка импорта ✔ |
| Пусто (фильтр/поиск) | «Ничего не найдено» + кнопка «Сбросить фильтры» |
| Ошибка чтения хранилища | inline-плашка `error` + «Повторить» |
| Множественный выбор | top bar → счётчик + действия (экспорт, архив, удалить); FAB скрыт |

Долгое нажатие на карточку → множественный выбор (haptic `LongPress`).
Одиночное нажатие → S3 с восстановлением страницы и viewport (данные уже в `PlanProject` ✔).

### Адаптив

* Compact: `NavigationBar` (Проекты · Недавние · Шаблоны · Настройки), сетка 2 колонки.
* Medium: `NavigationRail`, сетка 3–4 колонки.
* Expanded: `RAIL_WITH_DRAWER` (постоянный drawer 280 dp при ширине ≥ 1200 dp),
  список 360 dp + детальная панель проекта (`ListDetailPaneScaffold` `[P1]`).

---

## 5. S2 — Создание проекта `[P0]` `+`

`ModalBottomSheet` поверх S1 (на Expanded — `AlertDialog` шириной 480 dp).

| Поле | Компонент | Правила |
| --- | --- | --- |
| Название проекта | `OutlinedTextField`, single line, 120 символов ✔ | префилл — имя файла без расширения ✔ (`WorkspaceViewModel.kt:66`) |
| Категория | `FlowRow` из `FilterChip` 32 dp | 8 значений `TradeCategory` ✔ + «Пользовательская» |
| Адрес/объект | `OutlinedTextField`, 200 символов | необязательное |
| Единицы | `SingleChoiceSegmentedButtonRow` | из `LengthUnit` ✔ (mm/cm/m/in/ft) |

Кнопки: «Отмена» (`TextButton`) и «Создать проект» (`Button`, 48 dp, активна при
непустом названии). Категория влияет только на шаблоны и быстрые инструменты, не на
функциональность. После создания сразу открывается S3.

---

## 6. S3 — Рабочая область `[P0]` `~`

Ключевой экран. Требование §42: четыре ответа видны без открытия меню —
**какой инструмент активен** (`ToolButton` selected + подпись), **надёжна ли калибровка**
(`IndicatorChip` масштаба), **каков текущий результат** (`ValueBadge` у объекта +
в `ConfirmBar`), **как отменить** (Undo в верхней панели, на планшете дублируется в dock).

```kotlin
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    engine: EngineState,                  // ✔ из MeasurementEngineApi.state
    layout: PlanRulerLayout,
    onEvent: (WorkspaceEvent) -> Unit,
)

data class WorkspaceUiState(
    val phase: Phase,                     // Loading / Ready / Failed
    val project: ProjectHeader?,          // имя, страницы, selectedPage
    val page: RenderedPage?,              // ✔
    val viewport: ViewportState,          // ✔
    val tool: WorkspaceTool,              // NAVIGATE, SELECT, DISTANCE, POLYLINE, AREA, ANGLE, COUNTER, ANNOTATION
    val mode: WorkspaceMode,              // EDIT / VIEW
    val snapMode: SnapMode,               // AUTO, VERTEX, AXIS, EDGE, OFF
    val selection: SelectionState,        // id + vertexIndex + bbox на экране
    val draftHint: DraftHint?,            // сегмент N, длина последнего, общая длина
    val magnifier: MagnifierState?,
    val saveState: SaveBadge,             // SAVED / SAVING / FAILED
    val scaleConfidence: ScaleConfidence, // CONFIRMED / LIKELY / NEEDS_CHECK  [P1]
    val overlay: WorkspaceOverlay?,       // O1…O8, ровно один активный
    val focusMode: Boolean,
    val message: UiMessage?,
)
```

`WorkspaceTool` — новый enum поверх `MeasurementType` ✔: добавляет `NAVIGATE` и
`SELECT`, которых нет в доменной модели и не должно быть. Сейчас режим правки — это
булев `editMode` рядом с `tool` (`WorkspaceScreen.kt:61,71`) ~, из-за чего возможны
несогласованные комбинации; один enum их исключает.

### 6.1 Верхняя панель — 56 dp

| Зона | Ширина | Элемент | Состояния |
| --- | --- | --- | --- |
| Слева | 48 dp | «Назад» | всегда активна; при черновике — диалог |
| Центр | `weight(1f)` | Название + `1/4` под ним (`labelSmall`) | тап → карточка сведений; долгое нажатие → переименование |
| Справа | 48 × 4 | Статус сохранения · Undo · Redo · Меню `⋮` | см. ниже |

* **Статус сохранения**: `SAVED` — галочка 20 dp `success`; `SAVING` —
  `CircularProgressIndicator` 16 dp, 2 dp; `FAILED` — восклицательный знак `error`,
  тап открывает детали + «Повторить». Иконка облака не используется (приложение локальное).
* **Undo/Redo**: активность из `engine.canUndo/canRedo` ✔. Долгое нажатие → список
  последних 10 действий (`DropdownMenu`, строка 48 dp), выбор откатывает до состояния.
  Требует расширения `MeasurementEngineApi` полем `history: List<HistoryEntry>` `[P1]`.
* **Меню `⋮`**: страницы, ведомость, слои, калибровка, свойства проекта, экспорт,
  справка, закрыть проект.
* **Режим просмотра** (иконка «глаз»): на Compact живёт в меню `⋮`, на Medium+ —
  отдельной кнопкой. В `VIEW` скрываются управляющие точки, отключается hit-testing
  на перемещение, остаются zoom/pan и чтение значений.

### 6.2 Индикаторная строка — 40 dp

Четыре `IndicatorChip` по 32 dp, зазор 8 dp, горизонтальный скролл при `fontScale > 1.3`.

| Чип | Текст | Тап | Долгое нажатие | Статус |
| --- | --- | --- | --- | --- |
| Страница | `1 / 4` | лента страниц (O3) | — | `NEUTRAL`; свайп по чипу влево/вправо = смена страницы |
| Масштаб | `1:50` / `Калибровано` / `Не откалибровано` | калибровка (O1) | — | `OK` подтверждена · `WARNING` печатный масштаб без проверки · `ERROR` нет калибровки · `NEUTRAL` инструменту масштаб не нужен |
| Единицы | `м` | циклическая смена | все единицы + округление | `NEUTRAL` |
| Привязка | `Snap: Auto` | цикл Auto→Vertex→Axis→Edge→Off | настройки чувствительности | `OK` вкл · `NEUTRAL` выкл |

Сейчас эти элементы разбросаны по нижней панели (`WorkspaceScreen.kt:334–364`) ~ и
zoom показан процентами — переносятся наверх, процент zoom уходит в инструмент
«Навигация», где он и нужен.

### 6.3 Панель инструментов — 72 dp

`LazyRow` из `ToolButton`, `contentPadding` 12 dp, зазор 8 dp. Всегда видимы:
Навигация · Выбор · Длина · Полилиния · Площадь · Счётчик · Ещё.
В «Ещё» (`ModalBottomSheet`, сетка 3 колонки, ячейка 88 dp): Угол, Аннотация,
Калибровка, Слои, Ведомость, Focus Mode, Шаблоны трасс `[P1]`.

Поведение кнопки: тап — активировать; повторный тап по активной — настройки
инструмента (`ModalBottomSheet`, высота по контенту); долгое нажатие — `PlainTooltip`
с описанием и последними параметрами.

Настройки по инструментам:

| Инструмент | Быстрые параметры | Дополнительные кнопки |
| --- | --- | --- |
| Навигация | zoom `100%`, «Вписать страницу», «К выбранному», «Заблокировать viewport» | долгое на «Вписать»: по ширине / по высоте / вся страница |
| Выбор | размер точек, чувствительность | — |
| Длина | H / V / «продолжить цепочку», детализация ΔX ΔY | — |
| Полилиния | режим подписи (общая / сегменты / обе), «Трасса» `[P1]` | замкнуть, завершить |
| Площадь | вычитаемые зоны, коэффициент запаса, единица площади | замкнуть, завершить |
| Счётчик | тип символа (радиатор, кран, розетка, светильник, решётка, дверь, свой) | сменить тип, отменить последний |
| Аннотация | тип (заметка, предупреждение, вопрос, задача), голосовой ввод | — |

### 6.4 Панель подтверждения — 56 dp

Появляется только при активном черновике (`engine.draft != null` ✔), между канвой и
тулбаром, анимация `standard`.

```text
[Отмена 48dp]  [Назад 48dp]  Сегмент 3 · 4.26 m · Σ 12.80 m  [Подтвердить ≥120dp]
```

«Назад» удаляет **только последнюю точку** (нужен новый метод
`MeasurementEngineApi.removeLastPoint()` `+`; сейчас есть только `cancelMeasurement()` ✔).
Двойное нажатие по канве завершает полилинию/площадь; для `DISTANCE` (2 точки) и
`ANGLE` (3 точки) фиксация автоматическая ✔ (`WorkspaceScreen.kt:452`).

### 6.5 Слои отрисовки канвы (z-порядок)

1. `backdrop` → страница (`drawImage`) ✔ → рамка страницы → тень (кроме HighContrast)
2. Завершённые измерения ✔ (фильтр по `pageIndex` ✔ и по видимости слоя `[P1]`)
3. Черновик (пунктир, `canvasColors.draft`)
4. Направляющие привязки и snap-маркер ✔
5. Подписи с автоконтрастной подложкой (design system §6.5)
6. Управляющие точки выбранного объекта (радиус из `TouchProfile`)
7. Плавающая мини-панель выбора — 48 dp, 12 dp над bbox, переворачивается вниз ближе 64 dp к верху
8. Лупа (O7)
9. Круговое меню (O6)
10. `CoachTip` (O8)
11. Scrim + sheet-оверлеи
12. Snackbar (12 dp над тулбаром)

### 6.6 Состояния экрана

| Состояние | UI |
| --- | --- |
| `Loading` | `CircularProgressIndicator` по центру ✔ + скелет панелей (панели не «прыгают») |
| `Ready`, нет калибровки | чип масштаба `ERROR`; при первой попытке измерения — sheet калибровки с пояснением, а не голая ошибка |
| `Ready`, черновик | `ConfirmBar` + модификаторы H/V у линии |
| `Ready`, выделение | мини-панель + управляющие точки + панель свойств (по раскладке) |
| `Ready`, drag вершины | лупа, snap-маркер, обновление значения без анимации |
| `Failed` | полноэкранная карточка с типизированной ошибкой ✔ и действием («Импортировать снова» при `AccessLost`) |
| `VIEW` | управляющие точки скрыты, тулбар свёрнут до навигации и «Ещё» |
| `Focus` | только канва + активный инструмент + Undo + подтверждение |

---

## 7. Оверлеи рабочей области

### O1 — Калибровка `[P0]` `~`

Трёхэкранный поток в одном `ModalBottomSheet` (сейчас — один `AlertDialog`,
`WorkspaceScreen.kt:948` ~).

**Шаг 1. Способ** — три карточки по 72 dp: «По известному расстоянию»,
«По масштабу чертежа», «Использовать масштаб страницы» (скрыт для изображений —
условие уже вычисляется: `coordinateUnit == PDF_POINT` ✔).

**Шаг 2а. По расстоянию** — sheet сворачивается до 88 dp, пользователь проводит
отрезок на канве, sheet возвращается:

| Поле | Компонент | Правило |
| --- | --- | --- |
| Известная длина | `OutlinedTextField`, numeric, autofocus | > 0, до 12 знаков ✔ |
| Единица | `SingleChoiceSegmentedButtonRow` | `LengthUnit` ✔ |
| Точность калибровки | индикатор `Высокая/Средняя/Низкая` | по доле отрезка от ширины страницы: ≥ 30 % высокая, 10–30 % средняя, < 10 % низкая |
| Подсказка | `bodyMedium` | «Линия занимает 36 % ширины страницы. Чем длиннее эталон, тем выше точность» |

Кнопки: «Выбрать точки заново» / «Применить». После применения — зелёное
подтверждение 56 dp: «Калибровка сохранена · Проверочный отрезок: 2.500 m»,
haptic `CONFIRM`, автоскрытие через 3 s.

**Шаг 2б. По масштабу** — `1 : [50]`, размер PDF (`841 × 594 mm`, берётся из
`PageMetadata` ✔), вопрос «Печатался без изменения размера? Да / Не уверен».
«Не уверен» → `ScaleConfidence.NEEDS_CHECK` + рекомендация ручной проверки.

**Scale Confidence** `[P1]` — вычисляется, не вводится:

| Значение | Условие | Чип масштаба |
| --- | --- | --- |
| `CONFIRMED` | `Calibration.Method.REFERENCE` ✔ и эталон ≥ 30 % страницы | `OK` |
| `LIKELY` | `PRINT_RATIO` ✔ и `coordinateUnit == PDF_POINT` | `WARNING` |
| `NEEDS_CHECK` | растр, скриншот, фото или неизвестный размер печати | `ERROR` |

Хранится вместе с `Calibration` (расширение модели `+`), попадает в экспорт и в
подробности копирования значения.

Ошибки формулируются по §36 концепции: не «Invalid calibration», а
«Не удалось откалибровать план. Выбранные точки находятся слишком близко друг к
другу. Выберите более длинный известный отрезок» — маппинг из
`MeasurementError.InvalidGeometry` ✔.

### O2 — Свойства `[P0]` `~`

`MODAL_SHEET` (Compact, peek 320 dp, max 85 % высоты) / `SIDE_SHEET` / `FIXED_PANEL`
(Expanded). Единый контент `PropertyPanelContent`. Сейчас — `AlertDialog` с
горизонтальным скроллом (`WorkspaceScreen.kt:798`) ~, что для формы неудобно.

| Секция | Поля | Компонент |
| --- | --- | --- |
| Заголовок | тип + номер, кнопки Закрыть / Дублировать / Удалить (48 dp) | `Row` 56 dp |
| Результат | значение + `ValueBadge` с копированием | **только чтение**, пересчитывается engine ✔ |
| Название | `label` ✔ | `OutlinedTextField` 200 |
| Категория | `TradeCategory` ✔ | `FlowRow` чипов |
| Материал / размер / диаметр | `TakeoffProperties` ✔ | текстовые поля |
| Количество / запас | `quantity`, `wasteFactor` ✔ | numeric, > 0 ✔ |
| Слой | `layerId` ✔ | выпадающий список `[P1]` |
| Стиль | цвет (палитра 9 + свой), толщина 0.5…24 ✔, тип линии, размер текста, расположение подписи, показывать сегменты, показывать единицы, прозрачность заливки | применяется мгновенно |
| Комментарий | `comment` ✔ | multiline 2000 ✔ |

Правка стиля применяется сразу (live preview), текстовые поля — по `onValueChange`
с debounce 300 ms, чтобы не плодить записи undo. Транзакции уже поддержаны
(`beginEdit/previewVertex/commitEdit` ✔).

### O3 — Страницы `[P0]` `~`

Горизонтальная лента 96 dp над тулбаром. Миниатюра 68 × 88 dp, радиус 6 dp,
выбранная — контур 2 dp `primary` + номер жирным.

Под миниатюрой: номер, количество измерений, бейдж калибровки, имя страницы.
Меню страницы (долгое нажатие): переименовать, повернуть вид, скопировать калибровку,
очистить измерения, экспортировать страницу.

Копирование калибровки требует подтверждения: «Применить калибровку страницы 1
к странице 2? Используйте это только при одинаковом масштабе».

Миниатюры рендерятся через `DocumentGateway.renderPage` с уменьшенным `RenderRequest` ✔,
кешируются LRU на 12 страниц, генерация — `Dispatchers.Default`, отмена при закрытии ленты.

### O4 — Слои `[P1]` `+`

`ModalBottomSheet`, строка слоя 64 dp: видимость (глаз 48 dp), блокировка (замок),
цветовая метка 16 dp, название, счётчик объектов, «экспортировать» (`Switch`).
Кнопки: «+ Новый слой», «Показать все», «Скрыть все», «Заблокировать завершённые».
Заблокированный слой исключается из hit-testing.
Модель `Layer` ✔ уже есть (`Models.kt:149`), в UI не используется.

### O5 — Аннотация `[P0]` `~`

Компактное поле ввода **у точки касания**, а не диалог по центру: `Popup` шириной
280 dp, `OutlinedTextField` 3–8 строк ✔, счётчик символов ✔, кнопки «Отмена» /
«Голосовой ввод» / «Сохранить». Тип заметки (маркер, стрелка, предупреждение,
вопрос, задача) выбирается чипами 32 dp; типы различаются **формой значка**, а не
только цветом. Фото-вложение — `[P1]`.

### O6 — Круговое меню `[P1]` `+`

Долгое нажатие на пустое место: 6 секторов радиусом 96 dp вокруг точки касания
(мёртвая зона 40 dp, элемент 56 dp): Длина, Полилиния, Площадь, Счётчик, Заметка,
Калибровка. Выбор — отпусканием пальца на секторе (drag-to-select) или тапом.
У края экрана меню сдвигается внутрь, сохраняя привязку к точке.
Долгое нажатие **на объекте** открывает не круговое меню, а список: Свойства,
Редактировать точки, Дублировать, Скрыть, Удалить.

### O7 — Лупа `[P0]` `+`

132 × 132 dp, увеличение ×2.5, отступ 88 dp от пальца, положение
`AUTO/LEFT/RIGHT/TOP` (для левшей зеркалится). Содержит: перекрестие 1.5 dp,
ближайшие линии, snap-маркер, координаты будущей точки, расстояние до предыдущей.
Появляется при постановке и перемещении точки; в режиме стилуса отключается.
Реализация — собственный `Canvas` поверх страницы (`Modifier.magnifier` требует API 28
при `minSdk 26` и не позволяет накладывать оверлеи).

### O8 — Контекстные подсказки `[P0]` `+`

Одна подсказка за раз, максимум одна за сессию, кнопки «Понятно» / «Больше не
показывать» (`DataStore`, ключ на подсказку). Триггеры: первое открытие документа
(двухпальцевый zoom), первая калибровка, первое измерение (лупа), первое долгое
нажатие (меню редактирования), первое сохранение (автосохранение).

---

## 8. S4 — Ведомость измерений `[P0]` `+`

```kotlin
data class ScheduleUiState(
    val rows: List<ScheduleRow>,
    val grouping: ScheduleGrouping,   // PAGE, CATEGORY, MATERIAL, TYPE, LAYER, NONE
    val query: String,
    val filters: Set<ScheduleFilter>,
    val totals: Map<String, String>,  // «Длина, м» → «142.8»
)
```

Таблица: `LazyColumn` со «липкими» заголовками групп (`stickyHeader`, 40 dp),
строка 64 dp: тип (иконка 24 dp) · название · значение (табличные цифры, справа) ·
категория (чип 24 dp) · «прицел» 48 dp.

Верхние действия: поиск, фильтр, группировка, экспорт. Итоги по группам — в
заголовке группы; общий итог — в закреплённой нижней строке 56 dp.

Тап по строке → O2 (свойства). Тап по «прицелу» → возврат на S3, центрирование
объекта анимацией `centerOn` (400 ms) и мигание выделения 2 × 120 ms.

Адаптив: Compact — отдельный экран; Expanded — правая панель 360 dp во вкладке
рядом со «Свойствами», центрирование работает без ухода с канвы.

---

## 9. S5 — Экспорт `[P0]` `~`

Мастер из 4 шагов, единый `Scaffold` с индикатором шага 4 dp сверху.
Сейчас экспорт — выпадающее меню + диалог диапазона ✔ (`WorkspaceScreen.kt:163`) ~.

| Шаг | Содержимое | Компоненты |
| --- | --- | --- |
| 1. Формат | 4 карточки 96 dp: Размеченный PDF, Ведомость CSV, Проект JSON, Изображение страницы | `ExportFormat` ✔ + `PAGE_IMAGE` `+` |
| 2. Содержимое | текущая / выбранные / весь документ ✔ (`ExportPageSelection`); переключатели: измерения, аннотации, легенда, масштаб, дата, название, номера страниц, оригинальный фон | `Switch` 48 dp |
| 3. Внешний вид | цветной / ч-б / высокая контрастность; толщина линий; размер подписей | `SegmentedButton` + `Slider` |
| 4. Предпросмотр | реальная страница экспорта, pinch-zoom | `Image` + кнопки «Назад» / «Экспортировать» |

Результат: карточка «PDF создан» + `[Открыть]` `[Поделиться]` `[Показать в папке]`.
Ошибки — типизированные (`ExportError`), с указанием причины и действия.
Выбор целевого файла остаётся на SAF `CreateDocument` ✔ (`MainActivity.kt:137`).

---

## 10. S6 — Настройки `[P0]` `~`

Текущий `SettingsScreen` — заглушка из двух полей (`SettingsScreen.kt:11`) ~.
Полная структура: `LazyColumn` с секциями, строка 56 dp (72 dp при подзаголовке),
`ListItem` + `Switch`/`SegmentedButton`/`Slider`, поиск по настройкам сверху.

| Секция | Настройки |
| --- | --- |
| Измерения | единицы длины ✔ · единицы площади · знаки после запятой · дробные дюймы · показывать единицы у значений · показывать длины сегментов · подтверждать завершение фигур · предупреждать о неподтверждённой калибровке |
| Управление | левша/правша · положение панели · размер управляющих точек · чувствительность выбора · чувствительность привязки ✔ · показывать лупу · положение лупы · режим стилуса · glove mode · виброотклик · двойное нажатие для завершения |
| Внешний вид | системная/светлая/тёмная/Sunlight/Blueprint/HighContrast · цвета Android (31+) · размер текста интерфейса · размер подписей · толщина линий |
| Проекты | автосохранение · задержка автосохранения (сейчас 700 ms ✔) · backup ✔ · хранить удалённые · открывать последний проект · каталог экспорта · формат имени файла |
| Экспорт | PDF по умолчанию · легенда · масштаб · CSV delimiter · десятичный разделитель · кодировка · единицы экспорта |
| Конфиденциальность | пояснение о локальной обработке ✔ · удалить историю · удалить миниатюры · удалить временные файлы · показать используемое хранилище |

Хранилище — `DataStore`, доменная модель `AppSettings` в `:core:model`
(без Android-импортов — правило `check_architecture.ps1`), чтение — `Flow<AppSettings>`.

Язык (EN/RU) ✔ переезжает из top bar «Проектов» в секцию «Внешний вид».

---

## 11. Машина состояний жестов `[P0]` `+`

Требование §9: gesture-логика выносится из большого Composable в отдельный
контроллер. Сейчас она встроена в `PlanCanvas` двумя ветками `pointerInput`
(`WorkspaceScreen.kt:584–653`) ~, из-за чего режимы навигации, рисования и правки
переключаются булевыми флагами.

```kotlin
sealed interface CanvasIntent {
    data class Tap(val doc: DocPoint) : CanvasIntent
    data class DoubleTap(val doc: DocPoint) : CanvasIntent
    data class LongPress(val doc: DocPoint, val hit: Hit?) : CanvasIntent
    data class DragStart(val doc: DocPoint, val hit: Hit?, val pointer: PointerType) : CanvasIntent
    data class Drag(val doc: DocPoint) : CanvasIntent
    data object DragEnd : CanvasIntent
    data object DragCancel : CanvasIntent
    data class Transform(val centroid: Offset, val pan: Offset, val zoom: Float) : CanvasIntent
}

class CanvasGestureController(
    private val config: GestureConfig,      // из TouchProfile
    private val onIntent: (CanvasIntent) -> Unit,
) {
    var phase: GesturePhase private set     // IDLE, CANDIDATE, PANNING, DRAG_VERTEX, DRAG_OBJECT, DRAWING, LONG_PRESS
    suspend fun PointerInputScope.collect()
}
```

| Текущая фаза | Событие | Условие | Новая фаза | Эффект |
| --- | --- | --- | --- | --- |
| IDLE | 1 палец вниз | — | CANDIDATE | запомнить точку и время |
| CANDIDATE | 2-й палец вниз | всегда | PANNING | **отменить кандидата, точку не ставить** |
| CANDIDATE | смещение > `dragSlop` | инструмент = NAVIGATE | PANNING | pan одним пальцем |
| CANDIDATE | смещение > `dragSlop` | режим SELECT и есть попадание | DRAG_VERTEX / DRAG_OBJECT | `beginEdit` ✔, показать лупу |
| CANDIDATE | смещение > `dragSlop` | инструмент рисования | DRAWING | тянуть предпросмотр точки |
| CANDIDATE | отпускание < `longPress` | смещение < slop | IDLE | `Tap` |
| CANDIDATE | удержание ≥ `longPressTimeout` | — | LONG_PRESS | круговое меню / меню объекта, haptic |
| PANNING | пальцы подняты | — | IDLE | `updateViewport`, точка не создаётся |
| DRAG_* | движение | — | DRAG_* | `previewVertex/previewMove` ✔ + snap + лупа |
| DRAG_* | отпускание | — | IDLE | `commitEdit` ✔, haptic `CONFIRM` |
| DRAG_* | отмена жеста / уход указателя | — | IDLE | **`cancelEdit` ✔, черновик не сохраняется** |
| любая | смена инструмента | активен черновик | IDLE | спросить: завершить или отменить |

Пороги: `dragSlop` из `TouchProfile`; `longPressTimeout` — системный
`viewConfiguration.longPressTimeoutMillis` ✔; окно двойного тапа — из `TouchProfile`;
zoom ограничен 0.1…32 ✔ (`ViewportTransform.minZoom/maxZoom`).

Стилус: `PointerType.Stylus` → профиль `STYLUS` на время жеста, отклонение ладони
(игнорировать `Touch`-указатели, пока активен стилус), `pressure` не используется
для геометрии.

`Modifier.systemGestureExclusion` (API 29+) на 24 dp полосах у левого и правого краёв
канвы — иначе системный жест «назад» перехватывает рисование у края плана.

---

## 12. Соответствие текущему коду

| Файл | Действие |
| --- | --- |
| `app/src/new/.../MainActivity.kt` ~ | стек `Route`, `WindowSizeClass`, `PlanRulerLayout`, `DataStore` вместо трёх `var` и `SharedPreferences` |
| `app/src/new/.../PlanRulerTheme.kt` ~ | переезжает в `:core:designsystem`, +6 режимов темы, +`PlanRulerCanvasColors`, +`PlanRulerDimens` |
| `feature/projects/.../ProjectsScreen.kt` ~ | stateless-сигнатура, `ProjectCardModel`, поиск/фильтр/сортировка/вид, сетка, множественный выбор, вкладки |
| `feature/workspace/.../WorkspaceScreen.kt` ~ | разбивается: `WorkspaceScreen`, `WorkspaceTopBar`, `IndicatorStrip`, `ToolBar`, `ConfirmBar`, `PlanCanvas`, `CanvasGestureController`, `CanvasRenderer`, оверлеи O1–O8 (сейчас 1093 строки в одном файле) |
| `feature/workspace/.../WorkspaceViewModel.kt` ~ | `WorkspaceUiState` по §6, строки ошибок → `stringResource`, `ScaleConfidence`, история undo |
| `feature/settings/.../SettingsScreen.kt` ~ | полная структура §10 на `AppSettings`; каждый переключатель доходит до поведения, «мёртвых» настроек нет |
| `core/model/.../Models.kt` ~ | `+AppSettings`, `+ScaleConfidence` в `Calibration`, `+Layer` в UI-обиход |
| `core/engine-api/.../MeasurementEngineApi.kt` ~ | `+removeLastPoint()`, `+history`, `+SnapMode` вместо булева `enabled` |
| `app/src/androidTest/.../PlanRulerUiTest.kt` ~ | переход с локализованных строк на `PlanRulerTestTags` |
| `scripts/check_architecture.ps1` | без изменений — `:core:designsystem` не нарушает правил |

---

## 13. Порядок реализации

| Этап | Содержание | Разблокирует |
| --- | --- | --- |
| 1 | `:core:designsystem`: токены, 6 тем, `TouchProfile`, `PlanRulerDimens`, иконки, `PlanRulerTestTags` | всё остальное |
| 2 | `PlanRulerLayout` + `WindowSizeClass` + стек `Route` + `DataStore` | адаптив и настройки |
| 3 | Каркас S3: `WorkspaceTopBar`, `IndicatorStrip`, `ToolBar`, `ConfirmBar` (канва пока прежняя) | видимость 4 ключевых ответов |
| 4 | `CanvasGestureController` + `CanvasRenderer` + лупа + snap-режимы | точность работы пальцем |
| 5 | Оверлеи O1, O2, O3, O5, O8 + S4 ведомость | P0-функциональность |
| 6 | S1 переработка, S2, S5 мастер, S6 настройки, доступность и шорткаты | завершение P0 |
| 7 | `[P1]`: слои, Scale Confidence, Glove, Focus, круговое меню, шаблоны, многостраничные операции | конкурентные отличия |

Проверки после каждого этапа: `scripts/check_architecture.ps1`,
`gradlew :app:assembleDebug`, инструментальные тесты на API 26 и 35,
проверка `fontScale = 2.0` и всех шести тем на каждом новом экране.

---

## 14. Статус реализации

Дата среза: 2026-07-25. Проверено на эмуляторе API 34 (импорт → калибровка →
измерение → экспорт) и инструментальными тестами (7 тестов, все зелёные).

### Реализовано

| Экран/узел | Где |
| --- | --- |
| Дизайн-система: токены, `TouchProfile`, 6 тем, `PlanRulerCanvasColors`, иконки, теги | `core/designsystem/` |
| Адаптивные классы окна, `PlanRulerLayout` | `core/designsystem/layout/PlanRulerLayout.kt` |
| S1 Проекты: поиск, сортировка, сетка/список, нижняя навигация, rail на планшете, бейдж калибровки | `feature/projects/` |
| S3 Рабочая область: верхняя панель 56 dp, индикаторная строка, тулбар с `ToolButton`, панель подтверждения, Focus Mode, шорткаты клавиатуры | `feature/workspace/WorkspaceScreen.kt`, `WorkspaceBars.kt` |
| Машина состояний жестов (tap/double tap/long press/drag/pan/zoom, отмена черновика) | `feature/workspace/WorkspaceCanvas.kt` |
| Рендер канвы: фон темы, рамка и тень страницы, пунктирный черновик, ручки по профилю касания, автоконтрастные подписи по яркости пикселей | `WorkspaceCanvas.kt` |
| Лупа ×2.5 с перекрестием и snap-маркером (собственный `Canvas`, работает с API 26) | `WorkspaceCanvas.kt` |
| O1 Калибровка: выбор способа, эталон с оценкой точности, печатный масштаб, Scale Confidence в чипе | `WorkspaceOverlays.kt` |
| O2 Свойства: палитра 9 цветов, толщина, единицы, категория, запас, комментарий | `WorkspaceOverlays.kt` |
| O3 Страницы с миниатюрами, O5 Заметки, S4 Ведомость с группировкой и прицелом, S5 Мастер экспорта | `WorkspaceOverlays.kt` |
| S6 Настройки: 6 разделов на `AppSettings` + `SettingsStore` | `feature/settings/`, `app/SettingsStore.kt` |
| Движок: `removeLastPoint`, `setDisplayFormat`, `SnapContext.allowed` | `core/engine-api`, `core/engine-default` |

### Сознательные отклонения

* **Без новых внешних зависимостей.** Классы окна считаются из `LocalConfiguration`,
  а не из `material3-window-size-class`; настройки лежат в `SharedPreferences`, а не в
  `DataStore`. Обе замены дают тот же контракт и не расширяют список разрешённых зависимостей.
* Панель подтверждения, панель выделения и элементы навигации **плавают над канвой**, а
  не занимают место в `Scaffold`: иначе появление панели меняло размер канвы и план
  уезжал под пальцем между двумя точками измерения (проверено на устройстве).
* Мастер экспорта — 3 шага (формат, содержимое, предпросмотр); шаг «Внешний вид» не
  реализован. Переключатели легенды и масштаба живут прямо в `AppSettings`, поэтому
  мастер и экран настроек не могут разойтись; вместе с CSV-разделителем они доходят
  до `ExportRequest` и меняют выгрузку.
* Долгое нажатие на пустом месте открывает лист «Ещё» вместо кругового меню.

### Добавлено 2026-08-10

O4 Слои (видимость, блокировка, переименование, удаление пустого слоя, перенос
измерения в слой из O2) · подписи длин сегментов по настройке `showSegments` ·
предупреждение о неподтверждённом масштабе по `warnUncalibrated` · единицы и
толщина линий по умолчанию · подкатегория, диаметр и размер в O2 · tiled-рендер
видимой области при глубоком zoom.

### Не реализовано

S0 Welcome · S2 экран создания проекта · O6 Круговое меню ·
постоянная панель свойств на планшете (свойства всегда модальный лист) ·
вертикальный tool-rail в ландшафте · история Undo по долгому нажатию ·
ΔX/ΔY и модификаторы H/V · «Повторить длину» · трассы и шаблоны профессий.

---

## 15. Контрольные вопросы приёмки

Из §42 концепции — то, что должно быть видно на основном экране без меню:

| Вопрос | Где ответ | Проверка |
| --- | --- | --- |
| Какой инструмент активен? | `ToolButton` selected: заливка + увеличенная иконка + подпись + полоса-маркер | скриншот в ч-б: активность различима без цвета |
| Надёжна ли калибровка? | `IndicatorChip` масштаба: текст + статус формой | TalkBack произносит статус |
| Каков текущий результат? | `ValueBadge` у объекта и в `ConfirmBar` | значение видно во время drag, без анимации |
| Как отменить последнее действие? | Undo в верхней панели (на планшете дублируется в dock) | доступно одной рукой в режиме одной руки |

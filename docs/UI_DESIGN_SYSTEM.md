# PlanRuler UI Design System

Дата: 2026-07-25. Статус: **реализовано** в модуле `:core:designsystem`
(токены, шесть тем, профили касания, иконки, компоненты, реестр тегов).
Отклонения от спецификации зафиксированы в
[UX_SCREEN_MAP.md §14](UX_SCREEN_MAP.md#14-статус-реализации).

Документ задаёт токены, темы и контракты переиспользуемых Compose-компонентов.
Экранная карта и адаптивные правила — в [UX_SCREEN_MAP.md](UX_SCREEN_MAP.md).

Легенда статуса:

* `✔` — уже есть в коде;
* `~` — есть, требует переработки;
* `+` — новое.

---

## 1. Размещение кода

Новый модуль `:core:designsystem` (Android library + Compose), зависимости — только
`:core:model`. Feature-модули зависят от него как от UI-библиотеки; правила
`scripts/check_architecture.ps1` не нарушаются (нет импортов `engine.default`,
`document.android`, `project.local`).

```text
core/designsystem/src/main/kotlin/com/planruler/designsystem/
  theme/PlanRulerTheme.kt        (+ перенос из app/src/new/.../PlanRulerTheme.kt ~)
  theme/PlanRulerColors.kt       + расширенные цвета канвы
  theme/PlanRulerDimens.kt       + токены размеров
  theme/PlanRulerMotion.kt       +
  theme/TouchProfile.kt          +
  component/*.kt                 + контракты из §9
  icon/PlanRulerIcons.kt         + собственные ImageVector
  test/PlanRulerTestTags.kt      + реестр тегов
```

`settings.gradle.kts`: добавить `":core:designsystem"`.

### Новые зависимости

| Артефакт | Версия | Приоритет | Зачем |
| --- | --- | --- | --- |
| `androidx.compose.material3:material3-window-size-class` | из BOM | P0 | классы окна для адаптива |
| `androidx.lifecycle:lifecycle-runtime-compose` | 2.8.6 (уже в каталоге) | P0 | `collectAsStateWithLifecycle` в `:app` |
| `androidx.datastore:datastore-preferences` | 1.1.1 | P0 | настройки вместо `SharedPreferences` |
| `androidx.compose.material3.adaptive:adaptive-layout` | 1.0.0 | P1 | `ListDetailPaneScaffold` на планшете |
| `androidx.window:window` | 1.3.0 | P1 | складные устройства, hinge-aware |

Иконки: `material-icons-extended` **не подключаем** (≈ +9 МБ до R8, лишний вес ради 14 глифов).
Вместо этого — собственный набор `PlanRulerIcons` (24 dp, `ImageVector`, viewport 24×24):
`Hand`, `Cursor`, `Ruler`, `Polyline`, `Area`, `Angle`, `Counter`, `Note`, `Calibrate`,
`Snap`, `Undo`, `Redo`, `Layers`, `Pages`, `Schedule`, `Export`, `Eye`, `Focus`, `More`.
Текущие юникод-глифы (`↔`, `⌁`, `▱`, `∠`, `✎`, `＋` в `WorkspaceScreen.kt:1050`) заменяются —
они не масштабируются, не читаются в TalkBack и по-разному рисуются на разных прошивках.

---

## 2. Шкала отступов и радиусов

Базовая сетка — 4 dp.

| Токен | Значение | Применение |
| --- | --- | --- |
| `space05` | 2 dp | зазор иконка↔подпись внутри кнопки |
| `space1` | 4 dp | внутренний зазор чипов |
| `space2` | 8 dp | зазор между кнопками панели |
| `space3` | 12 dp | горизонтальный padding панелей |
| `space4` | 16 dp | стандартный padding контента |
| `space5` | 20 dp | padding карточек |
| `space6` | 24 dp | padding диалогов и bottom sheet |
| `space8` | 32 dp | отступ секций |
| `space10` | 40 dp | верхний отступ пустых состояний |

Радиусы (`MaterialTheme.shapes`, расширяем текущие ✔):

| Токен | Значение | Применение |
| --- | --- | --- |
| `extraSmall` | 6 dp | подпись измерения на канве, бейдж |
| `small` | 10 dp | чипы индикаторной строки ✔ |
| `medium` | 18 dp | карточки, кнопки инструментов ✔ |
| `large` | 28 dp | bottom sheet, диалоги ✔ |
| `extraLarge` | 32 dp | лупа, круговое меню |

Тени: панели — `tonalElevation` 3 dp + `shadowElevation` 0 dp; плавающие оверлеи над
канвой — `shadowElevation` 6 dp; лупа — 8 dp. В `HighContrast` все тени = 0 dp,
вместо них — `border` 2 dp `outline`.

---

## 3. Профили касания (`TouchProfile`)

Один enum управляет всеми точностными размерами. Значение выводится из настроек
(`Стилус` / `Палец` / `Перчатки`) и типа последнего указателя (`PointerType.Stylus`
переключает профиль автоматически, если включён «режим стилуса»).

| Токен | `STYLUS` | `FINGER` (по умолчанию) | `GLOVE` |
| --- | --- | --- | --- |
| `minTouchTarget` | 44 dp | 48 dp | 56 dp |
| `toolButtonHeight` | 48 dp | 56 dp | 64 dp |
| `vertexHandleRadius` (рисуемый) | 5 dp | 7 dp | 10 dp |
| `vertexHitRadius` (попадание) | 12 dp | 24 dp | 32 dp |
| `segmentHitTolerance` | 8 dp | 16 dp | 22 dp |
| `snapSensitivity` | 8 dp | 12 dp | 16 dp |
| `dragSlop` | 3 dp | 6 dp | 10 dp |
| `doubleTapWindowMs` | 250 | 300 | 380 |
| `magnifierDefault` | выключена | включена | включена |
| `inlineIconButtons` | показаны | показаны | скрыты → пункты меню |

`minTouchTarget` ≥ 48 dp для `FINGER`/`GLOVE` — требование доступности (§18 требований).
Для `STYLUS` уменьшение до 44 dp допустимо только для инструментов канвы, не для
системной навигации.

Связь с engine: `snapSensitivity` передаётся в существующий
`SnapSensitivity.documentUnits(sensitivityDp, density, zoom)` (`MeasurementEngineApi.kt:95`) ✔.
`vertexHitRadius` и `segmentHitTolerance` конвертируются так же: `dp * density / zoom`
(сейчас захардкожено `32.0 * density / zoom` в `WorkspaceScreen.kt:581` ~).

```kotlin
@Immutable
data class PlanRulerDimens(
    val touchProfile: TouchProfile,
    val minTouchTarget: Dp,
    val toolButtonHeight: Dp,
    val vertexHandleRadius: Dp,
    val vertexHitRadius: Dp,
    val segmentHitTolerance: Dp,
    val snapSensitivity: Dp,
    val dragSlop: Dp,
    val doubleTapWindowMs: Long,
    // фиксированные размеры каркаса — §4
    val topBarHeight: Dp = 56.dp,
    val indicatorStripHeight: Dp = 40.dp,
    val indicatorChipHeight: Dp = 32.dp,
    val toolBarVerticalPadding: Dp = 8.dp,
    val confirmBarHeight: Dp = 56.dp,
    val propertiesPanelWidth: Dp = 360.dp,
    val navigationRailWidth: Dp = 80.dp,
    val pageStripHeight: Dp = 96.dp,
    val magnifierSize: Dp = 132.dp,
    val radialMenuRadius: Dp = 96.dp,
)

val LocalPlanRulerDimens = staticCompositionLocalOf { PlanRulerDimens(TouchProfile.FINGER, /* ... */) }
```

---

## 4. Каркас: фиксированные размеры

| Элемент | Высота/ширина | Примечание |
| --- | --- | --- |
| Верхняя панель рабочей области | 56 dp | собственный `Row`, не `TopAppBar` — нужен полный контроль высоты и трёх зон |
| Верхняя панель «Проекты» | 64 dp | `CenterAlignedTopAppBar` ✔ |
| Индикаторная строка | 40 dp | 4 чипа по 32 dp, зазор 8 dp |
| Кнопка инструмента | 56 × (64…88) dp | иконка 24 dp + подпись 11 sp/14 |
| Панель инструментов (контейнер) | 72 dp + `navigationBars` | 8 + 56 + 8 |
| Панель подтверждения черновика | 56 dp | появляется над панелью инструментов |
| Нижняя навигация (Проекты) | 80 dp | `NavigationBar` M3 |
| Navigation Rail (планшет) | 80 dp | `NavigationRail` M3 |
| Панель свойств (планшет) | 360 dp (min 320, max 420) | правая колонка |
| Лента страниц | 96 dp | миниатюра 68 × 88 dp |
| Лупа | 132 × 132 dp | увеличение ×2.5, отступ от пальца 88 dp |
| Круговое меню | ⌀ 240 dp | 6 секторов, элемент 56 dp, мёртвая зона 40 dp |
| FAB «Новый проект» | 56 dp / Extended 56 dp | ✔ |
| Карточка проекта (список) | 88 dp | миниатюра 56 × 72 dp |
| Карточка проекта (сетка) | min 168 dp | миниатюра 3:4, высота 132 dp |

**Бюджет высоты на телефоне** (411 × 891 dp, статус-бар 24, жест-бар 24):

```text
891 − 24 (status) − 56 (top) − 40 (indicators) − 72 (toolbar) − 24 (gestures) = 675 dp канвы
Focus Mode:  891 − 24 − 24 = 843 dp канвы  (+25 %)
```

Это и есть аргумент за Focus Mode как P1-функцию: на телефоне он даёт четверть площади.

---

## 5. Типографика

Расширяем существующий `PlanRulerTypography` (`PlanRulerTheme.kt:58` ✔):

| Стиль | Размер/интерлиньяж | Вес | Применение |
| --- | --- | --- | --- |
| `headlineLarge` | 32/38 ✔ | Bold | пустое состояние |
| `headlineSmall` | 23/29 ✔ | Bold | заголовки секций |
| `titleLarge` | 20/26 ✔ | SemiBold | заголовок экрана |
| `titleMedium` | 16/22 ✔ | SemiBold | название проекта, строка ведомости |
| `bodyLarge` | 16/24 ✔ | Normal | текст диалогов |
| `bodyMedium` | 14/20 ✔ | Normal | вторичный текст |
| `labelLarge` | 14/20 ✔ | SemiBold | кнопки |
| `labelMedium` | 12/16 + | Medium | чипы индикаторной строки |
| `labelSmall` | 11/14 + | Medium | подписи кнопок инструментов |

**Числовые значения** (результат измерения, поля калибровки) — `FontFeatureSetting("tnum")`
(табличные цифры), чтобы значение не «прыгало» при пересчёте во время drag.

**Подписи на канве** — отдельная шкала, не наследует `MaterialTheme.typography`:

```kotlin
val labelPx = with(density) { (13.sp * settings.labelScale).toPx() }  // учитывает fontScale
```

`labelScale` ∈ {0.85, 1.0, 1.15, 1.35} из настроек «Размер подписей измерений».
Сейчас в `drawMeasurement` используется `measurement.style.textSize * 2.5f` в сырых
пикселях (`WorkspaceScreen.kt:770` ~) — системное увеличение шрифта игнорируется,
это нарушает §18. Исправляется переходом на `sp → px` через `LocalDensity`.

Системный `fontScale` до 2.0 должен корректно отображать: панель инструментов
(подписи усекаются до 1 строки с `TextOverflow.Ellipsis`, полное имя — в tooltip и
`contentDescription`), диалоги (скроллятся), индикаторная строка (чипы теряют текст,
оставляя иконку + `contentDescription`, начиная с `fontScale ≥ 1.6`).

---

## 6. Цвет

### 6.1 Бренд и семантика

| Роль | HEX | M3-слот |
| --- | --- | --- |
| Primary (Electric Blue) | `#2563EB` | `primary` |
| Primary container | `#DBE6FF` | `primaryContainer` |
| Secondary (Teal) | `#0D9488` | `secondary` |
| Warning (Amber) | `#D97706` | `tertiary` |
| Error (Red) | `#DC2626` | `error` |
| Success (Green) | `#16A34A` | расширенный слот `success` |

Текущая тема использует Indigo `#4F46E5` (`PlanRulerTheme.kt:17`) ~ — заменяется на
`#2563EB` согласно концепции. У M3 нет слота «success», поэтому вводится расширенная
палитра (§6.3).

### 6.2 Палитра измерений (9 фиксированных + пользовательский)

`#2563EB` синий · `#16A34A` зелёный · `#EA580C` оранжевый · `#9333EA` фиолетовый ·
`#0D9488` бирюзовый · `#DC2626` красный · `#CA8A04` жёлтый · `#FFFFFF` белый · `#111827` чёрный.

Хранится в `MeasurementStyle.colorArgb` ✔ (`Models.kt:105`). Палитра **не меняется**
темой и не подменяется Material You — иначе экспортированный PDF не совпадёт с экраном.

### 6.3 Расширенные цвета канвы

M3 `ColorScheme` не покрывает канву. Вводится:

```kotlin
@Immutable
data class PlanRulerCanvasColors(
    val backdrop: Color,        // фон за страницей
    val pageBorder: Color,
    val pageShadowAlpha: Float,
    val selection: Color,
    val draft: Color,
    val snapAccent: Color,
    val guide: Color,           // линии привязки H/V
    val labelBackdrop: Color,
    val labelText: Color,
    val labelOutline: Color,
    val handleFill: Color,
    val handleStroke: Color,
    val scrim: Color,
    val success: Color,
    val onSuccess: Color,
)

val LocalCanvasColors = staticCompositionLocalOf<PlanRulerCanvasColors> { error("no canvas colors") }
```

| Токен | Light | Dark | Sunlight | Blueprint | HighContrast (light) |
| --- | --- | --- | --- | --- | --- |
| `backdrop` | `#E7EAF0` | `#0B0F14` | `#FFFFFF` | `#0B1B3A` | `#FFFFFF` |
| `pageBorder` | `#C7CED9` 1 dp | `#2A323D` 1 dp | `#000000` 1.5 dp | `#7DD3FC` 1 dp | `#000000` 2 dp |
| `pageShadowAlpha` | 0.12 | 0.0 | 0.0 | 0.0 | 0.0 |
| `selection` | `#2563EB` | `#7AA2FF` | `#1D4ED8` | `#38BDF8` | `#0B3FCC` |
| `draft` | `#2563EB` α .70 | `#7AA2FF` α .70 | `#1D4ED8` α 1.0 (пунктир) | `#38BDF8` α .8 | `#0B3FCC` пунктир |
| `snapAccent` | `#16A34A` | `#34D399` | `#0F7A32` | `#34D399` | `#006B2C` |
| `guide` | `#16A34A` α .5 | `#34D399` α .5 | `#0F7A32` α 1.0 | `#34D399` α .6 | `#006B2C` α 1.0 |
| `labelBackdrop` | `#FFFFFF` α .90 | `#0B0F14` α .85 | `#FFFFFF` α 1.0 | `#0B1B3A` α .88 | `#FFFFFF` α 1.0 |
| `labelText` | `#111827` | `#F3F6FA` | `#000000` | `#E8F4FF` | `#000000` |
| `handleFill` | `#FFFFFF` | `#F3F6FA` | `#FFFFFF` | `#FFFFFF` | `#FFFFFF` |
| `scrim` | `#000000` α .32 | `#000000` α .48 | `#000000` α .24 | `#000814` α .48 | `#000000` α .60 |

Текущий жёстко зашитый фон канвы `Color(0xFF293241)` (`WorkspaceScreen.kt:114, 656`) ~
заменяется на `canvasColors.backdrop`.

### 6.4 Шесть режимов темы

```kotlin
enum class PlanRulerThemeMode { SYSTEM, LIGHT, DARK, SUNLIGHT, BLUEPRINT, HIGH_CONTRAST }
```

* `SUNLIGHT` — светлая база, ни одного элемента с прозрачностью < 0.96, вторичный
  текст поднимается до `onSurface` (серые подписи запрещены), контраст ≥ 7:1,
  толщина активных контуров +1 dp.
* `BLUEPRINT` — тёмно-синяя база, акценты `#7DD3FC`, управляющие элементы белые.
  Сам документ **не перекрашивается**: инверсия — отдельный переключатель
  `invertDocument`, влияющий только на рендер битмапа.
* `HIGH_CONTRAST` — границы 2 dp, фокус-кольцо 3 dp `#FFBF00`, полупрозрачность
  запрещена, `vertexHandleRadius` +2 dp, любое состояние продублировано формой/текстом.
* `SYSTEM` — следует `isSystemInDarkTheme()`.

### 6.5 Автоконтраст подписей

Подпись на чертеже читается независимо от того, что под ней. Алгоритм (дёшево,
не требует дополнительного рендера — `RenderedPage.argb` уже в памяти ✔):

1. Взять прямоугольник подписи, спроецировать в координаты битмапа.
2. Усреднить яркость по сетке 4 × 4 внутри него (`argb` уже есть, `IntArray`).
3. `luminance > 0.55` → тёмная подложка + светлый текст, иначе наоборот.
4. Если итоговый контраст < 4.5:1 — добавить `labelOutline` 1 dp контуром.
5. Результат кешировать по ключу `(measurementId, viewport.zoom округлённый до 0.25)`.

### 6.6 Material You

Опция «Использовать цвета устройства» (API 31+, `dynamicLightColorScheme` /
`dynamicDarkColorScheme`). По умолчанию **выключена**. Влияет только на слоты M3 UI;
`PlanRulerCanvasColors` и палитра измерений не затрагиваются. Недоступна для
`SUNLIGHT`, `BLUEPRINT`, `HIGH_CONTRAST` (переключатель показывается неактивным
с пояснением, а не скрывается).

---

## 7. Движение

| Токен | Длительность | Easing | Применение |
| --- | --- | --- | --- |
| `instant` | 0 ms | — | смена значения измерения при drag |
| `fast` | 120 ms | `LinearOutSlowIn` | нажатие чипа, подсветка привязки |
| `standard` | 200 ms | `FastOutSlowIn` | появление панелей, смена инструмента |
| `emphasized` | 320 ms | `EmphasizedDecelerate` | bottom sheet, круговое меню |
| `page` | 260 ms | `FastOutSlowIn` | переход между страницами PDF |
| `centerOn` | 400 ms | `FastOutSlowIn` | «К выбранному», центрирование из ведомости |

Запрещены: бесконечные пульсации, «прыгающие» кнопки, любые анимации, задерживающие
фиксацию точки. Значение измерения обновляется **без** анимации — профессиональный
инструмент не должен «догонять» палец.

**Reduced Motion**: читать `Settings.Global.getFloat(resolver, ANIMATOR_DURATION_SCALE, 1f)`;
при `0f` — все переходы заменяются на `Crossfade(80 ms)`, круговое меню появляется
мгновенно, `centerOn` становится телепортом. Значение публикуется через
`LocalReducedMotion` и перечитывается в `onResume`.

---

## 8. Тактильная отдача

Compose `LocalHapticFeedback` даёт только `LongPress` и `TextHandleMove`, поэтому для
остального — `LocalView.current.performHapticFeedback(...)` с проверкой API (minSdk 26):

| Событие | Константа | Fallback < API 30 |
| --- | --- | --- |
| Точка привязалась | `HapticFeedbackType.TextHandleMove` | тот же |
| Измерение завершено | `HapticFeedbackConstants.CONFIRM` (30+) | `VIRTUAL_KEY` |
| Объект выбран | `HapticFeedbackConstants.CLOCK_TICK` | `VIRTUAL_KEY` |
| Undo выполнен | `CLOCK_TICK` | `VIRTUAL_KEY` |
| Достигнут предел zoom | `GESTURE_END` (30+) | без отдачи |
| Ошибка / потеря калибровки | `REJECT` (30+) | `LONG_PRESS` ×2 |
| Удаление | `REJECT` (30+) | `LONG_PRESS` |

Полностью отключается настройкой; при `GLOVE` — усиленный паттерн (двойной импульс),
поскольку через перчатку слабая вибрация не ощущается.

---

## 9. Контракты компонентов

Все компоненты — stateless, состояние поднимается в screen-level state holder.

```kotlin
// Кнопка инструмента: одно нажатие — выбрать, повторное — настройки, долгое — подсказка.
@Composable
fun ToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    badge: String? = null,          // напр. «12» для счётчика
    enabled: Boolean = true,
    onClick: () -> Unit,
    onSettings: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Состояния `ToolButton` (выбор **не** передаётся только цветом — §18):

| Состояние | Контейнер | Иконка | Подпись | Доп. маркер |
| --- | --- | --- | --- | --- |
| Обычное | прозрачный | 24 dp, `onSurfaceVariant` | 11 sp | нет |
| Выбрано | `primaryContainer`, `medium` | 26 dp, `primary` | 11 sp SemiBold | полоса 3 dp снизу |
| Выбрано + черновик | `primaryContainer` | 26 dp | + счётчик точек | полоса + бейдж |
| Недоступно | прозрачный, α .38 | α .38 | α .38 | нет |
| Фокус (клавиатура) | контур 2 dp `primary` | — | — | — |

```kotlin
@Composable fun IndicatorChip(               // страница · масштаб · единицы · привязка
    icon: ImageVector, text: String, status: IndicatorStatus,
    onClick: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier,
)
enum class IndicatorStatus { OK, WARNING, ERROR, NEUTRAL }
```

`IndicatorStatus` рисуется цветом **и** формой: `OK` — сплошная заливка,
`WARNING` — заливка + иконка треугольника, `ERROR` — контур 2 dp + иконка `!`,
`NEUTRAL` — прозрачный контур 1 dp.

```kotlin
@Composable fun ConfirmBar(                  // Отмена / Назад / Подтвердить над тулбаром
    canUndoPoint: Boolean, confirmEnabled: Boolean, hint: String?,
    onCancel: () -> Unit, onBackPoint: () -> Unit, onConfirm: () -> Unit,
)

@Composable fun MeasurementMagnifier(        // §20 концепции
    page: ImageBitmap, focusDocument: DocPoint, transform: ViewportTransform,
    snap: SnapResult?, anchorDistanceText: String?, placement: MagnifierPlacement,
)
enum class MagnifierPlacement { AUTO, LEFT, RIGHT, TOP }

@Composable fun RadialMenu(                  // долгое нажатие на пустое место, P1
    items: List<RadialItem>, center: Offset, onSelect: (RadialItem) -> Unit, onDismiss: () -> Unit,
)

@Composable fun ValueBadge(                  // «4.26 m» + копирование
    value: String, detail: String?, onCopy: (CopyMode) -> Unit,
)
enum class CopyMode { VALUE, VALUE_WITH_UNIT, DETAILS }

@Composable fun PropertyPanelContent(        // общий контент для sheet (телефон) и панели (планшет)
    state: PropertiesUiState, onEvent: (PropertiesEvent) -> Unit, modifier: Modifier = Modifier,
)

@Composable fun EmptyState(icon: ImageVector, title: String, body: String, action: (@Composable () -> Unit)?)
@Composable fun CoachTip(text: String, onUnderstood: () -> Unit, onNeverShow: () -> Unit)
```

**Лупа реализуется своим рисованием, не `Modifier.magnifier`**: системный модификатор
требует API 28 (minSdk 26) и не позволяет наложить перекрестие, snap-маркер и
координаты. Мы уже держим страницу как `ImageBitmap` ✔ (`WorkspaceScreen.kt:574`),
поэтому лупа — это `Canvas` с `drawImage(srcOffset, srcSize, dstSize)` ×2.5 плюс оверлеи.

---

## 10. Политика сообщений

| Тип | Компонент | Пример |
| --- | --- | --- |
| Информация, обратимо | `Snackbar` (4 s, с действием «Отменить») | «Измерение удалено» |
| Требует решения | `AlertDialog` | «Применить калибровку страницы 1 к странице 2?» |
| Риск потери данных | `AlertDialog`, деструктивная кнопка `error` | «Выйти без сохранения черновика?» |
| Ошибка конкретного объекта | inline-подсветка на канве + плашка у объекта | «Контур пересекает сам себя» |
| Постоянное состояние | `IndicatorChip` `ERROR` | «Не откалибровано» |

Текст ошибки = «что случилось» + «как исправить», без stack trace (§19 требований).
Типизированные ошибки (`MeasurementError`, `DocumentError` ✔) маппятся в ресурсы строк
единой функцией в feature-модуле; сейчас маппинг захардкожен строками в
`WorkspaceViewModel.kt:243` ~ и не локализуется — переносится в `stringResource`.

---

## 11. Доступность (проверочный список)

* Все интерактивные элементы ≥ 48 dp (`Modifier.minimumInteractiveComponentSize()`).
* `contentDescription` у каждой иконки; у канвы — описание + количество объектов.
* Активный инструмент объявляется TalkBack: `semantics { stateDescription = "Выбран" }`
  плюс `liveRegion = Polite` на индикаторе результата.
* Цвет никогда не единственный носитель состояния (см. `ToolButton`, `IndicatorChip`).
* `fontScale` до 2.0 не ломает панели (§5).
* Portrait/landscape и телефон/планшет — §2 карты экранов.
* Аппаратная клавиатура: `Modifier.onPreviewKeyEvent` на корне рабочей области,
  шорткаты `V H D P A C N Esc Enter Delete +/- F`, `Ctrl+Z`, `Ctrl+Shift+Z`.
* Порядок фокуса: верхняя панель → индикаторы → канва → панель инструментов →
  панель подтверждения. Задаётся `Modifier.focusGroup()` + `focusProperties`.
* Канва как единый focusable-узел с кастомными действиями `CustomAccessibilityAction`
  («Следующее измерение», «Показать значение», «Удалить»), чтобы TalkBack мог работать
  с объектами без точного касания.

---

## 12. Реестр тестовых тегов

Текущие UI-тесты цепляются к локализованным строкам (`onNodeWithText("Snap: on")`,
`PlanRulerUiTest.kt:151`) ~ — при редизайне и переводе они ломаются. Вводится
стабильный реестр:

```kotlin
object PlanRulerTestTags {
    const val ProjectsList = "pr:projects:list"
    const val ProjectCard = "pr:projects:card"          // + ":$projectId"
    const val ProjectsFab = "pr:projects:fab"
    const val WorkspaceCanvas = "pr:workspace:canvas"
    const val ToolButton = "pr:tool"                    // + ":$MeasurementType"
    const val IndicatorScale = "pr:indicator:scale"
    const val IndicatorUnits = "pr:indicator:units"
    const val IndicatorSnap = "pr:indicator:snap"
    const val IndicatorPage = "pr:indicator:page"
    const val ConfirmDraft = "pr:draft:confirm"
    const val CancelDraft = "pr:draft:cancel"
    const val Undo = "pr:action:undo"
    const val Redo = "pr:action:redo"
    const val SaveStatus = "pr:status:save"
    const val PropertiesSheet = "pr:properties:sheet"
    const val CalibrationSheet = "pr:calibration:sheet"
    const val ScheduleRow = "pr:schedule:row"           // + ":$measurementId"
    const val ExportStep = "pr:export:step"             // + ":1..4"
}
```

Правило: тег — для навигации теста, `contentDescription` — для смысла и TalkBack.
Проверки текста остаются только там, где текст и есть предмет проверки
(значения измерений, сообщения об ошибках).

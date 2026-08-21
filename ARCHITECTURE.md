# Architecture

## Модули и зависимости

```text
app (composition root)
 ├─ feature:projects ──> core:model, core:project-api
 ├─ feature:workspace ─> model, engine-api, document-api, project-api, export-api
 ├─ feature:settings ──> core:model
 ├─ feature:pipe-calculator ─> core:pipe-calculator, core:fabrication-3d
 ├─ feature:crm ────────> core:crm-api
 ├─ core:engine-default ──> engine-api, model
 ├─ core:document-android ─> document-api, model
 ├─ core:project-local ────> project-api, model
 ├─ core:export-android ───> export-api, model
 ├─ core:crm-local ────────> crm-api, Room, Android Keystore
 └─ core:backup ───────────> project-api, crm-api
```

`core:fabrication-3d` is a renderer-neutral, pure Kotlin fabrication kernel.
It owns double-precision vectors, quaternions, coordinate frames, typed ports,
the versioned part/connection graph, port attachment and procedural triangle
meshes. The same graph is edited by immutable commands with undo/redo history;
the manual chain and the parallel-terminal spatial solver therefore cannot use
a presentation-only geometry model. It depends on `core:pipe-calculator` only
for the catalog/profile adapter and conversion of the verified two-elbow result
into a seven-part 3D graph. The Compose projection is an Android presentation
adapter, so a later GPU renderer or GLB exporter can consume the same assembly
and mesh.

Feature-модули не импортируют implementations. `scripts/check_architecture.ps1` запрещает такие зависимости и Android-imports в `core:model`/`core:engine-api`.

`Measurement` хранит документные координаты, стиль и свойства. Значения вычисляются по текущей `Calibration`; повторная калибровка не мигрирует геометрию. `ViewportTransform` переводит screen ↔ document и хранит центр в document coordinates.

Compose вызывает только `MeasurementEngineApi` и отображает `EngineState`. Реализации выбирает `AppGraph`.

`AndroidDocumentGateway` работает на `Dispatchers.IO`; descriptor, renderer, pages и streams закрываются через `use`. Bitmap страницы ограничен 4096 px, LRU-cache — 16 MiB.

Тот же класс реализует `TileDocumentGateway`: при zoom выше плотности страничного рендера видимый прямоугольник перерисовывается тайлами 512 pt (кэш 24 MiB, край тайла ≤ 2048 px). PDF рендерится через матрицу `PdfRenderer`, изображения — через `BitmapRegionDecoder`, поэтому с диска читаются только нужные пиксели. Прямоугольник тайла приходит в ориентированных координатах и переводится в сырые через `ImageOrientation.toRawRegion` — чистую функцию в `core:document-api` с unit-тестами на все восемь EXIF-ориентаций.

Для persistence выбран versioned JSON: агрегат невелик, индексируемые запросы не нужны. Запись идёт во временный файл, проверяется десериализацией, затем заменяет target atomic move; предыдущая версия остаётся backup.

`ExportGateway` пишет CSV, versioned JSON и новый annotated PDF. Исходник не изменяется.

`HeatDesign` — чистый Kotlin-слой предварительных расчётов. Каждый результат
содержит статус, метод, шаги формул и предупреждения. Нормативные профили SIA/DIN
хранятся отдельно и не запускаются, пока не пройдут нормативный acceptance gate.

CRM разделяет профили в Room. PIN проверяется через PBKDF2, а содержимое полей
клиентов и объектов шифруется AES-GCM ключом Android Keystore. Переносимый бэкап
дополнительно шифруется AES-256-GCM ключом из пароля; серверных адаптеров и
разрешения `INTERNET` в приложении нет.
# Hardening additions

- `DefaultSnapEngine` is a domain implementation outside Compose.
- Measurement edits support preview/commit/cancel transactions so a drag maps to
  one undo record.
- Measurements are page-scoped through `Measurement.pageIndex`.
- Export selection is expressed by `ExportPageSelection` and implemented in the
  Android export adapter.
- EXIF orientation is resolved once during document open and applied during render.
- Viewport metadata is persisted in `PlanProject`; bitmaps are never stored in saved state.

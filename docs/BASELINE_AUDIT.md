# Baseline audit

Дата: 2026-07-25.

| Область | PlanRuler | PlanMeasure |
|---|---|---|
| Модули | app, core-engine, core-data | app, model, engine-api, engine-geometry, document-api/android, workspace |
| Engine | calibration, distance/area/angle, undo/redo | простая geometry implementation |
| Documents | отсутствуют; Canvas содержит TODO | реальный PdfRenderer и image decode |
| Persistence | файловый PageSnapshot | отсутствует |
| UI | Compose-каркас без документа | Compose workspace |
| Tests | 6 JVM engine tests | geometry JVM tests |

Исходные папки не изменялись. Обе baseline-команды `gradle test` остановились до компиляции: `Your project path contains non-ASCII characters`. В архивах отсутствовал Gradle Wrapper.

Заявлено, но отсутствовало: project catalog, полный import в PlanRuler, annotations/counter, point editing, snapping, export, atomic persistence, calibration workflow, memory cache и release hardening.

Риски: screen/document coordinates были смешаны в UI; app зависел от implementations; donor возвращал полный `IntArray`; image decode не учитывал EXIF; старый Canvas содержал TODO вместо `drawImage`.

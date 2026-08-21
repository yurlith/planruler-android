# Merge plan

| Исходная часть | Решение | Итог |
|---|---|---|
| PlanRuler geometry/calibration | оставить и расширить | model + engine-api/default |
| PlanRuler undo/redo | переписать bounded states | engine-default |
| PlanRuler repository | переписать | project-api/local, atomic + backup |
| PlanRuler ViewModel/Canvas | переписать | feature:workspace |
| PlanMeasure DocumentGateway | перенести контракт | document-api |
| PlanMeasure PdfRenderer/decode | перенести и harden | document-android |
| Donor engine/UI | не переносить | заменены основным engine |

Порядок: modules → model/engine → documents → project vertical → UI → tools → export → hardening/tests/docs.

Критерии: запрещённые зависимости отсутствуют; JVM tests, lint, debug/release проходят; device-dependent проверки отделены.

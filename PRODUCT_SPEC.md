# Product specification

PlanRuler предназначен для монтажников отопления, сантехников, электриков, HVAC, отделочников и сметчиков. Работа локальна и не требует аккаунта.

P0: импортировать PDF/изображение → выбрать страницу → откалибровать → поставить измерения → сохранить → повторно открыть → экспортировать.

Поддерживаются Heating, Plumbing, Electrical, HVAC, Painting, Flooring и General, а также материал, диаметр, размер, количество, запас и комментарий в модели takeoff.

Точность engine для 72 PDF points при 1:50 проверяется с результатом 1270 мм и допуском меньше 0,01 мм. Это математическая точность; точность исходного скана и касания пользователя не гарантируется.
# Hardening scope implemented

- JPEG EXIF orientation.
- Project rename/duplicate/delete confirmation.
- Editable multiline annotations.
- Current/all/range annotated PDF export.
- Page-scoped measurement persistence.
- Adaptive launcher/splash branding.

The complete properties editor and pointer-driven edit/snapping workspace remain
required before Release Candidate status.

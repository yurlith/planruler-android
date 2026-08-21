# Export Validation

Date: 2026-07-25

## PDF

Validated by Android instrumentation on API 26 and API 35 using `PdfRenderer`.

- Source: runtime-created two-page 100 × 120 point PDF.
- Export all pages: 2 pages.
- Export range `2..2`: 1 page.
- Output page sizes: 100 × 120 on both pages.
- Page-specific red overlays detected after independent re-render.
- Source bytes compared before and after export: unchanged.
- Output is staged in app cache before copying to the SAF target.

## CSV

- Written explicitly as UTF-8.
- Stable US-locale decimal formatting.
- Header checked.
- Commas, quotes, Unicode, and a newline inside comments checked.
- Page numbers are exported per measurement.

## JSON

- `schemaVersion` present through encoded `PlanProject`.
- UTF-8 Unicode round-trip checked.
- Decoded independently in the instrumentation test.
- Unknown fields remain supported by repository `ignoreUnknownKeys`.

External desktop/mobile viewer interoperability is **NOT TESTED**.

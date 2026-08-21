# RC Hardening Report

## Pass 2 — 2026-08-10

An audit re-ran every automated gate on the current tree and found one defect
that the previous pass had not caught: exports were written with the plain `w`
mode, which does not truncate. Exporting over an existing longer file therefore
kept the old tail and produced unreadable JSON, CSV or PDF. The July suite only
passed because the emulator's cache was empty on first run. Fixed by writing
with `wt`; an explicit overwrite test now covers all three formats.

Also closed in this pass:

- annotated PDF gained a stamp with project name, export timestamp, units,
  calibration and a per-category legend, and now draws each measurement in its
  own colour instead of a fixed red;
- legend, scale information and the CSV delimiter reach `ExportRequest`; the
  export wizard and the settings screen share one persisted source of truth;
- default unit and default stroke width apply to new projects and measurements;
- the unverified-scale warning and per-segment polyline labels are wired;
- layers received a UI (visibility, lock, rename, delete, move measurement) and
  hidden or locked layers are excluded from drawing, hit testing and snapping;
- subcategory, diameter and size joined the properties editor;
- the renderer is tiled above the page render density, for PDF via a
  `PdfRenderer` matrix and for images via `BitmapRegionDecoder`.

Unchanged: the human field smoke, third-party viewer inspection, production
signing and Play pre-launch remain open, so the status stays **PROTOTYPE**.

## Pass 1 — 2026-07-25

Date: 2026-07-25

## Status

**PROTOTYPE — substantially hardened, final RC verification incomplete**

The requested UI implementation, language selection, real SAF workflow,
process-death recovery and measured renderer/engine stress work are implemented.
All currently implemented automated tests pass on API 26 and API 35.

All automatically verifiable RC gates are now closed. An honest RC label is
withheld only because the supplied definition of done also requires a human
field/manual acceptance pass. Production signing and Play pre-launch remain
production gates and are deliberately not simulated.

## Completed

- English/Russian language switch, persisted across Activity recreation and
  cold process restart.
- Measurement selection, handles, vertex/object drag, insert/remove vertex,
  duplicate, delete and undo/redo transaction boundaries.
- Visible snap state, vertex/horizontal/vertical/segment snapping and guides.
- Properties editor for label, material, comment, quantity, line width, label
  visibility and display unit.
- Annotation input/editing without hard-coded text.
- Persisted project/document identifiers and compact workspace state; bitmaps
  are not stored in Bundle or preferences.
- Real system OpenDocument/CreateDocument E2E on API 26 and API 35.
- Separate two-page PDF, rotated-JPEG, corrupt-PDF and PNG Compose journeys.
- PDF calibration/distance/undo/redo/save/reopen/page navigation/PDF+CSV export.
- Independent host export validation: Poppler PDF, CSV parser and JSON parser.
- Fixed format-specific SAF MIME contracts; PDF can no longer receive `.json`.
- Streaming `%PDF-`/`%%EOF` preflight protects API 26 `PdfRenderer` state.
- Modern Material 3 visual system, high-contrast canvas, tool accent colors,
  tonal/primary buttons, animated empty/content transitions and context panels.
- Cold restart after `am force-stop` restores the measurement canvas on both
  APIs.
- Stress rendering of A4, A3, A0-like PDFs and a 4096×3072 PNG without OOM.
- 2,000-measurement engine/snap stress test.
- Debug and minified unsigned release builds.

## Verification summary

- API 26: 17 instrumented tests PASS.
- API 35: 17 instrumented tests PASS.
- Unit tests: PASS.
- Android lint: PASS, 0 errors, 43 explained warnings.
- Architecture dependency check: PASS.
- Debug APK and minified unsigned release APK: PASS.

## Windows path workaround

- Original path: `C:\path\with-non-ascii\planruler-android`
- Mapping used: `R:\`
- Command: `subst R: "C:\path\with-non-ascii\planruler-android"`
- Reason: Gradle wrapper path resolution fails for this non-ASCII path.
- Removal: `subst R: /d`

# Smoke Test

Date: 2026-07-25

| Check | Status | Evidence |
|---|---|---|
| Application launches | PASS | Compose test on API 26/35 |
| Project list and language survive recreation | PASS | Compose recreation tests |
| Import through real system SAF | PASS | OpenDocument E2E on API 26/35 |
| Render imported PNG | PASS | Canvas semantics after SAF return |
| Create/edit annotation | PASS | Compose E2E |
| Select object and open properties | PASS | Compose E2E |
| Pointer drag, snapping UI and undo availability | PASS | Compose E2E |
| Save/reopen after real process stop | PASS | Repository restore + cold UI dump |
| PDF/PNG/JPEG/EXIF/corrupt document gateway | PASS | Android instrumentation |
| PDF/CSV/JSON technical validation | PASS | Android export instrumentation |
| A4/A3/A0/large-PNG stress | PASS | API 26/35 instrumentation |
| Complete PDF measurement journey in Compose | PASS | Real SAF PDF E2E |
| Complete rotated-JPEG journey in Compose | PASS | Real SAF JPEG E2E + EXIF dimensions |
| Corrupt PDF journey | PASS | Typed error + safe return |
| Independent PDF/CSV/JSON interoperability | PASS | Poppler + host CSV/JSON parsers |
| Visual third-party GUI interoperability | NOT TESTED | Human acceptance required |
| Real-device professional field smoke | NOT TESTED | Emulator-only pass |

This table deliberately separates automated runtime smoke from the human field
test. The remaining NOT TESTED items prevent an honest RC declaration under the
project's strict definition of done.

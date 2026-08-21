# Device Test Matrix

Date: 2026-08-12

| AVD | API | Android | ABI | RAM | Tests | Result |
|---|---:|---|---|---:|---:|---|
| `planruler_api26` | 26 | 8.0.0 | x86_64 | 2048 MB | 20 | PASS |
| `planruler_api35` | 35 | 15 | x86_64 | 2048 MB | 20 | PASS |

Per device: 8 app Compose/UI/E2E/performance tests, 8 Android document tests
(including the two tile tests) and 4 export tests (including the overwrite
regression). Both AVDs reported `sys.boot_completed=1` before execution.

Command:

```powershell
$env:ANDROID_HOME='C:\Android\Sdk'
R:\gradlew.bat connectedDebugAndroidTest
```

On API 26 the app suite needed a second attempt after the AVD was started with
`-wipe-data`; see the flakiness note in `docs/TEST_REPORT.md`.

Additional cold-process checks passed after a real SAF workflow and
`adb shell am force-stop com.planruler.app` (2026-07-25 run).

## Stage 1 beta check — 2026-08-12

`planruler_api35` completed the full 20-test suite after adding template,
repeat, exact-length and summary-export coverage. The signed minified QA APK
was then installed separately and launched successfully. A physical-device
field check remains required by `STAGE1_FIELD_ACCEPTANCE_PROTOCOL.md`.

## Stage 2 beta check — 2026-08-12

`planruler_api35` completed 24 tests: 11 app UI/E2E, 8 document and 5 export.
The new app route used the real system picker for a page revision, then tested
alignment, measurement carry-over, recreation and manual review. Stage 2 has not
been rerun on API 26; the existing API 26 regression baseline remains the stage 1
suite above. A physical-phone check remains required.

## Installer field-pack check — 2026-08-21

`planruler_api35` completed the targeted installer handoff journey: project-owned job,
sunlight/glove/keep-awake controls, persisted checked-by audit, real DocumentsUI PNG
and CSV output, and the complete ZIP field pack. Result: 1/1 PASS. The 252 496-byte
ZIP was pulled from the AVD and independently opened on the host; it contained the
two-page PDF, PNG, CSV and verification passport. Both PDF pages were rendered for
visual QA after simplifying crowded projection labels. This remains emulator evidence,
not the human real-device field acceptance required by the release checklist.

## Linked installer part check — 2026-08-21

On `planruler_api35`, the targeted journey selected pipe P1 from the installer-facing
cut card, verified its cut instruction and material summary, switched from Drawing to
3D model, and found P1 still selected. Result: 1/1 PASS. The complete PNG/CSV/ZIP field
handoff regression was then rerun through DocumentsUI and also passed 1/1. This is AVD
evidence; a physical-device installer acceptance remains required.

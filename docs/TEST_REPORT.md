# Test report

## Run of 2026-08-15 — manual five-elbow editor and spatial solver

Version: `1.5.0-alpha1-fabrication-workshop` (`versionCode 10500`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App journeys, including manual add/undo/redo and X/Y/Z solve | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, depth decode, EXIF and tiles | 11 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 31 | PASS |
| JVM/unit executions | 143 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 733 tasks | PASS |
| `scripts/check_architecture.ps1` | — | PASS |
| Android lint | — | PASS, 0 errors |

Eight new JVM tests cover arbitrary bend-plane roll, command history, the hard
five-elbow limit, flange closure, straight routes, impossible envelopes and
spatial Y/Z closure for every installation DN, translated coordinate frames and
typed rejection of non-parallel terminals. The Android journey adds a pipe
and elbow through a free port, verifies undo/redo, then solves and renders a
route to `(1600, 500, 300)` with zero reported closure error.

The first complete device attempt encountered a transient DocumentsUI failure
while waiting for a generated PNG fixture. That unchanged test passed in an
isolated retry, and a second complete 31-test run passed. Final debug APK
SHA-256: `4B7109606276923494CB5216EF900B403F3AE2ADD5F6B9556749F0BF97855F62`.

## Run of 2026-08-15 — parametric fabrication 3D vertical slice

Version: `1.5.0-alpha1-fabrication-workshop` (`versionCode 10500`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App Compose/SAF journeys, including interactive fabrication 3D | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, depth decode, EXIF and tiles | 11 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 31 | PASS |
| JVM/unit executions | 135 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 733 tasks | PASS |
| `scripts/check_architecture.ps1` | — | PASS |
| Android lint | — | PASS, 0 errors |

Eight new JVM tests exercise vector/quaternion/transform invariants, frame
orthogonality, port attachment, three-port tee readiness, exact conversion of
the default flanged offset, 56 DN/angle closure combinations and finite hollow
procedural meshes. The installation Android journey displays the 3D viewport,
performs an orbit gesture and verifies seven parts, six weld connections and
two free flange ports before checking the existing 2D drawing and cut list.

The result was visually inspected on the API 35 emulator in isometric view:
pipe bores, exact-radius elbows, tapered Type 11 flange profiles, bolt-hole
rings, weld rings, selected P2 highlighting, X/H dimensions and axis/grid
orientation are visible. The implementation remains offline and introduces no
Android network permission. Final debug APK SHA-256:
`FB8002E3BCA2282D1DEE79D88396C608AF3E8D5E93ECED140A8489EE6D0171D9`.

## Run of 2026-08-15 — fabrication workshop alpha

Version: `1.5.0-alpha1-fabrication-workshop` (`versionCode 10500`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App Compose/SAF journeys, including fabrication workshop | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, depth decode, EXIF and tiles | 11 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 31 | PASS |
| JVM/unit executions | 127 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 729 tasks | PASS |
| Translation coverage RU/EN/DE/PL/FR/IT | all static keys | PASS |
| `scripts/check_architecture.ps1` | — | PASS |

New engine coverage verifies a fixed DN 50 / PN 16 assembly, conservation of
stock length and kerf, impossible-envelope rejection, all 70 Type 11 flange
rows and dimensional closure for 14 DN values at 30°, 45°, 60° and 90°. The
Android journey regenerates the default assembly, checks the 640.1 mm diagonal
cut and verifies both the flange/bolt drawing and the stock cutting chart.

The complete UI was also visually inspected on the API 35 emulator. Lint: 0
errors and 60 previously classified dependency/toolchain warnings. Debug APK
SHA-256:
`EA672E18906DC7CF30F292E54AF2F57E4E85C9498FC6528A0FECB336224239BB`.

## Run of 2026-08-14 — format-independent depth decoder alpha

Version: `1.4.1-alpha2-depth-decoder` (`versionCode 10401`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App Compose/SAF journeys, including JPEG photo inspector | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, real GDepth decode, EXIF and tiles | 11 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 31 | PASS |
| JVM/unit executions | 122 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 748 tasks | PASS |
| Translation coverage RU/EN/DE/PL/FR/IT | all static keys | PASS |
| `scripts/check_architecture.ps1` | — | PASS |

New coverage verifies normative RangeLinear/RangeInverse conversion, `m/cm/mm`
normalisation, inverse-metre disparity, confidence-gated sampling, GDepth
base64, malformed-base64 rejection, Dynamic Depth concatenated directory
slicing, non-metric fallback, out-of-order JPEG extended-XMP chunks and exact
16-bit grayscale PNG values.
The Android fixture uses a primary JPEG deliberately named `.bin`, embeds a
real PNG depth payload in XMP and verifies the resulting 1–5 metre map. This
proves that file extension, provider MIME and depth codec are not domain-layer
dependencies.

Lint: 0 errors and 60 previously classified dependency/toolchain warnings.
Debug APK SHA-256:
`8686148348DA096A2BCECFA401B38FF86DA16C494D6A09B3558B805F63B62F9A`.

## Run of 2026-08-14 — photo data inspector alpha

Version: `1.4.0-alpha1-photo-inspector` (`versionCode 10400`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App Compose/SAF journeys, including JPEG photo inspector | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, EXIF/profile, corrupt PDF and tiles | 10 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 30 | PASS |
| JVM/unit executions | 105 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 729 tasks | PASS |
| Translation coverage RU/EN/DE/PL/FR/IT | all static keys | PASS |
| `scripts/check_architecture.ps1` | — | PASS |

New automated coverage comprises 7 domain tests for optical estimates,
readiness and median/MAD; 4 bounded container-scanner cases, executed for debug
and release; an Android EXIF round-trip/profile test; and the existing rotated
JPEG journey extended through the photo-data inspector and its no-auto-scale
warning. The complete connected set has 30/30 passing.

Lint remains at 0 errors and 60 previously classified warnings. Debug APK
SHA-256:
`E69B0B871219E9C7349801FE4EA7DCDFA69D7CFD3BC64A13E6730E360DD0223E`.

## Run of 2026-08-14 — local HVAC/CRM beta

Version: `1.3.0-beta1-local` (`versionCode 10300`). Device:
`planruler_api35` (API 35).

| Command / suite | Tests | Result |
|---|---:|---|
| App Compose, SAF, CRM and pipe-calculator journeys | 13 | PASS |
| Room CRM repository and profile isolation | 2 | PASS |
| Android document gateway, EXIF, corrupt PDF and tiles | 8 | PASS |
| PDF/CSV/JSON export | 5 | PASS |
| Total connected API 35 | 28 | PASS |
| JVM/unit executions | 90 | PASS, 0 failures |
| `check assembleDebug compileDebugAndroidTestKotlin` | 723 tasks | PASS |
| `scripts/check_architecture.ps1` | — | PASS |

The JVM total contains 87 unique test cases; the three localization cases run
for both debug and release variants. New coverage includes formula traces,
heated-volume semantics, separate linear bridges, water/glycol flow, critical
circuits, manifold-to-manifold UFH routing, locked SIA/DIN profiles, encrypted
backup corruption/wrong-password handling, project trash/restore/permanent
deletion and encrypted Room CRM isolation.

Build gates verified complete DE/PL/FR/IT translation dictionaries, no
`INTERNET` permission and disabled Android cloud backup. Lint has 0 errors and
60 warnings: 51 coordinated dependency notices, 6 Android Gradle Plugin update
notices and 3 SDK/backup metadata notices.

Debug APK SHA-256:
`9390E86EC7804E3FF046108129A1ACDB7A3809E3D25F73E1450EC9EC4E1FACBA`.

The first aggregate build found that `BuildConfig` generation was not enabled
after the backup began recording the application version. The configuration
was fixed, and both the complete build and the complete connected run then
passed.

## Run of 2026-08-12 — stage 2 beta

Version: `1.2.0-beta2-stage2` (`versionCode 10200`).

| Command / suite | API 35 | Result |
|---|---:|---|
| App Compose/UI/E2E/performance/revisions | 11 | PASS |
| Android document gateway, EXIF, corrupt PDF, stress, tiles | 8 | PASS |
| PDF/CSV/JSON export, totals and revision log | 5 | PASS |
| Total instrumented | 24 | PASS |
| JVM engine/snap/repository/model/localization | 50 | PASS |
| `test testDebugUnitTest lintDebug` | — | PASS, 0 errors |

New stage 2 coverage verifies complete Polish and German dictionaries and
language persistence, two- and three-point revision transforms, rejection of
degenerate control points, safe measurement carry-over and review status. The
real SAF app journey imports old/new images, aligns them, carries a measurement,
recreates the activity, confirms persistence and marks the copy as reviewed.
The export test independently verifies the revision log and JSON round-trip.

Lint reports 0 errors and 30 dependency/toolchain update warnings.

## Run of 2026-08-12 — stage 1 beta

Version: `1.1.0-beta1-stage1` (`versionCode 10100`).

| Command / suite | API 35 | Result |
|---|---:|---|
| App Compose/UI/E2E/performance | 8 | PASS |
| Android document gateway, EXIF, corrupt PDF, stress, tiles | 8 | PASS |
| PDF/CSV/JSON export and summary calculations | 4 | PASS |
| Total instrumented | 20 | PASS |
| JVM engine/snap/repository/model | 40 | PASS |
| `lintDebug assembleDebug assembleRelease` | — | PASS, 0 errors |

New stage 1 coverage verifies template properties on new measurements,
template updates without geometry loss, exact distance with free/H/V modes,
undo, quantity/waste totals, expanded CSV summary, selection of a starter
template and repeating its geometry inside the real PDF journey.

The first aggregate API 35 run exposed an existing timing race in the JPEG
test: it tried to press `Skip check` before the verification step was composed.
The test now waits for that state. The targeted rerun and the final complete
20-test run passed. Lint reports 0 errors and 47 dependency/toolchain warnings.

## Run of 2026-08-10

Branch: `hardening/release-candidate`. Same host and SDK as the July run.

| Command / suite | API 26 | API 35 | Result |
|---|---:|---:|---|
| `connectedDebugAndroidTest` — app Compose/UI/E2E/performance | 8 | 8 | PASS |
| Android document gateway, EXIF, corrupt PDF, stress, tiles | 8 | 8 | PASS |
| PDF/CSV/JSON export incl. overwrite of an existing file | 4 | 4 | PASS |
| Total instrumented | 20 | 20 | PASS |
| `test` — engine, snap, project repository, EXIF regions | 31 | 31 | PASS |
| `lint assembleDebug assembleRelease` | — | — | PASS, 0 errors |

New coverage in this run:

- exporting JSON, CSV and annotated PDF over an existing, longer file — the
  regression that the July suite missed because the device cache was empty;
- PDF tiles rendered at a density above the page render, and a tile outside the
  marked area proven not to contain it;
- tiles of a rotated JPEG compared against the corresponding halves of the full
  page render, so the EXIF region mapping is checked against the covered path;
- eight unit tests for `ImageOrientation.toRawRegion` across all EXIF values.

Lint: 0 errors, 29 explained warnings (dependency/toolchain notices).

Honest note on flakiness: on API 26 the first attempt of
`pdfJourneyCalibratesMeasuresRecreatesPagesAndExports` failed with
`CreateDocument Save action not found` on an emulator started with
`-wipe-data`. The system save dialog of a first-run DocumentsUI does not present
the save action where the UIAutomator helper looks for it. The same test passed
on the immediately following run on the same device and passes on API 35, so the
suite depends on the state of the system file picker and is not fully
first-run-proof. The numbers above are from the passing runs.

## Run of 2026-07-25

Date: 2026-07-25

Branch: `hardening/release-candidate`

Environment: Windows 10.0.26200, OpenJDK 21.0.10, Gradle 8.7,
Android SDK 26/35, build-tools 35.0.0.

| Command / suite | API 26 | API 35 | Result |
|---|---:|---:|---|
| `connectedDebugAndroidTest` — app Compose/UI/E2E/performance | 8 | 8 | PASS |
| Android document gateway, EXIF, corrupt PDF, renderer stress | 6 | 6 | PASS |
| PDF/CSV/JSON export | 3 | 3 | PASS |
| Total instrumented | 17 | 17 | PASS |
| `clean test lint assembleDebug assembleRelease` | — | — | PASS |
| `scripts/check_architecture.ps1` | — | — | PASS |

App instrumentation verifies:

- accessible projects screen and Activity recreation;
- English/Russian choice persisted across recreation;
- real system SAF OpenDocument PNG import and readable returned URI;
- two-page PDF calibration, distance, undo/redo, persistence, page navigation,
  PDF and CSV CreateDocument export;
- rotated JPEG EXIF orientation, calibration, measurement and reopen;
- corrupt PDF typed error and safe return to projects;
- workspace render, annotation creation, selection/properties, long-press drag,
  undo availability and real CreateDocument JSON export;
- 2,000-measurement evaluation and snapping stress.

Document instrumentation verifies two-page PDF, PNG, JPEG, all EXIF rotations,
horizontal mirroring, typed corrupt-PDF rejection and the renderer stress matrix.
Export instrumentation independently reopens PDF, parses CSV and round-trips
JSON.

Independent host validation:

- Poppler `pdfinfo`: PDF opened, 1 page, 612×792 pt;
- `ConvertFrom-Csv`: required headers and one measurement row;
- `ConvertFrom-Json`: valid project, `schemaVersion=2`.

True process-death approximation was performed separately:

| Device | Procedure | Cold start | Result |
|---|---|---:|---|
| `planruler_api26` | SAF flow → `am force-stop` → cold start → UIAutomator dump | 518 ms | canvas restored |
| `planruler_api35` | SAF flow → `am force-stop` → cold start → UIAutomator dump | 1039 ms | canvas restored |

Reports:

- `R:\app\build\reports\androidTests\connected\debug\`
- `R:\core\document-android\build\reports\androidTests\connected\debug\`
- `R:\core\export-android\build\reports\androidTests\connected\debug\`
- `R:\app\build\reports\lint-results-debug.html`

Lint result: 0 errors, 43 explained warnings. They are dependency/toolchain
update and compatibility/resource notices; upgrading the coordinated Android
toolchain is intentionally separated from this hardening pass.

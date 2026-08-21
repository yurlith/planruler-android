# Fabrication 3D engine v2: separate API, editable parameters, spatial routing

Date: 2026-08-16. Branch: `hardening/release-candidate`.
Plan: [FABRICATION_3D_ENGINE_V2_PLAN_2026-08-16.md](FABRICATION_3D_ENGINE_V2_PLAN_2026-08-16.md).

## Delivered

### Module split

`core/fabrication-3d` is gone. Three modules replace it and follow the convention the
rest of the codebase already uses:

| Module | Package | Contents |
|---|---|---|
| `core/fabrication3d-api` | `com.planruler.fabrication3d` | Value types, DTOs, quotas, typed results and the six engine interfaces |
| `core/fabrication3d-engine` | `com.planruler.fabrication3d.engine` | The single public class `DefaultFabrication3DEngine` plus internal implementations |
| `core/fabrication3d-catalog` | `com.planruler.fabrication3d.catalog` | The bridge from the verified 2D calculation to the engine API |

The engine no longer depends on `core:pipe-calculator`; the bridge owns that link, so the
engine can be tested and reused without the calculator. `AppGraph` resolves one
`Fabrication3DEngine` and hands the interface to the feature module, which reaches the
engine only through `Fabrication3DEngine`. `scripts/check_architecture.ps1` now fails the
build on `com.planruler.fabrication3d.engine.` inside `feature/**`, on `import android.`
inside either 3D module, and on any calculator reference from the engine.

### Errors instead of exceptions

`Fabrication3DResult<T>` is `Ok` or `Failure(Fabrication3DError)`. Errors carry structure
(`InvalidParameter(parameter, rule, actual, limit)`, `QuotaExceeded(quota, limit,
requested)`, `RouteNotFound(code, detail)`, `PortsIncompatible`, `AssemblyInvalid`,
`NothingToDo`, `Internal`), never prose. `Fabrication3DMessages` in the feature module
translates them into the six shipped languages, so a fitter no longer sees an internal
exception message in the interface.

### Editable parameters

The chain is stored as an ordered recipe (`ChainPlan3D` of `ChainStep3D`) and replayed by
the engine. Editing an element already welded into the model is therefore an ordinary list
edit; everything downstream is rebuilt by the same rules that placed it.

| Was | Now |
|---|---|
| `attach` only; a placed part could not change | `Append`, `InsertAt`, `Replace`, `RemoveAt`, `MoveStart`, `Clear` |
| Elbow limit hard-coded at 5 | `FabricationRules3D.maxElbows`, bounded by `EngineLimits3D.maxElbows` (24) |
| Elbow angle fixed to 15–90° | Configurable window inside (0°, 180°]; 11.25° and 180° now build |
| Angle set compiled in as 30/45/60/90 | `allowedElbowAnglesDeg`, editable |
| Elbow radius from the catalog only | `CATALOG`, `1D`, `1.5D`, `3D`, `5D` or a custom millimetre value |
| Wall, weld gap, minimum cut fixed | All three overridable and validated |
| Tessellation fixed at 24/24 | `MeshQuality3D.DRAFT/NORMAL/FINE` |
| Run always started at (0,0,0) along +X | `ChainStart3D` carries position, direction, up hint and terminal kind |
| Camera: orbit and zoom only | Orbit, pinch zoom and two-finger pan, with presets |

`AssemblyProfileOverrides3D` layers user values over the catalog; `ProfileCustomizer3D`
validates them and names the rule that failed.

### Three real dimensions

`RouteSolver3D` replaces the parallel-only solver:

| Topology | Condition | Elbows |
|---|---|---|
| `STRAIGHT` | Collinear terminals | 0 |
| `ROLLING_OFFSET` | Parallel terminals, lateral offset anywhere in space | 2 |
| `TURN` | Non-parallel terminals, target on the turn plane | 1 |
| `TURN_WITH_OFFSET` | Non-parallel terminals, target off the turn plane | 3 |

Closure uses the fact that, with every elbow angle and roll fixed, the end of a chain moves
**linearly** with the pipe cut lengths: the solver replays the candidate, measures the
residual and solves a 1–3 variable least-squares system (`LinearClosure3D`). That makes the
result exact and deterministic instead of iterative, and it keeps the previously shipped
cut lengths bit-for-bit (see the golden test below). Elbow rolls are measured on the
partially replayed chain, so a bend plane is expressed against the real incoming frame
rather than an assumed one.

Also new: `ReducerGeometry3D` and `CapGeometry3D` (the `REDUCER` and `CAP` enum entries
previously had no geometry and could not be built), centreline extraction, capsule-based
self-intersection detection, and axis-aligned obstacle checks with clearance.

### Safety

- `EngineLimits3D` caps parts, triangles, undo depth, elbows, route candidates and solver
  iterations. The tessellator estimates the triangle count **before** allocating, so an
  oversized scene returns `QuotaExceeded` instead of an out-of-memory kill.
- The undo history is bounded; it used to grow without limit.
- `ChainPlanSaver` persists only the recipe across configuration changes and process death.
  The graph and the mesh are always derived again by the engine, so nothing restored from a
  bundle can disagree with the engine's rules. Corrupt or truncated state restores as an
  empty chain rather than a half-built model.
- The engine performs no I/O and adds no permission; the manifest still declares none.
- The allowed elbow angle set is itself a quota: the solver evaluates one candidate route
  per angle, so both the profile customizer and the route request refuse a set larger than
  `maxRouteCandidates` instead of grinding through it.
- The catalog bridge converts a malformed 2D element list into `Internal` rather than
  letting an exception escape through a composable.

## Verification

| Check | Result |
|---|---|
| `:core:fabrication3d-api:test` + `:core:fabrication3d-engine:test` | 61 tests, all pass |
| `:feature:pipe-calculator:testDebugUnitTest` | 5 tests, all pass |
| `gradlew check` (all modules, lint included) | pass |
| `gradlew :app:assembleDebug` | pass |
| `scripts/check_architecture.ps1` | pass |
| `:app:connectedDebugAndroidTest` on `planruler_api35` | 25 tests, all pass |
| Key journeys on `planruler_api26` (minSdk) | 11 tests, all pass |

### The golden baseline

`Fabrication3DGoldenBaselineTest` was written against the old engine before any refactor and
pins fabrication output: the DN 50 reference route cuts to 1e-9 mm, the cut lengths for all
14 installation diameters to 1e-6 mm, the tessellation of the verified workshop assembly
(3 648 triangles, 14 polylines, 7 labels, exact bounds) and the terminal face of a fixed
manual command sequence. It passes unchanged against the new engine, so the rebuild did not
move a single cut length.

### Instrumentation coverage

`Assembly3DEditorJourneyTest` adds nine journeys: engine parameters reaching the model, a
refused parameter naming its rule, editing and removing an element already in the model, a
cut below the minimum being refused without losing the model, an elbow limit above five, a
non-parallel target closing and an impossible one being refused, handing a solved route to
the editor, orbit/zoom/pan across the presets, moving the start of the run away from the
origin, and the chain surviving an activity recreate.

## Known limitations

- On API 26 only the two pipe-calculator journey classes were run (11 tests); the document
  and CRM journeys were exercised on API 35 only.
- The `planruler_api26` AVD needs a cold boot (`-no-snapshot`); it stays `offline`
  indefinitely when started from its snapshot.
- The 3D card is one tall list item, so controls below the fold need the outer list to be
  scrolled. Tests drive those controls through their semantics click action, the same
  workaround the existing journey test already used for undo and redo.
- A 180° turn between anti-parallel terminals is refused: the turn construction needs a
  defined bend plane. Multi-elbow U-turns are still built by hand.
- Route solving and tessellation still run on the composition thread. They are quota bound
  and fast at the shipped sizes, but moving them onto `Dispatchers.Default` behind a
  ViewModel remains open.
- Tees are supported by the graph and the tessellator but the chain editor is linear, so a
  branch cannot yet be built from the phone.

## Build note

This repository sits under a Cyrillic path while the system ANSI code page is 1252, so any
JVM child process launched with that path in its arguments fails to resolve its class path.
Command-line Gradle therefore needs an ASCII path: map one with `subst` and build from
there. Android Studio is unaffected.

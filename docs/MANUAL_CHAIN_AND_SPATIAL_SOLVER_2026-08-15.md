# Manual chain editor and spatial solver

Date: 2026-08-15.

## Delivered

The mobile fabrication workshop now has three views backed by the same
`ParametricAssembly3D` graph:

- the verified two-elbow calculation;
- a manual chain editor limited to five elbows;
- an automatic parallel-terminal X/Y/Z route solver.

The manual editor starts at the weld port of F1. A user selects any free
butt-weld port, enters a physical pipe cut or selects a signed 30°, 45°, 60° or
90° elbow, and specifies roll around the incoming pipe axis. Every successful
operation stores an immutable snapshot. Undo and redo restore both the graph
and selected port; a new operation clears redo history. The chain may be closed
with a Type 11 weld-neck flange.

## Spatial solver

`SpatialAssemblySolver3D` accepts start and target flange-face coordinates,
terminal directions, minimum straight length, catalog dimensions and permitted
elbow angles. This stage solves parallel terminal axes. It decomposes the target
into axial and lateral components, derives the Y/Z bend-plane roll, evaluates
all permitted angles and constructs the winning route through the same manual
commands:

`F1 — P1 — E1 — P2 — E2 — P3 — F2`

For a zero lateral offset it emits the simpler `F1 — P1 — F2` route. Requests
that are behind the start, non-parallel, too small for the selected DN or unable
to satisfy the minimum cut return typed diagnostic failures and no partial
assembly. Successful results include the exact pipe cuts, selected angle, roll,
terminal direction error and positional closure error.

## Mobile interaction

The Installation screen exposes verified, manual and solver modes in one card.
Manual mode provides free-port selection, pipe length, bend sign, elbow angle,
roll, finish flange, undo, redo and reset. Solver mode provides X/Y/Z, minimum
straight length, automatic or fixed angle selection, diagnostics and a cut list.
The active result is immediately rendered in the existing orbit/zoom/picking
viewport with X/Y/Z dimensions.

All new copy is available in Polish, English, German, French, Italian and
Russian. The feature is offline and does not add network permission.

## Verification

- 143 JVM/unit tests pass with zero failures.
- 31 connected Android tests pass on API 35.
- Spatial closure is tested for all 14 installation DN values.
- `check`, `assembleDebug`, Android lint and architecture checks pass.
- The generated route was visually inspected at `(1600, 500, 300)` mm.

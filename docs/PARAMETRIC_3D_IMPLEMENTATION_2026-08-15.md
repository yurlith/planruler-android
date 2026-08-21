# Parametric fabrication 3D — first vertical slice

Date: 2026-08-15.

## Delivered

The mobile Installation workshop now converts the existing verified flanged
offset into a renderer-neutral `ParametricAssembly3D`:

`F1 — P1 — E1 — P2 — E2 — P3 — F2`

The graph contains seven part instances, six typed butt-weld connections with
explicit axial gaps, and two free flange-face ports. The existing 2D calculator
remains the only owner of fabrication lengths, so the 2D drawing, cut list and
3D model cannot diverge through duplicated formulas.

The new `core:fabrication-3d` JVM module provides:

- double-precision `Vec3`, quaternion, transform and right-handed frame math;
- any-number-of-ports part definitions and a versioned assembly graph;
- straight pipe, signed-angle elbow, Type 11 weld-neck flange and equal-tee
  definitions;
- compatibility and closure validation for DN, OD, connection type, direction
  and axial weld gap;
- a generic `attach` operation used by the future manual five-elbow chain;
- renderer-neutral procedural hollow meshes, exact torus-segment elbows,
  tapered flange hubs, bores, bolt-hole rings and weld rings.

The mobile 3D card provides touch orbit, pinch zoom, part picking, perspective
and orthographic projection, isometric/front/top/right presets, selected-part
properties, X/H dimensions, part labels, welds, an engineering grid and an
axis triad. UI copy is present in Polish, English, German, French, Italian and
Russian. The feature remains fully offline and adds no network permission.

## Compatibility gates

- The adapter validates every generated connection before returning a model.
- All 14 DN values close at 30°, 45°, 60° and 90°.
- P1/P2/P3 geometry lengths are copied from the existing physical saw cuts.
- F1 and F2 sealing faces close at the requested `(0, 0, 0)` and `(X, H, 0)`.
- The generic graph already accepts a three-port tee and arbitrary future part
  count without a schema redesign.

## Automated coverage

The new JVM suite covers quaternion/transform invariants, orthonormal frames,
port attachment, three-port tees, default assembly closure, every DN/angle
adapter combination, finite hollow meshes, bounds, labels and six weld rings.
The Android installation journey opens the 3D card, exercises orbit input and
verifies the part/connection/free-port summary before continuing through the
existing 2D cut-list checks.

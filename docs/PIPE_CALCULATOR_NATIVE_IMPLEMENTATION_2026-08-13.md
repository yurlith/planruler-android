# Native pipe calculator implementation

Date: 2026-08-13  
Target: Android `PlanRulerUnified` (`com.planruler.app`)

## Delivered in the native application

- A new **Calculator** destination in the existing project navigation.
- A platform-independent Kotlin calculation engine in `:core:pipe-calculator`.
- A Compose UI in `:feature:pipe-calculator`.
- Water, DOWFROST propylene glycol and manually specified fluid circuit calculations:
  - thermal mass flow;
  - volume flow and velocity;
  - Reynolds number and flow regime;
  - Darcy-Weisbach linear loss;
  - Colebrook turbulent friction factor;
  - local resistance loss;
  - internal pipe volume.
- Installation geometry:
  - straight spool cut length;
  - two-elbow target-height calculation with curved fitting contours, both weld faces and all four assembly endpoints marked;
  - separate `L` center-to-center, `F` weld-face-to-weld-face and `C` physical pipe-cut dimensions;
  - a prominent `CUT PIPE: C = ... mm` instruction between cut marks 2–3 after both elbow take-outs and weld gaps;
  - matching catalog DN pipe, insert mass and 3/6/12 m stock cutting plan;
  - 3D true length;
  - theoretical steel-pipe mass.
- An in-app DN/PN catalog with selectable manufacturer rows:
  - 14 selected pipe sizes, DN 15–300;
  - 14 seamless 45° 3D elbows, DN 15–300;
  - 11 equal tees, DN 15–150;
  - 13 eccentric reducers, DN 20×15–300×250;
  - 80 flange connecting-dimension rows, DN 15–400 and PN 6/10/16/25/40.
- Native dimensioned vector drawings in the catalog:
  - a selected 45° elbow is rendered as an actual bent pipe with proportional outside diameter and wall, inner/outer arcs, weld ends, an animated centerline and `alpha`, `R`, `A`, `D` and `s` dimensions;
  - a selected DN/PN flange is rendered in front view with proportional outside diameter `D`, bolt circle `k`, exact hole count and proportional hole diameter `d2`, plus a separate side-profile schematic and animated inspection marker;
  - selectors redraw the diagrams from the chosen catalog row instead of showing a generic icon.
- Preliminary closed-heating-system expansion-vessel sizing.
- A guarded Swiss gas destination that produces no sizing result until SVGW licensing and expert validation are complete.
- Russian and English UI copy; Polish and German use the application's established English fallback until dedicated translations are added.

## Data and licensing boundary

The application does not reproduce the paid text or the complete dimensional tables of an EN/DIN/SIA/SVGW standard. It bundles a deliberately limited set of openly published manufacturer product rows, each with organisation, document, edition/date, validation status and source URL. The UI calls these manufacturer catalog data and still retains manual entry.

The open catalog layer now covers selected rows associated with:

- pipe dimensions and mass: a Železiarne Podbrezová EN 10220 manufacturer series;
- stainless butt-welding elbows, equal tees and eccentric reducers: HECO EN 10253-4/A product sheets;
- steel flange connecting dimensions: SAMSON/Pfeiffer DIN EN 1092-1 table;
- DOWFROST density, heat capacity and dynamic viscosity: Dow technical data at 30/40/50 vol% and 10/40/65/90/120 °C.

The licensed/normative verification layer still remains for:

- the complete selectable scope of EN 10220, EN 10253 and EN 1092-1;
- Swiss heating verification: EN 12828 and SIA 384/1;
- Swiss gas installations: SVGW G1:2026.

Before a normative dataset is distributed with the application, each table needs a machine-readable distribution licence, a recorded edition, provenance metadata and engineering acceptance tests. Open manufacturer rows must not be described as the full standard and must be checked against the actual ordered product.

## Calculation trace and safety

Every hydraulic and expansion result includes the engine version, formula identifier, source identifiers and warnings. Advisory water checkpoints and manual glycol properties never receive a `VERIFIED` status automatically. Expansion-vessel output is explicitly preliminary. Gas calculations are unavailable rather than approximated from a different country's rules.

## Automated verification

Core tests cover exact examples, invalid input, all flow regimes, interpolation bounds, published DOWFROST checkpoints, catalog cardinality/uniqueness/provenance, PN dimension checkpoints, pressure guards, geometry and 250 deterministic property-style spool cases.

The Android journey test starts `MainActivity`, opens the Calculator destination, calculates a catalog-pipe DOWFROST circuit, opens the DN/PN tables, verifies the elbow and flange technical previews, calculates an insert/cutting plan and an expansion vessel, and checks the SVGW guard screen.

Verified commands:

```text
gradlew :core:pipe-calculator:test
gradlew test testDebugUnitTest
gradlew :app:assembleDebug :app:compileDebugAndroidTestKotlin
gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.planruler.app.PipeCalculatorJourneyTest
```

On this Windows workspace, Gradle test workers require the repository to be addressed through a temporary ASCII drive mapping (for example `P:`) because the wrapper cannot pass the Cyrillic parent path reliably to child JVMs.

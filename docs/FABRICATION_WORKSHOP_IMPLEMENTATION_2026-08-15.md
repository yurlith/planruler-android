# Fabrication workshop — implementation record

Date: 2026-08-15. Version: `1.5.0-alpha1-fabrication-workshop`.

## Delivered scope

The mobile Installation tab is now a live fabrication workspace for a complete
parallel-axis offset assembly:

`flange F1 — pipe P1 — elbow E1 — pipe P2 — elbow E2 — pipe P3 — flange F2`.

Changing DN, PN, angle, axis offset `H`, flange face-to-face dimension `X`, weld
gap, assembly quantity, stock length or saw kerf immediately regenerates:

- the scaled side contour with real pipe OD/wall, two elbows and two Type 11
  welding-neck flange profiles;
- physical saw lengths P1, P2 and P3, with square-cut angles;
- center travel, horizontal advance, fitting take-out and weld-face distance;
- six explicit butt-weld gaps per assembly;
- front flange view with `D`, `k`, hole count and hole diameter;
- pipe mass, bolt count and a first-fit stock-bar cutting chart;
- a dimensionally closed element graph suitable for later export to PDF/DXF.

## Calculation model

For elbow angle `α`, catalog centerline radius `R`, weld gap `g`, offset `H`,
flange face-to-weld height `h` and overall flange-face distance `X`:

```text
A  = R × tan(α / 2)
L  = H / sin(α)
Xa = H / tan(α)
F  = L − 2A
P2 = F − 2g
P1 + P3 = X − 2h − 4g − 2A − Xa
```

P1 and P3 are equal by default. All six weld gaps are separate geometric
elements. The engine rejects non-positive cuts, envelopes that cannot contain
the selected fittings, unsupported catalog pairs and cuts longer than stock.
The final generated flange face is checked against requested `(X, H)` to a
`1e-6 mm` closure tolerance.

For 90° the horizontal center advance is explicitly zero. Angles other than the
published 45° elbow use the constant-radius geometric relation above and carry
a warning to verify the actual manufactured or trimmed fitting.

## Catalog basis

- Pipe OD and wall series: Železiarne Podbrezová 2025 open manufacturer
  handbook, selected EN 10220-compatible series.
- Elbow radii: heco NB45 open manufacturer sheet, EN 10253-4/A 3D 45°.
- Flange connection dimensions: SAMSON/Pfeiffer AB 02 EN open connecting table.
- Type 11 thickness `b` and face-to-weld height `h`: heco welding-neck flange
  brochure. The implementation includes DN 15–300 and PN 6/10/16/25/40. The
  manufacturer rules that PN 10 below DN 200 follows PN 16 and PN 25 below
  DN 200 follows PN 40 are represented in the axial table.

Open sources:

- https://www.zelpo.sk/e-brochure/PL/Steel-tubes-and-pipes-handbook-of-Zeleziarne-Podbrezova-Group-2025.pdf
- https://www.heco.de/webservice/downloads/product-sheet/1966/en/heco-product-sheet-1966-Stainless-steel-bends-seamless-type-3-r-1-5xD-45-degree.pdf
- https://pfeiffer.samsongroup.com/document/t00020en.pdf
- https://www.heco.de/cms/fileadmin/heco/Seiten/Wissenswertes/Downloads/Flanges_broschure_EN_03.2024_web.pdf

These are manufacturer data associated with EN/DIN, not redistributed standard
text. Actual material, facing, tolerances, fitting execution and welding
procedure remain fabrication inputs that must be checked before cutting.

## Automated verification

The pure Kotlin engine tests cover:

- a fixed DN 50 / PN 16 reference assembly and every output dimension;
- geometric closure for all 14 supported pipe DN values at 30°, 45°, 60° and
  90° (56 generated assemblies);
- custom-angle warning and stock/kerf conservation;
- rejection of impossible envelopes and stock bars;
- all 70 Type 11 DN/PN axial catalog rows.

The Android journey additionally opens the live workshop, regenerates the
default assembly, verifies the 640.1 mm diagonal cut, the full flange view and
the stock cutting chart on an API 35 emulator.

# Pipe calculator catalog sources

Fitting series: 2026-08-13. Reference pipe tables: 2026-09-27.

## Implemented public tables

| Dataset | Implemented scope | Public primary/manufacturer source |
|---|---:|---|
| Steel pipe series associated with EN 10220 | 14 selected rows, DN 15–300 | [Železiarne Podbrezová, Steel tubes and pipes handbook 2025, Table 5](https://www.zelpo.sk/e-brochure/PL/Steel-tubes-and-pipes-handbook-of-Zeleziarne-Podbrezova-Group-2025.pdf) |
| Seamless stainless 45° 3D elbows, EN 10253-4/A | 14 rows, DN 15–300 | [HECO NB45 product sheet](https://www.heco.de/webservice/downloads/product-sheet/1966/en/heco-product-sheet-1966-Stainless-steel-bends-seamless-type-3-r-1-5xD-45-degree.pdf) |
| Seamless stainless equal tees, EN 10253-4/A | 11 rows, DN 15–150 | [HECO NT product sheet](https://www.heco.de/webservice/downloads/product-sheet/2024/en/heco-product-sheet-2024-Stainless-steel-T-X-Y-pieces-seamless.pdf) |
| Seamless stainless eccentric reducers, EN 10253-4/A | 13 adjacent-DN rows, DN 20×15–300×250 | [HECO NE product sheet](https://www.heco.de/webservice/downloads/product-sheet/2058/en/heco-product-sheet-2058-Stainless-steel-reducers-seamless-eccentric.pdf) |
| Flange connecting dimensions D/k/n/d2 according to DIN EN 1092-1 | 80 rows: DN 15–400 × PN 6/10/16/25/40 | [SAMSON/Pfeiffer AB 02 EN](https://pfeiffer.samsongroup.com/document/t00020en.pdf) |
| DOWFROST propylene-glycol density, specific heat and viscosity | 15 nodes: 30/40/50 vol% × 10/40/65/90/120 °C | [Dow DOWFROST technical data sheet, Form 180-01587-11](https://www.dow.com/content/dam/internal/documents/180/180-01587-11-dowfrost-technical-data-sheet.pdf?iframe=true) |

## Reference pipe tables (Workshop → DN/PN tables → Pipe tables)

Transcribed from suissetec, *Formeln und Tabellen für die Gebäudetechnik*,
chapter 10 "Tabellen Rohre", tables [1.52]–[1.61]:

| Table | Standard / system | Rows |
|---|---|---:|
| 1.52 Gewinderohr | DIN EN 10255, DN 10–100 | 10 |
| 1.53 Kupferrohr | DIN EN 1057, 10–54 mm | 9 |
| 1.54 Siederohr nahtlos | DIN EN 10220, DN 32–100 | 10 |
| 1.55 PE-X | DIN 16893, 16–63 mm | 7 |
| 1.56 Optiflex | Nussbaum PE-Xc/PE-RT/PB, 16–63 mm | 8 |
| 1.57 Sanipex MT | JRG PE-X/Al/PE-X, 16–63 mm | 7 |
| 1.58 PushFit | Geberit PE-Xb/Al/PE-RT, 16–25 mm | 3 |
| 1.59 PE 100 PN 16 | EN 12201, 20–250 mm | 16 |
| 1.60 Metallverbundrohr PN 10 | PE-C/Al/PE, 16–63 mm | 7 |
| 1.61 PE-HD Abwasser | Geberit, DN 50–150 | 8 |

Outside diameter, wall, inside diameter and mass per metre are stored as printed.
Flow area and water content are derived from the printed inside diameter, so a
misprint in the book's area or volume column (for example PushFit 16 mm printed
as 0.133 l/m instead of 0.113 l/m, or the PE 100 table computed with π = 3.14)
cannot reach a calculation. Where the book's inside diameter differs from
OD − 2·s (several EN 10220 rows), the printed bore is kept.

Additional nominal series (mass is theoretical from density): stainless press
pipe EN 10312, carbon steel press pipe EN 10305-3, PP-R PN 20 (SDR 6) and
PN 10 (SDR 11) per EN ISO 15874, PP drainage per EN 1451. Check the current
manufacturer sheet before ordering.

## Interpretation rules

- Pipe rows are a selected manufacturer production series, not every wall thickness permitted by EN 10220.
- HECO rows describe the named stainless-steel product/material execution. They are not interchangeable with every EN 10253 category or material.
- The flange table provides connecting dimensions only. It does not select a flange type, facing, material, thickness, pressure-temperature rating, gasket or bolting.
- DOWFROST concentration is volume percent. Dynamic viscosity from the sheet is converted from mPa·s to Pa·s. The engine interpolates between published nodes and rejects extrapolation outside the implemented range.
- A catalog selection is traceable and useful for preliminary layout/calculation, but the current product sheet, project specification and licensed standard remain controlling for procurement and acceptance.

## Machine-readable implementation

- Dimensional tables: `core/pipe-calculator/src/main/kotlin/com/planruler/pipecalculator/Catalog.kt`
- Reference pipe tables: `core/pipe-calculator/src/main/kotlin/com/planruler/pipecalculator/PipeTables.kt`
  (tests: `PipeTablesTest.kt`)
- Fluid table: `core/pipe-calculator/src/main/kotlin/com/planruler/pipecalculator/Fluids.kt`
- Catalog and checkpoint tests: `core/pipe-calculator/src/test/kotlin/com/planruler/pipecalculator/CatalogTest.kt` and `FluidsTest.kt`

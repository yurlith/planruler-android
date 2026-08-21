# Pipe calculator catalog sources

Research and implementation date: 2026-08-13  
Target: native Android `PlanRulerUnified/mobilePlanRuler`

## Implemented public tables

| Dataset | Implemented scope | Public primary/manufacturer source |
|---|---:|---|
| Steel pipe series associated with EN 10220 | 14 selected rows, DN 15–300 | [Železiarne Podbrezová, Steel tubes and pipes handbook 2025, Table 5](https://www.zelpo.sk/e-brochure/PL/Steel-tubes-and-pipes-handbook-of-Zeleziarne-Podbrezova-Group-2025.pdf) |
| Seamless stainless 45° 3D elbows, EN 10253-4/A | 14 rows, DN 15–300 | [HECO NB45 product sheet](https://www.heco.de/webservice/downloads/product-sheet/1966/en/heco-product-sheet-1966-Stainless-steel-bends-seamless-type-3-r-1-5xD-45-degree.pdf) |
| Seamless stainless equal tees, EN 10253-4/A | 11 rows, DN 15–150 | [HECO NT product sheet](https://www.heco.de/webservice/downloads/product-sheet/2024/en/heco-product-sheet-2024-Stainless-steel-T-X-Y-pieces-seamless.pdf) |
| Seamless stainless eccentric reducers, EN 10253-4/A | 13 adjacent-DN rows, DN 20×15–300×250 | [HECO NE product sheet](https://www.heco.de/webservice/downloads/product-sheet/2058/en/heco-product-sheet-2058-Stainless-steel-reducers-seamless-eccentric.pdf) |
| Flange connecting dimensions D/k/n/d2 according to DIN EN 1092-1 | 80 rows: DN 15–400 × PN 6/10/16/25/40 | [SAMSON/Pfeiffer AB 02 EN](https://pfeiffer.samsongroup.com/document/t00020en.pdf) |
| DOWFROST propylene-glycol density, specific heat and viscosity | 15 nodes: 30/40/50 vol% × 10/40/65/90/120 °C | [Dow DOWFROST technical data sheet, Form 180-01587-11](https://www.dow.com/content/dam/internal/documents/180/180-01587-11-dowfrost-technical-data-sheet.pdf?iframe=true) |

## Interpretation rules

- Pipe rows are a selected manufacturer production series, not every wall thickness permitted by EN 10220.
- HECO rows describe the named stainless-steel product/material execution. They are not interchangeable with every EN 10253 category or material.
- The flange table provides connecting dimensions only. It does not select a flange type, facing, material, thickness, pressure-temperature rating, gasket or bolting.
- DOWFROST concentration is volume percent. Dynamic viscosity from the sheet is converted from mPa·s to Pa·s. The engine interpolates between published nodes and rejects extrapolation outside the implemented range.
- A catalog selection is traceable and useful for preliminary layout/calculation, but the current product sheet, project specification and licensed standard remain controlling for procurement and acceptance.

## Machine-readable implementation

- Dimensional tables: `core/pipe-calculator/src/main/kotlin/com/planruler/pipecalculator/Catalog.kt`
- Fluid table: `core/pipe-calculator/src/main/kotlin/com/planruler/pipecalculator/Fluids.kt`
- Catalog and checkpoint tests: `core/pipe-calculator/src/test/kotlin/com/planruler/pipecalculator/CatalogTest.kt` and `FluidsTest.kt`

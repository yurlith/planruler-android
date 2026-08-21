# HeatCalcPro → PlanRuler native integration

Date: 2026-08-14

## Source reviewed

The private `yurlith/heatcalc-pro-production` repository was used only as a
functional reference for heat-loss inputs, hydronic concepts and floor-heating
drawing interactions. No JavaScript, web view or web runtime is embedded in the
Android application.

## Native result

`core/pipe-calculator/HeatDesign.kt` now provides a traceable preliminary
calculation rather than copying the former `EN 12831-lite` label or formulas:

- explicit heated-volume semantics;
- separate transmission, linear thermal-bridge and ventilation losses;
- heat-recovery and infiltration inputs shown in the trace;
- water/glycol flow from heat capacity, density and design ΔT;
- pump head only from a defined critical circuit and Darcy losses;
- UFH length only from generated manifold-to-manifold circuits;
- no hidden `860`, `flow/40` or equivalent sizing constants;
- validation status, method identifier, formula steps and warnings on every
  calculation result.

The drawing and hydraulics pages share the same critical-circuit and routed-loop
state. Each generated UFH loop has a visible supply start, return end, feeder
length and independent colour.

## Normative boundary

The historical `EN 12831-lite` mode has been removed. `Preliminary planning` is
the only executable profile and makes no SIA, DIN, EN or SVGW compliance claim.
SIA 384/2:2020+C1:2021, DIN EN 12831-1:2017/DIN TS 12831-1 and the prEN 12831-1
draft are registered as locked profiles. Unlocking one requires licensed text,
versioned coefficients, signed golden cases and acceptance by a competent Swiss
HVAC engineer.

## Automated coverage

- envelope, volume-semantics, ventilation and linear-bridge tests;
- water and glycol flow tests;
- critical-circuit pump-head tests;
- manifold loop continuity, maximum length and bend-radius tests;
- explicit checks that normative profiles remain locked;
- Android journeys for calculation, automatic routing and drawing input.

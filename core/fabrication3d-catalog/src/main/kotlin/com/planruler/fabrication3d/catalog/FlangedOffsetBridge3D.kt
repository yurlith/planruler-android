package com.planruler.fabrication3d.catalog

import com.planruler.fabrication3d.AssemblyMetadata3D
import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ElbowProfile3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.FabricationRules3D
import com.planruler.fabrication3d.FlangeProfile3D
import com.planruler.fabrication3d.Frame3D
import com.planruler.fabrication3d.GEOMETRY_EPSILON
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ParametricPartDefinition3D
import com.planruler.fabrication3d.PartConnection3D
import com.planruler.fabrication3d.PartFactory3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.PipeProfile3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.TeeProfile3D
import com.planruler.fabrication3d.Transform3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.flatMap
import com.planruler.pipecalculator.EQUAL_TEE_CATALOG
import com.planruler.pipecalculator.FabricationElement
import com.planruler.pipecalculator.FabricationPointMm
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult

/**
 * Bridges the verified two-dimensional fabrication calculation to the 3D engine. It is
 * the only place that knows both the catalog and the engine API, which keeps the engine
 * itself free of any dependency on the calculator.
 */
fun FlangedOffsetAssemblyResult.toAssemblyProfile3D(
    rules: FabricationRules3D = FabricationRules3D(weldGapMm = input.weldGapMm),
): AssemblyProfile3D = AssemblyProfile3D(
    nominalDiameter = pipe.dn,
    pressureClass = flange.pn,
    pipe = PipeProfile3D(
        catalogId = pipe.id,
        outsideDiameterMm = pipe.outsideDiameterMm,
        wallThicknessMm = pipe.wallThicknessMm,
    ),
    elbow = ElbowProfile3D(
        catalogId = elbow.id,
        centerlineRadiusMm = elbow.centerlineRadiusMm,
        outsideDiameterMm = elbow.outsideDiameterMm,
        wallThicknessMm = elbow.wallThicknessMm,
    ),
    flange = FlangeProfile3D(
        catalogId = flange.id,
        faceToWeldMm = flange.faceToWeldMm,
        thicknessMm = flange.thicknessMm,
        outsideDiameterMm = flange.outsideDiameterMm,
        boltCircleDiameterMm = flange.boltCircleDiameterMm,
        boltHoleCount = flange.boltHoleCount,
        boltHoleDiameterMm = flange.boltHoleDiameterMm,
    ),
    rules = rules,
    tee = equalTeeProfile3D(pipe.dn),
)

/**
 * The verified calculation expressed as an editable recipe. Opening the standard
 * two-elbow spool in the editor is how a fitter departs from the template: every cut,
 * angle and roll then becomes an ordinary parameter instead of a fixed drawing.
 */
fun FlangedOffsetAssemblyResult.toChainPlan3D(): ChainPlan3D = ChainPlan3D(
    start = ChainStart3D(
        position = Vec3.ZERO,
        direction = Vec3.UNIT_X,
        upHint = Vec3.UNIT_Z,
        terminal = ChainTerminal3D.FLANGE,
    ),
    steps = listOf(
        ChainStep3D.Pipe(inletPipeCutMm),
        ChainStep3D.Elbow(input.angleDeg, 0.0),
        ChainStep3D.Pipe(diagonalPipeCutMm),
        ChainStep3D.Elbow(-input.angleDeg, 0.0),
        ChainStep3D.Pipe(outletPipeCutMm),
        ChainStep3D.Flange(0.0),
    ),
)

/** The catalog carries equal tees up to DN 150; larger diameters simply have none. */
fun equalTeeProfile3D(nominalDiameter: Int): TeeProfile3D? =
    EQUAL_TEE_CATALOG.firstOrNull { it.dn == nominalDiameter }?.let { entry ->
        TeeProfile3D(
            catalogId = entry.id,
            overallRunMm = entry.overallRunMm,
            branchCenterToEndMm = entry.centerToEndMm,
            outsideDiameterMm = entry.outsideDiameterMm,
            wallThicknessMm = entry.wallThicknessMm,
        )
    }

/** Converts the proven 2D fabrication result into the common parametric 3D graph. */
fun FlangedOffsetAssemblyResult.toParametricAssembly3D(
    parts: PartFactory3D,
): Fabrication3DResult<ParametricAssembly3D> {
    val pipeDefinitions = mutableMapOf<String, ParametricPartDefinition3D>()
    cuts.forEach { cut ->
        val definition = parts.straightPipe(
            id = "${pipe.id}:${cut.code}:${cut.lengthMm}",
            catalogId = pipe.id,
            nominalDiameter = pipe.dn,
            lengthMm = cut.lengthMm,
            outsideDiameterMm = pipe.outsideDiameterMm,
            wallThicknessMm = pipe.wallThicknessMm,
        )
        when (definition) {
            is Fabrication3DResult.Ok -> pipeDefinitions[cut.code] = definition.value
            is Fabrication3DResult.Failure -> return definition
        }
    }

    return parts.elbow(
        id = "${elbow.id}:E1:${input.angleDeg}",
        catalogId = elbow.id,
        nominalDiameter = pipe.dn,
        angleDeg = input.angleDeg,
        centerlineRadiusMm = elbow.centerlineRadiusMm,
        outsideDiameterMm = elbow.outsideDiameterMm,
        wallThicknessMm = elbow.wallThicknessMm,
    ).flatMap { firstElbow ->
        parts.elbow(
            id = "${elbow.id}:E2:${-input.angleDeg}",
            catalogId = elbow.id,
            nominalDiameter = pipe.dn,
            angleDeg = -input.angleDeg,
            centerlineRadiusMm = elbow.centerlineRadiusMm,
            outsideDiameterMm = elbow.outsideDiameterMm,
            wallThicknessMm = elbow.wallThicknessMm,
        ).flatMap { secondElbow ->
            parts.weldNeckFlange(
                id = flange.id,
                catalogId = flange.id,
                nominalDiameter = pipe.dn,
                faceToWeldMm = flange.faceToWeldMm,
                thicknessMm = flange.thicknessMm,
                outsideDiameterMm = flange.outsideDiameterMm,
                pipeOutsideDiameterMm = pipe.outsideDiameterMm,
                pipeWallThicknessMm = pipe.wallThicknessMm,
                boltCircleDiameterMm = flange.boltCircleDiameterMm,
                boltHoleCount = flange.boltHoleCount,
                boltHoleDiameterMm = flange.boltHoleDiameterMm,
            ).flatMap { flangeDefinition ->
                // The 2D result is trusted input, but a malformed element list must surface
                // as a typed failure rather than an exception thrown through a composable.
                runCatching {
                    assemble(pipeDefinitions, firstElbow, secondElbow, flangeDefinition)
                }.fold(
                    onSuccess = { Fabrication3DResult.Ok(it) },
                    onFailure = {
                        Fabrication3DResult.Failure(Fabrication3DError.Internal("flangedOffsetBridge"))
                    },
                )
            }
        }
    }
}

private fun FlangedOffsetAssemblyResult.assemble(
    pipeDefinitions: Map<String, ParametricPartDefinition3D>,
    firstElbow: ParametricPartDefinition3D,
    secondElbow: ParametricPartDefinition3D,
    flangeDefinition: ParametricPartDefinition3D,
): ParametricAssembly3D {
    val f1 = element("F1")
    val p1 = element("P1")
    val e1 = element("E1")
    val p2 = element("P2")
    val e2 = element("E2")
    val p3 = element("P3")
    val f2 = element("F2")

    val parts = listOf(
        PartInstance3D("F1", "F1", flangeDefinition, along(point(f1.start), point(f1.end))),
        PartInstance3D("P1", "P1", pipeDefinitions.getValue("P1"), along(point(p1.start), point(p1.end))),
        PartInstance3D("E1", "E1", firstElbow, along(point(e1.start), point(requireNotNull(e1.control)))),
        PartInstance3D("P2", "P2", pipeDefinitions.getValue("P2"), along(point(p2.start), point(p2.end))),
        PartInstance3D("E2", "E2", secondElbow, along(point(e2.start), point(requireNotNull(e2.control)))),
        PartInstance3D("P3", "P3", pipeDefinitions.getValue("P3"), along(point(p3.start), point(p3.end))),
        // The second flange definition runs from sealing face to weld, therefore its local axis is reversed.
        PartInstance3D("F2", "F2", flangeDefinition, along(point(f2.end), point(f2.start))),
    )
    val chain = listOf(
        Triple(PortReference3D("F1", "weld"), PortReference3D("P1", "start"), "W1"),
        Triple(PortReference3D("P1", "end"), PortReference3D("E1", "start"), "W2"),
        Triple(PortReference3D("E1", "end"), PortReference3D("P2", "start"), "W3"),
        Triple(PortReference3D("P2", "end"), PortReference3D("E2", "start"), "W4"),
        Triple(PortReference3D("E2", "end"), PortReference3D("P3", "start"), "W5"),
        Triple(PortReference3D("P3", "end"), PortReference3D("F2", "weld"), "W6"),
    )
    return ParametricAssembly3D(
        metadata = AssemblyMetadata3D(
            name = "Flanged offset DN ${pipe.dn} PN ${flange.pn}",
            nominalDiameter = pipe.dn,
            pressureClass = flange.pn,
            sourceCalculation = "flanged-offset-v1",
        ),
        parts = parts,
        connections = chain.mapIndexed { index, (first, second, weldCode) ->
            PartConnection3D(
                id = "C${index + 1}:$weldCode",
                first = first,
                second = second,
                axialGapMm = input.weldGapMm,
            )
        },
    )
}

/** Fails with a typed error instead of throwing when the 2D result cannot be lifted. */
fun FlangedOffsetAssemblyResult.toValidatedAssembly3D(
    parts: PartFactory3D,
    validate: (ParametricAssembly3D) -> List<com.planruler.fabrication3d.AssemblyIssue3D>,
): Fabrication3DResult<ParametricAssembly3D> = toParametricAssembly3D(parts).flatMap { assembly ->
    val issues = validate(assembly)
    if (issues.isEmpty()) {
        Fabrication3DResult.Ok(assembly)
    } else {
        Fabrication3DResult.Failure(Fabrication3DError.AssemblyInvalid(issues))
    }
}

private fun FlangedOffsetAssemblyResult.element(code: String): FabricationElement =
    elements.single { it.code == code }

private fun point(point: FabricationPointMm) = Vec3(point.x, point.y, 0.0)

private fun along(start: Vec3, end: Vec3): Transform3D {
    val direction = end - start
    require(direction.length > GEOMETRY_EPSILON) { "A fabrication element cannot have zero length" }
    return Transform3D(start, Frame3D.of(start, direction).rotation)
}

package com.planruler.fabrication3d

/** Builds parametric part definitions. Catalog lookup happens outside; this applies geometry. */
interface PartFactory3D {
    fun straightPipe(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        lengthMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D>

    fun elbow(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        angleDeg: Double,
        centerlineRadiusMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D>

    fun weldNeckFlange(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        faceToWeldMm: Double,
        thicknessMm: Double,
        outsideDiameterMm: Double,
        pipeOutsideDiameterMm: Double,
        pipeWallThicknessMm: Double,
        boltCircleDiameterMm: Double,
        boltHoleCount: Int,
        boltHoleDiameterMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D>

    fun equalTee(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        overallRunMm: Double,
        branchCenterToEndMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D>

    fun reducer(
        id: String,
        catalogId: String?,
        largeNominalDiameter: Int,
        smallNominalDiameter: Int,
        lengthMm: Double,
        largeOutsideDiameterMm: Double,
        largeWallThicknessMm: Double,
        smallOutsideDiameterMm: Double,
        smallWallThicknessMm: Double,
        eccentric: Boolean,
    ): Fabrication3DResult<ParametricPartDefinition3D>

    fun cap(
        id: String,
        catalogId: String?,
        nominalDiameter: Int,
        heightMm: Double,
        outsideDiameterMm: Double,
        wallThicknessMm: Double,
    ): Fabrication3DResult<ParametricPartDefinition3D>
}

/** Every rule that changes or checks the assembly graph. */
interface AssemblyGraph3D {
    fun validate(
        assembly: ParametricAssembly3D,
        tolerances: AssemblyTolerances3D = AssemblyTolerances3D(),
    ): List<AssemblyIssue3D>

    fun attach(
        assembly: ParametricAssembly3D,
        anchor: PortReference3D,
        newPartId: String,
        newPartCode: String,
        definition: ParametricPartDefinition3D,
        attachingPortId: String,
        axialGapMm: Double = 0.0,
        rollDeg: Double = 0.0,
    ): Fabrication3DResult<ParametricAssembly3D>

    fun removePart(
        assembly: ParametricAssembly3D,
        partId: String,
    ): Fabrication3DResult<ParametricAssembly3D>

    fun transformed(assembly: ParametricAssembly3D, transform: Transform3D): ParametricAssembly3D

    /** Centreline segments that come closer than the pipe wall allows. */
    fun selfIntersections(
        assembly: ParametricAssembly3D,
        clearanceMm: Double = 0.0,
    ): List<AssemblyIssue3D>

    /** Ids of the obstacles the assembly runs through. */
    fun obstructions(
        assembly: ParametricAssembly3D,
        obstacles: List<ObstacleBox3D>,
        clearanceMm: Double = 0.0,
    ): List<String>
}

interface MeshTessellator3D {
    fun build(
        assembly: ParametricAssembly3D,
        quality: MeshQuality3D = MeshQuality3D.DEFAULT,
    ): Fabrication3DResult<AssemblyMesh3D>
}

interface ChainEditor3D {
    fun create(
        profile: AssemblyProfile3D,
        start: ChainStart3D = ChainStart3D(),
    ): Fabrication3DResult<ChainEditorState3D>

    fun fromPlan(
        profile: AssemblyProfile3D,
        plan: ChainPlan3D,
    ): Fabrication3DResult<ChainEditorState3D>

    /**
     * Applies [command] without recording history. Intended for a gesture in flight: the
     * first call of a drag remembers where it started, every later call replaces the
     * previewed result, and [commitPreview] turns the whole gesture into a single undo
     * entry. A rejected preview leaves the last accepted one standing, so a handle simply
     * stops at the rule instead of tearing the model down.
     */
    fun preview(
        state: ChainEditorState3D,
        command: ChainCommand3D,
    ): Fabrication3DResult<ChainEditorState3D>

    /** Closes an open preview, recording exactly one undo entry for the whole gesture. */
    fun commitPreview(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D>

    /** Closes an open preview and restores the recipe as it stood when the gesture began. */
    fun cancelPreview(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D>

    /**
     * Applies [command] as its own history entry. An open preview is committed first, so a
     * discrete edit never silently discards a gesture that is still in flight.
     */
    fun execute(
        state: ChainEditorState3D,
        command: ChainCommand3D,
    ): Fabrication3DResult<ChainEditorState3D>

    fun undo(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D>

    fun redo(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D>

    fun select(state: ChainEditorState3D, partId: String?): Fabrication3DResult<ChainEditorState3D>
}

interface RouteSolver3D {
    fun solve(request: RouteRequest3D): Fabrication3DResult<RouteSolution3D>
}

interface ProfileCustomizer3D {
    fun apply(
        profile: AssemblyProfile3D,
        overrides: AssemblyProfileOverrides3D,
    ): Fabrication3DResult<AssemblyProfile3D>
}

/**
 * The single entry point callers resolve from the composition root. Implementations
 * live in a separate module; nothing outside the engine may reference them directly.
 */
interface Fabrication3DEngine {
    val limits: EngineLimits3D
    val parts: PartFactory3D
    val graph: AssemblyGraph3D
    val mesh: MeshTessellator3D
    val chains: ChainEditor3D
    val router: RouteSolver3D
    val profiles: ProfileCustomizer3D
}

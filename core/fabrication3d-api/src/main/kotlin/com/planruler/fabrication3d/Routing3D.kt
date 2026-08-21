package com.planruler.fabrication3d

/** A flange face the route has to meet: where it sits and which way the pipe leaves it. */
data class RouteTerminal3D(
    val face: Vec3,
    val direction: Vec3 = Vec3.UNIT_X,
    val upHint: Vec3 = Vec3.UNIT_Z,
)

/** An axis-aligned volume the route must stay out of, in the same millimetre frame. */
data class ObstacleBox3D(
    val id: String,
    val bounds: Bounds3D,
)

data class RouteRequest3D(
    val profile: AssemblyProfile3D,
    val start: RouteTerminal3D = RouteTerminal3D(Vec3.ZERO),
    val target: RouteTerminal3D,
    val preferredElbowAngleDeg: Double? = null,
    val allowedElbowAnglesDeg: List<Double>? = null,
    val minimumStraightMm: Double = 50.0,
    val maxElbows: Int? = null,
    val obstacles: List<ObstacleBox3D> = emptyList(),
    val clearanceMm: Double = 0.0,
)

enum class RouteFailureCode3D {
    INVALID_REQUEST,
    TARGET_BEHIND_START,
    MAX_ELBOWS_TOO_SMALL,
    NO_FABRICABLE_ROUTE,
    OBSTRUCTED,
    INTERNAL_CLOSURE_ERROR,
}

/** Which construction closed the route; useful in the cut list and in tests. */
enum class RouteTopology3D {
    STRAIGHT,
    ROLLING_OFFSET,
    TURN,
    TURN_WITH_OFFSET,

    /** Terminals facing each other: two quarter bends carry the run back alongside itself. */
    U_TURN,
}

enum class RouteDiagnostic3D {
    PARALLEL_TERMINALS,
    NON_PARALLEL_TERMINALS,
    ANTI_PARALLEL_TERMINALS,
    STRAIGHT_ROUTE,
    TWO_ELBOW_OFFSET,
    TWO_ELBOW_TURN,
    THREE_ELBOW_TURN,
    FOUR_ELBOW_TURN,
    CLEARANCE_CHECKED,
}

data class PipeCut3D(
    val code: String,
    val lengthMm: Double,
)

data class RouteSolution3D(
    val assembly: ParametricAssembly3D,
    /** The same recipe the manual editor uses, so a solved route can be hand-tuned. */
    val plan: ChainPlan3D,
    val topology: RouteTopology3D,
    val elbowAnglesDeg: List<Double>,
    val pipeCuts: List<PipeCut3D>,
    val targetErrorMm: Double,
    val terminalDirectionErrorDeg: Double,
    val diagnostics: List<RouteDiagnostic3D>,
) {
    val elbowCount: Int get() = assembly.elbowCount()
    val totalPipeCutMm: Double get() = pipeCuts.sumOf { it.lengthMm }
}

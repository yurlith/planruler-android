package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.ObstacleBox3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.PipeCut3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.RouteDiagnostic3D
import com.planruler.fabrication3d.RouteFailureCode3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteSolution3D
import com.planruler.fabrication3d.RouteSolver3D
import com.planruler.fabrication3d.RouteTopology3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.fold
import kotlin.math.abs
import kotlin.math.acos

/**
 * Closes a route between two flange faces anywhere in space. Parallel terminals keep
 * the proven straight and rolling-offset constructions; non-parallel terminals are
 * turned with an elbow and, when the target sits off the turn plane, the residual is
 * absorbed by a rolling offset placed in the inlet leg.
 */
internal class DefaultRouteSolver3D(
    private val limits: EngineLimits3D,
    private val editor: DefaultChainEditor3D,
    private val graph: DefaultAssemblyGraph3D,
) : RouteSolver3D {

    override fun solve(request: RouteRequest3D): Fabrication3DResult<RouteSolution3D> {
        validateRequest(request)?.let { return it }

        val startDirection = request.start.direction.normalizedOrNull()
            ?: return invalid("start.direction", ParameterRule3D.MUST_BE_POSITIVE, request.start.direction)
        val targetDirection = request.target.direction.normalizedOrNull()
            ?: return invalid("target.direction", ParameterRule3D.MUST_BE_POSITIVE, request.target.direction)

        val maxElbows = minOf(
            request.maxElbows ?: request.profile.rules.maxElbows,
            request.profile.rules.maxElbows,
            limits.maxElbows,
        )
        val delta = request.target.face - request.start.face
        val parallel = startDirection.dot(targetDirection) > 1.0 - PARALLEL_TOLERANCE

        return if (parallel) {
            solveParallel(request, startDirection, delta, maxElbows)
        } else {
            solveTurn(request, startDirection, targetDirection, delta, maxElbows)
        }
    }

    // ---------------------------------------------------------------- parallel axes

    private fun solveParallel(
        request: RouteRequest3D,
        direction: Vec3,
        delta: Vec3,
        maxElbows: Int,
    ): Fabrication3DResult<RouteSolution3D> {
        val axial = delta.dot(direction)
        if (axial <= 0.0) {
            return failure(RouteFailureCode3D.TARGET_BEHIND_START, "target is not ahead of the start face")
        }
        val lateralVector = delta - direction * axial
        val lateral = lateralVector.length
        if (lateral <= POSITION_TOLERANCE_MM) {
            return solveStraight(request, direction)
        }
        if (maxElbows < 2) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, maxElbows, 2),
            )
        }
        val lateralDirection = lateralVector / lateral
        return solveRollingOffset(request, direction, lateralDirection)
    }

    private fun solveStraight(
        request: RouteRequest3D,
        direction: Vec3,
    ): Fabrication3DResult<RouteSolution3D> {
        val start = startOf(request, direction)
        return closeRoute(
            request = request,
            start = start,
            build = { lengths, _ -> listOf(ChainStep3D.Pipe(lengths[0]), ChainStep3D.Flange()) },
            unknownDirections = listOf(direction),
            baseLengths = doubleArrayOf(request.minimumStraightMm),
            topology = RouteTopology3D.STRAIGHT,
            diagnostics = listOf(RouteDiagnostic3D.PARALLEL_TERMINALS, RouteDiagnostic3D.STRAIGHT_ROUTE),
            elbowAngles = emptyList(),
            targetDirection = direction,
        )
    }

    private fun solveRollingOffset(
        request: RouteRequest3D,
        direction: Vec3,
        lateralDirection: Vec3,
    ): Fabrication3DResult<RouteSolution3D> {
        val start = startOf(request, direction)
        val candidates = elbowAngles(request)
        if (candidates.isEmpty()) {
            return invalid("allowedElbowAnglesDeg", ParameterRule3D.EMPTY_SET)
        }
        var lastFailure: Fabrication3DResult.Failure? = null
        val solutions = candidates.mapNotNull { angle ->
            val diagonalDirection = rotateTowards(direction, lateralDirection, angle)
            val attempt = closeRoute(
                request = request,
                start = start,
                build = { lengths, rolls ->
                    listOf(
                        ChainStep3D.Pipe(lengths[0] / 2.0),
                        ChainStep3D.Elbow(angle, rolls[0]),
                        ChainStep3D.Pipe(lengths[1]),
                        ChainStep3D.Elbow(-angle, 0.0),
                        ChainStep3D.Pipe(lengths[0] / 2.0),
                        ChainStep3D.Flange(),
                    )
                },
                unknownDirections = listOf(direction, diagonalDirection),
                baseLengths = doubleArrayOf(2.0 * request.minimumStraightMm, request.minimumStraightMm),
                topology = RouteTopology3D.ROLLING_OFFSET,
                diagnostics = listOf(RouteDiagnostic3D.PARALLEL_TERMINALS, RouteDiagnostic3D.TWO_ELBOW_OFFSET),
                elbowAngles = listOf(angle, -angle),
                targetDirection = direction,
                bendDirections = listOf(lateralDirection),
            )
            attempt.fold({ it }, { lastFailure = Fabrication3DResult.Failure(it); null })
        }
        val best = solutions.minWithOrNull(
            compareBy<RouteSolution3D> { it.totalPipeCutMm }
                .thenByDescending { it.elbowAnglesDeg.firstOrNull() ?: 0.0 },
        )
        return best?.let { ok(it) }
            ?: lastFailure
            ?: failure(RouteFailureCode3D.NO_FABRICABLE_ROUTE, "no allowed elbow angle fits the envelope")
    }

    // ------------------------------------------------------------ non-parallel axes

    private fun solveTurn(
        request: RouteRequest3D,
        startDirection: Vec3,
        targetDirection: Vec3,
        delta: Vec3,
        maxElbows: Int,
    ): Fabrication3DResult<RouteSolution3D> {
        if (maxElbows < 1) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, maxElbows, 1),
            )
        }
        val turnAngle = Math.toDegrees(
            acos(startDirection.dot(targetDirection).coerceIn(-1.0, 1.0)),
        )
        if (!request.profile.rules.permits(turnAngle)) {
            return invalid(
                "turnAngleDeg",
                ParameterRule3D.ANGLE_NOT_ALLOWED,
                turnAngle,
                "${request.profile.rules.minElbowAngleDeg}..${request.profile.rules.maxElbowAngleDeg}",
            )
        }
        val turnLateral = (targetDirection - startDirection * startDirection.dot(targetDirection))
            .normalizedOrNull()
            // Facing terminals leave the bend plane undefined by direction alone, so it is
            // taken from the offset between the two faces instead.
            ?: return solveUTurn(request, startDirection, delta, maxElbows)
        val planeNormal = startDirection.cross(turnLateral).normalizedOrNull()
            ?: return failure(RouteFailureCode3D.NO_FABRICABLE_ROUTE, "turn plane is degenerate")
        val outOfPlane = delta.dot(planeNormal)
        val start = startOf(request, startDirection)

        if (abs(outOfPlane) <= POSITION_TOLERANCE_MM) {
            val planar = closeRoute(
                request = request,
                start = start,
                build = { lengths, rolls ->
                    listOf(
                        ChainStep3D.Pipe(lengths[0]),
                        ChainStep3D.Elbow(turnAngle, rolls[0]),
                        ChainStep3D.Pipe(lengths[1]),
                        ChainStep3D.Flange(),
                    )
                },
                unknownDirections = listOf(startDirection, targetDirection),
                baseLengths = doubleArrayOf(request.minimumStraightMm, request.minimumStraightMm),
                topology = RouteTopology3D.TURN,
                diagnostics = listOf(
                    RouteDiagnostic3D.NON_PARALLEL_TERMINALS,
                    RouteDiagnostic3D.THREE_ELBOW_TURN,
                ),
                elbowAngles = listOf(turnAngle),
                targetDirection = targetDirection,
                bendDirections = listOf(turnLateral),
            )
            if (planar is Fabrication3DResult.Ok) return planar
        }

        if (maxElbows < 3) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, maxElbows, 3),
            )
        }
        val shiftDirection = if (outOfPlane >= 0.0) planeNormal else -planeNormal
        val candidates = elbowAngles(request)
        if (candidates.isEmpty()) {
            return invalid("allowedElbowAnglesDeg", ParameterRule3D.EMPTY_SET)
        }
        var lastFailure: Fabrication3DResult.Failure? = null
        val solutions = candidates.mapNotNull { angle ->
            val diagonalDirection = rotateTowards(startDirection, shiftDirection, angle)
            val attempt = closeRoute(
                request = request,
                start = start,
                build = { lengths, rolls ->
                    listOf(
                        ChainStep3D.Pipe(lengths[0] / 2.0),
                        ChainStep3D.Elbow(angle, rolls[0]),
                        ChainStep3D.Pipe(lengths[1]),
                        ChainStep3D.Elbow(-angle, 0.0),
                        ChainStep3D.Pipe(lengths[0] / 2.0),
                        ChainStep3D.Elbow(turnAngle, rolls[1]),
                        ChainStep3D.Pipe(lengths[2]),
                        ChainStep3D.Flange(),
                    )
                },
                unknownDirections = listOf(startDirection, diagonalDirection, targetDirection),
                baseLengths = doubleArrayOf(
                    2.0 * request.minimumStraightMm,
                    request.minimumStraightMm,
                    request.minimumStraightMm,
                ),
                topology = RouteTopology3D.TURN_WITH_OFFSET,
                diagnostics = listOf(
                    RouteDiagnostic3D.NON_PARALLEL_TERMINALS,
                    RouteDiagnostic3D.TWO_ELBOW_OFFSET,
                    RouteDiagnostic3D.FOUR_ELBOW_TURN,
                ),
                elbowAngles = listOf(angle, -angle, turnAngle),
                targetDirection = targetDirection,
                bendDirections = listOf(shiftDirection, turnLateral),
            )
            attempt.fold({ it }, { lastFailure = Fabrication3DResult.Failure(it); null })
        }
        val best = solutions.minByOrNull { it.totalPipeCutMm }
        return best?.let { ok(it) }
            ?: lastFailure
            ?: failure(RouteFailureCode3D.NO_FABRICABLE_ROUTE, "no allowed elbow angle fits the envelope")
    }

    /**
     * Terminals that face each other. The run leaves along the start axis, crosses to the
     * side the target sits on and comes back parallel to itself, which is the two quarter
     * bend spool a fitter would make. One leg is pinned to the minimum cut so the remaining
     * two lengths form a solvable pair; if that pins the wrong end, the other end is tried.
     */
    private fun solveUTurn(
        request: RouteRequest3D,
        startDirection: Vec3,
        delta: Vec3,
        maxElbows: Int,
    ): Fabrication3DResult<RouteSolution3D> {
        if (maxElbows < 2) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, maxElbows, 2),
            )
        }
        if (!request.profile.rules.permits(U_TURN_ELBOW_DEG)) {
            return invalid(
                "turnAngleDeg",
                ParameterRule3D.ANGLE_NOT_ALLOWED,
                U_TURN_ELBOW_DEG,
                "${request.profile.rules.minElbowAngleDeg}..${request.profile.rules.maxElbowAngleDeg}",
            )
        }
        val crossing = (delta - startDirection * delta.dot(startDirection)).normalizedOrNull()
            ?: return failure(
                RouteFailureCode3D.NO_FABRICABLE_ROUTE,
                "facing terminals share one axis, so the return run would sit inside the supply",
            )
        val start = startOf(request, startDirection)
        val minimum = maxOf(request.minimumStraightMm, request.profile.rules.minPipeLengthMm)
        val diagnostics = listOf(
            RouteDiagnostic3D.ANTI_PARALLEL_TERMINALS,
            RouteDiagnostic3D.TWO_ELBOW_TURN,
        )
        val bendDirections = listOf(crossing, -startDirection)

        val pinnedInlet = closeRoute(
            request = request,
            start = start,
            build = { lengths, rolls ->
                listOf(
                    ChainStep3D.Pipe(minimum),
                    ChainStep3D.Elbow(U_TURN_ELBOW_DEG, rolls[0]),
                    ChainStep3D.Pipe(lengths[0]),
                    ChainStep3D.Elbow(U_TURN_ELBOW_DEG, rolls[1]),
                    ChainStep3D.Pipe(lengths[1]),
                    ChainStep3D.Flange(),
                )
            },
            unknownDirections = listOf(crossing, -startDirection),
            baseLengths = doubleArrayOf(minimum, minimum),
            topology = RouteTopology3D.U_TURN,
            diagnostics = diagnostics,
            elbowAngles = listOf(U_TURN_ELBOW_DEG, U_TURN_ELBOW_DEG),
            targetDirection = -startDirection,
            bendDirections = bendDirections,
        )
        if (pinnedInlet is Fabrication3DResult.Ok) return pinnedInlet

        return closeRoute(
            request = request,
            start = start,
            build = { lengths, rolls ->
                listOf(
                    ChainStep3D.Pipe(lengths[0]),
                    ChainStep3D.Elbow(U_TURN_ELBOW_DEG, rolls[0]),
                    ChainStep3D.Pipe(lengths[1]),
                    ChainStep3D.Elbow(U_TURN_ELBOW_DEG, rolls[1]),
                    ChainStep3D.Pipe(minimum),
                    ChainStep3D.Flange(),
                )
            },
            unknownDirections = listOf(startDirection, crossing),
            baseLengths = doubleArrayOf(minimum, minimum),
            topology = RouteTopology3D.U_TURN,
            diagnostics = diagnostics,
            elbowAngles = listOf(U_TURN_ELBOW_DEG, U_TURN_ELBOW_DEG),
            targetDirection = -startDirection,
            bendDirections = bendDirections,
        )
    }

    // ------------------------------------------------------------------- machinery

    /**
     * Builds the candidate, measures how far its end lands from the target and corrects
     * the free pipe lengths in one linear step. Elbow rolls are measured on the partially
     * replayed chain, so a bend plane is always expressed against the real incoming frame.
     */
    private fun closeRoute(
        request: RouteRequest3D,
        start: ChainStart3D,
        build: (DoubleArray, DoubleArray) -> List<ChainStep3D>,
        unknownDirections: List<Vec3>,
        baseLengths: DoubleArray,
        topology: RouteTopology3D,
        diagnostics: List<RouteDiagnostic3D>,
        elbowAngles: List<Double>,
        targetDirection: Vec3,
        bendDirections: List<Vec3> = emptyList(),
    ): Fabrication3DResult<RouteSolution3D> {
        val profile = request.profile
        val minimum = maxOf(request.minimumStraightMm, profile.rules.minPipeLengthMm)
        var lengths = baseLengths.copyOf()
        var rolls = DoubleArray(bendDirections.size)

        repeat(CLOSURE_PASSES) {
            rolls = measureRolls(profile, start, lengths, rolls, build, bendDirections)
                .fold({ it }, { return Fabrication3DResult.Failure(it) })
            val plan = ChainPlan3D(start, build(lengths, rolls))
            val replayed = editor.replay(profile, plan)
                .fold({ it }, { return Fabrication3DResult.Failure(it) })
            val achieved = terminalFace(replayed.assembly)
                ?: return failure(RouteFailureCode3D.INTERNAL_CLOSURE_ERROR, "route has no terminal flange")
            val residual = request.target.face - achieved
            if (residual.length <= POSITION_TOLERANCE_MM) return@repeat
            val closure = LinearClosure3D.solve(unknownDirections, residual)
                ?: return failure(RouteFailureCode3D.NO_FABRICABLE_ROUTE, "the topology cannot reach the target")
            if (closure.unreachableMm > POSITION_TOLERANCE_MM) {
                return failure(
                    RouteFailureCode3D.NO_FABRICABLE_ROUTE,
                    "the topology leaves ${closure.unreachableMm} mm uncovered",
                )
            }
            val corrected = DoubleArray(lengths.size) { lengths[it] + closure.delta[it] }
            // Rejected here rather than after the loop, because replaying a negative cut
            // would surface as a parameter error instead of an unfabricable envelope.
            val shortest = shortestCut(build(corrected, rolls))
            if (shortest + POSITION_TOLERANCE_MM < minimum) {
                return failure(
                    RouteFailureCode3D.NO_FABRICABLE_ROUTE,
                    "a cut of $shortest mm is below the minimum straight length",
                )
            }
            lengths = corrected
        }

        val plan = ChainPlan3D(start, build(lengths, rolls))
        val replayed = editor.replay(profile, plan)
            .fold({ it }, { return Fabrication3DResult.Failure(it) })
        val assembly = replayed.assembly.copy(
            metadata = replayed.assembly.metadata.copy(
                name = "Spatial route DN ${profile.nominalDiameter}",
                sourceCalculation = "spatial-route-v2",
            ),
        )
        val achieved = terminalFace(assembly)
            ?: return failure(RouteFailureCode3D.INTERNAL_CLOSURE_ERROR, "route has no terminal flange")
        val targetError = achieved.distanceTo(request.target.face)
        val terminalFrame = terminalFaceFrame(assembly)
            ?: return failure(RouteFailureCode3D.INTERNAL_CLOSURE_ERROR, "route has no terminal flange")
        val directionError = Math.toDegrees(
            acos(terminalFrame.dot(targetDirection).coerceIn(-1.0, 1.0)),
        )
        if (targetError > POSITION_TOLERANCE_MM || directionError > DIRECTION_TOLERANCE_DEG) {
            return failure(
                RouteFailureCode3D.INTERNAL_CLOSURE_ERROR,
                "closure error $targetError mm, direction error $directionError deg",
            )
        }
        val issues = graph.validate(assembly)
        if (issues.isNotEmpty()) {
            return Fabrication3DResult.Failure(Fabrication3DError.AssemblyInvalid(issues))
        }
        val blocked = graph.obstructions(assembly, request.obstacles, request.clearanceMm)
        if (blocked.isNotEmpty()) {
            return failure(RouteFailureCode3D.OBSTRUCTED, blocked.joinToString())
        }

        val cuts = assembly.parts.mapNotNull { part ->
            (part.definition.geometry as? StraightPipeGeometry3D)?.let { PipeCut3D(part.code, it.lengthMm) }
        }
        val allDiagnostics = if (request.obstacles.isEmpty()) {
            diagnostics
        } else {
            diagnostics + RouteDiagnostic3D.CLEARANCE_CHECKED
        }
        return ok(
            RouteSolution3D(
                assembly = assembly,
                plan = plan,
                topology = topology,
                elbowAnglesDeg = elbowAngles,
                pipeCuts = cuts,
                targetErrorMm = targetError,
                terminalDirectionErrorDeg = directionError,
                diagnostics = allDiagnostics,
            ),
        )
    }

    /**
     * Replays the chain up to each elbow and reads the incoming port frame, so the roll
     * that puts the bend plane onto [bendDirections] is measured rather than assumed.
     */
    private fun measureRolls(
        profile: AssemblyProfile3D,
        start: ChainStart3D,
        lengths: DoubleArray,
        currentRolls: DoubleArray,
        build: (DoubleArray, DoubleArray) -> List<ChainStep3D>,
        bendDirections: List<Vec3>,
    ): Fabrication3DResult<DoubleArray> {
        if (bendDirections.isEmpty()) return ok(DoubleArray(0))
        val rolls = currentRolls.copyOf()
        val fullSteps = build(lengths, rolls)
        val elbowPositions = fullSteps.withIndex()
            .filter { it.value is ChainStep3D.Elbow }
            .map { it.index }

        bendDirections.indices.forEach { bendIndex ->
            val stepIndex = elbowPositions.getOrNull(rolledElbowIndex(bendIndex, fullSteps))
                ?: return Fabrication3DResult.Failure(Fabrication3DError.Internal("measureRolls"))
            val prefix = ChainPlan3D(start, build(lengths, rolls).take(stepIndex))
            val replayed = editor.replay(profile, prefix)
                .fold({ it }, { return Fabrication3DResult.Failure(it) })
            val openEnd = replayed.assembly.freePorts().lastOrNull { reference ->
                replayed.assembly.worldPortOrNull(reference)?.connectionKind ==
                    com.planruler.fabrication3d.PortConnectionKind.BUTT_WELD
            } ?: return Fabrication3DResult.Failure(Fabrication3DError.Internal("measureRolls"))
            val frame = replayed.assembly.worldPort(openEnd).frame
            val bendNormal = frame.forward.cross(bendDirections[bendIndex]).normalizedOrNull()
                ?: return Fabrication3DResult.Failure(Fabrication3DError.Internal("measureRolls"))
            rolls[bendIndex] = signedAngleAroundAxis(frame.up, bendNormal, frame.forward)
        }
        return ok(rolls)
    }

    private fun shortestCut(steps: List<ChainStep3D>): Double = steps
        .filterIsInstance<ChainStep3D.Pipe>()
        .minOfOrNull { it.lengthMm }
        ?: 0.0

    /** Rolls are applied to the elbows that open a bend plane: the first and, after an offset, the turn. */
    private fun rolledElbowIndex(bendIndex: Int, steps: List<ChainStep3D>): Int {
        val elbowCount = steps.count { it is ChainStep3D.Elbow }
        return if (bendIndex == 0) 0 else elbowCount - 1
    }

    private fun terminalFace(assembly: ParametricAssembly3D): Vec3? = terminalFacePort(assembly)?.frame?.position

    private fun terminalFaceFrame(assembly: ParametricAssembly3D): Vec3? =
        terminalFacePort(assembly)?.frame?.forward

    private fun terminalFacePort(assembly: ParametricAssembly3D) = assembly.parts
        .lastOrNull { it.definition.kind == FabricationPartKind.FLANGE }
        ?.let { assembly.worldPortOrNull(PortReference3D(it.id, "face")) }

    private fun startOf(request: RouteRequest3D, direction: Vec3) = ChainStart3D(
        position = request.start.face,
        direction = direction,
        upHint = request.start.upHint,
        terminal = ChainTerminal3D.FLANGE,
    )

    private fun elbowAngles(request: RouteRequest3D): List<Double> {
        val allowed = (request.allowedElbowAnglesDeg ?: request.profile.rules.allowedElbowAnglesDeg)
            .filter { it.isFinite() && request.profile.rules.permits(it) }
            .distinct()
        val preferred = request.preferredElbowAngleDeg ?: return allowed
        return allowed.filter { abs(it - preferred) <= ANGLE_MATCH_TOLERANCE_DEG }
    }

    /** Rotates [direction] by [angleDeg] in the plane it spans with [towards]. */
    private fun rotateTowards(direction: Vec3, towards: Vec3, angleDeg: Double): Vec3 {
        val radians = Math.toRadians(angleDeg)
        return (direction * kotlin.math.cos(radians) + towards * kotlin.math.sin(radians)).normalized()
    }

    private fun validateRequest(request: RouteRequest3D): Fabrication3DResult.Failure? {
        if (!request.start.face.isFinite() || !request.target.face.isFinite()) {
            return invalid("terminals", ParameterRule3D.NOT_FINITE)
        }
        requireAtLeast("minimumStraightMm", request.minimumStraightMm, request.profile.rules.minPipeLengthMm)
            ?.let { return it }
        requireAtLeast("clearanceMm", request.clearanceMm, 0.0)?.let { return it }
        request.preferredElbowAngleDeg?.let { preferred ->
            requireFinite("preferredElbowAngleDeg", preferred)?.let { return it }
            if (!request.profile.rules.permits(preferred)) {
                return invalid("preferredElbowAngleDeg", ParameterRule3D.ANGLE_NOT_ALLOWED, preferred)
            }
        }
        request.allowedElbowAnglesDeg?.let { angles ->
            if (angles.isEmpty()) return invalid("allowedElbowAnglesDeg", ParameterRule3D.EMPTY_SET)
            if (angles.size > limits.maxRouteCandidates) {
                return Fabrication3DResult.Failure(
                    Fabrication3DError.QuotaExceeded(
                        EngineQuota3D.ROUTE_CANDIDATES,
                        limits.maxRouteCandidates,
                        angles.size,
                    ),
                )
            }
        }
        request.maxElbows?.let { maximum ->
            if (maximum < 0) return invalid("maxElbows", ParameterRule3D.MUST_BE_POSITIVE, maximum)
        }
        if (request.obstacles.size > limits.maxRouteCandidates) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(
                    EngineQuota3D.ROUTE_CANDIDATES,
                    limits.maxRouteCandidates,
                    request.obstacles.size,
                ),
            )
        }
        request.obstacles.forEach { obstacle: ObstacleBox3D ->
            if (!obstacle.bounds.minimum.isFinite() || !obstacle.bounds.maximum.isFinite()) {
                return invalid("obstacles", ParameterRule3D.NOT_FINITE, obstacle.id)
            }
        }
        return null
    }

    private fun failure(code: RouteFailureCode3D, detail: String?) =
        Fabrication3DResult.Failure(Fabrication3DError.RouteNotFound(code, detail))

    private companion object {
        const val POSITION_TOLERANCE_MM = 1e-6
        const val DIRECTION_TOLERANCE_DEG = 1e-5
        const val PARALLEL_TOLERANCE = 1e-9
        const val ANGLE_MATCH_TOLERANCE_DEG = 1e-7
        const val CLOSURE_PASSES = 3
        const val U_TURN_ELBOW_DEG = 90.0
    }
}

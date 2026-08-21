package com.planruler.fabrication3d

enum class EngineQuota3D {
    PARTS,
    TRIANGLES,
    UNDO_DEPTH,
    ELBOWS,
    ROUTE_CANDIDATES,
    SOLVER_ITERATIONS,
}

/**
 * Hard ceilings the engine enforces so a pathological input degrades into a typed
 * failure instead of an out-of-memory kill or a frozen frame on a phone.
 */
data class EngineLimits3D(
    val maxParts: Int = 256,
    val maxTriangles: Int = 240_000,
    val maxUndoDepth: Int = 64,
    val maxElbows: Int = 24,
    val maxRouteCandidates: Int = 4_096,
    val maxSolverIterations: Int = 256,
) {
    init {
        require(maxParts in 8..8_192) { "maxParts out of supported range" }
        require(maxTriangles in 1_000..4_000_000) { "maxTriangles out of supported range" }
        require(maxUndoDepth in 1..1_024) { "maxUndoDepth out of supported range" }
        require(maxElbows in 1..512) { "maxElbows out of supported range" }
        require(maxRouteCandidates in 1..1_000_000) { "maxRouteCandidates out of supported range" }
        require(maxSolverIterations in 1..100_000) { "maxSolverIterations out of supported range" }
    }

    fun ceiling(quota: EngineQuota3D): Int = when (quota) {
        EngineQuota3D.PARTS -> maxParts
        EngineQuota3D.TRIANGLES -> maxTriangles
        EngineQuota3D.UNDO_DEPTH -> maxUndoDepth
        EngineQuota3D.ELBOWS -> maxElbows
        EngineQuota3D.ROUTE_CANDIDATES -> maxRouteCandidates
        EngineQuota3D.SOLVER_ITERATIONS -> maxSolverIterations
    }

    companion object {
        /** Sized for a mid-range phone rendering with the software rasterizer. */
        val MOBILE = EngineLimits3D()
    }
}

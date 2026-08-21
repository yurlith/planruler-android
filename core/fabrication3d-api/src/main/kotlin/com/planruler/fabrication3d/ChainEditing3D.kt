package com.planruler.fabrication3d

/** How a chain end is terminated. */
enum class ChainTerminal3D { FLANGE, OPEN_END }

/** Where the run starts and which way it leaves the wall; no longer fixed to the origin. */
data class ChainStart3D(
    val position: Vec3 = Vec3.ZERO,
    val direction: Vec3 = Vec3.UNIT_X,
    val upHint: Vec3 = Vec3.UNIT_Z,
    val terminal: ChainTerminal3D = ChainTerminal3D.FLANGE,
)

/**
 * One fabricated element in run order. The chain is stored as this recipe rather than
 * as the resulting graph, so any parameter of an already placed element stays editable:
 * changing a step and replaying rebuilds everything downstream of it.
 */
sealed interface ChainStep3D {
    data class Pipe(val lengthMm: Double) : ChainStep3D

    /** [rollDeg] rotates the bend plane around the incoming pipe axis. */
    data class Elbow(val angleDeg: Double, val rollDeg: Double = 0.0) : ChainStep3D

    /**
     * A run element that also opens a takeoff. [branch] is a chain in its own right,
     * fabricated from the tee's branch port, so a spool can carry a real drop.
     */
    data class Tee(
        val rollDeg: Double = 0.0,
        val branch: List<ChainStep3D> = emptyList(),
    ) : ChainStep3D

    /**
     * Necks the bore down from whatever diameter the chain is currently carrying to
     * [smallOutsideDiameterMm]. Every element downstream of it — pipe, flange or cap —
     * is fabricated at the new bore; an elbow or tee downstream still expects the
     * original catalog diameter, so attaching one there is refused as a port mismatch
     * rather than built at the wrong size.
     */
    data class Reducer(
        val lengthMm: Double,
        val smallNominalDiameter: Int,
        val smallOutsideDiameterMm: Double,
        val smallWallThicknessMm: Double,
        val eccentric: Boolean = true,
        val rollDeg: Double = 0.0,
    ) : ChainStep3D

    data class Flange(val rollDeg: Double = 0.0) : ChainStep3D

    data class Cap(val rollDeg: Double = 0.0) : ChainStep3D
}

/**
 * Address of a step inside the recipe tree. `[]` is the main run itself, `[2]` its third
 * step, and `[2, 0]` the first step of the branch opened by that step.
 */
data class ChainPath3D(val indices: List<Int> = emptyList()) {
    fun child(index: Int): ChainPath3D = ChainPath3D(indices + index)

    val parent: ChainPath3D? get() = if (indices.isEmpty()) null else ChainPath3D(indices.dropLast(1))

    val lastIndex: Int? get() = indices.lastOrNull()

    val depth: Int get() = indices.size

    override fun toString(): String = if (indices.isEmpty()) "main" else indices.joinToString(".")

    companion object {
        val ROOT = ChainPath3D()
    }
}

data class ChainPlan3D(
    val start: ChainStart3D = ChainStart3D(),
    val steps: List<ChainStep3D> = emptyList(),
) {
    val elbowCount: Int get() = countSteps(steps) { it is ChainStep3D.Elbow }
    val pipeCount: Int get() = countSteps(steps) { it is ChainStep3D.Pipe }
    val teeCount: Int get() = countSteps(steps) { it is ChainStep3D.Tee }
    val reducerCount: Int get() = countSteps(steps) { it is ChainStep3D.Reducer }
    val stepCount: Int get() = countSteps(steps) { true }

    /** True once the main run is terminated, which is when nothing more may be appended to it. */
    val isClosed: Boolean get() = isChainClosed(steps)

    /** The chain addressed by [path]: the main run for the root, otherwise a tee's branch. */
    fun chainAt(path: ChainPath3D): List<ChainStep3D>? {
        if (path.indices.isEmpty()) return steps
        val owner = stepAt(path) ?: return null
        return (owner as? ChainStep3D.Tee)?.branch
    }

    fun stepAt(path: ChainPath3D): ChainStep3D? {
        var current: List<ChainStep3D> = steps
        var found: ChainStep3D? = null
        path.indices.forEach { index ->
            found = current.getOrNull(index) ?: return null
            current = (found as? ChainStep3D.Tee)?.branch ?: emptyList()
        }
        return found
    }

    /** Every step in replay order, so callers can enumerate branches without recursing. */
    fun paths(): List<ChainPath3D> = collectPaths(steps, ChainPath3D.ROOT)

    private companion object {
        fun countSteps(steps: List<ChainStep3D>, predicate: (ChainStep3D) -> Boolean): Int =
            steps.sumOf { step ->
                val own = if (predicate(step)) 1 else 0
                own + if (step is ChainStep3D.Tee) countSteps(step.branch, predicate) else 0
            }

        fun isChainClosed(steps: List<ChainStep3D>): Boolean =
            steps.lastOrNull().let { it is ChainStep3D.Flange || it is ChainStep3D.Cap }

        fun collectPaths(steps: List<ChainStep3D>, prefix: ChainPath3D): List<ChainPath3D> =
            steps.flatMapIndexed { index, step ->
                val path = prefix.child(index)
                listOf(path) + if (step is ChainStep3D.Tee) collectPaths(step.branch, path) else emptyList()
            }
    }
}

sealed interface ChainCommand3D {
    /** [parent] is the chain to extend: the root for the main run, or a tee for its branch. */
    data class Append(
        val step: ChainStep3D,
        val parent: ChainPath3D = ChainPath3D.ROOT,
    ) : ChainCommand3D

    data class InsertAt(val path: ChainPath3D, val step: ChainStep3D) : ChainCommand3D

    /** The edit that makes an already placed element parametric again. */
    data class Replace(val path: ChainPath3D, val step: ChainStep3D) : ChainCommand3D

    data class RemoveAt(val path: ChainPath3D) : ChainCommand3D

    data class MoveStart(val start: ChainStart3D) : ChainCommand3D

    data object Clear : ChainCommand3D
}

/**
 * Immutable editor state. [plan] is the source of truth, [assembly] is its replay, and
 * [stepPartIds] maps each step address to the part id the replay produced so a tap on the
 * viewport can find the step to edit.
 */
data class ChainEditorState3D(
    val profile: AssemblyProfile3D,
    val plan: ChainPlan3D,
    val assembly: ParametricAssembly3D,
    val stepPartIds: Map<ChainPath3D, String>,
    val selectedPartId: String? = null,
    val undoStack: List<ChainPlan3D> = emptyList(),
    val redoStack: List<ChainPlan3D> = emptyList(),
    /**
     * The recipe as it stood when the current drag began. A drag issues one preview per
     * frame, and without this the undo stack would take an entry per frame — "undo" would
     * step back a fraction of a millimetre instead of undoing the drag.
     */
    val previewBaseline: ChainPlan3D? = null,
) {
    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** True between the first [ChainEditor3D.preview] of a gesture and its commit or cancel. */
    val isPreviewing: Boolean get() = previewBaseline != null
    val elbowCount: Int get() = plan.elbowCount
    val pipeCount: Int get() = plan.pipeCount
    val teeCount: Int get() = plan.teeCount
    val reducerCount: Int get() = plan.reducerCount
    val isClosed: Boolean get() = plan.isClosed

    fun pathForPart(partId: String): ChainPath3D? =
        stepPartIds.entries.firstOrNull { it.value == partId }?.key

    fun partIdAt(path: ChainPath3D): String? = stepPartIds[path]

    fun stepForPart(partId: String): ChainStep3D? = pathForPart(partId)?.let(plan::stepAt)

    /** Tee branches available as append targets, in replay order. */
    fun branchPaths(): List<ChainPath3D> = plan.paths().filter { plan.stepAt(it) is ChainStep3D.Tee }

    /** True when the chain addressed by [parent] can still take another element. */
    fun canAppendTo(parent: ChainPath3D): Boolean {
        val chain = plan.chainAt(parent) ?: return false
        return !(chain.lastOrNull() is ChainStep3D.Flange || chain.lastOrNull() is ChainStep3D.Cap)
    }
}

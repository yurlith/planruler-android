package com.planruler.isometric

import com.planruler.model.IsoComponent
import com.planruler.model.IsoComponentId
import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoNode
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipe
import com.planruler.model.IsoPipeId
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import com.planruler.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

sealed interface IsoError {
    data class NotFound(val id: String) : IsoError
    data object ZeroLength : IsoError
    data object Duplicate : IsoError
    data object Overlap : IsoError
    /** A length change cannot move one side of a pipe that closes a loop. */
    data object Loop : IsoError
    data object NothingToUndo : IsoError
    data class Invalid(val reason: String) : IsoError
}

sealed interface IsoResult<out T> {
    data class Ok<T>(val value: T) : IsoResult<T>
    data class Error(val error: IsoError) : IsoResult<Nothing>
}

/** Where a new run begins. */
sealed interface RunStart {
    data class Node(val id: IsoNodeId) : RunStart
    data class Point(val position: Vec3) : RunStart
    /** Branch from the middle of an existing run; [distanceMm] is measured from its `from` node. */
    data class OnPipe(val pipeId: IsoPipeId, val distanceMm: Double) : RunStart
}

data class RunAdded(
    /** The last pipe created; a run crossing existing nodes is split at each of them. */
    val pipeId: IsoPipeId,
    val pipeIds: List<IsoPipeId>,
    val startNodeId: IsoNodeId,
    val endNodeId: IsoNodeId,
    /** True when the run ended on an existing node or pipe instead of open paper. */
    val joinedExisting: Boolean,
)

data class IsoEngineState(
    val sheet: IsometricSheet,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)

/**
 * The isometric drawing engine. It owns one sheet, applies validated commands to its 3D
 * network and keeps an undo history; drawings, fittings and quantities are derived from the
 * network by [IsoSceneBuilder], [IsoNetwork] and [IsoTakeoff].
 */
class IsometricEngine(
    initial: IsometricSheet,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
    private val historyLimit: Int = 100,
) {
    private val mutableState = MutableStateFlow(IsoEngineState(initial))
    val state: StateFlow<IsoEngineState> = mutableState.asStateFlow()
    val sheet: IsometricSheet get() = state.value.sheet
    private val undo = ArrayDeque<IsometricSheet>()
    private val redo = ArrayDeque<IsometricSheet>()

    /** Replaces the sheet without an undo record, e.g. when another sheet is opened. */
    fun load(sheet: IsometricSheet) {
        undo.clear()
        redo.clear()
        mutableState.value = IsoEngineState(sheet)
    }

    fun addRun(start: RunStart, delta: Vec3, spec: IsoPipeSpec): IsoResult<RunAdded> {
        if (!delta.isFinite || delta.length < IsoNetwork.MIN_LENGTH_MM) return IsoResult.Error(IsoError.ZeroLength)
        var draft = sheet
        val startNode = when (val resolved = resolveStart(draft, start)) {
            is IsoResult.Error -> return resolved
            is IsoResult.Ok -> {
                draft = resolved.value.first
                resolved.value.second
            }
        }
        val network = IsoNetwork(draft)
        val origin = network.position(startNode) ?: return IsoResult.Error(IsoError.NotFound(startNode.value))
        val direction = delta.normalized()
        // Doubling back over an existing run would create two pipes in the same place.
        val overlaps = network.incident[startNode].orEmpty().any { pipe ->
            network.directionFrom(pipe, startNode)?.let { it.dot(direction) > OVERLAP_COSINE } == true
        }
        if (overlaps) return IsoResult.Error(IsoError.Overlap)
        val end = origin + delta

        var joined = false
        var endNode: IsoNodeId? = draft.nodes.firstOrNull { it.position.toVec().distanceTo(end) <= MERGE_MM }?.id
        if (endNode == startNode) return IsoResult.Error(IsoError.ZeroLength)
        if (endNode != null) {
            joined = true
        } else {
            val hostPipe = draft.pipes.firstOrNull { pipe ->
                val a = network.position(pipe.from) ?: return@firstOrNull false
                val b = network.position(pipe.to) ?: return@firstOrNull false
                val (distance, t) = distanceToSegment(end, a, b)
                distance <= MERGE_MM && t * a.distanceTo(b) > MERGE_MM && (1 - t) * a.distanceTo(b) > MERGE_MM
            }
            if (hostPipe != null) {
                val hostNetwork = IsoNetwork(draft)
                val a = hostNetwork.position(hostPipe.from)!!
                when (val split = split(draft, hostPipe.id, a.distanceTo(end))) {
                    is IsoResult.Error -> return split
                    is IsoResult.Ok -> {
                        draft = split.value.first
                        endNode = split.value.second
                        joined = true
                    }
                }
            }
        }
        if (endNode == null) {
            val node = IsoNode(IsoNodeId(idGenerator()), end.toIsoPoint())
            draft = draft.copy(nodes = draft.nodes + node)
            endNode = node.id
        }
        val endId = requireNotNull(endNode)
        if (draft.pipes.any { (it.from == startNode && it.to == endId) || (it.from == endId && it.to == startNode) }) {
            return IsoResult.Error(IsoError.Duplicate)
        }

        // Existing nodes lying exactly on the new run become joints, keeping the topology true.
        val stops = draft.nodes
            .filter { it.id != startNode && it.id != endId }
            .mapNotNull { node ->
                val (distance, t) = distanceToSegment(node.position.toVec(), origin, end)
                if (distance <= MERGE_MM && t > 0.0 && t < 1.0) t to node.id else null
            }
            .sortedBy { it.first }
            .map { it.second }
        val chain = listOf(startNode) + stops + endId
        // A stretch that already exists between two joints is reused rather than doubled.
        val created = chain.zipWithNext()
            .filterNot { (from, to) ->
                draft.pipes.any { (it.from == from && it.to == to) || (it.from == to && it.to == from) }
            }
            .map { (from, to) -> IsoPipe(IsoPipeId(idGenerator()), from, to, spec) }
        if (created.isEmpty()) return IsoResult.Error(IsoError.Duplicate)
        draft = draft.copy(pipes = draft.pipes + created)
        commit(draft)
        return IsoResult.Ok(RunAdded(created.last().id, created.map { it.id }, startNode, endId, joined || stops.isNotEmpty()))
    }

    /** Sets the true length of a run; the side of [keep] stays, everything beyond moves along the run. */
    fun setPipeLength(pipeId: IsoPipeId, lengthMm: Double, keep: IsoNodeId? = null): IsoResult<Unit> {
        if (!lengthMm.isFinite() || lengthMm < IsoNetwork.MIN_LENGTH_MM) return IsoResult.Error(IsoError.ZeroLength)
        val network = IsoNetwork(sheet)
        val pipe = network.pipesById[pipeId] ?: return IsoResult.Error(IsoError.NotFound(pipeId.value))
        val anchor = keep?.takeIf { it == pipe.from || it == pipe.to } ?: pipe.from
        val moving = network.other(pipe, anchor)
        val anchorPosition = network.position(anchor) ?: return IsoResult.Error(IsoError.NotFound(anchor.value))
        val movingPosition = network.position(moving) ?: return IsoResult.Error(IsoError.NotFound(moving.value))
        val current = anchorPosition.distanceTo(movingPosition)
        if (current < IsoNetwork.MIN_LENGTH_MM) return IsoResult.Error(IsoError.ZeroLength)
        val side = network.sideOf(pipe, anchor) ?: return IsoResult.Error(IsoError.Loop)
        val shift = (movingPosition - anchorPosition) / current * (lengthMm - current)
        commit(translate(sheet, side, shift))
        return IsoResult.Ok(Unit)
    }

    /** Moves one node; connected runs follow as straight lines. */
    fun moveNode(nodeId: IsoNodeId, position: Vec3): IsoResult<Unit> {
        if (!position.isFinite) return IsoResult.Error(IsoError.Invalid("Position must be finite"))
        val current = sheet.nodes.firstOrNull { it.id == nodeId }
            ?: return IsoResult.Error(IsoError.NotFound(nodeId.value))
        if (current.position.toVec() == position) return IsoResult.Ok(Unit)
        val network = IsoNetwork(sheet)
        if (network.incident[nodeId].orEmpty().any { pipe ->
                network.position(network.other(pipe, nodeId))?.distanceTo(position)?.let { it < IsoNetwork.MIN_LENGTH_MM } == true
            }
        ) return IsoResult.Error(IsoError.ZeroLength)
        commit(sheet.copy(nodes = sheet.nodes.map { if (it.id == nodeId) it.copy(position = position.toIsoPoint()) else it }))
        return IsoResult.Ok(Unit)
    }

    /** Moves a node and everything connected to it, e.g. to raise a whole branch to a new level. */
    fun translateConnected(nodeId: IsoNodeId, shift: Vec3): IsoResult<Unit> {
        if (!shift.isFinite) return IsoResult.Error(IsoError.Invalid("Shift must be finite"))
        val network = IsoNetwork(sheet)
        if (nodeId !in network.nodesById) return IsoResult.Error(IsoError.NotFound(nodeId.value))
        val seen = linkedSetOf(nodeId)
        val queue = ArrayDeque(listOf(nodeId))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            network.incident[node].orEmpty().forEach { pipe ->
                val next = network.other(pipe, node)
                if (seen.add(next)) queue.addLast(next)
            }
        }
        commit(translate(sheet, seen, shift))
        return IsoResult.Ok(Unit)
    }

    /** Inserts a joint into a run, e.g. to branch a tee from it later. */
    fun splitPipe(pipeId: IsoPipeId, distanceFromStartMm: Double): IsoResult<IsoNodeId> =
        when (val result = split(sheet, pipeId, distanceFromStartMm)) {
            is IsoResult.Error -> result
            is IsoResult.Ok -> {
                commit(result.value.first)
                IsoResult.Ok(result.value.second)
            }
        }

    fun deletePipe(pipeId: IsoPipeId): IsoResult<Unit> {
        if (sheet.pipes.none { it.id == pipeId }) return IsoResult.Error(IsoError.NotFound(pipeId.value))
        val without = sheet.copy(
            pipes = sheet.pipes.filterNot { it.id == pipeId },
            components = sheet.components.filterNot { it.pipeId == pipeId },
        )
        commit(removeOrphans(without))
        return IsoResult.Ok(Unit)
    }

    /**
     * Deletes a joint. A straight joint between two equal runs is healed into one run so a
     * redundant split never leaves a gap; any other node takes its runs with it.
     */
    fun deleteNode(nodeId: IsoNodeId): IsoResult<Unit> {
        val network = IsoNetwork(sheet)
        if (nodeId !in network.nodesById) return IsoResult.Error(IsoError.NotFound(nodeId.value))
        val pipes = network.incident[nodeId].orEmpty()
        if (pipes.size == 2 && pipes[0].spec == pipes[1].spec) {
            val first = network.directionFrom(pipes[0], nodeId)
            val second = network.directionFrom(pipes[1], nodeId)
            if (first != null && second != null && first.dot(second) < -OVERLAP_COSINE) {
                commit(heal(network, nodeId, pipes[0], pipes[1]))
                return IsoResult.Ok(Unit)
            }
        }
        val removedPipes = pipes.mapTo(mutableSetOf()) { it.id }
        val without = sheet.copy(
            nodes = sheet.nodes.filterNot { it.id == nodeId },
            pipes = sheet.pipes.filterNot { it.id in removedPipes },
            components = sheet.components.filterNot { it.nodeId == nodeId || it.pipeId in removedPipes },
        )
        commit(removeOrphans(without))
        return IsoResult.Ok(Unit)
    }

    fun setPipeSpec(pipeIds: Collection<IsoPipeId>, spec: IsoPipeSpec): IsoResult<Int> {
        val targets = pipeIds.toSet()
        val count = sheet.pipes.count { it.id in targets }
        if (count == 0) return IsoResult.Error(IsoError.NotFound(pipeIds.firstOrNull()?.value ?: ""))
        commit(sheet.copy(pipes = sheet.pipes.map { if (it.id in targets) it.copy(spec = spec) else it }))
        return IsoResult.Ok(count)
    }

    fun setPipeLabel(pipeId: IsoPipeId, label: String?): IsoResult<Unit> {
        if (sheet.pipes.none { it.id == pipeId }) return IsoResult.Error(IsoError.NotFound(pipeId.value))
        val normalized = label?.trim()?.take(LABEL_LIMIT)?.takeIf(String::isNotEmpty)
        commit(sheet.copy(pipes = sheet.pipes.map { if (it.id == pipeId) it.copy(label = normalized) else it }))
        return IsoResult.Ok(Unit)
    }

    fun setNodeLabel(nodeId: IsoNodeId, label: String?): IsoResult<Unit> {
        if (sheet.nodes.none { it.id == nodeId }) return IsoResult.Error(IsoError.NotFound(nodeId.value))
        val normalized = label?.trim()?.take(LABEL_LIMIT)?.takeIf(String::isNotEmpty)
        commit(sheet.copy(nodes = sheet.nodes.map { if (it.id == nodeId) it.copy(label = normalized) else it }))
        return IsoResult.Ok(Unit)
    }

    fun addComponent(kind: IsoComponentKind, pipeId: IsoPipeId, offset: Double = 0.5): IsoResult<IsoComponentId> {
        if (sheet.pipes.none { it.id == pipeId }) return IsoResult.Error(IsoError.NotFound(pipeId.value))
        if (!offset.isFinite()) return IsoResult.Error(IsoError.Invalid("Offset must be finite"))
        val component = IsoComponent(IsoComponentId(idGenerator()), kind, pipeId = pipeId, offset = offset.coerceIn(0.05, 0.95))
        commit(sheet.copy(components = sheet.components + component))
        return IsoResult.Ok(component.id)
    }

    fun addComponentAtNode(kind: IsoComponentKind, nodeId: IsoNodeId): IsoResult<IsoComponentId> {
        if (sheet.nodes.none { it.id == nodeId }) return IsoResult.Error(IsoError.NotFound(nodeId.value))
        val component = IsoComponent(IsoComponentId(idGenerator()), kind, nodeId = nodeId)
        commit(sheet.copy(components = sheet.components + component))
        return IsoResult.Ok(component.id)
    }

    fun removeComponent(id: IsoComponentId): IsoResult<Unit> {
        if (sheet.components.none { it.id == id }) return IsoResult.Error(IsoError.NotFound(id.value))
        commit(removeOrphans(sheet.copy(components = sheet.components.filterNot { it.id == id })))
        return IsoResult.Ok(Unit)
    }

    fun rename(name: String): IsoResult<Unit> {
        val normalized = name.trim().take(NAME_LIMIT)
        if (normalized.isEmpty()) return IsoResult.Error(IsoError.Invalid("Name cannot be empty"))
        commit(sheet.copy(name = normalized))
        return IsoResult.Ok(Unit)
    }

    fun setProjection(style: IsoProjectionStyle) {
        if (style != sheet.projection) commit(sheet.copy(projection = style))
    }

    fun setGridStep(stepMm: Double): IsoResult<Unit> {
        if (!stepMm.isFinite() || stepMm < MIN_GRID_MM || stepMm > MAX_GRID_MM) {
            return IsoResult.Error(IsoError.Invalid("Grid step out of range"))
        }
        commit(sheet.copy(gridStepMm = stepMm))
        return IsoResult.Ok(Unit)
    }

    fun setWorkingLevel(levelMm: Double): IsoResult<Unit> {
        if (!levelMm.isFinite()) return IsoResult.Error(IsoError.Invalid("Level must be finite"))
        commit(sheet.copy(workingLevelMm = levelMm))
        return IsoResult.Ok(Unit)
    }

    fun setDisplay(dimensions: Boolean? = null, elevations: Boolean? = null, systemCodes: Boolean? = null) {
        val next = sheet.copy(
            showDimensions = dimensions ?: sheet.showDimensions,
            showElevations = elevations ?: sheet.showElevations,
            showSystemCodes = systemCodes ?: sheet.showSystemCodes,
        )
        if (next != sheet) commit(next)
    }

    /** View state is not an edit: it neither creates an undo step nor clears redo. */
    fun setViewport(viewport: ViewportState) {
        mutableState.value = state.value.copy(sheet = sheet.copy(viewport = viewport))
    }

    /** Replaces the network in one undoable step (used by generation from the plan). */
    fun replaceNetwork(source: IsometricSheet) {
        commit(
            sheet.copy(
                nodes = source.nodes,
                pipes = source.pipes,
                components = source.components,
            ),
        )
    }

    fun undo(): IsoResult<Unit> {
        val previous = undo.removeLastOrNull() ?: return IsoResult.Error(IsoError.NothingToUndo)
        redo.addLast(sheet)
        mutableState.value = IsoEngineState(previous.copy(viewport = sheet.viewport), canUndo = undo.isNotEmpty(), canRedo = true)
        return IsoResult.Ok(Unit)
    }

    fun redo(): IsoResult<Unit> {
        val next = redo.removeLastOrNull() ?: return IsoResult.Error(IsoError.NothingToUndo)
        undo.addLast(sheet)
        mutableState.value = IsoEngineState(next.copy(viewport = sheet.viewport), canUndo = true, canRedo = redo.isNotEmpty())
        return IsoResult.Ok(Unit)
    }

    private fun commit(next: IsometricSheet) {
        undo.addLast(sheet)
        while (undo.size > historyLimit) undo.removeFirst()
        redo.clear()
        mutableState.value = IsoEngineState(next.copy(modifiedAtEpochMs = clock()), canUndo = true, canRedo = false)
    }

    private fun resolveStart(draft: IsometricSheet, start: RunStart): IsoResult<Pair<IsometricSheet, IsoNodeId>> = when (start) {
        is RunStart.Node ->
            if (draft.nodes.any { it.id == start.id }) IsoResult.Ok(draft to start.id)
            else IsoResult.Error(IsoError.NotFound(start.id.value))
        is RunStart.OnPipe -> {
            val network = IsoNetwork(draft)
            val pipe = network.pipesById[start.pipeId]
            if (pipe == null) {
                IsoResult.Error(IsoError.NotFound(start.pipeId.value))
            } else {
                val length = network.length(pipe)
                when {
                    start.distanceMm <= MERGE_MM -> IsoResult.Ok(draft to pipe.from)
                    start.distanceMm >= length - MERGE_MM -> IsoResult.Ok(draft to pipe.to)
                    else -> split(draft, pipe.id, start.distanceMm)
                }
            }
        }
        is RunStart.Point -> {
            if (!start.position.isFinite) {
                IsoResult.Error(IsoError.Invalid("Position must be finite"))
            } else {
                val existing = draft.nodes.firstOrNull { it.position.toVec().distanceTo(start.position) <= MERGE_MM }
                if (existing != null) {
                    IsoResult.Ok(draft to existing.id)
                } else {
                    val network = IsoNetwork(draft)
                    val host = draft.pipes.firstOrNull { pipe ->
                        val a = network.position(pipe.from) ?: return@firstOrNull false
                        val b = network.position(pipe.to) ?: return@firstOrNull false
                        distanceToSegment(start.position, a, b).first <= MERGE_MM
                    }
                    if (host != null) {
                        split(draft, host.id, network.position(host.from)!!.distanceTo(start.position))
                    } else {
                        val node = IsoNode(IsoNodeId(idGenerator()), start.position.toIsoPoint())
                        IsoResult.Ok(draft.copy(nodes = draft.nodes + node) to node.id)
                    }
                }
            }
        }
    }

    private fun split(draft: IsometricSheet, pipeId: IsoPipeId, distanceMm: Double): IsoResult<Pair<IsometricSheet, IsoNodeId>> {
        val network = IsoNetwork(draft)
        val pipe = network.pipesById[pipeId] ?: return IsoResult.Error(IsoError.NotFound(pipeId.value))
        val a = network.position(pipe.from) ?: return IsoResult.Error(IsoError.NotFound(pipe.from.value))
        val b = network.position(pipe.to) ?: return IsoResult.Error(IsoError.NotFound(pipe.to.value))
        val length = a.distanceTo(b)
        if (!distanceMm.isFinite() || distanceMm <= MERGE_MM || distanceMm >= length - MERGE_MM) {
            return IsoResult.Error(IsoError.Invalid("Split point must lie inside the run"))
        }
        val middle = IsoNode(IsoNodeId(idGenerator()), (a + (b - a) / length * distanceMm).toIsoPoint())
        val first = pipe.copy(to = middle.id)
        val second = pipe.copy(id = IsoPipeId(idGenerator()), from = middle.id, label = null)
        val ratio = distanceMm / length
        val components = draft.components.map { component ->
            if (component.pipeId != pipe.id) {
                component
            } else if (component.offset <= ratio) {
                component.copy(offset = (component.offset / ratio).coerceIn(0.0, 1.0))
            } else {
                component.copy(pipeId = second.id, offset = ((component.offset - ratio) / (1.0 - ratio)).coerceIn(0.0, 1.0))
            }
        }
        val pipes = draft.pipes.flatMap { if (it.id == pipe.id) listOf(first, second) else listOf(it) }
        return IsoResult.Ok(draft.copy(nodes = draft.nodes + middle, pipes = pipes, components = components) to middle.id)
    }

    private fun heal(network: IsoNetwork, nodeId: IsoNodeId, first: IsoPipe, second: IsoPipe): IsometricSheet {
        val start = network.other(first, nodeId)
        val end = network.other(second, nodeId)
        val a = network.position(start)!!
        val joint = network.position(nodeId)!!
        val b = network.position(end)!!
        val firstLength = a.distanceTo(joint)
        val total = firstLength + joint.distanceTo(b)
        val merged = first.copy(from = start, to = end)
        // Offsets are fractions from a pipe's own `from` node; re-express them along the merged run.
        fun along(offset: Double, alignedWithMerged: Boolean, segmentStart: Double, segmentLength: Double): Double {
            val fraction = if (alignedWithMerged) offset else 1.0 - offset
            return ((segmentStart + fraction * segmentLength) / total).coerceIn(0.0, 1.0)
        }
        val components = sheet.components.map { component ->
            when (component.pipeId) {
                first.id -> component.copy(
                    pipeId = merged.id,
                    offset = along(component.offset, first.from == start, 0.0, firstLength),
                )
                second.id -> component.copy(
                    pipeId = merged.id,
                    offset = along(component.offset, second.from == nodeId, firstLength, total - firstLength),
                )
                else -> if (component.nodeId == nodeId) component.copy(nodeId = null, pipeId = merged.id, offset = firstLength / total) else component
            }
        }
        return sheet.copy(
            nodes = sheet.nodes.filterNot { it.id == nodeId },
            pipes = sheet.pipes.filterNot { it.id == second.id }.map { if (it.id == first.id) merged else it },
            components = components,
        )
    }

    private fun translate(source: IsometricSheet, nodes: Set<IsoNodeId>, shift: Vec3): IsometricSheet =
        source.copy(
            nodes = source.nodes.map { node ->
                if (node.id in nodes) node.copy(position = (node.position.toVec() + shift).toIsoPoint()) else node
            },
        )

    private fun removeOrphans(source: IsometricSheet): IsometricSheet {
        val used = buildSet {
            source.pipes.forEach { add(it.from); add(it.to) }
            source.components.forEach { component -> component.nodeId?.let(::add) }
        }
        val validPipes = source.pipes.mapTo(mutableSetOf()) { it.id }
        return source.copy(
            nodes = source.nodes.filter { it.id in used },
            components = source.components.filter { component ->
                (component.pipeId == null || component.pipeId in validPipes) &&
                    (component.nodeId == null || source.nodes.any { it.id == component.nodeId })
            },
        )
    }

    companion object {
        /** Two points closer than this are the same joint. */
        const val MERGE_MM = 0.5
        const val MIN_GRID_MM = 1.0
        const val MAX_GRID_MM = 10_000.0
        private const val LABEL_LIMIT = 80
        private const val NAME_LIMIT = 120
        private val OVERLAP_COSINE = kotlin.math.cos(Math.toRadians(0.5))

        fun newSheet(id: String, name: String, nowEpochMs: Long, projection: IsoProjectionStyle = IsoProjectionStyle.ISOMETRIC) =
            IsometricSheet(
                id = com.planruler.model.IsometricSheetId(id),
                name = name,
                projection = projection,
                createdAtEpochMs = nowEpochMs,
                modifiedAtEpochMs = nowEpochMs,
            )
    }
}

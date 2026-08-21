package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyMetadata3D
import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditor3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.EditorAction3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.Frame3D
import com.planruler.fabrication3d.ParameterRule3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ParametricPartDefinition3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.PortReference3D
import com.planruler.fabrication3d.Quaternion
import com.planruler.fabrication3d.alignFrame
import com.planruler.fabrication3d.fold
import com.planruler.fabrication3d.map

/**
 * The editor stores the chain as an ordered recipe and replays it. Editing an element
 * that is already welded in the model is therefore an ordinary list edit, and everything
 * downstream is rebuilt from the same rules that placed it originally. A tee carries its
 * branch inside the step, so the recipe is a tree and every element keeps an address.
 */
internal class DefaultChainEditor3D(
    private val limits: EngineLimits3D,
    private val parts: DefaultPartFactory3D,
    private val graph: DefaultAssemblyGraph3D,
) : ChainEditor3D {

    override fun create(
        profile: AssemblyProfile3D,
        start: ChainStart3D,
    ): Fabrication3DResult<ChainEditorState3D> = fromPlan(profile, ChainPlan3D(start = start))

    override fun fromPlan(
        profile: AssemblyProfile3D,
        plan: ChainPlan3D,
    ): Fabrication3DResult<ChainEditorState3D> = replay(profile, plan).map { replayed ->
        ChainEditorState3D(
            profile = profile,
            plan = plan,
            assembly = replayed.assembly,
            stepPartIds = replayed.stepPartIds,
            selectedPartId = replayed.stepPartIds.values.lastOrNull(),
        )
    }

    override fun preview(
        state: ChainEditorState3D,
        command: ChainCommand3D,
    ): Fabrication3DResult<ChainEditorState3D> {
        // The first preview of a gesture pins where it started; later ones keep that pin so
        // the whole drag stays a single undoable step.
        val baseline = state.previewBaseline ?: state.plan
        val nextPlan = applyCommand(state, command)
            .fold({ it }, { return Fabrication3DResult.Failure(it) })

        return replay(state.profile, nextPlan).map { replayed ->
            state.copy(
                plan = nextPlan,
                assembly = replayed.assembly,
                stepPartIds = replayed.stepPartIds,
                selectedPartId = focusOf(state, command, replayed),
                previewBaseline = baseline,
            )
        }
    }

    override fun commitPreview(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        val baseline = state.previewBaseline
            ?: return Fabrication3DResult.Failure(Fabrication3DError.NothingToDo(EditorAction3D.UPDATE))
        // A gesture that ended where it started is not worth an undo entry.
        if (baseline == state.plan) return ok(state.copy(previewBaseline = null))
        return ok(
            state.copy(
                previewBaseline = null,
                undoStack = (state.undoStack + baseline).takeLast(limits.maxUndoDepth),
                redoStack = emptyList(),
            ),
        )
    }

    override fun cancelPreview(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        val baseline = state.previewBaseline
            ?: return Fabrication3DResult.Failure(Fabrication3DError.NothingToDo(EditorAction3D.UPDATE))
        return replay(state.profile, baseline).map { replayed ->
            state.copy(
                plan = baseline,
                assembly = replayed.assembly,
                stepPartIds = replayed.stepPartIds,
                selectedPartId = state.selectedPartId
                    ?.takeIf { id -> replayed.assembly.partOrNull(id) != null },
                previewBaseline = null,
            )
        }
    }

    override fun execute(
        state: ChainEditorState3D,
        command: ChainCommand3D,
    ): Fabrication3DResult<ChainEditorState3D> {
        // Never drop a gesture still in flight; settle it into history first.
        val settled = if (state.isPreviewing) {
            commitPreview(state).fold({ it }, { return Fabrication3DResult.Failure(it) })
        } else {
            state
        }
        val nextPlan = applyCommand(settled, command)
            .fold({ it }, { return Fabrication3DResult.Failure(it) })

        return replay(settled.profile, nextPlan).map { replayed ->
            settled.copy(
                plan = nextPlan,
                assembly = replayed.assembly,
                stepPartIds = replayed.stepPartIds,
                selectedPartId = focusOf(settled, command, replayed),
                undoStack = (settled.undoStack + settled.plan).takeLast(limits.maxUndoDepth),
                redoStack = emptyList(),
            )
        }
    }

    /** The element a command acted on becomes the one the inspector edits next. */
    private fun focusOf(
        state: ChainEditorState3D,
        command: ChainCommand3D,
        replayed: Replayed,
    ): String? {
        val focus = when (command) {
            is ChainCommand3D.Append ->
                state.plan.chainAt(command.parent)?.let { command.parent.child(it.size) }
            is ChainCommand3D.InsertAt -> command.path
            is ChainCommand3D.Replace -> command.path
            else -> null
        }
        return focus?.let(replayed.stepPartIds::get)
            ?: state.selectedPartId?.takeIf { id -> replayed.assembly.partOrNull(id) != null }
            ?: replayed.stepPartIds.values.lastOrNull()
    }

    private fun applyCommand(
        state: ChainEditorState3D,
        command: ChainCommand3D,
    ): Fabrication3DResult<ChainPlan3D> = when (command) {
        is ChainCommand3D.Append -> {
            if (!state.canAppendTo(command.parent)) {
                invalid("steps", ParameterRule3D.OUT_OF_RANGE, "closed chain at ${command.parent}")
            } else {
                editChain(state.plan.steps, command.parent.indices) { chain -> chain + command.step }
                    .toPlan(state.plan, "parent")
            }
        }

        is ChainCommand3D.InsertAt -> withParentChain(state.plan, command.path) { chain, index ->
            if (index !in 0..chain.size) null else chain.toMutableList().apply { add(index, command.step) }
        }

        is ChainCommand3D.Replace -> withParentChain(state.plan, command.path) { chain, index ->
            if (index !in chain.indices) {
                null
            } else {
                chain.toMutableList().apply { this[index] = preserveBranch(chain[index], command.step) }
            }
        }

        is ChainCommand3D.RemoveAt -> withParentChain(state.plan, command.path) { chain, index ->
            if (index !in chain.indices) null else chain.toMutableList().apply { removeAt(index) }
        }

        is ChainCommand3D.MoveStart -> ok(state.plan.copy(start = command.start))

        ChainCommand3D.Clear -> ok(ChainPlan3D(start = state.plan.start))
    }

    /** Replacing a tee keeps the branch it already carries, so a parameter edit is not a demolition. */
    private fun preserveBranch(existing: ChainStep3D, replacement: ChainStep3D): ChainStep3D =
        if (existing is ChainStep3D.Tee && replacement is ChainStep3D.Tee && replacement.branch.isEmpty()) {
            replacement.copy(branch = existing.branch)
        } else {
            replacement
        }

    private fun withParentChain(
        plan: ChainPlan3D,
        path: ChainPath3D,
        edit: (List<ChainStep3D>, Int) -> List<ChainStep3D>?,
    ): Fabrication3DResult<ChainPlan3D> {
        val index = path.lastIndex
            ?: return invalid("path", ParameterRule3D.OUT_OF_RANGE, "the root is not a step")
        val parentChain = path.indices.dropLast(1)
        return editChain(plan.steps, parentChain) { chain -> edit(chain, index) }.toPlan(plan, "path")
    }

    private fun List<ChainStep3D>?.toPlan(plan: ChainPlan3D, parameter: String): Fabrication3DResult<ChainPlan3D> =
        this?.let { ok(plan.copy(steps = it)) }
            ?: invalid(parameter, ParameterRule3D.OUT_OF_RANGE)

    /** Applies [edit] to the chain addressed by [chainPath]; null when the address is not a chain. */
    private fun editChain(
        steps: List<ChainStep3D>,
        chainPath: List<Int>,
        edit: (List<ChainStep3D>) -> List<ChainStep3D>?,
    ): List<ChainStep3D>? {
        if (chainPath.isEmpty()) return edit(steps)
        val index = chainPath.first()
        val owner = steps.getOrNull(index) as? ChainStep3D.Tee ?: return null
        val branch = editChain(owner.branch, chainPath.drop(1), edit) ?: return null
        return steps.toMutableList().apply { this[index] = owner.copy(branch = branch) }
    }

    override fun undo(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        // Settle a gesture in flight first, so "undo" steps back over the whole drag.
        val settled = if (state.isPreviewing) {
            commitPreview(state).fold({ it }, { return Fabrication3DResult.Failure(it) })
        } else {
            state
        }
        return undoSettled(settled)
    }

    private fun undoSettled(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        val previous = state.undoStack.lastOrNull()
            ?: return Fabrication3DResult.Failure(Fabrication3DError.NothingToDo(EditorAction3D.UNDO))
        return replay(state.profile, previous).map { replayed ->
            state.copy(
                plan = previous,
                assembly = replayed.assembly,
                stepPartIds = replayed.stepPartIds,
                selectedPartId = replayed.stepPartIds.values.lastOrNull(),
                undoStack = state.undoStack.dropLast(1),
                redoStack = (state.redoStack + state.plan).takeLast(limits.maxUndoDepth),
            )
        }
    }

    override fun redo(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        val settled = if (state.isPreviewing) {
            commitPreview(state).fold({ it }, { return Fabrication3DResult.Failure(it) })
        } else {
            state
        }
        return redoSettled(settled)
    }

    private fun redoSettled(state: ChainEditorState3D): Fabrication3DResult<ChainEditorState3D> {
        val next = state.redoStack.lastOrNull()
            ?: return Fabrication3DResult.Failure(Fabrication3DError.NothingToDo(EditorAction3D.REDO))
        return replay(state.profile, next).map { replayed ->
            state.copy(
                plan = next,
                assembly = replayed.assembly,
                stepPartIds = replayed.stepPartIds,
                selectedPartId = replayed.stepPartIds.values.lastOrNull(),
                undoStack = (state.undoStack + state.plan).takeLast(limits.maxUndoDepth),
                redoStack = state.redoStack.dropLast(1),
            )
        }
    }

    override fun select(
        state: ChainEditorState3D,
        partId: String?,
    ): Fabrication3DResult<ChainEditorState3D> {
        if (partId != null && state.assembly.partOrNull(partId) == null) {
            return Fabrication3DResult.Failure(Fabrication3DError.UnknownPart(partId))
        }
        return ok(state.copy(selectedPartId = partId))
    }

    internal data class Replayed(
        val assembly: ParametricAssembly3D,
        val stepPartIds: Map<ChainPath3D, String>,
    )

    /**
     * The bore the chain is currently carrying. Every branch starts from the profile's
     * catalog diameter; a [ChainStep3D.Reducer] is the only step that changes it, and
     * only for the elements placed after it in the same chain.
     */
    private data class ActiveSection3D(
        val nominalDiameter: Int,
        val outsideDiameterMm: Double,
        val wallThicknessMm: Double,
    )

    private fun catalogSection(profile: AssemblyProfile3D) = ActiveSection3D(
        nominalDiameter = profile.nominalDiameter,
        outsideDiameterMm = profile.pipe.outsideDiameterMm,
        wallThicknessMm = profile.pipe.wallThicknessMm,
    )

    private class ReplayCursor(
        var assembly: ParametricAssembly3D,
        val counters: PartCounters = PartCounters(),
        val stepPartIds: LinkedHashMap<ChainPath3D, String> = LinkedHashMap(),
    )

    /** Rebuilds the whole graph from the recipe; the only place chain geometry is produced. */
    internal fun replay(
        profile: AssemblyProfile3D,
        plan: ChainPlan3D,
    ): Fabrication3DResult<Replayed> {
        validatePlan(profile, plan)?.let { return it }

        val cursor = ReplayCursor(
            ParametricAssembly3D(
                metadata = AssemblyMetadata3D(
                    name = "Chain DN ${profile.nominalDiameter}",
                    nominalDiameter = profile.nominalDiameter,
                    pressureClass = profile.pressureClass,
                    sourceCalculation = "chain-plan-v2",
                ),
                parts = emptyList(),
                connections = emptyList(),
            ),
        )
        var anchor: PortReference3D? = null

        if (plan.start.terminal == ChainTerminal3D.FLANGE) {
            val code = cursor.counters.next(FabricationPartKind.FLANGE)
            val definition = flangeDefinition(profile, code, catalogSection(profile))
                .fold({ it }, { return Fabrication3DResult.Failure(it) })
            val direction = plan.start.direction.normalizedOrNull()
                ?: return invalid("start.direction", ParameterRule3D.MUST_BE_POSITIVE, plan.start.direction)
            val faceTarget = Frame3D.of(plan.start.position, -direction, plan.start.upHint)
            val placement = guarded("placeStartFlange") {
                PartInstance3D(
                    id = code,
                    code = code,
                    definition = definition,
                    transform = alignFrame(definition.port("face").frame, faceTarget),
                )
            }.fold({ it }, { return Fabrication3DResult.Failure(it) })
            cursor.assembly = cursor.assembly.copy(parts = listOf(placement))
            anchor = PortReference3D(code, "weld")
        }

        replayChain(profile, plan, plan.steps, ChainPath3D.ROOT, anchor, catalogSection(profile), cursor)
            ?.let { return it }

        val issues = graph.validate(cursor.assembly)
        if (issues.isNotEmpty()) {
            return Fabrication3DResult.Failure(Fabrication3DError.AssemblyInvalid(issues))
        }
        return ok(Replayed(cursor.assembly, cursor.stepPartIds))
    }

    /** Returns a failure, or null when the whole chain and its branches were placed. */
    private fun replayChain(
        profile: AssemblyProfile3D,
        plan: ChainPlan3D,
        steps: List<ChainStep3D>,
        prefix: ChainPath3D,
        incomingAnchor: PortReference3D?,
        incomingSection: ActiveSection3D,
        cursor: ReplayCursor,
    ): Fabrication3DResult.Failure? {
        var anchor = incomingAnchor
        var active = incomingSection
        steps.forEachIndexed { index, step ->
            val path = prefix.child(index)
            val code = cursor.counters.next(step.kind())
            val definition = definitionFor(profile, step, code, active)
                .fold({ it }, { return Fabrication3DResult.Failure(it) })
            val currentAnchor = anchor

            cursor.assembly = if (currentAnchor == null) {
                placeFirst(cursor.assembly, plan.start, definition, code, step.attachingPortId(), step.rollDeg())
                    .fold({ it }, { return Fabrication3DResult.Failure(it) })
            } else {
                graph.attach(
                    assembly = cursor.assembly,
                    anchor = currentAnchor,
                    newPartId = code,
                    newPartCode = code,
                    definition = definition,
                    attachingPortId = step.attachingPortId(),
                    axialGapMm = profile.weldGapMm,
                    rollDeg = step.rollDeg(),
                ).fold({ it }, { return Fabrication3DResult.Failure(it) })
            }
            cursor.stepPartIds[path] = code

            if (step is ChainStep3D.Tee) {
                replayChain(
                    profile = profile,
                    plan = plan,
                    steps = step.branch,
                    prefix = path,
                    incomingAnchor = PortReference3D(code, "branch"),
                    incomingSection = active,
                    cursor = cursor,
                )?.let { return it }
            }
            if (step is ChainStep3D.Reducer) {
                active = ActiveSection3D(
                    nominalDiameter = step.smallNominalDiameter,
                    outsideDiameterMm = step.smallOutsideDiameterMm,
                    wallThicknessMm = step.smallWallThicknessMm,
                )
            }
            anchor = step.outgoingPortId()?.let { PortReference3D(code, it) }
        }
        return null
    }

    /** Places the very first element of an open-ended run, where there is no anchor yet. */
    private fun placeFirst(
        assembly: ParametricAssembly3D,
        start: ChainStart3D,
        definition: ParametricPartDefinition3D,
        code: String,
        attachingPortId: String,
        rollDeg: Double,
    ): Fabrication3DResult<ParametricAssembly3D> {
        val direction = start.direction.normalizedOrNull()
            ?: return invalid("start.direction", ParameterRule3D.MUST_BE_POSITIVE, start.direction)
        val localPort = definition.portOrNull(attachingPortId)
            ?: return Fabrication3DResult.Failure(
                Fabrication3DError.UnknownPort(PortReference3D(code, attachingPortId)),
            )
        return guarded("placeFirst") {
            val up = Quaternion.axisAngle(direction, rollDeg).rotate(start.upHint)
            val target = Frame3D.of(start.position, -direction, up)
            assembly.copy(
                parts = assembly.parts + PartInstance3D(
                    id = code,
                    code = code,
                    definition = definition,
                    transform = alignFrame(localPort.frame, target),
                ),
            )
        }
    }

    private fun validatePlan(profile: AssemblyProfile3D, plan: ChainPlan3D): Fabrication3DResult.Failure? {
        val startParts = if (plan.start.terminal == ChainTerminal3D.FLANGE) 1 else 0
        val totalParts = plan.stepCount + startParts
        if (totalParts > limits.maxParts) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.PARTS, limits.maxParts, totalParts),
            )
        }
        val elbows = plan.elbowCount
        val elbowCeiling = minOf(profile.rules.maxElbows, limits.maxElbows)
        if (elbows > elbowCeiling) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.ELBOWS, elbowCeiling, elbows),
            )
        }
        if (!plan.start.position.isFinite()) {
            return invalid("start.position", ParameterRule3D.NOT_FINITE, plan.start.position)
        }
        if (plan.start.direction.normalizedOrNull() == null) {
            return invalid("start.direction", ParameterRule3D.MUST_BE_POSITIVE, plan.start.direction)
        }
        return validateChain(profile, plan.steps, ChainPath3D.ROOT)
    }

    private fun validateChain(
        profile: AssemblyProfile3D,
        steps: List<ChainStep3D>,
        prefix: ChainPath3D,
    ): Fabrication3DResult.Failure? {
        steps.forEachIndexed { index, step ->
            val path = prefix.child(index)
            when (step) {
                is ChainStep3D.Pipe ->
                    requireAtLeast("steps[$path].lengthMm", step.lengthMm, profile.rules.minPipeLengthMm)
                        ?.let { return it }

                is ChainStep3D.Elbow -> {
                    requireFinite("steps[$path].angleDeg", step.angleDeg)?.let { return it }
                    requireFinite("steps[$path].rollDeg", step.rollDeg)?.let { return it }
                    if (!profile.rules.permits(step.angleDeg)) {
                        return invalid(
                            "steps[$path].angleDeg",
                            ParameterRule3D.ANGLE_NOT_ALLOWED,
                            step.angleDeg,
                            "${profile.rules.minElbowAngleDeg}..${profile.rules.maxElbowAngleDeg}",
                        )
                    }
                }

                is ChainStep3D.Tee -> {
                    requireFinite("steps[$path].rollDeg", step.rollDeg)?.let { return it }
                    if (profile.tee == null) {
                        return invalid("steps[$path]", ParameterRule3D.EMPTY_SET, "no tee in the catalog")
                    }
                    validateChain(profile, step.branch, path)?.let { return it }
                }

                is ChainStep3D.Reducer -> {
                    requireAtLeast("steps[$path].lengthMm", step.lengthMm, profile.rules.minPipeLengthMm)
                        ?.let { return it }
                    requireFinite("steps[$path].rollDeg", step.rollDeg)?.let { return it }
                    if (step.smallNominalDiameter <= 0) {
                        return invalid(
                            "steps[$path].smallNominalDiameter",
                            ParameterRule3D.MUST_BE_POSITIVE,
                            step.smallNominalDiameter,
                        )
                    }
                }

                is ChainStep3D.Flange -> requireFinite("steps[$path].rollDeg", step.rollDeg)?.let { return it }
                is ChainStep3D.Cap -> requireFinite("steps[$path].rollDeg", step.rollDeg)?.let { return it }
            }
            if (step.outgoingPortId() == null && index != steps.lastIndex) {
                return invalid("steps[$path]", ParameterRule3D.OUT_OF_RANGE, "step after a terminal element")
            }
        }
        return null
    }

    private fun definitionFor(
        profile: AssemblyProfile3D,
        step: ChainStep3D,
        code: String,
        active: ActiveSection3D,
    ): Fabrication3DResult<ParametricPartDefinition3D> = when (step) {
        is ChainStep3D.Pipe -> parts.straightPipe(
            id = "chain-pipe:$code:${step.lengthMm}",
            catalogId = profile.pipe.catalogId.takeIf { active.nominalDiameter == profile.nominalDiameter },
            nominalDiameter = active.nominalDiameter,
            lengthMm = step.lengthMm,
            outsideDiameterMm = active.outsideDiameterMm,
            wallThicknessMm = active.wallThicknessMm,
        )

        is ChainStep3D.Elbow -> parts.elbow(
            id = "chain-elbow:$code:${step.angleDeg}",
            catalogId = profile.elbow.catalogId,
            nominalDiameter = profile.nominalDiameter,
            angleDeg = step.angleDeg,
            centerlineRadiusMm = profile.elbow.centerlineRadiusMm,
            outsideDiameterMm = profile.elbow.outsideDiameterMm,
            wallThicknessMm = profile.elbow.wallThicknessMm,
        )

        is ChainStep3D.Tee -> {
            val tee = profile.tee
            if (tee == null) {
                invalid("tee", ParameterRule3D.EMPTY_SET, "no tee in the catalog")
            } else {
                parts.equalTee(
                    id = "chain-tee:$code",
                    catalogId = tee.catalogId,
                    nominalDiameter = profile.nominalDiameter,
                    overallRunMm = tee.overallRunMm,
                    branchCenterToEndMm = tee.branchCenterToEndMm,
                    outsideDiameterMm = tee.outsideDiameterMm,
                    wallThicknessMm = tee.wallThicknessMm,
                )
            }
        }

        is ChainStep3D.Reducer -> parts.reducer(
            id = "chain-reducer:$code",
            catalogId = null,
            largeNominalDiameter = active.nominalDiameter,
            smallNominalDiameter = step.smallNominalDiameter,
            lengthMm = step.lengthMm,
            largeOutsideDiameterMm = active.outsideDiameterMm,
            largeWallThicknessMm = active.wallThicknessMm,
            smallOutsideDiameterMm = step.smallOutsideDiameterMm,
            smallWallThicknessMm = step.smallWallThicknessMm,
            eccentric = step.eccentric,
        )

        is ChainStep3D.Flange -> flangeDefinition(profile, code, active)

        is ChainStep3D.Cap -> parts.cap(
            id = "chain-cap:$code",
            catalogId = null,
            nominalDiameter = active.nominalDiameter,
            heightMm = active.outsideDiameterMm * 0.5,
            outsideDiameterMm = active.outsideDiameterMm,
            wallThicknessMm = active.wallThicknessMm,
        )
    }

    private fun flangeDefinition(
        profile: AssemblyProfile3D,
        code: String,
        active: ActiveSection3D,
    ): Fabrication3DResult<ParametricPartDefinition3D> = parts.weldNeckFlange(
        id = "chain-flange:$code",
        catalogId = profile.flange.catalogId,
        nominalDiameter = active.nominalDiameter,
        faceToWeldMm = profile.flange.faceToWeldMm,
        thicknessMm = profile.flange.thicknessMm,
        outsideDiameterMm = profile.flange.outsideDiameterMm,
        pipeOutsideDiameterMm = active.outsideDiameterMm,
        pipeWallThicknessMm = active.wallThicknessMm,
        boltCircleDiameterMm = profile.flange.boltCircleDiameterMm,
        boltHoleCount = profile.flange.boltHoleCount,
        boltHoleDiameterMm = profile.flange.boltHoleDiameterMm,
    )

    private fun ChainStep3D.kind(): FabricationPartKind = when (this) {
        is ChainStep3D.Pipe -> FabricationPartKind.PIPE
        is ChainStep3D.Elbow -> FabricationPartKind.ELBOW
        is ChainStep3D.Tee -> FabricationPartKind.TEE
        is ChainStep3D.Reducer -> FabricationPartKind.REDUCER
        is ChainStep3D.Flange -> FabricationPartKind.FLANGE
        is ChainStep3D.Cap -> FabricationPartKind.CAP
    }

    private fun ChainStep3D.attachingPortId(): String = when (this) {
        is ChainStep3D.Pipe, is ChainStep3D.Elbow -> "start"
        is ChainStep3D.Tee -> "run-start"
        is ChainStep3D.Reducer -> "large"
        is ChainStep3D.Flange, is ChainStep3D.Cap -> "weld"
    }

    private fun ChainStep3D.outgoingPortId(): String? = when (this) {
        is ChainStep3D.Pipe, is ChainStep3D.Elbow -> "end"
        is ChainStep3D.Tee -> "run-end"
        is ChainStep3D.Reducer -> "small"
        is ChainStep3D.Flange, is ChainStep3D.Cap -> null
    }

    private fun ChainStep3D.rollDeg(): Double = when (this) {
        is ChainStep3D.Pipe -> 0.0
        is ChainStep3D.Elbow -> rollDeg
        is ChainStep3D.Tee -> rollDeg
        is ChainStep3D.Reducer -> rollDeg
        is ChainStep3D.Flange -> rollDeg
        is ChainStep3D.Cap -> rollDeg
    }

    private class PartCounters {
        private val counts = mutableMapOf<FabricationPartKind, Int>()

        fun next(kind: FabricationPartKind): String {
            val index = counts.getOrDefault(kind, 0) + 1
            counts[kind] = index
            return "${kind.prefix()}$index"
        }

        private fun FabricationPartKind.prefix(): String = when (this) {
            FabricationPartKind.PIPE -> "P"
            FabricationPartKind.ELBOW -> "E"
            FabricationPartKind.FLANGE -> "F"
            FabricationPartKind.TEE -> "T"
            FabricationPartKind.REDUCER -> "R"
            FabricationPartKind.CAP -> "K"
        }
    }
}

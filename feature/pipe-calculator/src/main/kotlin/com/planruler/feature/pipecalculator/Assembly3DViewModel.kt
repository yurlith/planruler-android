package com.planruler.feature.pipecalculator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.planruler.fabrication3d.AssemblyIssue3D
import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.AssemblyProfile3D
import com.planruler.fabrication3d.AssemblyProfileOverrides3D
import com.planruler.fabrication3d.ChainCommand3D
import com.planruler.fabrication3d.ChainEditorState3D
import com.planruler.fabrication3d.ChainPath3D
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.Fabrication3DEngine
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.MeshQuality3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.RouteRequest3D
import com.planruler.fabrication3d.RouteSolution3D
import com.planruler.fabrication3d.RouteTerminal3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.catalog.toAssemblyProfile3D
import com.planruler.fabrication3d.catalog.toChainPlan3D
import com.planruler.fabrication3d.catalog.toParametricAssembly3D
import com.planruler.fabrication3d.fold
import com.planruler.fabrication3d.getOrNull
import com.planruler.pipecalculator.FlangedOffsetAssemblyResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal enum class AssemblyWorkspaceMode3D { VERIFIED, MANUAL, AUTO, PARAMETERS }

internal data class Assembly3DUiState(
    val mode: AssemblyWorkspaceMode3D = AssemblyWorkspaceMode3D.VERIFIED,
    val profile: AssemblyProfile3D? = null,
    val verified: ParametricAssembly3D? = null,
    val editor: ChainEditorState3D? = null,
    val solution: RouteSolution3D? = null,
    val solveTarget: Vec3? = null,
    val quality: MeshQuality3D = MeshQuality3D.NORMAL,
    val mesh: AssemblyMesh3D? = null,
    val selectedPartId: String? = null,
    val activeBranch: ChainPath3D = ChainPath3D.ROOT,
    val error: Fabrication3DError? = null,
    val busy: Boolean = false,
    val selfIntersections: List<AssemblyIssue3D> = emptyList(),
) {
    val shownAssembly: ParametricAssembly3D?
        get() = when (mode) {
            AssemblyWorkspaceMode3D.MANUAL -> editor?.assembly
            AssemblyWorkspaceMode3D.AUTO -> solution?.assembly ?: verified
            else -> verified
        }
}

/**
 * Keeps the 3D workshop off the composition thread. Every engine call runs on a background
 * dispatcher, the mesh is rebuilt in a job that supersedes the previous one, and the state
 * outlives both the list item that scrolled away and the activity that was recreated.
 */
internal class Assembly3DViewModel(
    private val engine: Fabrication3DEngine,
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(Assembly3DUiState())
    val state: StateFlow<Assembly3DUiState> = _state.asStateFlow()

    private val mutations = Mutex()
    private var baseProfile: AssemblyProfile3D? = null
    private var boundPlan: ChainPlan3D? = null
    private var overrides = AssemblyProfileOverrides3D.NONE
    private var meshJob: Job? = null
    private var qualityBeforePreview: MeshQuality3D? = null

    /** Attaches the workshop calculation. Re-binding an unchanged profile is a no-op. */
    fun bind(result: FlangedOffsetAssemblyResult, restoredPlan: ChainPlan3D) {
        val base = result.toAssemblyProfile3D()
        if (base == baseProfile && restoredPlan == boundPlan && _state.value.editor != null) return
        baseProfile = base
        boundPlan = restoredPlan
        launchWork {
            val profile = engine.profiles.apply(base, overrides).getOrNull() ?: base
            val verified = result.toParametricAssembly3D(engine.parts).getOrNull()
            val editor = engine.chains.fromPlan(profile, restoredPlan).getOrNull()
                ?: engine.chains.create(profile).getOrNull()
            _state.update {
                it.copy(profile = profile, verified = verified, editor = editor, error = null)
            }
        }
    }

    fun setMode(mode: AssemblyWorkspaceMode3D) {
        _state.update { it.copy(mode = mode, error = null) }
        refreshMesh()
    }

    fun setQuality(quality: MeshQuality3D) {
        if (qualityBeforePreview != null) {
            // The explicit quality picked while dragging becomes the quality restored at release.
            qualityBeforePreview = quality
            return
        }
        if (_state.value.quality == quality) return
        _state.update { it.copy(quality = quality) }
        refreshMesh()
    }

    fun setActiveBranch(path: ChainPath3D) {
        _state.update { it.copy(activeBranch = path) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Re-applies the fitter's overrides and replays the current recipe on the new profile. */
    fun setOverrides(next: AssemblyProfileOverrides3D) {
        if (next == overrides) return
        overrides = next
        val base = baseProfile ?: return
        launchWork {
            when (val applied = engine.profiles.apply(base, next)) {
                is Fabrication3DResult.Failure -> _state.update { it.copy(error = applied.error) }
                is Fabrication3DResult.Ok -> {
                    val profile = applied.value
                    val current = _state.value.editor
                    val editor = current
                        ?.let { existing ->
                            engine.chains.fromPlan(profile, existing.plan).getOrNull()?.copy(
                                undoStack = existing.undoStack,
                                redoStack = existing.redoStack,
                                selectedPartId = existing.selectedPartId,
                            )
                        }
                        ?: engine.chains.create(profile).getOrNull()
                    _state.update { it.copy(profile = profile, editor = editor, error = null) }
                }
            }
        }
    }

    fun execute(command: ChainCommand3D) = editorWork { engine.chains.execute(it, command) }

    /** A frame of a direct-manipulation gesture; no history entry is created yet. */
    fun preview(command: ChainCommand3D) {
        if (qualityBeforePreview == null) {
            qualityBeforePreview = _state.value.quality
            _state.update { it.copy(quality = MeshQuality3D.DRAFT) }
        }
        editorWork { engine.chains.preview(it, command) }
    }

    /** Settles every queued preview frame as exactly one undoable edit. */
    fun commitPreview() = settlePreview { engine.chains.commitPreview(it) }

    /** Restores the recipe from gesture start without adding anything to history. */
    fun cancelPreview() = settlePreview { engine.chains.cancelPreview(it) }

    fun undo() = editorWork { engine.chains.undo(it) }

    fun redo() = editorWork { engine.chains.redo(it) }

    fun select(partId: String?) = editorWork { engine.chains.select(it, partId) }

    /** Selection belongs to the job, so the 3D scene and every generated view stay linked. */
    fun selectPart(partId: String?) {
        _state.update { it.copy(selectedPartId = partId) }
        if (_state.value.mode == AssemblyWorkspaceMode3D.MANUAL) select(partId)
    }

    fun solve(target: Vec3, direction: Vec3, minimumStraightMm: Double, preferredAngleDeg: Double?) {
        // Never leave an older route visible while a new set of site dimensions is being checked.
        _state.update { it.copy(busy = true, solution = null, solveTarget = null, error = null) }
        launchWork {
            val profile = _state.value.profile ?: run {
                _state.update { it.copy(busy = false) }
                return@launchWork
            }
            val outcome = engine.router.solve(
                RouteRequest3D(
                    profile = profile,
                    target = RouteTerminal3D(target, direction),
                    preferredElbowAngleDeg = preferredAngleDeg,
                    minimumStraightMm = minimumStraightMm,
                ),
            )
            outcome.fold(
                onOk = { solution ->
                    _state.update {
                        it.copy(solution = solution, solveTarget = target, error = null, busy = false)
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(solution = null, solveTarget = null, error = error, busy = false)
                    }
                },
            )
        }
    }

    /**
     * Opens the verified calculation in the editor. Until this existed the standard
     * two-elbow spool was a picture: correct, and impossible to depart from.
     */
    fun editVerified(result: FlangedOffsetAssemblyResult) {
        launchWork {
            val profile = _state.value.profile ?: return@launchWork
            engine.chains.fromPlan(profile, result.toChainPlan3D()).fold(
                onOk = { editor ->
                    _state.update {
                        it.copy(
                            editor = editor,
                            mode = AssemblyWorkspaceMode3D.MANUAL,
                            activeBranch = ChainPath3D.ROOT,
                            error = null,
                        )
                    }
                },
                onFailure = { error -> _state.update { it.copy(error = error) } },
            )
        }
    }

    /** Moves the solved route into the editor so it can be tuned by hand. */
    fun adoptSolution() {
        launchWork {
            val profile = _state.value.profile ?: return@launchWork
            val plan = _state.value.solution?.plan ?: return@launchWork
            engine.chains.fromPlan(profile, plan).fold(
                onOk = { editor ->
                    _state.update {
                        it.copy(
                            editor = editor,
                            mode = AssemblyWorkspaceMode3D.MANUAL,
                            activeBranch = ChainPath3D.ROOT,
                            error = null,
                        )
                    }
                },
                onFailure = { error -> _state.update { it.copy(error = error) } },
            )
        }
    }

    private fun editorWork(block: (ChainEditorState3D) -> Fabrication3DResult<ChainEditorState3D>) {
        launchWork {
            // Read inside the lock: a tap that arrives while the previous edit is still in
            // flight must build on its result, not on the state the screen last rendered.
            val current = _state.value.editor ?: return@launchWork
            block(current).fold(
                onOk = { next ->
                    _state.update { it.copy(editor = next, selectedPartId = next.selectedPartId, error = null) }
                },
                onFailure = { error -> _state.update { it.copy(error = error) } },
            )
        }
    }

    private fun settlePreview(
        block: (ChainEditorState3D) -> Fabrication3DResult<ChainEditorState3D>,
    ) {
        launchWork {
            val current = _state.value.editor ?: return@launchWork
            block(current).fold(
                onOk = { next ->
                    val restoredQuality = qualityBeforePreview ?: _state.value.quality
                    qualityBeforePreview = null
                    _state.update {
                        it.copy(
                            editor = next,
                            selectedPartId = next.selectedPartId,
                            quality = restoredQuality,
                            error = null,
                        )
                    }
                },
                onFailure = { error ->
                    val restoredQuality = qualityBeforePreview ?: _state.value.quality
                    qualityBeforePreview = null
                    _state.update { it.copy(quality = restoredQuality, error = error) }
                },
            )
        }
    }

    /** Serialises every mutation so rapid input cannot drop an edit. */
    private fun launchWork(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutations.withLock { withContext(background) { block() } }
            refreshMesh()
        }
    }

    /**
     * Rebuilds the mesh and re-checks for centreline self-intersections for whatever the
     * screen shows; a newer request cancels the older one. Both come from the same
     * assembly snapshot, so a stretched-out edit never shows a mesh and a warning that
     * were computed against two different geometries.
     */
    private fun refreshMesh() {
        val current = _state.value
        val assembly = current.shownAssembly
        meshJob?.cancel()
        if (assembly == null) {
            _state.update { it.copy(mesh = null, selfIntersections = emptyList()) }
            return
        }
        meshJob = viewModelScope.launch {
            val built = withContext(background) { engine.mesh.build(assembly, current.quality) }
            val intersections = withContext(background) { engine.graph.selfIntersections(assembly) }
            coroutineContext.ensureActive()
            built.fold(
                onOk = { mesh -> _state.update { it.copy(mesh = mesh, selfIntersections = intersections) } },
                onFailure = { error ->
                    _state.update { it.copy(mesh = null, error = error, selfIntersections = intersections) }
                },
            )
        }
    }

    companion object {
        fun factory(engine: Fabrication3DEngine) = viewModelFactory {
            initializer { Assembly3DViewModel(engine) }
        }
    }
}

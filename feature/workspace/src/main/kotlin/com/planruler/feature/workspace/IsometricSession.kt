package com.planruler.feature.workspace

import com.planruler.isometric.IsoEngineState
import com.planruler.isometric.IsoResult
import com.planruler.isometric.IsometricEngine
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import com.planruler.model.IsometricSheetId
import com.planruler.model.ViewportState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * The isometric sheets of one project and the engine of the sheet being edited. Every
 * successful edit is written back into the sheet list and reported through [onChanged];
 * the workspace stores that list with the project like any other project data.
 */
class IsometricSession(
    private val clock: () -> Long = System::currentTimeMillis,
    private val ids: () -> String = { UUID.randomUUID().toString() },
    private val onChanged: (sheets: List<IsometricSheet>, active: IsometricSheetId?) -> Unit,
) {
    private val mutableSheets = MutableStateFlow<List<IsometricSheet>>(emptyList())
    val sheets: StateFlow<List<IsometricSheet>> = mutableSheets.asStateFlow()
    private val mutableActive = MutableStateFlow<IsometricSheetId?>(null)
    val active: StateFlow<IsometricSheetId?> = mutableActive.asStateFlow()
    val engine = IsometricEngine(IsometricEngine.newSheet(PLACEHOLDER, "", 0L), clock, ids)
    val state: StateFlow<IsoEngineState> get() = engine.state

    /** Takes the stored sheets of a freshly loaded project; nothing is reported back. */
    fun attach(stored: List<IsometricSheet>, activeId: IsometricSheetId?) {
        mutableSheets.value = stored
        val current = stored.firstOrNull { it.id == activeId } ?: stored.firstOrNull()
        mutableActive.value = current?.id
        current?.let(engine::load)
    }

    /** Opens the last used sheet, creating the first one when the project has none. */
    fun open(defaultName: String): IsometricSheet {
        val current = mutableSheets.value.firstOrNull { it.id == mutableActive.value }
        if (current != null) {
            if (engine.sheet.id != current.id) engine.load(current)
            return current
        }
        return create(defaultName)
    }

    fun create(name: String, projection: IsoProjectionStyle = IsoProjectionStyle.ISOMETRIC): IsometricSheet {
        val sheet = IsometricEngine.newSheet(ids(), uniqueName(name), clock(), projection)
        mutableSheets.value = mutableSheets.value + sheet
        select(sheet.id)
        return sheet
    }

    /** Adds a finished sheet, e.g. one generated from the plan, and opens it. */
    fun add(sheet: IsometricSheet): IsometricSheet {
        val named = sheet.copy(name = uniqueName(sheet.name))
        mutableSheets.value = mutableSheets.value + named
        select(named.id)
        return named
    }

    fun select(id: IsometricSheetId) {
        val sheet = mutableSheets.value.firstOrNull { it.id == id } ?: return
        mutableActive.value = id
        engine.load(sheet)
        publish()
    }

    fun delete(id: IsometricSheetId) {
        if (mutableSheets.value.none { it.id == id }) return
        val remaining = mutableSheets.value.filterNot { it.id == id }
        mutableSheets.value = remaining
        if (mutableActive.value == id) {
            val next = remaining.lastOrNull()
            mutableActive.value = next?.id
            engine.load(next ?: IsometricEngine.newSheet(PLACEHOLDER, "", 0L))
        }
        publish()
    }

    /** Runs one engine command and stores the sheet when it succeeded. */
    fun <T> edit(command: IsometricEngine.() -> IsoResult<T>): IsoResult<T> {
        if (mutableActive.value == null) return IsoResult.Error(com.planruler.isometric.IsoError.NotFound(PLACEHOLDER))
        val result = engine.command()
        if (result is IsoResult.Ok) store()
        return result
    }

    /** Commands that cannot fail, such as the projection or the annotation switches. */
    fun change(command: IsometricEngine.() -> Unit) {
        if (mutableActive.value == null) return
        engine.command()
        store()
    }

    /** View changes are kept with the sheet without creating undo steps. */
    fun updateViewport(viewport: ViewportState) {
        if (mutableActive.value == null || engine.sheet.viewport == viewport) return
        engine.setViewport(viewport)
        store()
    }

    private fun store() {
        val sheet = engine.sheet
        if (mutableSheets.value.none { it.id == sheet.id }) return
        mutableSheets.value = mutableSheets.value.map { if (it.id == sheet.id) sheet else it }
        publish()
    }

    private fun publish() = onChanged(mutableSheets.value, mutableActive.value)

    private fun uniqueName(base: String): String {
        val trimmed = base.trim().take(NAME_LIMIT).ifEmpty { "Isometric" }
        val names = mutableSheets.value.mapTo(mutableSetOf()) { it.name }
        if (trimmed !in names) return trimmed
        var index = 2
        while ("$trimmed $index" in names) index++
        return "$trimmed $index"
    }

    private companion object {
        const val PLACEHOLDER = "isometric-placeholder"
        const val NAME_LIMIT = 120
    }
}

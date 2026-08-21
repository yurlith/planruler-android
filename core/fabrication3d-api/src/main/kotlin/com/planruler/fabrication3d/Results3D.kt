package com.planruler.fabrication3d

/**
 * Every engine call that can fail on user input returns this instead of throwing, so
 * the caller cannot accidentally surface an internal exception message to a fitter.
 * Exceptions stay for programming errors that no user input can trigger.
 */
sealed interface Fabrication3DResult<out T> {
    data class Ok<T>(val value: T) : Fabrication3DResult<T>
    data class Failure(val error: Fabrication3DError) : Fabrication3DResult<Nothing>
}

fun <T> Fabrication3DResult<T>.getOrNull(): T? = when (this) {
    is Fabrication3DResult.Ok -> value
    is Fabrication3DResult.Failure -> null
}

fun <T> Fabrication3DResult<T>.errorOrNull(): Fabrication3DError? = when (this) {
    is Fabrication3DResult.Ok -> null
    is Fabrication3DResult.Failure -> error
}

inline fun <T, R> Fabrication3DResult<T>.map(transform: (T) -> R): Fabrication3DResult<R> = when (this) {
    is Fabrication3DResult.Ok -> Fabrication3DResult.Ok(transform(value))
    is Fabrication3DResult.Failure -> this
}

inline fun <T, R> Fabrication3DResult<T>.flatMap(
    transform: (T) -> Fabrication3DResult<R>,
): Fabrication3DResult<R> = when (this) {
    is Fabrication3DResult.Ok -> transform(value)
    is Fabrication3DResult.Failure -> this
}

inline fun <T, R> Fabrication3DResult<T>.fold(
    onOk: (T) -> R,
    onFailure: (Fabrication3DError) -> R,
): R = when (this) {
    is Fabrication3DResult.Ok -> onOk(value)
    is Fabrication3DResult.Failure -> onFailure(error)
}

/** The rule a value broke. Callers translate these; the engine never ships user-facing prose. */
enum class ParameterRule3D {
    NOT_FINITE,
    MUST_BE_POSITIVE,
    BELOW_MINIMUM,
    ABOVE_MAXIMUM,
    OUT_OF_RANGE,
    WALL_TOO_THICK,
    RADIUS_TOO_SMALL,
    DIAMETER_MISMATCH,
    CONNECTION_MISMATCH,
    ANGLE_NOT_ALLOWED,
    BLANK_ID,
    DUPLICATE_ID,
    EMPTY_SET,
    NOT_A_NUMBER,
}

enum class EditorAction3D { UNDO, REDO, SELECT, ATTACH, UPDATE, REMOVE, INSERT }

sealed interface Fabrication3DError {
    /** [actual] and [limit] are diagnostics for logs and tests, never a localized message. */
    data class InvalidParameter(
        val parameter: String,
        val rule: ParameterRule3D,
        val actual: String? = null,
        val limit: String? = null,
    ) : Fabrication3DError

    data class UnknownPart(val partId: String) : Fabrication3DError

    data class UnknownPort(val reference: PortReference3D) : Fabrication3DError

    data class PortNotFree(val reference: PortReference3D) : Fabrication3DError

    data class PortsIncompatible(
        val anchor: PortReference3D,
        val rule: ParameterRule3D,
    ) : Fabrication3DError

    data class AssemblyInvalid(val issues: List<AssemblyIssue3D>) : Fabrication3DError

    data class QuotaExceeded(
        val quota: EngineQuota3D,
        val limit: Int,
        val requested: Int,
    ) : Fabrication3DError

    data class RouteNotFound(
        val code: RouteFailureCode3D,
        val detail: String? = null,
    ) : Fabrication3DError

    data class NothingToDo(val action: EditorAction3D) : Fabrication3DError

    /** A closure or construction step the engine believed impossible; a bug, not user input. */
    data class Internal(val stage: String) : Fabrication3DError
}

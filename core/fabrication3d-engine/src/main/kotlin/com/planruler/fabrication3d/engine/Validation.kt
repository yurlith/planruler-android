package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.ParameterRule3D

internal fun invalid(
    parameter: String,
    rule: ParameterRule3D,
    actual: Any? = null,
    limit: Any? = null,
): Fabrication3DResult.Failure = Fabrication3DResult.Failure(
    Fabrication3DError.InvalidParameter(parameter, rule, actual?.toString(), limit?.toString()),
)

internal fun <T> ok(value: T): Fabrication3DResult<T> = Fabrication3DResult.Ok(value)

/** Returns a failure when [value] is not a usable millimetre magnitude, otherwise null. */
internal fun requirePositive(parameter: String, value: Double): Fabrication3DResult.Failure? = when {
    !value.isFinite() -> invalid(parameter, ParameterRule3D.NOT_FINITE, value)
    value <= 0.0 -> invalid(parameter, ParameterRule3D.MUST_BE_POSITIVE, value)
    else -> null
}

internal fun requireFinite(parameter: String, value: Double): Fabrication3DResult.Failure? =
    if (value.isFinite()) null else invalid(parameter, ParameterRule3D.NOT_FINITE, value)

internal fun requireAtLeast(parameter: String, value: Double, minimum: Double): Fabrication3DResult.Failure? = when {
    !value.isFinite() -> invalid(parameter, ParameterRule3D.NOT_FINITE, value)
    value < minimum -> invalid(parameter, ParameterRule3D.BELOW_MINIMUM, value, minimum)
    else -> null
}

/** A tube needs a wall that leaves a bore; anything else would render as an inverted solid. */
internal fun requireTube(
    diameterParameter: String,
    wallParameter: String,
    outsideDiameterMm: Double,
    wallThicknessMm: Double,
): Fabrication3DResult.Failure? {
    requirePositive(diameterParameter, outsideDiameterMm)?.let { return it }
    requirePositive(wallParameter, wallThicknessMm)?.let { return it }
    if (wallThicknessMm * 2.0 >= outsideDiameterMm) {
        return invalid(wallParameter, ParameterRule3D.WALL_TOO_THICK, wallThicknessMm, outsideDiameterMm / 2.0)
    }
    return null
}

internal fun requireIdentifier(parameter: String, value: String): Fabrication3DResult.Failure? =
    if (value.isBlank()) invalid(parameter, ParameterRule3D.BLANK_ID, value) else null

/** Wraps a construction that can only fail through an engine bug into a typed failure. */
internal inline fun <T> guarded(stage: String, block: () -> T): Fabrication3DResult<T> =
    runCatching(block).fold(
        onSuccess = { Fabrication3DResult.Ok(it) },
        onFailure = { Fabrication3DResult.Failure(Fabrication3DError.Internal(stage)) },
    )

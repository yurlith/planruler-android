package com.planruler.feature.pipecalculator

import androidx.compose.runtime.saveable.Saver
import com.planruler.fabrication3d.ChainPlan3D
import com.planruler.fabrication3d.ChainStart3D
import com.planruler.fabrication3d.ChainStep3D
import com.planruler.fabrication3d.ChainTerminal3D
import com.planruler.fabrication3d.Vec3
import java.util.Locale

/**
 * Persists the chain recipe across configuration changes and process death. Only the
 * recipe is stored: the graph and the mesh are derived again by the engine, so nothing
 * that could disagree with the engine's own rules is ever restored from a bundle.
 *
 * A tee opens a branch, written as `T,<roll>` followed by the branch steps and closed by
 * `/`, so the tree survives without a nested container format.
 */
internal val ChainPlanSaver: Saver<ChainPlan3D, String> = Saver(
    save = { plan -> encodeChainPlan(plan) },
    restore = { encoded -> decodeChainPlan(encoded) },
)

private const val SECTION = '|'
private const val FIELD = ';'
private const val VALUE = ','
private const val BRANCH_END = "/"

internal fun encodeChainPlan(plan: ChainPlan3D): String {
    val start = plan.start
    val header = listOf(
        number(start.position.x),
        number(start.position.y),
        number(start.position.z),
        number(start.direction.x),
        number(start.direction.y),
        number(start.direction.z),
        number(start.upHint.x),
        number(start.upHint.y),
        number(start.upHint.z),
        start.terminal.name,
    ).joinToString(VALUE.toString())
    return "$header$SECTION${encodeSteps(plan.steps)}"
}

private fun encodeSteps(steps: List<ChainStep3D>): String = steps.flatMap { step ->
    when (step) {
        is ChainStep3D.Pipe -> listOf("P$VALUE${number(step.lengthMm)}")
        is ChainStep3D.Elbow -> listOf("E$VALUE${number(step.angleDeg)}$VALUE${number(step.rollDeg)}")
        is ChainStep3D.Reducer -> listOf(
            "R$VALUE${number(step.lengthMm)}$VALUE${step.smallNominalDiameter}$VALUE" +
                "${number(step.smallOutsideDiameterMm)}$VALUE${number(step.smallWallThicknessMm)}$VALUE" +
                "${if (step.eccentric) 1 else 0}$VALUE${number(step.rollDeg)}",
        )
        is ChainStep3D.Flange -> listOf("F$VALUE${number(step.rollDeg)}")
        is ChainStep3D.Cap -> listOf("K$VALUE${number(step.rollDeg)}")
        is ChainStep3D.Tee -> buildList {
            add("T$VALUE${number(step.rollDeg)}")
            if (step.branch.isNotEmpty()) add(encodeSteps(step.branch))
            add(BRANCH_END)
        }
    }
}.filter { it.isNotEmpty() }.joinToString(FIELD.toString())

/** Anything unparseable restores as an empty chain rather than a half-built model. */
internal fun decodeChainPlan(encoded: String): ChainPlan3D {
    val sections = encoded.split(SECTION)
    val header = sections.firstOrNull()?.split(VALUE) ?: return ChainPlan3D()
    if (header.size != 10) return ChainPlan3D()
    val numbers = header.take(9).map { it.toDoubleOrNull() ?: return ChainPlan3D() }
    val terminal = runCatching { ChainTerminal3D.valueOf(header[9]) }.getOrNull() ?: return ChainPlan3D()
    val start = ChainStart3D(
        position = Vec3(numbers[0], numbers[1], numbers[2]),
        direction = Vec3(numbers[3], numbers[4], numbers[5]),
        upHint = Vec3(numbers[6], numbers[7], numbers[8]),
        terminal = terminal,
    )
    if (start.direction.normalizedOrNull() == null) return ChainPlan3D()

    val body = sections.getOrNull(1).orEmpty()
    if (body.isBlank()) return ChainPlan3D(start = start)
    val tokens = body.split(FIELD)
    val cursor = intArrayOf(0)
    val steps = decodeSteps(tokens, cursor, depth = 0) ?: return ChainPlan3D(start = start)
    if (cursor[0] != tokens.size) return ChainPlan3D(start = start)
    return ChainPlan3D(start = start, steps = steps)
}

private fun decodeSteps(tokens: List<String>, cursor: IntArray, depth: Int): List<ChainStep3D>? {
    if (depth > MAX_BRANCH_DEPTH) return null
    val steps = mutableListOf<ChainStep3D>()
    while (cursor[0] < tokens.size) {
        val raw = tokens[cursor[0]]
        if (raw == BRANCH_END) {
            if (depth == 0) return null
            cursor[0]++
            return steps
        }
        cursor[0]++
        val parts = raw.split(VALUE)
        val values = parts.drop(1).map { it.toDoubleOrNull() ?: return null }
        steps += when (parts.firstOrNull()) {
            "P" -> ChainStep3D.Pipe(values.getOrNull(0) ?: return null)
            "E" -> ChainStep3D.Elbow(values.getOrNull(0) ?: return null, values.getOrElse(1) { 0.0 })
            "R" -> ChainStep3D.Reducer(
                lengthMm = values.getOrNull(0) ?: return null,
                smallNominalDiameter = values.getOrNull(1)?.toInt() ?: return null,
                smallOutsideDiameterMm = values.getOrNull(2) ?: return null,
                smallWallThicknessMm = values.getOrNull(3) ?: return null,
                eccentric = (values.getOrNull(4) ?: return null) != 0.0,
                rollDeg = values.getOrElse(5) { 0.0 },
            )
            "F" -> ChainStep3D.Flange(values.getOrElse(0) { 0.0 })
            "K" -> ChainStep3D.Cap(values.getOrElse(0) { 0.0 })
            "T" -> {
                val branch = decodeSteps(tokens, cursor, depth + 1) ?: return null
                ChainStep3D.Tee(values.getOrElse(0) { 0.0 }, branch)
            }
            else -> return null
        }
    }
    return if (depth == 0) steps else null
}

private const val MAX_BRANCH_DEPTH = 8

private fun number(value: Double): String = String.format(Locale.US, "%.6f", value)

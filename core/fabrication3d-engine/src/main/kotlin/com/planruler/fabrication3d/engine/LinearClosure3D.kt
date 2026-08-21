package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.Vec3
import kotlin.math.abs

/**
 * With every elbow angle and roll fixed, the end of a chain moves exactly linearly with
 * the pipe cut lengths. Closing a route is therefore a small least-squares problem
 * rather than an iterative search, which keeps the result deterministic.
 */
internal object LinearClosure3D {

    /**
     * Solves `sum(delta[k] * directions[k]) = residual` in the least-squares sense and
     * reports how much of [residual] the directions could not reach.
     */
    fun solve(directions: List<Vec3>, residual: Vec3): Closure3D? {
        val n = directions.size
        if (n !in 1..3) return null
        val normal = Array(n) { row -> DoubleArray(n) { column -> directions[row].dot(directions[column]) } }
        val rightHandSide = DoubleArray(n) { directions[it].dot(residual) }
        val delta = gaussianSolve(normal, rightHandSide) ?: return null
        var reached = Vec3.ZERO
        delta.forEachIndexed { index, value -> reached += directions[index] * value }
        return Closure3D(delta, (residual - reached).length)
    }

    data class Closure3D(val delta: DoubleArray, val unreachableMm: Double) {
        override fun equals(other: Any?): Boolean = other is Closure3D &&
            delta.contentEquals(other.delta) &&
            unreachableMm == other.unreachableMm

        override fun hashCode(): Int = delta.contentHashCode() * 31 + unreachableMm.hashCode()
    }

    private fun gaussianSolve(matrix: Array<DoubleArray>, vector: DoubleArray): DoubleArray? {
        val n = vector.size
        val a = Array(n) { row -> matrix[row].copyOf() }
        val b = vector.copyOf()
        for (column in 0 until n) {
            var pivotRow = column
            for (row in column + 1 until n) {
                if (abs(a[row][column]) > abs(a[pivotRow][column])) pivotRow = row
            }
            if (abs(a[pivotRow][column]) < PIVOT_EPSILON) return null
            if (pivotRow != column) {
                val swapRow = a[pivotRow]
                a[pivotRow] = a[column]
                a[column] = swapRow
                val swapValue = b[pivotRow]
                b[pivotRow] = b[column]
                b[column] = swapValue
            }
            for (row in column + 1 until n) {
                val factor = a[row][column] / a[column][column]
                if (factor == 0.0) continue
                for (inner in column until n) a[row][inner] -= factor * a[column][inner]
                b[row] -= factor * b[column]
            }
        }
        val result = DoubleArray(n)
        for (row in n - 1 downTo 0) {
            var sum = b[row]
            for (column in row + 1 until n) sum -= a[row][column] * result[column]
            result[row] = sum / a[row][row]
        }
        return result
    }

    private const val PIVOT_EPSILON = 1e-12
}

/** Signed rotation from [from] to [to] measured around [axis], in degrees. */
internal fun signedAngleAroundAxis(from: Vec3, to: Vec3, axis: Vec3): Double {
    val a = (from - axis * from.dot(axis)).normalizedOrNull() ?: return 0.0
    val b = (to - axis * to.dot(axis)).normalizedOrNull() ?: return 0.0
    return Math.toDegrees(kotlin.math.atan2(axis.dot(a.cross(b)), a.dot(b).coerceIn(-1.0, 1.0)))
}

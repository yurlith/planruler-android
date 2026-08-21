package com.planruler.fabrication3d

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Double precision geometry used by fabrication calculations. Coordinates are
 * millimetres; unit direction vectors use the same type to keep transformations
 * allocation-free and independent from Android rendering classes.
 */
data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    operator fun times(scale: Double) = Vec3(x * scale, y * scale, z * scale)
    operator fun div(scale: Double) = Vec3(x / scale, y / scale, z / scale)

    fun dot(other: Vec3): Double = x * other.x + y * other.y + z * other.z
    fun cross(other: Vec3) = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x,
    )

    val lengthSquared: Double get() = dot(this)
    val length: Double get() = sqrt(lengthSquared)

    fun normalized(): Vec3 {
        val magnitude = length
        require(magnitude > GEOMETRY_EPSILON) { "A zero-length vector has no direction" }
        return this / magnitude
    }

    /** Null instead of an exception, for callers that treat a degenerate input as a typed failure. */
    fun normalizedOrNull(): Vec3? = if (isFinite() && length > GEOMETRY_EPSILON) this / length else null

    fun distanceTo(other: Vec3): Double = (this - other).length
    fun isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

    companion object {
        val ZERO = Vec3(0.0, 0.0, 0.0)
        val UNIT_X = Vec3(1.0, 0.0, 0.0)
        val UNIT_Y = Vec3(0.0, 1.0, 0.0)
        val UNIT_Z = Vec3(0.0, 0.0, 1.0)
    }
}

operator fun Double.times(vector: Vec3): Vec3 = vector * this

data class Quaternion(
    val w: Double,
    val x: Double,
    val y: Double,
    val z: Double,
) {
    operator fun times(other: Quaternion) = Quaternion(
        w = w * other.w - x * other.x - y * other.y - z * other.z,
        x = w * other.x + x * other.w + y * other.z - z * other.y,
        y = w * other.y - x * other.z + y * other.w + z * other.x,
        z = w * other.z + x * other.y - y * other.x + z * other.w,
    )

    fun normalized(): Quaternion {
        val magnitude = sqrt(w * w + x * x + y * y + z * z)
        require(magnitude > GEOMETRY_EPSILON) { "A zero quaternion cannot represent a rotation" }
        return Quaternion(w / magnitude, x / magnitude, y / magnitude, z / magnitude)
    }

    fun conjugate() = Quaternion(w, -x, -y, -z)

    fun inverse(): Quaternion {
        val magnitudeSquared = w * w + x * x + y * y + z * z
        require(magnitudeSquared > GEOMETRY_EPSILON) { "A zero quaternion cannot be inverted" }
        val conjugate = conjugate()
        return Quaternion(
            conjugate.w / magnitudeSquared,
            conjugate.x / magnitudeSquared,
            conjugate.y / magnitudeSquared,
            conjugate.z / magnitudeSquared,
        )
    }

    fun rotate(vector: Vec3): Vec3 {
        val unit = normalized()
        val pure = Quaternion(0.0, vector.x, vector.y, vector.z)
        val rotated = unit * pure * unit.conjugate()
        return Vec3(rotated.x, rotated.y, rotated.z)
    }

    companion object {
        val IDENTITY = Quaternion(1.0, 0.0, 0.0, 0.0)

        fun axisAngle(axis: Vec3, angleDeg: Double): Quaternion {
            val radians = Math.toRadians(angleDeg)
            val half = radians / 2.0
            val direction = axis.normalized()
            val sine = sin(half)
            return Quaternion(cos(half), direction.x * sine, direction.y * sine, direction.z * sine)
                .normalized()
        }

        fun between(from: Vec3, to: Vec3): Quaternion {
            val a = from.normalized()
            val b = to.normalized()
            val dot = a.dot(b).coerceIn(-1.0, 1.0)
            if (dot > 1.0 - 1e-12) return IDENTITY
            if (dot < -1.0 + 1e-12) {
                val helper = if (abs(a.x) < 0.8) Vec3.UNIT_X else Vec3.UNIT_Y
                return axisAngle(a.cross(helper).normalized(), 180.0)
            }
            val axis = a.cross(b).normalized()
            return axisAngle(axis, Math.toDegrees(acos(dot)))
        }

        /** Creates a rotation whose columns are the world directions of local X/Y/Z. */
        fun fromAxes(xAxis: Vec3, yAxis: Vec3, zAxis: Vec3): Quaternion {
            val m00 = xAxis.x
            val m01 = yAxis.x
            val m02 = zAxis.x
            val m10 = xAxis.y
            val m11 = yAxis.y
            val m12 = zAxis.y
            val m20 = xAxis.z
            val m21 = yAxis.z
            val m22 = zAxis.z
            val trace = m00 + m11 + m22
            val result = when {
                trace > 0.0 -> {
                    val s = sqrt(trace + 1.0) * 2.0
                    Quaternion(
                        w = 0.25 * s,
                        x = (m21 - m12) / s,
                        y = (m02 - m20) / s,
                        z = (m10 - m01) / s,
                    )
                }
                m00 > m11 && m00 > m22 -> {
                    val s = sqrt(1.0 + m00 - m11 - m22) * 2.0
                    Quaternion(
                        w = (m21 - m12) / s,
                        x = 0.25 * s,
                        y = (m01 + m10) / s,
                        z = (m02 + m20) / s,
                    )
                }
                m11 > m22 -> {
                    val s = sqrt(1.0 + m11 - m00 - m22) * 2.0
                    Quaternion(
                        w = (m02 - m20) / s,
                        x = (m01 + m10) / s,
                        y = 0.25 * s,
                        z = (m12 + m21) / s,
                    )
                }
                else -> {
                    val s = sqrt(1.0 + m22 - m00 - m11) * 2.0
                    Quaternion(
                        w = (m10 - m01) / s,
                        x = (m02 + m20) / s,
                        y = (m12 + m21) / s,
                        z = 0.25 * s,
                    )
                }
            }
            return result.normalized()
        }
    }
}

data class Transform3D(
    val translation: Vec3 = Vec3.ZERO,
    val rotation: Quaternion = Quaternion.IDENTITY,
) {
    fun point(local: Vec3): Vec3 = translation + rotation.rotate(local)
    fun direction(local: Vec3): Vec3 = rotation.rotate(local)

    /** Returns this transform followed by [child] in the local coordinate system. */
    operator fun times(child: Transform3D) = Transform3D(
        translation = point(child.translation),
        rotation = (rotation * child.rotation).normalized(),
    )

    fun inverse(): Transform3D {
        val inverseRotation = rotation.inverse()
        return Transform3D(inverseRotation.rotate(-translation), inverseRotation)
    }

    companion object {
        val IDENTITY = Transform3D()
    }
}

/** A right-handed engineering frame: X is forward, Z is up and Y is left. */
class Frame3D private constructor(
    val position: Vec3,
    val forward: Vec3,
    val up: Vec3,
) {
    val left: Vec3 get() = up.cross(forward).normalized()
    val rotation: Quaternion get() = Quaternion.fromAxes(forward, left, up)

    fun transformed(transform: Transform3D): Frame3D = of(
        position = transform.point(position),
        forward = transform.direction(forward),
        upHint = transform.direction(up),
    )

    override fun equals(other: Any?): Boolean = other is Frame3D &&
        position == other.position &&
        forward == other.forward &&
        up == other.up

    override fun hashCode(): Int = (position.hashCode() * 31 + forward.hashCode()) * 31 + up.hashCode()

    override fun toString(): String = "Frame3D(position=$position, forward=$forward, up=$up)"

    companion object {
        fun of(position: Vec3, forward: Vec3, upHint: Vec3 = Vec3.UNIT_Z): Frame3D {
            val xAxis = forward.normalized()
            var projectedUp = upHint - xAxis * upHint.dot(xAxis)
            if (projectedUp.length <= GEOMETRY_EPSILON) {
                val fallback = if (abs(xAxis.z) < 0.8) Vec3.UNIT_Z else Vec3.UNIT_Y
                projectedUp = fallback - xAxis * fallback.dot(xAxis)
            }
            return Frame3D(position, xAxis, projectedUp.normalized())
        }
    }
}

fun alignFrame(local: Frame3D, target: Frame3D): Transform3D {
    val rotation = (target.rotation * local.rotation.inverse()).normalized()
    return Transform3D(
        translation = target.position - rotation.rotate(local.position),
        rotation = rotation,
    )
}

const val GEOMETRY_EPSILON = 1e-9

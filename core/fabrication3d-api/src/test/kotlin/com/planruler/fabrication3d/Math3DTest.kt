package com.planruler.fabrication3d

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Math3DTest {
    @Test
    fun `quaternion rotates vectors without changing their length`() {
        val rotation = Quaternion.axisAngle(Vec3.UNIT_Z, 90.0)
        val rotated = rotation.rotate(Vec3.UNIT_X)

        assertVec(Vec3.UNIT_Y, rotated)
        assertEquals(1.0, rotated.length, 1e-9)
    }

    @Test
    fun `transform inverse returns an engineering point to local space`() {
        val transform = Transform3D(
            translation = Vec3(120.0, -45.0, 18.0),
            rotation = Quaternion.axisAngle(Vec3(1.0, 1.0, 0.0), 37.0),
        )
        val local = Vec3(40.0, 7.0, -12.0)

        assertVec(local, transform.inverse().point(transform.point(local)))
    }

    @Test
    fun `frame construction remains orthonormal with a parallel up hint`() {
        val frame = Frame3D.of(Vec3.ZERO, Vec3.UNIT_Z, Vec3.UNIT_Z)

        assertEquals(0.0, frame.forward.dot(frame.up), 1e-12)
        assertEquals(0.0, frame.forward.dot(frame.left), 1e-12)
        assertEquals(0.0, frame.up.dot(frame.left), 1e-12)
        assertTrue(frame.forward.cross(frame.left).dot(frame.up) > 0.999999)
    }

    @Test
    fun `frames compare by value so editor snapshots can be compared`() {
        val first = Frame3D.of(Vec3(1.0, 2.0, 3.0), Vec3.UNIT_X)
        val second = Frame3D.of(Vec3(1.0, 2.0, 3.0), Vec3.UNIT_X)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `degenerate directions report null instead of throwing`() {
        assertNull(Vec3.ZERO.normalizedOrNull())
        assertNull(Vec3(Double.NaN, 0.0, 0.0).normalizedOrNull())
    }

    @Test
    fun `random transform chains stay orthonormal`() {
        var transform = Transform3D.IDENTITY
        var seed = 20_260_816L
        repeat(2_000) {
            seed = seed * 6_364_136_223_846_793_005L + 1_442_695_040_888_963_407L
            val axis = Vec3(
                ((seed shr 16) % 1_000).toDouble() / 500.0 - 1.0,
                ((seed shr 26) % 1_000).toDouble() / 500.0 - 1.0,
                ((seed shr 36) % 1_000).toDouble() / 500.0 - 1.0,
            )
            val usable = axis.normalizedOrNull() ?: Vec3.UNIT_Z
            transform *= Transform3D(
                translation = usable * 12.0,
                rotation = Quaternion.axisAngle(usable, ((seed shr 8) % 360).toDouble()),
            )
        }
        val x = transform.direction(Vec3.UNIT_X)
        val y = transform.direction(Vec3.UNIT_Y)
        val z = transform.direction(Vec3.UNIT_Z)
        assertEquals(1.0, x.length, 1e-9)
        assertEquals(1.0, y.length, 1e-9)
        assertEquals(1.0, z.length, 1e-9)
        assertEquals(0.0, x.dot(y), 1e-9)
        assertEquals(0.0, x.dot(z), 1e-9)
        assertEquals(0.0, y.dot(z), 1e-9)
    }

    private fun assertVec(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-8)
        assertEquals(expected.y, actual.y, 1e-8)
        assertEquals(expected.z, actual.z, 1e-8)
    }
}

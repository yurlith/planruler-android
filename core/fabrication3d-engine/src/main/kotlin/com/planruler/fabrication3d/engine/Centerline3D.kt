package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** A straight piece of centreline with the outer radius that sweeps along it. */
internal data class CenterlineSegment3D(
    val partId: String,
    val start: Vec3,
    val end: Vec3,
    val outerRadiusMm: Double,
)

internal object Centerline3D {
    private const val ELBOW_SAMPLES = 8

    fun of(part: PartInstance3D): List<CenterlineSegment3D> {
        val transform = part.transform
        fun segment(a: Vec3, b: Vec3, radius: Double) =
            CenterlineSegment3D(part.id, transform.point(a), transform.point(b), radius)

        return when (val geometry = part.definition.geometry) {
            is StraightPipeGeometry3D -> listOf(
                segment(Vec3.ZERO, Vec3(geometry.lengthMm, 0.0, 0.0), geometry.outsideDiameterMm / 2.0),
            )

            is ElbowGeometry3D -> {
                val radians = Math.toRadians(abs(geometry.angleDeg))
                val sign = if (geometry.angleDeg >= 0.0) 1.0 else -1.0
                val radius = geometry.centerlineRadiusMm
                val points = List(ELBOW_SAMPLES + 1) { index ->
                    val angle = radians * index / ELBOW_SAMPLES
                    Vec3(radius * sin(angle), sign * radius * (1.0 - cos(angle)), 0.0)
                }
                points.zipWithNext { a, b -> segment(a, b, geometry.outsideDiameterMm / 2.0) }
            }

            is WeldNeckFlangeGeometry3D -> listOf(
                segment(
                    Vec3.ZERO,
                    Vec3(geometry.faceToWeldMm, 0.0, 0.0),
                    max(geometry.outsideDiameterMm, geometry.pipeOutsideDiameterMm) / 2.0,
                ),
            )

            is EqualTeeGeometry3D -> {
                val half = geometry.overallRunMm / 2.0
                val radius = geometry.outsideDiameterMm / 2.0
                listOf(
                    segment(Vec3(-half, 0.0, 0.0), Vec3(half, 0.0, 0.0), radius),
                    segment(Vec3.ZERO, Vec3(0.0, geometry.branchCenterToEndMm, 0.0), radius),
                )
            }

            is ReducerGeometry3D -> listOf(
                segment(
                    Vec3.ZERO,
                    Vec3(geometry.lengthMm, 0.0, 0.0),
                    geometry.largeOutsideDiameterMm / 2.0,
                ),
            )

            is CapGeometry3D -> listOf(
                segment(Vec3.ZERO, Vec3(geometry.heightMm, 0.0, 0.0), geometry.outsideDiameterMm / 2.0),
            )
        }
    }

    /** Shortest distance between two finite segments; the standard clamped-parameter solution. */
    fun distance(
        firstStart: Vec3,
        firstEnd: Vec3,
        secondStart: Vec3,
        secondEnd: Vec3,
    ): Double {
        val u = firstEnd - firstStart
        val v = secondEnd - secondStart
        val w = firstStart - secondStart
        val a = u.dot(u)
        val b = u.dot(v)
        val c = v.dot(v)
        val d = u.dot(w)
        val e = v.dot(w)
        val denominator = a * c - b * b
        var sN: Double
        var sD = denominator
        var tN: Double
        var tD = denominator

        if (denominator < 1e-12) {
            sN = 0.0
            sD = 1.0
            tN = e
            tD = c
        } else {
            sN = b * e - c * d
            tN = a * e - b * d
            if (sN < 0.0) {
                sN = 0.0
                tN = e
                tD = c
            } else if (sN > sD) {
                sN = sD
                tN = e + b
                tD = c
            }
        }

        if (tN < 0.0) {
            tN = 0.0
            when {
                -d < 0.0 -> sN = 0.0
                -d > a -> sN = sD
                else -> {
                    sN = -d
                    sD = a
                }
            }
        } else if (tN > tD) {
            tN = tD
            val value = -d + b
            when {
                value < 0.0 -> sN = 0.0
                value > a -> sN = sD
                else -> {
                    sN = value
                    sD = a
                }
            }
        }

        val s = if (abs(sD) < 1e-12) 0.0 else sN / sD
        val t = if (abs(tD) < 1e-12) 0.0 else tN / tD
        return (w + u * s - v * t).length
    }
}

package com.planruler.engine.default

import com.planruler.model.DocPoint
import kotlin.math.abs
import kotlin.math.atan2

object Geometry {
    fun polylineLength(points: List<DocPoint>) = points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
    fun polygonArea(points: List<DocPoint>): Double = abs(points.indices.sumOf { i ->
        val a = points[i]
        val b = points[(i + 1) % points.size]
        a.x * b.y - b.x * a.y
    }) / 2.0
    fun polygonPerimeter(points: List<DocPoint>) =
        if (points.size < 2) 0.0 else polylineLength(points) + points.last().distanceTo(points.first())
    fun angleDegrees(a: DocPoint, vertex: DocPoint, c: DocPoint): Double {
        require(a.distanceTo(vertex) > 0.0 && c.distanceTo(vertex) > 0.0)
        var value = Math.toDegrees(abs(atan2(a.y - vertex.y, a.x - vertex.x) - atan2(c.y - vertex.y, c.x - vertex.x)))
        if (value > 180.0) value = 360.0 - value
        return value
    }
    fun selfIntersects(points: List<DocPoint>): Boolean {
        if (points.size < 4) return false
        fun cross(a: DocPoint, b: DocPoint, c: DocPoint) =
            (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)
        fun intersects(a: DocPoint, b: DocPoint, c: DocPoint, d: DocPoint) =
            cross(a, b, c) * cross(a, b, d) < 0 && cross(c, d, a) * cross(c, d, b) < 0
        for (i in points.indices) {
            val a = points[i]
            val b = points[(i + 1) % points.size]
            for (j in i + 1 until points.size) {
                if (j == i || j == i + 1 || (i == 0 && j == points.lastIndex)) continue
                if (intersects(a, b, points[j], points[(j + 1) % points.size])) return true
            }
        }
        return false
    }
    fun snap(point: DocPoint, candidates: List<DocPoint>, thresholdDocumentUnits: Double): DocPoint =
        candidates.minByOrNull(point::distanceTo)?.takeIf { point.distanceTo(it) <= thresholdDocumentUnits } ?: point
}

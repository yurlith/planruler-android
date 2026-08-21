package com.planruler.fabrication3d.engine

import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.Bounds3D
import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EngineLimits3D
import com.planruler.fabrication3d.EngineQuota3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.Fabrication3DError
import com.planruler.fabrication3d.Fabrication3DResult
import com.planruler.fabrication3d.Frame3D
import com.planruler.fabrication3d.MeshLabel3D
import com.planruler.fabrication3d.MeshMaterial3D
import com.planruler.fabrication3d.MeshPolyline3D
import com.planruler.fabrication3d.MeshQuality3D
import com.planruler.fabrication3d.MeshTessellator3D
import com.planruler.fabrication3d.MeshTriangle3D
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.Quaternion
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Transform3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Produces renderer-neutral engineering meshes; Android types never enter the calculation core. */
internal class DefaultMeshTessellator3D(
    private val limits: EngineLimits3D,
) : MeshTessellator3D {

    override fun build(
        assembly: ParametricAssembly3D,
        quality: MeshQuality3D,
    ): Fabrication3DResult<AssemblyMesh3D> {
        val radial = quality.radialSegments
        val estimate = estimateTriangles(assembly, quality)
        if (estimate > limits.maxTriangles) {
            return Fabrication3DResult.Failure(
                Fabrication3DError.QuotaExceeded(EngineQuota3D.TRIANGLES, limits.maxTriangles, estimate),
            )
        }

        return guarded("tessellate") {
            val triangles = mutableListOf<MeshTriangle3D>()
            val lines = mutableListOf<MeshPolyline3D>()
            val labels = mutableListOf<MeshLabel3D>()

            assembly.parts.forEach { part ->
                val local = when (val geometry = part.definition.geometry) {
                    is StraightPipeGeometry3D -> pipeMesh(part.id, geometry, radial)
                    is ElbowGeometry3D -> elbowMesh(part.id, geometry, radial, quality.curveSegmentsPerQuarter)
                    is WeldNeckFlangeGeometry3D -> flangeMesh(part.id, geometry, radial)
                    is EqualTeeGeometry3D -> teeMesh(part.id, geometry, radial)
                    is ReducerGeometry3D -> reducerMesh(part.id, geometry, radial)
                    is CapGeometry3D -> capMesh(part.id, geometry, radial)
                }
                triangles += local.triangles.map { triangle -> triangle.transformed(part.transform) }
                lines += local.polylines.map { line -> line.transformed(part.transform) }
                labels += MeshLabel3D(part.id, part.code, part.transform.point(local.labelAnchor))
            }

            assembly.connections.forEach { connection ->
                val first = assembly.worldPortOrNull(connection.first) ?: return@forEach
                val second = assembly.worldPortOrNull(connection.second) ?: return@forEach
                val midpoint = (first.frame.position + second.frame.position) / 2.0
                val frame = Frame3D.of(midpoint, first.frame.forward, first.frame.up)
                lines += MeshPolyline3D(
                    ownerId = connection.id,
                    material = MeshMaterial3D.WELD,
                    points = circleInFrame(frame, first.outsideDiameterMm * 0.56, radial),
                    closed = true,
                )
            }

            val allPoints = buildList {
                triangles.forEach { add(it.a); add(it.b); add(it.c) }
                lines.forEach { addAll(it.points) }
            }
            AssemblyMesh3D(triangles, lines, labels, Bounds3D.from(allPoints))
        }
    }

    /** Upper bound evaluated before any allocation, so an oversized scene fails fast. */
    private fun estimateTriangles(assembly: ParametricAssembly3D, quality: MeshQuality3D): Int {
        val radial = quality.radialSegments
        return assembly.parts.sumOf { part ->
            when (val geometry = part.definition.geometry) {
                is StraightPipeGeometry3D -> tubeTriangles(radial)
                is ElbowGeometry3D -> {
                    val segments = curveSegments(geometry.angleDeg, quality.curveSegmentsPerQuarter)
                    segments * radial * 4 + radial * 4
                }
                is WeldNeckFlangeGeometry3D -> 3 * radial * 2 + radial * 2 + radial * 4
                is EqualTeeGeometry3D -> tubeTriangles(radial) * 2
                is ReducerGeometry3D -> tubeTriangles(radial)
                is CapGeometry3D -> tubeTriangles(radial) + radial
            }
        }
    }

    private fun tubeTriangles(radial: Int) = radial * 8

    private fun curveSegments(angleDeg: Double, perQuarter: Int) =
        max(4, (perQuarter * abs(angleDeg) / 90.0).toInt())

    private data class LocalPartMesh(
        val triangles: List<MeshTriangle3D>,
        val polylines: List<MeshPolyline3D>,
        val labelAnchor: Vec3,
    )

    private fun pipeMesh(partId: String, geometry: StraightPipeGeometry3D, radial: Int): LocalPartMesh {
        val triangles = tubeAlongX(
            partId,
            MeshMaterial3D.PIPE,
            startX = 0.0,
            endX = geometry.lengthMm,
            outerRadius = geometry.outsideDiameterMm / 2.0,
            innerRadius = geometry.outsideDiameterMm / 2.0 - geometry.wallThicknessMm,
            radial = radial,
        )
        return LocalPartMesh(
            triangles,
            emptyList(),
            Vec3(geometry.lengthMm / 2.0, 0.0, geometry.outsideDiameterMm * 0.62),
        )
    }

    private fun elbowMesh(
        partId: String,
        geometry: ElbowGeometry3D,
        radial: Int,
        curveSegmentsPerQuarter: Int,
    ): LocalPartMesh {
        val angleRadians = Math.toRadians(abs(geometry.angleDeg))
        val sign = if (geometry.angleDeg >= 0.0) 1.0 else -1.0
        val curveSegments = curveSegments(geometry.angleDeg, curveSegmentsPerQuarter)
        val outerRadius = geometry.outsideDiameterMm / 2.0
        val innerRadius = outerRadius - geometry.wallThicknessMm
        val outerRings = mutableListOf<List<Vec3>>()
        val innerRings = mutableListOf<List<Vec3>>()
        repeat(curveSegments + 1) { curveIndex ->
            val angle = angleRadians * curveIndex / curveSegments
            val center = elbowCenterline(geometry.centerlineRadiusMm, sign, angle)
            val tangent = Vec3(cos(angle), sign * sin(angle), 0.0)
            val left = Vec3.UNIT_Z.cross(tangent).normalized()
            outerRings += circularRing(center, left, Vec3.UNIT_Z, outerRadius, radial)
            innerRings += circularRing(center, left, Vec3.UNIT_Z, innerRadius, radial)
        }
        val triangles = mutableListOf<MeshTriangle3D>()
        connectRingSeries(partId, MeshMaterial3D.ELBOW, outerRings, triangles, reverse = false)
        connectRingSeries(partId, MeshMaterial3D.INNER_BORE, innerRings, triangles, reverse = true)
        annulus(partId, MeshMaterial3D.ELBOW, outerRings.first(), innerRings.first(), triangles, reverse = true)
        annulus(partId, MeshMaterial3D.ELBOW, outerRings.last(), innerRings.last(), triangles, reverse = false)
        val mid = elbowCenterline(geometry.centerlineRadiusMm, sign, angleRadians / 2.0)
        return LocalPartMesh(triangles, emptyList(), mid + Vec3(0.0, 0.0, outerRadius * 1.25))
    }

    private fun flangeMesh(partId: String, geometry: WeldNeckFlangeGeometry3D, radial: Int): LocalPartMesh {
        val flangeRadius = geometry.outsideDiameterMm / 2.0
        val pipeRadius = geometry.pipeOutsideDiameterMm / 2.0
        val innerRadius = pipeRadius - geometry.pipeWallThicknessMm
        val hubRadius = max(pipeRadius * 1.48, min(flangeRadius * 0.56, pipeRadius * 2.25))
        val discBackX = min(geometry.thicknessMm, geometry.faceToWeldMm * 0.72)
        val outerProfile = listOf(
            ringAtX(0.0, flangeRadius, radial),
            ringAtX(discBackX, flangeRadius, radial),
            ringAtX(discBackX, hubRadius, radial),
            ringAtX(geometry.faceToWeldMm, pipeRadius, radial),
        )
        val innerProfile = listOf(
            ringAtX(0.0, innerRadius, radial),
            ringAtX(geometry.faceToWeldMm, innerRadius, radial),
        )
        val triangles = mutableListOf<MeshTriangle3D>()
        connectRingSeries(partId, MeshMaterial3D.FLANGE, outerProfile, triangles, reverse = false)
        connectRingSeries(partId, MeshMaterial3D.INNER_BORE, innerProfile, triangles, reverse = true)
        annulus(partId, MeshMaterial3D.FLANGE, outerProfile.first(), innerProfile.first(), triangles, reverse = true)
        annulus(partId, MeshMaterial3D.FLANGE, outerProfile.last(), innerProfile.last(), triangles, reverse = false)

        val holeLines = List(geometry.boltHoleCount) { index ->
            val boltAngle = 2.0 * PI * index / geometry.boltHoleCount
            val centerY = cos(boltAngle) * geometry.boltCircleDiameterMm / 2.0
            val centerZ = sin(boltAngle) * geometry.boltCircleDiameterMm / 2.0
            val holePoints = List(radial + 1) { step ->
                val angle = 2.0 * PI * step / radial
                Vec3(
                    -0.8,
                    centerY + cos(angle) * geometry.boltHoleDiameterMm / 2.0,
                    centerZ + sin(angle) * geometry.boltHoleDiameterMm / 2.0,
                )
            }
            MeshPolyline3D(partId, MeshMaterial3D.INNER_BORE, holePoints, closed = true)
        }
        return LocalPartMesh(triangles, holeLines, Vec3(discBackX / 2.0, 0.0, flangeRadius * 1.16))
    }

    private fun teeMesh(partId: String, geometry: EqualTeeGeometry3D, radial: Int): LocalPartMesh {
        val outer = geometry.outsideDiameterMm / 2.0
        val inner = outer - geometry.wallThicknessMm
        val halfRun = geometry.overallRunMm / 2.0
        val run = tubeAlongX(partId, MeshMaterial3D.TEE, -halfRun, halfRun, outer, inner, radial)
        val branchTransform = Transform3D(Vec3.ZERO, Quaternion.between(Vec3.UNIT_X, Vec3.UNIT_Y))
        val branch = tubeAlongX(
            partId,
            MeshMaterial3D.TEE,
            0.0,
            geometry.branchCenterToEndMm,
            outer,
            inner,
            radial,
        ).map { it.transformed(branchTransform) }
        return LocalPartMesh(
            triangles = run + branch,
            polylines = emptyList(),
            labelAnchor = Vec3(0.0, geometry.branchCenterToEndMm * 0.55, outer * 1.3),
        )
    }

    private fun reducerMesh(partId: String, geometry: ReducerGeometry3D, radial: Int): LocalPartMesh {
        val largeOuter = geometry.largeOutsideDiameterMm / 2.0
        val smallOuter = geometry.smallOutsideDiameterMm / 2.0
        val largeInner = largeOuter - geometry.largeWallThicknessMm
        val smallInner = smallOuter - geometry.smallWallThicknessMm
        val offsetZ = if (geometry.eccentric) largeOuter - smallOuter else 0.0
        val outer = listOf(
            ringAtX(0.0, largeOuter, radial),
            ringAtX(geometry.lengthMm, smallOuter, radial, offsetZ),
        )
        val inner = listOf(
            ringAtX(0.0, largeInner, radial),
            ringAtX(geometry.lengthMm, smallInner, radial, offsetZ),
        )
        val triangles = mutableListOf<MeshTriangle3D>()
        connectRingSeries(partId, MeshMaterial3D.REDUCER, outer, triangles, reverse = false)
        connectRingSeries(partId, MeshMaterial3D.INNER_BORE, inner, triangles, reverse = true)
        annulus(partId, MeshMaterial3D.REDUCER, outer.first(), inner.first(), triangles, reverse = true)
        annulus(partId, MeshMaterial3D.REDUCER, outer.last(), inner.last(), triangles, reverse = false)
        return LocalPartMesh(
            triangles,
            emptyList(),
            Vec3(geometry.lengthMm / 2.0, 0.0, largeOuter * 1.3 + offsetZ),
        )
    }

    private fun capMesh(partId: String, geometry: CapGeometry3D, radial: Int): LocalPartMesh {
        val outerRadius = geometry.outsideDiameterMm / 2.0
        val innerRadius = outerRadius - geometry.wallThicknessMm
        val triangles = mutableListOf<MeshTriangle3D>()
        val outer = listOf(ringAtX(0.0, outerRadius, radial), ringAtX(geometry.heightMm, outerRadius, radial))
        val inner = listOf(ringAtX(0.0, innerRadius, radial), ringAtX(geometry.heightMm, innerRadius, radial))
        connectRingSeries(partId, MeshMaterial3D.CAP, outer, triangles, reverse = false)
        connectRingSeries(partId, MeshMaterial3D.INNER_BORE, inner, triangles, reverse = true)
        annulus(partId, MeshMaterial3D.CAP, outer.first(), inner.first(), triangles, reverse = true)
        disc(partId, MeshMaterial3D.CAP, outer.last(), Vec3(geometry.heightMm, 0.0, 0.0), triangles)
        return LocalPartMesh(
            triangles,
            emptyList(),
            Vec3(geometry.heightMm / 2.0, 0.0, outerRadius * 1.3),
        )
    }

    private fun tubeAlongX(
        partId: String,
        material: MeshMaterial3D,
        startX: Double,
        endX: Double,
        outerRadius: Double,
        innerRadius: Double,
        radial: Int,
    ): List<MeshTriangle3D> {
        val outer = listOf(ringAtX(startX, outerRadius, radial), ringAtX(endX, outerRadius, radial))
        val inner = listOf(ringAtX(startX, innerRadius, radial), ringAtX(endX, innerRadius, radial))
        val result = mutableListOf<MeshTriangle3D>()
        connectRingSeries(partId, material, outer, result, reverse = false)
        connectRingSeries(partId, MeshMaterial3D.INNER_BORE, inner, result, reverse = true)
        annulus(partId, material, outer.first(), inner.first(), result, reverse = true)
        annulus(partId, material, outer.last(), inner.last(), result, reverse = false)
        return result
    }

    private fun connectRingSeries(
        partId: String,
        material: MeshMaterial3D,
        rings: List<List<Vec3>>,
        destination: MutableList<MeshTriangle3D>,
        reverse: Boolean,
    ) {
        rings.zipWithNext().forEach { (first, second) ->
            val segments = min(first.size, second.size)
            repeat(segments) { index ->
                val next = (index + 1) % segments
                addQuad(partId, material, first[index], second[index], second[next], first[next], destination, reverse)
            }
        }
    }

    private fun annulus(
        partId: String,
        material: MeshMaterial3D,
        outer: List<Vec3>,
        inner: List<Vec3>,
        destination: MutableList<MeshTriangle3D>,
        reverse: Boolean,
    ) {
        val segments = min(outer.size, inner.size)
        repeat(segments) { index ->
            val next = (index + 1) % segments
            addQuad(partId, material, outer[index], inner[index], inner[next], outer[next], destination, reverse)
        }
    }

    private fun disc(
        partId: String,
        material: MeshMaterial3D,
        ring: List<Vec3>,
        center: Vec3,
        destination: MutableList<MeshTriangle3D>,
    ) {
        repeat(ring.size) { index ->
            val next = (index + 1) % ring.size
            destination += MeshTriangle3D(partId, material, center, ring[index], ring[next])
        }
    }

    private fun addQuad(
        partId: String,
        material: MeshMaterial3D,
        a: Vec3,
        b: Vec3,
        c: Vec3,
        d: Vec3,
        destination: MutableList<MeshTriangle3D>,
        reverse: Boolean,
    ) {
        if (reverse) {
            destination += MeshTriangle3D(partId, material, a, c, b)
            destination += MeshTriangle3D(partId, material, a, d, c)
        } else {
            destination += MeshTriangle3D(partId, material, a, b, c)
            destination += MeshTriangle3D(partId, material, a, c, d)
        }
    }

    private fun circularRing(center: Vec3, firstAxis: Vec3, secondAxis: Vec3, radius: Double, radial: Int) =
        List(radial) { index ->
            val angle = 2.0 * PI * index / radial
            center + firstAxis * (cos(angle) * radius) + secondAxis * (sin(angle) * radius)
        }

    private fun ringAtX(x: Double, radius: Double, radial: Int, centerZ: Double = 0.0) = List(radial) { index ->
        val angle = 2.0 * PI * index / radial
        Vec3(x, cos(angle) * radius, centerZ + sin(angle) * radius)
    }

    private fun elbowCenterline(radius: Double, sign: Double, angleRadians: Double) = Vec3(
        radius * sin(angleRadians),
        sign * radius * (1.0 - cos(angleRadians)),
        0.0,
    )

    private fun circleInFrame(frame: Frame3D, radius: Double, radial: Int): List<Vec3> =
        List(radial) { index ->
            val angle = 2.0 * PI * index / radial
            frame.position + frame.left * (cos(angle) * radius) + frame.up * (sin(angle) * radius)
        }

    private fun MeshTriangle3D.transformed(transform: Transform3D) = copy(
        a = transform.point(a),
        b = transform.point(b),
        c = transform.point(c),
    )

    private fun MeshPolyline3D.transformed(transform: Transform3D) = copy(points = points.map(transform::point))
}

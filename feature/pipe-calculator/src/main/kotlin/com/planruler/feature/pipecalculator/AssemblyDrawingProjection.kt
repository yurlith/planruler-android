package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.PartInstance3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.Vec3
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal enum class AssemblyDrawingView { ISOMETRIC, TOP, SIDE, RIGHT, END }

internal enum class AssemblyDrawingLayer { INSTALLATION, CUTTING, DETAILS, ALL }

internal data class DrawingPoint2D(val x: Double, val y: Double) {
    operator fun plus(other: DrawingPoint2D) = DrawingPoint2D(x + other.x, y + other.y)
    operator fun minus(other: DrawingPoint2D) = DrawingPoint2D(x - other.x, y - other.y)
    operator fun times(scale: Double) = DrawingPoint2D(x * scale, y * scale)
    val length: Double get() = hypot(x, y)
}

internal data class DrawingBounds2D(
    val minX: Double,
    val minY: Double,
    val maxX: Double,
    val maxY: Double,
) {
    val width: Double get() = max(1.0, maxX - minX)
    val height: Double get() = max(1.0, maxY - minY)
    val center: DrawingPoint2D get() = DrawingPoint2D((minX + maxX) / 2.0, (minY + maxY) / 2.0)

    fun expanded(margin: Double) = DrawingBounds2D(minX - margin, minY - margin, maxX + margin, maxY + margin)

    companion object {
        fun from(points: List<DrawingPoint2D>): DrawingBounds2D {
            require(points.isNotEmpty()) { "A drawing needs at least one point" }
            return DrawingBounds2D(
                minX = points.minOf { it.x },
                minY = points.minOf { it.y },
                maxX = points.maxOf { it.x },
                maxY = points.maxOf { it.y },
            )
        }
    }
}

internal data class DrawingStroke2D(
    val partId: String,
    val code: String,
    val kind: FabricationPartKind,
    val points: List<DrawingPoint2D>,
    val outsideRadiusMm: Double,
)

internal data class DrawingCircle2D(
    val partId: String,
    val center: DrawingPoint2D,
    val radiusMm: Double,
)

internal data class DrawingDimension2D(
    val partId: String?,
    val start: DrawingPoint2D,
    val end: DrawingPoint2D,
    val label: String,
    val lane: Int,
)

internal data class DrawingLabel2D(
    val partId: String,
    val text: String,
    val position: DrawingPoint2D,
)

internal data class AssemblyDrawing2D(
    val view: AssemblyDrawingView,
    val layer: AssemblyDrawingLayer,
    val strokes: List<DrawingStroke2D>,
    val circles: List<DrawingCircle2D>,
    val dimensions: List<DrawingDimension2D>,
    val labels: List<DrawingLabel2D>,
    val welds: List<DrawingPoint2D>,
    val bounds: DrawingBounds2D,
)

internal data class DrawingViewport2D(
    val drawingBounds: DrawingBounds2D,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val padding: Double,
) {
    val scale: Double = min(
        (width - padding * 2.0).coerceAtLeast(1.0) / drawingBounds.width,
        (height - padding * 2.0).coerceAtLeast(1.0) / drawingBounds.height,
    )
    private val screenCenter = DrawingPoint2D(left + width / 2.0, top + height / 2.0)

    fun map(point: DrawingPoint2D): DrawingPoint2D {
        val relative = point - drawingBounds.center
        return DrawingPoint2D(screenCenter.x + relative.x * scale, screenCenter.y - relative.y * scale)
    }
}

/** Builds every working view from the same parametric assembly used by the 3D viewport. */
internal object AssemblyDrawingGenerator {
    private const val ELBOW_SAMPLES = 16

    fun generate(
        assembly: ParametricAssembly3D,
        view: AssemblyDrawingView,
        layer: AssemblyDrawingLayer,
    ): AssemblyDrawing2D {
        val basis = projectionBasis(assembly, view)
        val included = assembly.parts.filter { part ->
            when (layer) {
                AssemblyDrawingLayer.INSTALLATION, AssemblyDrawingLayer.ALL -> true
                AssemblyDrawingLayer.CUTTING -> part.definition.kind == FabricationPartKind.PIPE
                AssemblyDrawingLayer.DETAILS -> part.definition.kind != FabricationPartKind.PIPE
            }
        }
        val strokes = included.flatMap { part ->
            centerlines(part).map { line ->
                DrawingStroke2D(
                    partId = part.id,
                    code = part.code,
                    kind = part.definition.kind,
                    points = line.map(basis::project),
                    outsideRadiusMm = outsideRadius(part),
                )
            }
        }
        val circles = included.flatMap { part -> endCircles(part, basis) }
        val projectedPoints = (strokes.flatMap { it.points } + circles.map { it.center }).ifEmpty {
            listOf(DrawingPoint2D(0.0, 0.0))
        }
        val maxRadius = max(
            strokes.maxOfOrNull { it.outsideRadiusMm } ?: 1.0,
            circles.maxOfOrNull { it.radiusMm } ?: 1.0,
        )
        val bounds = DrawingBounds2D.from(projectedPoints).expanded(maxRadius + 20.0)
        val dimensions = buildList {
            if (layer == AssemblyDrawingLayer.INSTALLATION || layer == AssemblyDrawingLayer.ALL) {
                addAll(overallDimensions(assembly, basis))
            }
            if (layer != AssemblyDrawingLayer.INSTALLATION) {
                included.forEachIndexed { index, part ->
                    partDimension(part, basis, lane = 4 + index % 5)?.let(::add)
                }
            }
        }
        val labels = included.mapNotNull { part ->
            val points = centerlines(part).flatten()
            if (points.isEmpty()) null else DrawingLabel2D(
                partId = part.id,
                text = if (layer == AssemblyDrawingLayer.INSTALLATION) part.code else partLabel(part),
                position = basis.project(points.reduce(Vec3::plus) / points.size.toDouble()),
            )
        }
        val welds = if (layer == AssemblyDrawingLayer.CUTTING) emptyList() else assembly.connections.mapNotNull { connection ->
            val first = assembly.worldPortOrNull(connection.first)?.frame?.position ?: return@mapNotNull null
            val second = assembly.worldPortOrNull(connection.second)?.frame?.position ?: return@mapNotNull null
            basis.project((first + second) / 2.0)
        }
        return AssemblyDrawing2D(view, layer, strokes, circles, dimensions, labels, welds, bounds)
    }

    private fun centerlines(part: PartInstance3D): List<List<Vec3>> {
        val transform = part.transform
        fun world(points: List<Vec3>) = points.map(transform::point)
        return when (val geometry = part.definition.geometry) {
            is StraightPipeGeometry3D -> listOf(world(listOf(Vec3.ZERO, Vec3(geometry.lengthMm, 0.0, 0.0))))
            is ElbowGeometry3D -> {
                val radians = Math.toRadians(abs(geometry.angleDeg))
                val sign = if (geometry.angleDeg >= 0.0) 1.0 else -1.0
                listOf(
                    world(
                        List(ELBOW_SAMPLES + 1) { index ->
                            val angle = radians * index / ELBOW_SAMPLES
                            Vec3(
                                geometry.centerlineRadiusMm * sin(angle),
                                sign * geometry.centerlineRadiusMm * (1.0 - cos(angle)),
                                0.0,
                            )
                        },
                    ),
                )
            }
            is WeldNeckFlangeGeometry3D -> listOf(world(listOf(Vec3.ZERO, Vec3(geometry.faceToWeldMm, 0.0, 0.0))))
            is EqualTeeGeometry3D -> {
                val half = geometry.overallRunMm / 2.0
                listOf(
                    world(listOf(Vec3(-half, 0.0, 0.0), Vec3(half, 0.0, 0.0))),
                    world(listOf(Vec3.ZERO, Vec3(0.0, geometry.branchCenterToEndMm, 0.0))),
                )
            }
            is ReducerGeometry3D -> {
                val z = if (geometry.eccentric) {
                    (geometry.largeOutsideDiameterMm - geometry.smallOutsideDiameterMm) / 2.0
                } else 0.0
                listOf(world(listOf(Vec3.ZERO, Vec3(geometry.lengthMm, 0.0, z))))
            }
            is CapGeometry3D -> listOf(world(listOf(Vec3.ZERO, Vec3(geometry.heightMm, 0.0, 0.0))))
        }
    }

    private fun outsideRadius(part: PartInstance3D): Double = when (val geometry = part.definition.geometry) {
        is StraightPipeGeometry3D -> geometry.outsideDiameterMm / 2.0
        is ElbowGeometry3D -> geometry.outsideDiameterMm / 2.0
        is WeldNeckFlangeGeometry3D -> geometry.outsideDiameterMm / 2.0
        is EqualTeeGeometry3D -> geometry.outsideDiameterMm / 2.0
        is ReducerGeometry3D -> max(geometry.largeOutsideDiameterMm, geometry.smallOutsideDiameterMm) / 2.0
        is CapGeometry3D -> geometry.outsideDiameterMm / 2.0
    }

    private fun endCircles(part: PartInstance3D, basis: ProjectionBasis): List<DrawingCircle2D> {
        val portCircles = part.definition.ports.mapNotNull { port ->
            val world = port.frame.transformed(part.transform)
            if (abs(world.forward.dot(basis.normal)) < 0.88) return@mapNotNull null
            DrawingCircle2D(part.id, basis.project(world.position), port.outsideDiameterMm / 2.0)
        }
        val flange = part.definition.geometry as? WeldNeckFlangeGeometry3D ?: return portCircles
        val face = part.worldPortOrNull("face") ?: return portCircles
        return if (abs(face.frame.forward.dot(basis.normal)) >= 0.88) {
            portCircles + DrawingCircle2D(part.id, basis.project(face.frame.position), flange.outsideDiameterMm / 2.0)
        } else portCircles
    }

    private fun overallDimensions(assembly: ParametricAssembly3D, basis: ProjectionBasis): List<DrawingDimension2D> {
        val points = assembly.parts.flatMap(::centerlines).flatten()
        if (points.isEmpty()) return emptyList()
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        val minZ = points.minOf { it.z }
        val maxX = points.maxOf { it.x }
        val maxY = points.maxOf { it.y }
        val maxZ = points.maxOf { it.z }
        val origin = Vec3(minX, minY, minZ)
        return listOf(
            Triple(origin, Vec3(maxX, minY, minZ), "X ${drawingNumber(maxX - minX)} mm"),
            Triple(origin, Vec3(minX, maxY, minZ), "Y ${drawingNumber(maxY - minY)} mm"),
            Triple(origin, Vec3(minX, minY, maxZ), "Z ${drawingNumber(maxZ - minZ)} mm"),
        ).mapIndexedNotNull { index, (start, end, label) ->
            val a = basis.project(start)
            val b = basis.project(end)
            if ((b - a).length < 1e-5) null else DrawingDimension2D(null, a, b, label, index + 1)
        }
    }

    private fun partDimension(
        part: PartInstance3D,
        basis: ProjectionBasis,
        lane: Int,
    ): DrawingDimension2D? {
        val line = centerlines(part).maxByOrNull { points ->
            if (points.size < 2) 0.0 else points.zipWithNext().sumOf { (a, b) -> a.distanceTo(b) }
        } ?: return null
        val start = basis.project(line.first())
        val end = basis.project(line.last())
        if ((end - start).length < 1e-5) return null
        val geometry = part.definition.geometry
        val label = when (geometry) {
            is StraightPipeGeometry3D -> "${part.code} CUT ${drawingNumber(geometry.lengthMm)} · Ø${drawingNumber(geometry.outsideDiameterMm)}×${drawingNumber(geometry.wallThicknessMm)}"
            is ElbowGeometry3D -> "${part.code} ${drawingNumber(abs(geometry.angleDeg))}° CLR ${drawingNumber(geometry.centerlineRadiusMm)}"
            is WeldNeckFlangeGeometry3D -> "${part.code} L ${drawingNumber(geometry.faceToWeldMm)} Ø${drawingNumber(geometry.outsideDiameterMm)} PCD ${drawingNumber(geometry.boltCircleDiameterMm)}"
            is EqualTeeGeometry3D -> "${part.code} RUN ${drawingNumber(geometry.overallRunMm)} BR ${drawingNumber(geometry.branchCenterToEndMm)}"
            is ReducerGeometry3D -> "${part.code} L ${drawingNumber(geometry.lengthMm)} Ø${drawingNumber(geometry.largeOutsideDiameterMm)}/${drawingNumber(geometry.smallOutsideDiameterMm)}"
            is CapGeometry3D -> "${part.code} H ${drawingNumber(geometry.heightMm)}"
        }
        return DrawingDimension2D(part.id, start, end, label, lane)
    }

    private fun partLabel(part: PartInstance3D): String = when (val geometry = part.definition.geometry) {
        is StraightPipeGeometry3D -> "${part.code} · Ø${drawingNumber(geometry.outsideDiameterMm)} t${drawingNumber(geometry.wallThicknessMm)}"
        is ElbowGeometry3D -> "${part.code} · ${drawingNumber(abs(geometry.angleDeg))}° R${drawingNumber(geometry.centerlineRadiusMm)}"
        is WeldNeckFlangeGeometry3D -> "${part.code} · ${geometry.boltHoleCount}×Ø${drawingNumber(geometry.boltHoleDiameterMm)} PCD${drawingNumber(geometry.boltCircleDiameterMm)}"
        is EqualTeeGeometry3D -> "${part.code} · TEE Ø${drawingNumber(geometry.outsideDiameterMm)}"
        is ReducerGeometry3D -> "${part.code} · Ø${drawingNumber(geometry.largeOutsideDiameterMm)}/${drawingNumber(geometry.smallOutsideDiameterMm)}${if (geometry.eccentric) " ECC" else ""}"
        is CapGeometry3D -> "${part.code} · CAP Ø${drawingNumber(geometry.outsideDiameterMm)}"
    }

    private fun projectionBasis(assembly: ParametricAssembly3D, view: AssemblyDrawingView): ProjectionBasis = when (view) {
        AssemblyDrawingView.ISOMETRIC -> ProjectionBasis(
            right = Vec3(1.0, -1.0, 0.0).normalized(),
            up = Vec3(1.0, 1.0, 2.0).normalized(),
            normal = Vec3(-1.0, -1.0, 1.0).normalized(),
        )
        AssemblyDrawingView.TOP -> ProjectionBasis(Vec3.UNIT_X, Vec3.UNIT_Y, Vec3.UNIT_Z)
        AssemblyDrawingView.SIDE -> ProjectionBasis(Vec3.UNIT_X, Vec3.UNIT_Z, -Vec3.UNIT_Y)
        AssemblyDrawingView.RIGHT -> ProjectionBasis(-Vec3.UNIT_Y, Vec3.UNIT_Z, Vec3.UNIT_X)
        AssemblyDrawingView.END -> {
            val terminal = assembly.freePorts().lastOrNull()?.let(assembly::worldPortOrNull)?.frame
            if (terminal == null) ProjectionBasis(-Vec3.UNIT_Y, Vec3.UNIT_Z, Vec3.UNIT_X)
            else ProjectionBasis(terminal.left, terminal.up, terminal.forward)
        }
    }

    private data class ProjectionBasis(val right: Vec3, val up: Vec3, val normal: Vec3) {
        fun project(point: Vec3) = DrawingPoint2D(point.dot(right), point.dot(up))
    }
}

internal fun pickDrawingPart(
    drawing: AssemblyDrawing2D,
    viewport: DrawingViewport2D,
    point: DrawingPoint2D,
    tolerancePx: Double = 22.0,
): String? = drawing.strokes
    .flatMap { stroke -> stroke.points.zipWithNext().map { (a, b) -> Triple(stroke.partId, viewport.map(a), viewport.map(b)) } }
    .map { (partId, start, end) -> partId to pointToSegmentDistance(point, start, end) }
    .filter { it.second <= tolerancePx }
    .minByOrNull { it.second }
    ?.first

private fun pointToSegmentDistance(point: DrawingPoint2D, start: DrawingPoint2D, end: DrawingPoint2D): Double {
    val line = end - start
    if (line.length < 1e-9) return (point - start).length
    val relative = point - start
    val t = ((relative.x * line.x + relative.y * line.y) / (line.x * line.x + line.y * line.y)).coerceIn(0.0, 1.0)
    return (point - (start + line * t)).length
}

private fun drawingNumber(value: Double): String = if (abs(value - value.toLong()) < 1e-6) {
    value.toLong().toString()
} else String.format(Locale.US, "%.1f", value)

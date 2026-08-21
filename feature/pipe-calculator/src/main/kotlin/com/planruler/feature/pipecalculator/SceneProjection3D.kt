package com.planruler.feature.pipecalculator

import androidx.compose.ui.geometry.Offset
import com.planruler.fabrication3d.AssemblyMesh3D
import com.planruler.fabrication3d.Quaternion
import com.planruler.fabrication3d.Vec3
import kotlin.math.min

internal data class ProjectedPoint3D(val screen: Offset, val depth: Double, val visible: Boolean)

/**
 * Maps the engine's millimetre world onto the viewport. Kept out of the drawing file so the
 * camera and the drag arithmetic below can be checked by ordinary unit tests rather than
 * only through the rendered scene.
 */
internal class SceneProjector3D(
    mesh: AssemblyMesh3D,
    private val width: Float,
    private val height: Float,
    yawDeg: Float,
    pitchDeg: Float,
    zoom: Float,
    private val perspective: Boolean,
    private val panX: Float = 0f,
    private val panY: Float = 0f,
) {
    private val center = mesh.bounds.center
    private val radius = mesh.bounds.radius.coerceAtLeast(1.0)
    private val cameraRotation = (
        Quaternion.axisAngle(Vec3.UNIT_X, pitchDeg.toDouble()) *
            Quaternion.axisAngle(Vec3.UNIT_Y, yawDeg.toDouble())
        ).normalized()
    private val distance = radius * 4.1 / zoom
    private val focal = min(width, height) * 1.32
    private val orthographicScale = min(width, height) * zoom / (radius * 2.65)

    fun project(world: Vec3): ProjectedPoint3D {
        val view = cameraRotation.rotate(world - center)
        val denominator = distance - view.z
        val scale = if (perspective) focal / denominator.coerceAtLeast(radius * 0.12) else orthographicScale
        return ProjectedPoint3D(
            screen = Offset(
                width / 2f + panX + (view.x * scale).toFloat(),
                height / 2f + panY - (view.y * scale).toFloat(),
            ),
            depth = view.z,
            visible = !perspective || denominator > 0.0,
        )
    }

    fun rotateDirection(direction: Vec3): Vec3 = cameraRotation.rotate(direction)
}

/** How far a finger travelled along a part's own axis, and how well that axis reads on screen. */
internal data class AxisDrag3D(
    val millimetres: Double,
    /** Screen pixels one millimetre of the axis covers; small means the axis points at the camera. */
    val pixelsPerMm: Float,
)

internal object DragProjection3D {
    /**
     * An axis nearly pointing at the camera collapses to a few pixels on screen, where a
     * small finger movement would translate into a huge length change. Below this the drag
     * is reported as uncontrollable instead of guessed at.
     */
    const val MIN_PIXELS_PER_MM = 0.02f

    /** The projected scale of a world axis, or null when either probe is behind the camera. */
    fun axisPixelsPerMm(
        projector: SceneProjector3D,
        origin: Vec3,
        axis: Vec3,
        probeMm: Double = 1.0,
    ): Float? {
        val direction = axis.normalizedOrNull() ?: return null
        val anchor = projector.project(origin)
        val probe = projector.project(origin + direction * probeMm)
        if (!anchor.visible || !probe.visible) return null
        val screenAxis = probe.screen - anchor.screen
        return kotlin.math.sqrt(
            (screenAxis.x * screenAxis.x + screenAxis.y * screenAxis.y).toDouble(),
        ).toFloat() / probeMm.toFloat()
    }

    /**
     * Converts a screen drag into millimetres along the world [axis] anchored at [origin].
     *
     * The axis is projected by sampling it one probe length out, which keeps perspective
     * foreshortening in the answer: the same finger travel means fewer millimetres when the
     * part is edge-on and more when it recedes from the camera. Returns null when the axis
     * is too foreshortened to control.
     */
    fun alongAxis(
        projector: SceneProjector3D,
        origin: Vec3,
        axis: Vec3,
        dragPx: Offset,
        probeMm: Double = 1.0,
    ): AxisDrag3D? {
        val direction = axis.normalizedOrNull() ?: return null
        val anchor = projector.project(origin)
        val probe = projector.project(origin + direction * probeMm)
        if (!anchor.visible || !probe.visible) return null

        val screenAxis = probe.screen - anchor.screen
        val lengthSquared = screenAxis.x * screenAxis.x + screenAxis.y * screenAxis.y
        val pixelsPerMm = axisPixelsPerMm(projector, origin, direction, probeMm) ?: return null
        if (pixelsPerMm < MIN_PIXELS_PER_MM || lengthSquared <= 0f) return null

        // Least-squares projection of the drag onto the screen axis, scaled back to millimetres.
        val along = (dragPx.x * screenAxis.x + dragPx.y * screenAxis.y) / lengthSquared
        return AxisDrag3D(millimetres = along.toDouble() * probeMm, pixelsPerMm = pixelsPerMm)
    }

    /**
     * Snaps a dragged length to the step a fitter marks out, so a finger can still land on a
     * round number. Values below [step] keep their precision rather than snapping to zero.
     */
    fun snapLength(valueMm: Double, stepMm: Double): Double =
        if (stepMm <= 0.0 || valueMm < stepMm) valueMm else Math.round(valueMm / stepMm) * stepMm

    /** Snaps an angle to the nearest catalog angle when the finger lands close enough. */
    fun snapAngle(valueDeg: Double, allowedDeg: List<Double>, toleranceDeg: Double = 3.0): Double {
        val nearest = allowedDeg.minByOrNull { kotlin.math.abs(it - kotlin.math.abs(valueDeg)) } ?: return valueDeg
        val magnitude = kotlin.math.abs(valueDeg)
        if (kotlin.math.abs(nearest - magnitude) > toleranceDeg) return valueDeg
        return if (valueDeg < 0.0) -nearest else nearest
    }
}

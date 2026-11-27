package com.planruler.isometric

import com.planruler.model.IsoNodeId
import com.planruler.model.IsometricSheet
import kotlin.math.round

/** A gesture is a preview until release. Its origin never changes as the finger moves. */
data class IsoNodeDrag(
    val nodeId: IsoNodeId,
    val origin: Vec3,
    val pointerOrigin: P2,
) {
    /** Move along one model axis, preserving the grip offset and snapping the displacement. */
    fun target(pointer: P2, projection: IsoProjection, stepMm: Double): Vec3 {
        if (!stepMm.isFinite() || stepMm <= 0.0) return origin
        val match = projection.matchRun(pointer - pointerOrigin, allowOffsets = false) ?: return origin
        val distance = round(match.modelLengthMm / stepMm) * stepMm
        return origin + match.run.direction * distance
    }

    fun preview(sheet: IsometricSheet, target: Vec3): IsometricSheet = sheet.copy(
        nodes = sheet.nodes.map { if (it.id == nodeId) it.copy(position = target.toIsoPoint()) else it },
    )
}

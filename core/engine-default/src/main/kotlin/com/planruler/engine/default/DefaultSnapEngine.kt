package com.planruler.engine.default

import com.planruler.engine.api.SnapContext
import com.planruler.engine.api.SnapEngine
import com.planruler.engine.api.SnapResult
import com.planruler.engine.api.SnapTarget
import com.planruler.engine.api.SnapType
import com.planruler.model.DocPoint
import kotlin.math.abs

class DefaultSnapEngine : SnapEngine {
    override fun resolve(candidate: DocPoint, context: SnapContext): SnapResult {
        if (!context.enabled || context.sensitivityDocumentUnits <= 0.0) {
            return SnapResult(candidate, SnapType.NONE)
        }
        val limit = context.sensitivityDocumentUnits
        val options = mutableListOf<Candidate>()

        context.measurements
            .asSequence()
            .filter { it.id != context.excludedMeasurementId }
            .forEach { measurement ->
                measurement.points.forEachIndexed { index, point ->
                    val distance = candidate.distanceTo(point)
                    if (distance <= limit) {
                        options += Candidate(
                            SnapResult(
                                point,
                                SnapType.VERTEX,
                                SnapTarget(measurement.id, vertexIndex = index),
                                distance,
                            ),
                            priority = 0,
                        )
                    }
                }
                measurement.points.zipWithNext().forEachIndexed { index, (start, end) ->
                    val projected = projectToSegment(candidate, start, end)
                    val distance = candidate.distanceTo(projected)
                    if (distance <= limit) {
                        options += Candidate(
                            SnapResult(
                                projected,
                                SnapType.SEGMENT,
                                SnapTarget(measurement.id, segmentIndex = index),
                                distance,
                                start,
                                end,
                            ),
                            priority = 3,
                        )
                    }
                }
            }

        context.anchor?.let { anchor ->
            val horizontalDistance = abs(candidate.y - anchor.y)
            if (horizontalDistance <= limit) {
                options += Candidate(
                    SnapResult(
                        candidate.copy(y = anchor.y),
                        SnapType.HORIZONTAL,
                        distance = horizontalDistance,
                        guideStart = anchor,
                        guideEnd = candidate.copy(y = anchor.y),
                    ),
                    priority = 1,
                )
            }
            val verticalDistance = abs(candidate.x - anchor.x)
            if (verticalDistance <= limit) {
                options += Candidate(
                    SnapResult(
                        candidate.copy(x = anchor.x),
                        SnapType.VERTICAL,
                        distance = verticalDistance,
                        guideStart = anchor,
                        guideEnd = candidate.copy(x = anchor.x),
                    ),
                    priority = 2,
                )
            }
        }

        // Explicit points are the strongest user intent, followed by axis guides
        // and finally an arbitrary point projected onto a segment.
        return options
            .filter { it.result.type in context.allowed }
            .minWithOrNull(compareBy<Candidate> { it.priority }.thenBy { it.result.distance })
            ?.result
            ?: SnapResult(candidate, SnapType.NONE)
    }

    private fun projectToSegment(point: DocPoint, start: DocPoint, end: DocPoint): DocPoint {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0.0) return start
        val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        return DocPoint(start.x + t * dx, start.y + t * dy)
    }

    private data class Candidate(val result: SnapResult, val priority: Int)
}

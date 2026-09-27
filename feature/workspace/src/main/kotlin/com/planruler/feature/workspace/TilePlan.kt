package com.planruler.feature.workspace

import com.planruler.model.ScreenPoint
import com.planruler.model.ViewportState
import com.planruler.model.ViewportTransform
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class TileCell(val left: Double, val top: Double, val right: Double, val bottom: Double)

internal data class TilePlan(val scale: Double, val cells: List<TileCell>)

/**
 * Sharp re-render of exactly what is on screen: the visible page rectangle plus a small
 * margin for panning, at the display scale (one tile pixel per screen pixel), split into
 * cells no larger than [cellPixels]. When the grid would exceed [maxCells] the scale is
 * lowered, so memory stays bounded at any zoom.
 */
internal fun viewportTilePlan(
    viewport: ViewportState,
    canvasWidth: Int,
    canvasHeight: Int,
    pageWidth: Double,
    pageHeight: Double,
    cellPixels: Double = WorkspaceViewModel.TILE_PIXELS,
    maxCells: Int = WorkspaceViewModel.TILE_LIMIT,
    margin: Double = 0.1,
    maxScale: Double = 64.0,
): TilePlan? {
    if (canvasWidth <= 0 || canvasHeight <= 0 || pageWidth <= 0.0 || pageHeight <= 0.0) return null
    val transform = ViewportTransform(canvasWidth.toDouble(), canvasHeight.toDouble(), viewport)
    val topLeft = transform.screenToDocument(ScreenPoint(0.0, 0.0))
    val bottomRight = transform.screenToDocument(ScreenPoint(canvasWidth.toDouble(), canvasHeight.toDouble()))
    val padX = (bottomRight.x - topLeft.x) * margin
    val padY = (bottomRight.y - topLeft.y) * margin
    val left = max(0.0, topLeft.x - padX)
    val top = max(0.0, topLeft.y - padY)
    val right = min(pageWidth, bottomRight.x + padX)
    val bottom = min(pageHeight, bottomRight.y + padY)
    if (right <= left || bottom <= top) return null

    var scale = viewport.zoom.coerceIn(0.01, maxScale)
    var columns = ceil((right - left) * scale / cellPixels).toInt().coerceAtLeast(1)
    var rows = ceil((bottom - top) * scale / cellPixels).toInt().coerceAtLeast(1)
    while (columns * rows > maxCells) {
        scale *= min(0.9, sqrt(maxCells.toDouble() / (columns * rows)))
        columns = ceil((right - left) * scale / cellPixels).toInt().coerceAtLeast(1)
        rows = ceil((bottom - top) * scale / cellPixels).toInt().coerceAtLeast(1)
    }
    val cellWidth = (right - left) / columns
    val cellHeight = (bottom - top) / rows
    val cells = buildList {
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                add(
                    TileCell(
                        left = left + column * cellWidth,
                        top = top + row * cellHeight,
                        right = if (column == columns - 1) right else left + (column + 1) * cellWidth,
                        bottom = if (row == rows - 1) bottom else top + (row + 1) * cellHeight,
                    ),
                )
            }
        }
    }
    return TilePlan(scale, cells)
}

package com.planruler.isometric

import com.planruler.model.IsometricSheet
import kotlin.math.max

/** One print layout shared by the PDF adapter and its geometry tests. Units remain model mm. */
data class IsoPaperDrawing(
    val scene: IsoScene,
    val style: IsoSceneStyle,
    val scale: Double,
    val bounds: Rect2,
) {
    // A3 landscape, expanding only for unusually long labels rather than clipping them.
    val widthMm: Double get() = max(420.0, bounds.width / scale + 20.0)
    val heightMm: Double get() = max(297.0, bounds.height / scale + 35.0)

    companion object {
        fun build(sheet: IsometricSheet, labeler: IsoLabeler = DefaultIsoLabeler): IsoPaperDrawing {
            var scale = IsoExport.paperScaleFor(sheet)
            var result: IsoPaperDrawing
            var attempts = 0
            do {
                val style = IsoSceneStyle.forPaper(scale)
                val scene = IsoSceneBuilder.build(sheet, style, labeler)
                val bounds = (scene.bounds ?: Rect2(0.0, 0.0, scale, scale)).expanded(style.textHeightMm * 2)
                result = IsoPaperDrawing(scene, style, scale, bounds)
                if (bounds.width / scale <= 400.0 && bounds.height / scale <= 262.0) return result
                scale = IsoExport.nextPaperScale(scale)
            } while (++attempts < 8)
            return result
        }
    }
}

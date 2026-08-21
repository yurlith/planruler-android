package com.planruler.feature.pipecalculator

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.planruler.designsystem.PlanRulerTestTags
import com.planruler.designsystem.component.PlanRulerToolTile
import com.planruler.designsystem.icon.PlanRulerIcons
import com.planruler.designsystem.localization.UiTextKey
import com.planruler.designsystem.localization.uiText
import com.planruler.designsystem.theme.LocalToolAccents
import com.planruler.designsystem.theme.Space
import com.planruler.model.AppLanguage

private data class WorkshopEntry(
    val tool: CalculatorTool,
    val title: UiTextKey,
    val body: UiTextKey,
)

private val workshopEntries = listOf(
    WorkshopEntry(CalculatorTool.INSTALLATION, UiTextKey.TOOL_INSTALLATION, UiTextKey.TOOL_INSTALLATION_BODY),
    WorkshopEntry(CalculatorTool.HYDRAULICS, UiTextKey.TOOL_HYDRAULICS, UiTextKey.TOOL_HYDRAULICS_BODY),
    WorkshopEntry(CalculatorTool.HEATING, UiTextKey.TOOL_HEATING, UiTextKey.TOOL_HEATING_BODY),
    WorkshopEntry(CalculatorTool.EXPANSION, UiTextKey.TOOL_EXPANSION, UiTextKey.TOOL_EXPANSION_BODY),
    WorkshopEntry(CalculatorTool.CATALOG, UiTextKey.TOOL_CATALOG, UiTextKey.TOOL_CATALOG_BODY),
    WorkshopEntry(CalculatorTool.GAS_CH, UiTextKey.TOOL_GAS, UiTextKey.TOOL_GAS_BODY),
)

@Composable
internal fun WorkshopHome(language: AppLanguage, onSelect: (CalculatorTool) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        modifier = Modifier.fillMaxSize().testTag(PlanRulerTestTags.WorkshopRoot),
        contentPadding = PaddingValues(start = Space.x4, end = Space.x4, top = Space.x4, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
        verticalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        workshopEntries.forEach { entry ->
            item(key = entry.tool.name) {
                val accent = workshopAccent(entry.tool)
                PlanRulerToolTile(
                    icon = workshopIcon(entry.tool),
                    title = uiText(language, entry.title),
                    body = uiText(language, entry.body),
                    onClick = { onSelect(entry.tool) },
                    modifier = Modifier.testTag(PlanRulerTestTags.workshopTool(entry.tool.name)),
                    accent = accent,
                    preview = { WorkshopDiagram(entry.tool, accent) },
                )
            }
        }
    }
}

@Composable
private fun workshopAccent(tool: CalculatorTool): Color {
    val accents = LocalToolAccents.current
    return when (tool) {
        CalculatorTool.INSTALLATION -> MaterialTheme.colorScheme.primary
        CalculatorTool.HYDRAULICS -> accents.hydraulics
        CalculatorTool.HEATING -> accents.heating
        CalculatorTool.EXPANSION -> accents.expansion
        CalculatorTool.CATALOG -> accents.catalog
        CalculatorTool.GAS_CH -> accents.gas
    }
}

private fun workshopIcon(tool: CalculatorTool) = when (tool) {
    CalculatorTool.INSTALLATION -> PlanRulerIcons.Ruler
    CalculatorTool.HYDRAULICS -> PlanRulerIcons.Counter
    CalculatorTool.HEATING -> PlanRulerIcons.Home
    CalculatorTool.EXPANSION -> PlanRulerIcons.Area
    CalculatorTool.CATALOG -> PlanRulerIcons.Document
    CalculatorTool.GAS_CH -> PlanRulerIcons.Warning
}

@Composable
private fun WorkshopDiagram(tool: CalculatorTool, accent: Color) {
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    Canvas(Modifier.fillMaxWidth().height(92.dp)) {
        val w = size.width
        val h = size.height
        when (tool) {
            CalculatorTool.INSTALLATION -> {
                val points = listOf(
                    Offset(w * .08f, h * .72f), Offset(w * .25f, h * .72f),
                    Offset(w * .42f, h * .28f), Offset(w * .72f, h * .28f), Offset(w * .9f, h * .58f),
                )
                points.zipWithNext().forEach { (a, b) -> drawLine(accent, a, b, 14.dp.toPx(), StrokeCap.Round) }
                drawCircle(secondary, 16.dp.toPx(), points.first())
                drawCircle(accent, 11.dp.toPx(), points.first(), style = Stroke(4.dp.toPx()))
                drawCircle(secondary, 16.dp.toPx(), points.last())
                drawCircle(accent, 11.dp.toPx(), points.last(), style = Stroke(4.dp.toPx()))
            }
            CalculatorTool.HYDRAULICS -> {
                drawLine(accent, Offset(w * .08f, h * .56f), Offset(w * .92f, h * .56f), 16.dp.toPx(), StrokeCap.Round)
                repeat(4) { index ->
                    val x = w * (.25f + index * .16f)
                    val arrow = Path().apply {
                        moveTo(x - 10.dp.toPx(), h * .35f)
                        lineTo(x + 7.dp.toPx(), h * .56f)
                        lineTo(x - 10.dp.toPx(), h * .77f)
                    }
                    drawPath(arrow, Color.White.copy(alpha = .85f), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                drawCircle(secondary, 20.dp.toPx(), Offset(w * .78f, h * .24f))
                drawLine(accent, Offset(w * .78f, h * .24f), Offset(w * .87f, h * .16f), 3.dp.toPx())
            }
            CalculatorTool.HEATING -> {
                val house = Path().apply {
                    moveTo(w * .12f, h * .45f); lineTo(w * .32f, h * .12f); lineTo(w * .52f, h * .45f)
                    lineTo(w * .52f, h * .86f); lineTo(w * .12f, h * .86f); close()
                }
                drawPath(house, accent, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
                repeat(3) { row ->
                    drawLine(accent, Offset(w * .62f, h * (.28f + row * .22f)), Offset(w * .92f, h * (.28f + row * .22f)), 7.dp.toPx(), StrokeCap.Round)
                }
                drawLine(secondary, Offset(w * .58f, h * .2f), Offset(w * .58f, h * .8f), 3.dp.toPx())
            }
            CalculatorTool.EXPANSION -> {
                val vessel = Rect(w * .34f, h * .08f, w * .66f, h * .86f)
                drawOval(accent.copy(alpha = .22f), topLeft = vessel.topLeft, size = vessel.size)
                drawOval(accent, topLeft = vessel.topLeft, size = vessel.size, style = Stroke(4.dp.toPx()))
                drawLine(accent, Offset(w * .5f, h * .86f), Offset(w * .5f, h), 8.dp.toPx(), StrokeCap.Round)
                drawLine(secondary, Offset(w * .35f, h * .53f), Offset(w * .65f, h * .53f), 3.dp.toPx())
            }
            CalculatorTool.CATALOG -> {
                drawCircle(accent.copy(alpha = .16f), h * .4f, Offset(w * .5f, h * .5f))
                drawCircle(accent, h * .4f, Offset(w * .5f, h * .5f), style = Stroke(4.dp.toPx()))
                drawCircle(accent, h * .17f, Offset(w * .5f, h * .5f), style = Stroke(4.dp.toPx()))
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    drawCircle(
                        accent,
                        3.5.dp.toPx(),
                        Offset(w * .5f + kotlin.math.cos(angle).toFloat() * h * .29f, h * .5f + kotlin.math.sin(angle).toFloat() * h * .29f),
                    )
                }
            }
            CalculatorTool.GAS_CH -> {
                drawLine(accent, Offset(w * .08f, h * .62f), Offset(w * .92f, h * .62f), 15.dp.toPx(), StrokeCap.Round)
                drawCircle(Color.White.copy(alpha = .88f), 7.dp.toPx(), Offset(w * .34f, h * .62f))
                drawCircle(Color.White.copy(alpha = .88f), 7.dp.toPx(), Offset(w * .68f, h * .62f))
                val shield = Path().apply {
                    moveTo(w * .5f, h * .08f); lineTo(w * .62f, h * .2f); lineTo(w * .6f, h * .47f)
                    lineTo(w * .5f, h * .58f); lineTo(w * .4f, h * .47f); lineTo(w * .38f, h * .2f); close()
                }
                drawPath(shield, accent)
                drawLine(Color.White, Offset(w * .45f, h * .32f), Offset(w * .49f, h * .39f), 3.dp.toPx())
                drawLine(Color.White, Offset(w * .49f, h * .39f), Offset(w * .56f, h * .25f), 3.dp.toPx())
            }
        }
    }
}

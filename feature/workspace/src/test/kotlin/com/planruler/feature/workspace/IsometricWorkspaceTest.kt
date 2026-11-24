package com.planruler.feature.workspace

import com.planruler.isometric.IsoAxis
import com.planruler.isometric.IsoProjection
import com.planruler.isometric.IsoResult
import com.planruler.isometric.RunStart
import com.planruler.isometric.Vec3
import com.planruler.model.AppLanguage
import com.planruler.model.DocPoint
import com.planruler.model.IsoComponentKind
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsoProjectionStyle
import com.planruler.model.IsometricSheet
import com.planruler.model.IsometricSheetId
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.PipeRoute
import com.planruler.model.TakeoffProperties
import com.planruler.model.TradeCategory
import com.planruler.model.ViewportState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsometricWorkspaceTest {
    private var ids = 0
    private val published = mutableListOf<Pair<List<IsometricSheet>, IsometricSheetId?>>()
    private fun session() = IsometricSession(clock = { 5L }, ids = { "id${ids++}" }) { sheets, active -> published += sheets to active }
    private val dn20 = IsoPipeSpec(IsoPipeSystem.COLD_WATER, 20)

    @Test
    fun `opening creates the first sheet once and edits are written back`() {
        val session = session()
        val sheet = session.open("Изометрия")
        assertEquals("Изометрия", sheet.name)
        assertEquals(sheet.id, session.open("Изометрия").id)
        assertEquals(1, session.sheets.value.size)

        val run = session.edit { addRun(RunStart.Point(Vec3.ZERO), Vec3(0.0, 0.0, 1_500.0), dn20) }
        assertTrue(run is IsoResult.Ok)
        assertEquals(1, session.sheets.value.single().pipes.size)
        assertEquals(1, published.last().first.single().pipes.size)

        val failed = session.edit { addRun(RunStart.Point(Vec3.ZERO), Vec3.ZERO, dn20) }
        assertTrue(failed is IsoResult.Error)
    }

    @Test
    fun `sheet names stay unique and deleting falls back to another sheet`() {
        val session = session()
        val first = session.create("Стояк")
        val second = session.create("Стояк")
        assertEquals("Стояк 2", second.name)
        assertEquals(second.id, session.active.value)
        session.delete(second.id)
        assertEquals(first.id, session.active.value)
        session.delete(first.id)
        assertNull(session.active.value)
        assertTrue(session.edit { rename("x") } is IsoResult.Error)
    }

    @Test
    fun `attaching stored sheets reports nothing and views are kept without undo`() {
        val session = session()
        val stored = session.create("A")
        published.clear()
        val other = session()
        other.attach(listOf(stored), stored.id)
        assertTrue(published.isEmpty())
        other.updateViewport(ViewportState(0.2, 100.0, -50.0))
        assertEquals(ViewportState(0.2, 100.0, -50.0), other.sheets.value.single().viewport)
        assertTrue(!other.state.value.canUndo)
    }

    @Test
    fun `drawing labels follow each language`() {
        val russian = IsometricLabeler(AppLanguage.RUSSIAN)
        assertEquals("±0,000", russian.elevation(0.2))
        assertEquals("+2,700", russian.elevation(2_700.0))
        assertEquals("−0,350", russian.elevation(-350.0))
        assertEquals("+2.700", IsometricLabeler(AppLanguage.ENGLISH).elevation(2_700.0))
        assertEquals("1250", russian.length(1_249.6))
        assertEquals("Т1 DN25", russian.pipeTag(IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25)))
        assertEquals("VL DN25", IsometricLabeler(AppLanguage.GERMAN).pipeTag(IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25)))
        assertNull(russian.pipeTag(IsoPipeSpec(IsoPipeSystem.OTHER, null)))
        AppLanguage.entries.forEach { language ->
            IsoPipeSystem.entries.filter { it != IsoPipeSystem.OTHER }.forEach { system ->
                assertNotNull("$language $system", isoSystemCode(language, system))
            }
        }
    }

    @Test
    fun `direction pad arrows match each projection`() {
        val iso = IsoProjection.of(IsoProjectionStyle.ISOMETRIC)
        val frontal = IsoProjection.of(IsoProjectionStyle.FRONTAL)
        assertEquals(listOf("↗", "↙", "↖", "↘", "↑", "↓"), IsoAxis.entries.map { isoArrow(iso, it) })
        assertEquals(listOf("→", "←", "↗", "↙", "↑", "↓"), IsoAxis.entries.map { isoArrow(frontal, it) })
    }

    @Test
    fun `bill of materials exports as CSV with the user's language and decimal comma`() {
        val session = session()
        session.open("Sheet")
        val main = (session.edit { addRun(RunStart.Point(Vec3.ZERO), Vec3(2_500.0, 0.0, 0.0), IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25, "Сталь; ВГП")) } as IsoResult.Ok).value
        session.edit { addRun(RunStart.Node(main.endNodeId), Vec3(0.0, 0.0, 1_200.0), IsoPipeSpec(IsoPipeSystem.HEATING_SUPPLY, 25, "Сталь; ВГП")) }
        session.edit { addComponent(IsoComponentKind.BALL_VALVE, main.pipeId, 0.5) }
        val sheet = session.sheets.value.single()
        val csv = isometricDocument(sheet, IsometricExportFormat.CSV, Wt(AppLanguage.RUSSIAN), AppLanguage.RUSSIAN, ";")
        assertTrue(csv.startsWith("﻿"))
        val lines = csv.trimEnd().lines()
        assertEquals(5, lines.size) // header, pipe, elbow, open ends (one line, 2 pcs), valve
        assertTrue(lines.any { "Открытый конец" in it && it.contains(";2;") })
        assertTrue(lines.any { "3,70" in it && "\"Сталь; ВГП\"" in it })
        assertTrue(lines.any { "Отвод 90°" in it })
        assertTrue(lines.any { "Кран шаровой" in it })
        assertTrue(isometricDocument(sheet, IsometricExportFormat.SVG, Wt(AppLanguage.ENGLISH), AppLanguage.ENGLISH).contains("<svg"))
        assertTrue(isometricDocument(sheet, IsometricExportFormat.DXF, Wt(AppLanguage.POLISH), AppLanguage.POLISH).contains("ENTITIES"))
    }

    @Test
    fun `pipe runs count corner elbows and the bends of risers`() {
        fun run(category: TradeCategory, route: PipeRoute? = null) = Measurement(
            id = MeasurementId("p"),
            type = MeasurementType.POLYLINE,
            points = listOf(DocPoint(0.0, 0.0), DocPoint(100.0, 0.0), DocPoint(100.0, 100.0), DocPoint(150.0, 150.0)),
            takeoff = TakeoffProperties(category = category),
            createdAtEpochMs = 0,
            route = route,
        )
        // Corners: 90° at vertex 1 and 45° at vertex 2.
        assertEquals(2, pipeElbows(listOf(run(TradeCategory.HEATING))))
        assertEquals(0, pipeElbows(listOf(run(TradeCategory.PAINTING))))
        // A riser inside a run is two elbows and replaces that corner; one at each end is one more.
        val route = PipeRoute(risesMm = listOf(300.0, 2_400.0, 0.0, -600.0))
        assertEquals(1 + 2 + 1 + 1, pipeElbows(listOf(run(TradeCategory.PAINTING, route))))
    }

    @Test
    fun `field strip adds elbows and plan labels show the leg height`() {
        assertEquals("12.50 m", fieldTotalWithElbows("12.50 m", 0, "elb."))
        assertEquals("12.50 m · 3 elb.", fieldTotalWithElbows("12.50 m", 3, "elb."))
        assertEquals("2 отв.", fieldTotalWithElbows("—", 2, "отв."))
        assertEquals("↑ 2700 mm", riseLabel(2_700.0))
        assertEquals("↓ 350 mm", riseLabel(-350.4))
    }

    @Test
    fun `export names come from the sheet and stay valid file names`() {
        assertEquals("Котельная Т1.svg", isometricFileName("Котельная Т1", IsometricExportFormat.SVG))
        assertEquals("a b c.dxf", isometricFileName("a/b:c", IsometricExportFormat.DXF))
        assertEquals("isometric.csv", isometricFileName("  ?*  ", IsometricExportFormat.CSV))
        assertEquals(64, isometricFileName("x".repeat(200), IsometricExportFormat.SVG).length)
    }
}

package com.planruler.feature.pipecalculator

import com.planruler.fabrication3d.CapGeometry3D
import com.planruler.fabrication3d.ElbowGeometry3D
import com.planruler.fabrication3d.EqualTeeGeometry3D
import com.planruler.fabrication3d.FabricationPartKind
import com.planruler.fabrication3d.ParametricAssembly3D
import com.planruler.fabrication3d.ReducerGeometry3D
import com.planruler.fabrication3d.StraightPipeGeometry3D
import com.planruler.fabrication3d.WeldNeckFlangeGeometry3D
import com.planruler.model.AppLanguage
import java.util.Locale
import kotlin.math.abs

/** Installer-facing information shared by the drawing, cut list and material schedule. */
internal data class AssemblyPartFieldRow(
    val partId: String,
    val code: String,
    val kind: FabricationPartKind,
    val title: String,
    val primaryValue: String,
    val specification: String,
    val connectedCodes: List<String>,
)

internal data class AssemblyMaterialFieldSummary(
    val pipeCount: Int,
    val totalPipeLengthMm: Double,
    val fittingCounts: Map<FabricationPartKind, Int>,
) {
    fun label(language: AppLanguage): String = buildList {
        if (pipeCount > 0) {
            add(
                if (language == AppLanguage.RUSSIAN) {
                    "Труба: $pipeCount рез. · ${scheduleNumber(totalPipeLengthMm)} mm"
                } else {
                    "Pipe: $pipeCount cuts · ${scheduleNumber(totalPipeLengthMm)} mm"
                },
            )
        }
        fittingCounts.forEach { (kind, count) ->
            add("${kind.fieldKindName(language)}: $count")
        }
    }.joinToString("  |  ")
}

internal fun buildAssemblyPartFieldRows(
    assembly: ParametricAssembly3D,
    language: AppLanguage,
): List<AssemblyPartFieldRow> = assembly.parts.map { part ->
    val geometry = part.definition.geometry
    val (primary, specification) = when (geometry) {
        is StraightPipeGeometry3D -> {
            val cut = if (language == AppLanguage.RUSSIAN) "РЕЗАТЬ" else "CUT"
            "$cut ${scheduleNumber(geometry.lengthMm)} mm" to
                "Ø${scheduleNumber(geometry.outsideDiameterMm)} × ${scheduleNumber(geometry.wallThicknessMm)} mm"
        }
        is ElbowGeometry3D -> "${scheduleNumber(abs(geometry.angleDeg))}°" to
            scheduleText(
                language,
                "Радиус по оси ${scheduleNumber(geometry.centerlineRadiusMm)} mm · Ø${scheduleNumber(geometry.outsideDiameterMm)}",
                "Centerline radius ${scheduleNumber(geometry.centerlineRadiusMm)} mm · Ø${scheduleNumber(geometry.outsideDiameterMm)}",
            )
        is WeldNeckFlangeGeometry3D -> "DN ${assembly.metadata.nominalDiameter}" to
            "PCD ${scheduleNumber(geometry.boltCircleDiameterMm)} · ${geometry.boltHoleCount}×Ø${scheduleNumber(geometry.boltHoleDiameterMm)}"
        is EqualTeeGeometry3D -> scheduleText(language, "Тройник", "Tee") to
            scheduleText(
                language,
                "Проход ${scheduleNumber(geometry.overallRunMm)} mm · ветка ${scheduleNumber(geometry.branchCenterToEndMm)} mm",
                "Run ${scheduleNumber(geometry.overallRunMm)} mm · branch ${scheduleNumber(geometry.branchCenterToEndMm)} mm",
            )
        is ReducerGeometry3D -> "Ø${scheduleNumber(geometry.largeOutsideDiameterMm)} → Ø${scheduleNumber(geometry.smallOutsideDiameterMm)}" to
            scheduleText(
                language,
                "Длина ${scheduleNumber(geometry.lengthMm)} mm${if (geometry.eccentric) " · эксцентрический" else ""}",
                "Length ${scheduleNumber(geometry.lengthMm)} mm${if (geometry.eccentric) " · eccentric" else ""}",
            )
        is CapGeometry3D -> scheduleText(language, "Заглушка", "Cap") to
            "H ${scheduleNumber(geometry.heightMm)} mm · Ø${scheduleNumber(geometry.outsideDiameterMm)}"
    }
    val connected = assembly.connectionsOf(part.id).mapNotNull { connection ->
        val otherId = if (connection.first.partId == part.id) connection.second.partId else connection.first.partId
        assembly.partOrNull(otherId)?.code
    }.distinct()
    AssemblyPartFieldRow(
        partId = part.id,
        code = part.code,
        kind = part.definition.kind,
        title = "${part.code} · ${part.definition.kind.fieldKindName(language)}",
        primaryValue = primary,
        specification = specification,
        connectedCodes = connected,
    )
}

internal fun buildAssemblyMaterialFieldSummary(assembly: ParametricAssembly3D): AssemblyMaterialFieldSummary {
    val pipes = assembly.parts.mapNotNull { it.definition.geometry as? StraightPipeGeometry3D }
    val fittings = FabricationPartKind.entries
        .filterNot { it == FabricationPartKind.PIPE }
        .mapNotNull { kind ->
            assembly.parts.count { it.definition.kind == kind }.takeIf { it > 0 }?.let { kind to it }
        }
        .toMap(linkedMapOf())
    return AssemblyMaterialFieldSummary(
        pipeCount = pipes.size,
        totalPipeLengthMm = pipes.sumOf { it.lengthMm },
        fittingCounts = fittings,
    )
}

private fun FabricationPartKind.fieldKindName(language: AppLanguage): String = when (this) {
    FabricationPartKind.PIPE -> scheduleText(language, "Труба", "Pipe")
    FabricationPartKind.ELBOW -> scheduleText(language, "Отвод", "Elbow")
    FabricationPartKind.TEE -> scheduleText(language, "Тройник", "Tee")
    FabricationPartKind.FLANGE -> scheduleText(language, "Фланец", "Flange")
    FabricationPartKind.REDUCER -> scheduleText(language, "Переход", "Reducer")
    FabricationPartKind.CAP -> scheduleText(language, "Заглушка", "Cap")
}

private fun scheduleText(language: AppLanguage, russian: String, english: String): String =
    if (language == AppLanguage.RUSSIAN) russian else english

private fun scheduleNumber(value: Double): String = if (abs(value - value.toLong()) < 1e-6) {
    value.toLong().toString()
} else String.format(Locale.US, "%.1f", value)

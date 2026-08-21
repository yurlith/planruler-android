package com.planruler.fabrication3d.catalog

import com.planruler.pipecalculator.ECCENTRIC_REDUCER_CATALOG
import com.planruler.pipecalculator.ELBOW_45_3D_CATALOG
import com.planruler.pipecalculator.EQUAL_TEE_CATALOG
import com.planruler.pipecalculator.WELD_NECK_FLANGE_TYPE11_CATALOG

/**
 * A catalog position offered to the fitter. Only the flange series carries real choice
 * within one diameter — the pipe, elbow and tee catalogues hold a single position per DN,
 * so those are reported for reading rather than picking.
 */
data class FlangeOption3D(
    val catalogId: String,
    val pressureClass: Int,
    val outsideDiameterMm: Double,
    val faceToWeldMm: Double,
    val thicknessMm: Double,
    val boltCircleDiameterMm: Double,
    val boltHoleCount: Int,
    val boltHoleDiameterMm: Double,
    val type: String,
) {
    /** Short label for a chip: the class and the disc it implies. */
    val shortLabel: String get() = "PN $pressureClass · Ø${outsideDiameterMm.toInt()}"
}

data class CatalogPositionInfo3D(
    val catalogId: String,
    val summary: String,
)

/** Weld-neck flanges available for a diameter, ordered by pressure class. */
fun weldNeckFlangeOptions(nominalDiameter: Int): List<FlangeOption3D> =
    WELD_NECK_FLANGE_TYPE11_CATALOG
        .filter { it.dn == nominalDiameter }
        .sortedBy { it.pn }
        .map { entry ->
            FlangeOption3D(
                catalogId = entry.id,
                pressureClass = entry.pn,
                outsideDiameterMm = entry.outsideDiameterMm,
                faceToWeldMm = entry.faceToWeldMm,
                thicknessMm = entry.thicknessMm,
                boltCircleDiameterMm = entry.boltCircleDiameterMm,
                boltHoleCount = entry.boltHoleCount,
                boltHoleDiameterMm = entry.boltHoleDiameterMm,
                type = entry.type,
            )
        }

/** The single catalog elbow for a diameter; its radius is what the 1.5D chip reproduces. */
fun elbowCatalogPosition(nominalDiameter: Int): CatalogPositionInfo3D? =
    ELBOW_45_3D_CATALOG.firstOrNull { it.dn == nominalDiameter }?.let { entry ->
        CatalogPositionInfo3D(
            catalogId = entry.id,
            summary = "${entry.angleDeg.toInt()}° · R ${entry.centerlineRadiusMm} · " +
                "Ø ${entry.outsideDiameterMm} × ${entry.wallThicknessMm} mm",
        )
    }

/** The dimensions needed to build a [com.planruler.fabrication3d.ChainStep3D.Reducer] step. */
data class ReducerOption3D(
    val catalogId: String,
    val smallNominalDiameter: Int,
    val lengthMm: Double,
    val smallOutsideDiameterMm: Double,
    val smallWallThicknessMm: Double,
)

/**
 * The one-step-down reducer the catalog carries for [largeNominalDiameter], if any. The
 * manufacturer publishes reducers between consecutive nominal sizes only; a bigger drop
 * is fabricated as two reducers in series, the same way the shop would cut it.
 */
fun reducerCatalogOption(largeNominalDiameter: Int): ReducerOption3D? =
    ECCENTRIC_REDUCER_CATALOG.firstOrNull { it.largeDn == largeNominalDiameter }?.let { entry ->
        ReducerOption3D(
            catalogId = entry.id,
            smallNominalDiameter = entry.smallDn,
            lengthMm = entry.lengthMm,
            smallOutsideDiameterMm = entry.smallOutsideDiameterMm,
            smallWallThicknessMm = entry.smallWallThicknessMm,
        )
    }

fun teeCatalogPosition(nominalDiameter: Int): CatalogPositionInfo3D? =
    EQUAL_TEE_CATALOG.firstOrNull { it.dn == nominalDiameter }?.let { entry ->
        CatalogPositionInfo3D(
            catalogId = entry.id,
            summary = "${entry.overallRunMm} × ${entry.centerToEndMm} · " +
                "Ø ${entry.outsideDiameterMm} × ${entry.wallThicknessMm} mm",
        )
    }

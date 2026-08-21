package com.planruler.pipecalculator

data class PipeCatalogEntry(
    val id: String,
    val dn: Int,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val material: String,
    val source: DataSource,
) {
    val innerDiameterMm: Double get() = outsideDiameterMm - 2 * wallThicknessMm
    val theoreticalMassKgM: Double
        get() = theoreticalPipeMassKg(outsideDiameterMm, wallThicknessMm, 1.0)

    fun dimensions(roughnessMm: Double = 0.05) = PipeDimensions(
        outsideDiameterMm = outsideDiameterMm,
        wallThicknessMm = wallThicknessMm,
        roughnessMm = roughnessMm,
        source = source,
    )
}

data class ElbowCatalogEntry(
    val id: String,
    val dn: Int,
    val angleDeg: Double,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val centerlineRadiusMm: Double,
    val radiusToleranceMm: Double,
    val material: String,
    val source: DataSource,
) {
    val centerToEndMm: Double
        get() = centerlineRadiusMm * kotlin.math.tan(Math.toRadians(angleDeg / 2.0))
}

data class EqualTeeCatalogEntry(
    val id: String,
    val dn: Int,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val overallRunMm: Double,
    val centerToEndMm: Double,
    val material: String,
    val source: DataSource,
)

data class ReducerCatalogEntry(
    val id: String,
    val largeDn: Int,
    val smallDn: Int,
    val largeOutsideDiameterMm: Double,
    val smallOutsideDiameterMm: Double,
    val largeWallThicknessMm: Double,
    val smallWallThicknessMm: Double,
    val lengthMm: Double,
    val eccentric: Boolean,
    val material: String,
    val source: DataSource,
)

data class FlangeCatalogEntry(
    val id: String,
    val dn: Int,
    val pn: Int,
    val outsideDiameterMm: Double,
    val boltCircleDiameterMm: Double,
    val boltHoleCount: Int,
    val boltHoleDiameterMm: Double,
    val source: DataSource,
)

data class WeldNeckFlangeCatalogEntry(
    val id: String,
    val dn: Int,
    val pn: Int,
    val pipeOutsideDiameterMm: Double,
    val outsideDiameterMm: Double,
    val thicknessMm: Double,
    val boltCircleDiameterMm: Double,
    val boltHoleCount: Int,
    val boltHoleDiameterMm: Double,
    /** Axial mounting height from the sealing face to the butt-weld end. */
    val faceToWeldMm: Double,
    val type: String,
    val source: DataSource,
)

val EN10220_MANUFACTURER_SOURCE = DataSource(
    id = "zelpo-en10220-table5-2025",
    organisation = "Železiarne Podbrezová a.s.",
    document = "Steel tubes and pipes handbook 2025, Table 5",
    edition = "2025",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://www.zelpo.sk/e-brochure/PL/Steel-tubes-and-pipes-handbook-of-Zeleziarne-Podbrezova-Group-2025.pdf",
)

val HECO_ELBOW_SOURCE = DataSource(
    id = "heco-nb45-en10253-4-2026-08-11",
    organisation = "heco gmbh",
    document = "45° seamless bend type 3D, EN 10253-4/A, NB45",
    edition = "2026-08-11",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://www.heco.de/webservice/downloads/product-sheet/1966/en/heco-product-sheet-1966-Stainless-steel-bends-seamless-type-3-r-1-5xD-45-degree.pdf",
)

val HECO_TEE_SOURCE = DataSource(
    id = "heco-nt-en10253-4-2026-08-06",
    organisation = "heco gmbh",
    document = "T-piece seamless EN 10253-4/A, NT",
    edition = "2026-08-06",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://www.heco.de/webservice/downloads/product-sheet/2024/en/heco-product-sheet-2024-Stainless-steel-T-X-Y-pieces-seamless.pdf",
)

val HECO_REDUCER_SOURCE = DataSource(
    id = "heco-ne-en10253-4-2026-08-08",
    organisation = "heco gmbh",
    document = "Seamless eccentric reducer EN 10253-4/A, NE",
    edition = "2026-08-08",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://www.heco.de/webservice/downloads/product-sheet/2058/en/heco-product-sheet-2058-Stainless-steel-reducers-seamless-eccentric.pdf",
)

val SAMSON_FLANGE_SOURCE = DataSource(
    id = "samson-ab02-en-2018-03",
    organisation = "SAMSON AG / Pfeiffer",
    document = "AB 02 EN: Flange connecting dimensions according to DIN EN 1092-1",
    edition = "2018-03",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-13",
    url = "https://pfeiffer.samsongroup.com/document/t00020en.pdf",
)

val HECO_WELD_NECK_FLANGE_SOURCE = DataSource(
    id = "heco-en1092-1-type11-brochure-2024-03",
    organisation = "heco gmbh",
    document = "Stainless steel welding neck flanges, EN 1092-1 Type 11",
    edition = "2024-03",
    kind = SourceKind.MANUFACTURER,
    validationStatus = ValidationStatus.VERIFIED,
    checkedAt = "2026-08-15",
    url = "https://www.heco.de/cms/fileadmin/heco/Seiten/Wissenswertes/Downloads/Flanges_broschure_EN_03.2024_web.pdf",
)

private fun pipe(dn: Int, outside: Double, wall: Double) = PipeCatalogEntry(
    id = "en10220-dn$dn-${outside}x$wall",
    dn = dn,
    outsideDiameterMm = outside,
    wallThicknessMm = wall,
    material = "Carbon steel, selected EN 10220 manufacturer series",
    source = EN10220_MANUFACTURER_SOURCE,
)

/**
 * Selected manufacturer series compatible with the fitting dimensions below.
 * This is deliberately not presented as every permissible EN 10220 wall thickness.
 */
val PIPE_INSTALLATION_SERIES: List<PipeCatalogEntry> = listOf(
    pipe(15, 21.3, 2.0),
    pipe(20, 26.9, 2.3),
    pipe(25, 33.7, 2.6),
    pipe(32, 42.4, 2.6),
    pipe(40, 48.3, 2.6),
    pipe(50, 60.3, 2.9),
    pipe(65, 76.1, 2.9),
    pipe(80, 88.9, 3.2),
    pipe(100, 114.3, 3.6),
    pipe(125, 139.7, 4.0),
    pipe(150, 168.3, 4.5),
    pipe(200, 219.1, 6.3),
    pipe(250, 273.0, 6.3),
    pipe(300, 323.9, 7.1),
)

private fun elbow(dn: Int, outside: Double, wall: Double, radius: Double, tolerance: Double) =
    ElbowCatalogEntry(
        id = "heco-nb45-dn$dn-${outside}x$wall",
        dn = dn,
        angleDeg = 45.0,
        outsideDiameterMm = outside,
        wallThicknessMm = wall,
        centerlineRadiusMm = radius,
        radiusToleranceMm = tolerance,
        material = "Stainless steel 1.4541 / 1.4571",
        source = HECO_ELBOW_SOURCE,
    )

val ELBOW_45_3D_CATALOG: List<ElbowCatalogEntry> = listOf(
    elbow(15, 21.3, 2.0, 28.0, 2.5),
    elbow(20, 26.9, 2.6, 29.0, 2.5),
    elbow(25, 33.7, 2.6, 38.0, 2.5),
    elbow(32, 42.4, 2.6, 47.5, 2.5),
    elbow(40, 48.3, 2.6, 57.0, 3.0),
    elbow(50, 60.3, 2.9, 76.0, 3.0),
    elbow(65, 76.1, 2.9, 95.0, 3.0),
    elbow(80, 88.9, 3.2, 114.5, 3.0),
    elbow(100, 114.3, 3.6, 152.5, 3.0),
    elbow(125, 139.7, 4.0, 190.5, 4.0),
    elbow(150, 168.3, 4.5, 228.0, 4.0),
    elbow(200, 219.1, 6.3, 305.0, 4.0),
    elbow(250, 273.0, 6.3, 381.0, 5.0),
    elbow(300, 323.9, 7.1, 457.0, 5.0),
)

private fun tee(dn: Int, outside: Double, wall: Double, overall: Double, takeout: Double) =
    EqualTeeCatalogEntry(
        id = "heco-nt-dn$dn-${outside}x$wall",
        dn = dn,
        outsideDiameterMm = outside,
        wallThicknessMm = wall,
        overallRunMm = overall,
        centerToEndMm = takeout,
        material = "Stainless steel 1.4541 / 1.4571",
        source = HECO_TEE_SOURCE,
    )

val EQUAL_TEE_CATALOG: List<EqualTeeCatalogEntry> = listOf(
    tee(15, 21.3, 2.0, 50.0, 25.0),
    tee(20, 26.9, 2.3, 58.0, 29.0),
    tee(25, 33.7, 2.6, 76.0, 38.0),
    tee(32, 42.4, 2.6, 96.0, 48.0),
    tee(40, 48.3, 2.6, 114.0, 57.0),
    tee(50, 60.3, 2.9, 128.0, 64.0),
    tee(65, 76.1, 2.9, 152.0, 76.0),
    tee(80, 88.9, 3.2, 172.0, 86.0),
    tee(100, 114.3, 3.6, 210.0, 105.0),
    tee(125, 139.7, 4.0, 248.0, 124.0),
    tee(150, 168.3, 4.5, 286.0, 143.0),
)

private fun reducer(
    largeDn: Int,
    smallDn: Int,
    largeOutside: Double,
    smallOutside: Double,
    largeWall: Double,
    smallWall: Double,
    length: Double,
) = ReducerCatalogEntry(
    id = "heco-ne-dn$largeDn-dn$smallDn",
    largeDn = largeDn,
    smallDn = smallDn,
    largeOutsideDiameterMm = largeOutside,
    smallOutsideDiameterMm = smallOutside,
    largeWallThicknessMm = largeWall,
    smallWallThicknessMm = smallWall,
    lengthMm = length,
    eccentric = true,
    material = "Stainless steel 1.4571",
    source = HECO_REDUCER_SOURCE,
)

val ECCENTRIC_REDUCER_CATALOG: List<ReducerCatalogEntry> = listOf(
    reducer(20, 15, 26.9, 21.3, 2.3, 2.0, 38.0),
    reducer(25, 20, 33.7, 26.9, 2.6, 2.3, 50.0),
    reducer(32, 25, 42.4, 33.7, 2.6, 2.6, 50.0),
    reducer(40, 32, 48.3, 42.4, 2.6, 2.6, 64.0),
    reducer(50, 40, 60.3, 48.3, 2.9, 2.6, 76.0),
    reducer(65, 50, 76.1, 60.3, 2.9, 2.9, 90.0),
    reducer(80, 65, 88.9, 76.1, 3.2, 2.9, 90.0),
    reducer(100, 80, 114.3, 88.9, 3.6, 3.2, 100.0),
    reducer(125, 100, 139.7, 114.3, 4.0, 3.6, 127.0),
    reducer(150, 125, 168.3, 139.7, 4.5, 4.0, 140.0),
    reducer(200, 150, 219.1, 168.3, 6.3, 4.5, 152.0),
    reducer(250, 200, 273.0, 219.1, 6.3, 6.3, 178.0),
    reducer(300, 250, 323.9, 273.0, 7.1, 6.3, 203.0),
)

private val flangePns = listOf(6, 10, 16, 25, 40)

private fun flangeRow(dn: Int, vararg values: Int): List<FlangeCatalogEntry> {
    require(values.size == flangePns.size * 4)
    return flangePns.mapIndexed { index, pn ->
        val offset = index * 4
        FlangeCatalogEntry(
            id = "samson-en1092-dn$dn-pn$pn",
            dn = dn,
            pn = pn,
            outsideDiameterMm = values[offset].toDouble(),
            boltCircleDiameterMm = values[offset + 1].toDouble(),
            boltHoleCount = values[offset + 2],
            boltHoleDiameterMm = values[offset + 3].toDouble(),
            source = SAMSON_FLANGE_SOURCE,
        )
    }
}

/** Connecting dimensions D / k / n / d2 for PN 6, 10, 16, 25 and 40. */
val FLANGE_CONNECTING_DIMENSIONS: List<FlangeCatalogEntry> = listOf(
    flangeRow(15, 80, 55, 4, 11, 95, 65, 4, 14, 95, 65, 4, 14, 95, 65, 4, 14, 95, 65, 4, 14),
    flangeRow(20, 90, 65, 4, 11, 105, 75, 4, 14, 105, 75, 4, 14, 105, 75, 4, 14, 105, 75, 4, 14),
    flangeRow(25, 100, 75, 4, 11, 115, 85, 4, 14, 115, 85, 4, 14, 115, 85, 4, 14, 115, 85, 4, 14),
    flangeRow(32, 120, 90, 4, 14, 140, 100, 4, 18, 140, 100, 4, 18, 140, 100, 4, 18, 140, 100, 4, 18),
    flangeRow(40, 130, 100, 4, 14, 150, 110, 4, 18, 150, 110, 4, 18, 150, 110, 4, 18, 150, 110, 4, 18),
    flangeRow(50, 140, 110, 4, 14, 165, 125, 4, 18, 165, 125, 4, 18, 165, 125, 4, 18, 165, 125, 4, 18),
    flangeRow(65, 160, 130, 4, 14, 185, 145, 8, 18, 185, 145, 8, 18, 185, 145, 8, 18, 185, 145, 8, 18),
    flangeRow(80, 190, 150, 4, 18, 200, 160, 8, 18, 200, 160, 8, 18, 200, 160, 8, 18, 200, 160, 8, 18),
    flangeRow(100, 210, 170, 4, 18, 220, 180, 8, 18, 220, 180, 8, 18, 235, 190, 8, 22, 235, 190, 8, 22),
    flangeRow(125, 240, 200, 8, 18, 250, 210, 8, 18, 250, 210, 8, 18, 270, 220, 8, 26, 270, 220, 8, 26),
    flangeRow(150, 265, 225, 8, 18, 285, 240, 8, 22, 285, 240, 8, 22, 300, 250, 8, 26, 300, 250, 8, 26),
    flangeRow(200, 320, 280, 8, 18, 340, 295, 8, 22, 340, 295, 12, 22, 360, 310, 12, 26, 375, 320, 12, 30),
    flangeRow(250, 375, 335, 12, 18, 395, 350, 12, 22, 405, 355, 12, 26, 425, 370, 12, 30, 450, 385, 12, 33),
    flangeRow(300, 440, 395, 12, 22, 445, 400, 12, 22, 460, 410, 12, 26, 485, 430, 16, 30, 515, 450, 16, 33),
    flangeRow(350, 490, 445, 12, 22, 505, 460, 16, 22, 520, 470, 16, 26, 555, 490, 16, 33, 580, 510, 16, 36),
    flangeRow(400, 540, 495, 16, 22, 565, 515, 16, 26, 580, 525, 16, 30, 620, 550, 16, 36, 660, 585, 16, 39),
).flatten()

private data class WeldNeckAxialDimensions(val thicknessMm: Double, val faceToWeldMm: Double)

private val weldNeckDns = listOf(15, 20, 25, 32, 40, 50, 65, 80, 100, 125, 150, 200, 250, 300)

private fun axialRows(
    pn: Int,
    thicknesses: List<Double>,
    heights: List<Double>,
): Map<Pair<Int, Int>, WeldNeckAxialDimensions> {
    require(thicknesses.size == weldNeckDns.size && heights.size == weldNeckDns.size)
    return weldNeckDns.indices.associate { index ->
        (weldNeckDns[index] to pn) to WeldNeckAxialDimensions(thicknesses[index], heights[index])
    }
}

/**
 * Open manufacturer dimensions b / h for EN 1092-1 Type 11 welding-neck flanges.
 * PN 10 below DN 200 references the PN 16 series; PN 25 below DN 200 references
 * the PN 40 series in the cited manufacturer documentation.
 */
private val WELD_NECK_AXIAL_DIMENSIONS = buildMap {
    putAll(
        axialRows(
            pn = 6,
            thicknesses = listOf(12.0, 14.0, 14.0, 14.0, 14.0, 14.0, 14.0, 16.0, 16.0, 18.0, 18.0, 20.0, 22.0, 22.0),
            heights = listOf(30.0, 32.0, 35.0, 35.0, 38.0, 38.0, 38.0, 42.0, 45.0, 48.0, 48.0, 55.0, 60.0, 62.0),
        ),
    )
    putAll(
        axialRows(
            pn = 10,
            thicknesses = listOf(14.0, 16.0, 16.0, 16.0, 16.0, 18.0, 18.0, 20.0, 20.0, 22.0, 22.0, 24.0, 26.0, 26.0),
            heights = listOf(35.0, 38.0, 38.0, 40.0, 42.0, 45.0, 45.0, 50.0, 52.0, 55.0, 55.0, 62.0, 68.0, 68.0),
        ),
    )
    putAll(
        axialRows(
            pn = 16,
            thicknesses = listOf(14.0, 16.0, 16.0, 16.0, 16.0, 18.0, 18.0, 20.0, 20.0, 22.0, 22.0, 24.0, 26.0, 28.0),
            heights = listOf(35.0, 38.0, 38.0, 40.0, 42.0, 45.0, 45.0, 50.0, 52.0, 55.0, 55.0, 62.0, 70.0, 78.0),
        ),
    )
    putAll(
        axialRows(
            pn = 25,
            thicknesses = listOf(16.0, 18.0, 18.0, 18.0, 18.0, 20.0, 22.0, 24.0, 24.0, 26.0, 28.0, 30.0, 32.0, 34.0),
            heights = listOf(38.0, 40.0, 40.0, 42.0, 45.0, 48.0, 52.0, 58.0, 65.0, 68.0, 75.0, 80.0, 88.0, 92.0),
        ),
    )
    putAll(
        axialRows(
            pn = 40,
            thicknesses = listOf(16.0, 18.0, 18.0, 18.0, 18.0, 20.0, 22.0, 24.0, 24.0, 26.0, 28.0, 34.0, 38.0, 42.0),
            heights = listOf(38.0, 40.0, 40.0, 42.0, 45.0, 48.0, 52.0, 58.0, 65.0, 68.0, 75.0, 88.0, 105.0, 115.0),
        ),
    )
}

val WELD_NECK_FLANGE_TYPE11_CATALOG: List<WeldNeckFlangeCatalogEntry> =
    FLANGE_CONNECTING_DIMENSIONS.mapNotNull { connecting ->
        val axial = WELD_NECK_AXIAL_DIMENSIONS[connecting.dn to connecting.pn] ?: return@mapNotNull null
        val pipe = PIPE_INSTALLATION_SERIES.singleOrNull { it.dn == connecting.dn } ?: return@mapNotNull null
        WeldNeckFlangeCatalogEntry(
            id = "heco-type11-dn${connecting.dn}-pn${connecting.pn}",
            dn = connecting.dn,
            pn = connecting.pn,
            pipeOutsideDiameterMm = pipe.outsideDiameterMm,
            outsideDiameterMm = connecting.outsideDiameterMm,
            thicknessMm = axial.thicknessMm,
            boltCircleDiameterMm = connecting.boltCircleDiameterMm,
            boltHoleCount = connecting.boltHoleCount,
            boltHoleDiameterMm = connecting.boltHoleDiameterMm,
            faceToWeldMm = axial.faceToWeldMm,
            type = "EN 1092-1 Type 11 / B1",
            source = HECO_WELD_NECK_FLANGE_SOURCE,
        )
    }

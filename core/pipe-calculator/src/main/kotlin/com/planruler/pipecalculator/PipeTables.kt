package com.planruler.pipecalculator

import kotlin.math.PI

/** Pipe family used to group the reference tables in the UI and to pick a roughness. */
enum class PipeTableMaterial(val roughnessMm: Double) {
    STEEL(0.045),
    STAINLESS(0.0015),
    COPPER(0.0015),
    PLASTIC(0.007),
    MULTILAYER(0.007),
}

/**
 * One row of a reference pipe table. Diameter, wall and mass are stored as printed;
 * flow area and water content are derived from the printed inside diameter so that a
 * misprint in either column of the source cannot reach a calculation.
 */
data class PipeTableRow(
    val dn: Int?,
    val inch: String?,
    val outsideDiameterMm: Double,
    val wallThicknessMm: Double,
    val innerDiameterMm: Double,
    /** Mass per metre as printed by the source, or null when the source gives none. */
    val massKgM: Double?,
) {
    val flowAreaMm2: Double get() = PI / 4.0 * innerDiameterMm * innerDiameterMm

    /** Water content in litres (dm³) per metre. */
    val volumeLitresPerM: Double get() = flowAreaMm2 / 1_000.0

    val label: String
        get() = buildString {
            dn?.let { append("DN $it · ") }
            inch?.let { append("$it\" · ") }
            append("${trim(outsideDiameterMm)}×${trim(wallThicknessMm)}")
        }

    fun dimensions(roughnessMm: Double, source: DataSource) = PipeDimensions(
        outsideDiameterMm = outsideDiameterMm,
        wallThicknessMm = (outsideDiameterMm - innerDiameterMm) / 2.0,
        roughnessMm = roughnessMm,
        source = source,
    )
}

data class PipeTable(
    val id: String,
    val reference: String,
    val title: String,
    val standard: String,
    val material: PipeTableMaterial,
    val source: DataSource,
    val rows: List<PipeTableRow>,
    val note: String? = null,
)

private fun trim(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

val SUISSETEC_TABLES_SOURCE = DataSource(
    id = "suissetec-formeln-tabellen-gebaeudetechnik-kap10",
    organisation = "suissetec",
    document = "Formeln und Tabellen für die Gebäudetechnik, Kapitel 10 Tabellen Rohre [1.52–1.61]",
    edition = "print",
    kind = SourceKind.SECONDARY,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "2026-09-27",
)

val PRESS_SYSTEM_SERIES_SOURCE = DataSource(
    id = "press-system-nominal-series-en10312-en10305-3",
    organisation = "EN 10312 / EN 10305-3 press-fitting system series",
    document = "Nominal outside diameter × wall series of stainless and carbon steel press pipes",
    edition = "reference",
    kind = SourceKind.SECONDARY,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "2026-09-27",
)

val PLASTIC_SERIES_SOURCE = DataSource(
    id = "en-iso-15874-en1451-nominal-series",
    organisation = "EN ISO 15874 (PP-R) / EN 1451 (PP drainage)",
    document = "Nominal outside diameter × wall series by SDR",
    edition = "reference",
    kind = SourceKind.SECONDARY,
    validationStatus = ValidationStatus.ADVISORY,
    checkedAt = "2026-09-27",
)

private fun row(dn: Int?, inch: String?, od: Double, wall: Double, id: Double, mass: Double?) =
    PipeTableRow(dn, inch, od, wall, id, mass)

/** Row for a series defined by OD × wall only; mass is theoretical at [density] kg/m³. */
private fun series(od: Double, wall: Double, density: Double, dn: Int? = null) = PipeTableRow(
    dn = dn,
    inch = null,
    outsideDiameterMm = od,
    wallThicknessMm = wall,
    innerDiameterMm = ((od - 2 * wall) * 100.0).let { kotlin.math.round(it) / 100.0 },
    massKgM = ((PI / 4.0 * (od * od - (od - 2 * wall) * (od - 2 * wall)) * density / 1_000_000.0) * 1_000.0)
        .let { kotlin.math.round(it) / 1_000.0 },
)

val PIPE_REFERENCE_TABLES: List<PipeTable> = listOf(
    PipeTable(
        id = "din-en-10255-gewinderohr",
        reference = "1.52",
        title = "Gewinderohr / Threaded steel pipe",
        standard = "DIN EN 10255",
        material = PipeTableMaterial.STEEL,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(10, "3/8", 17.2, 2.35, 12.5, 0.86),
            row(15, "1/2", 21.3, 2.65, 16.0, 1.22),
            row(20, "3/4", 26.9, 2.65, 21.6, 1.58),
            row(25, "1", 33.7, 3.25, 27.2, 2.44),
            row(32, "1 1/4", 42.4, 3.25, 35.9, 3.14),
            row(40, "1 1/2", 48.3, 3.25, 41.8, 3.61),
            row(50, "2", 60.3, 3.65, 53.0, 5.10),
            row(65, "2 1/2", 76.1, 3.65, 68.8, 6.51),
            row(80, "3", 88.9, 4.05, 80.8, 8.47),
            row(100, "4", 114.3, 4.5, 105.3, 12.1),
        ),
    ),
    PipeTable(
        id = "din-en-1057-kupfer",
        reference = "1.53",
        title = "Kupferrohr / Copper tube",
        standard = "DIN EN 1057",
        material = PipeTableMaterial.COPPER,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 10.0, 1.0, 8.0, 0.25),
            row(null, null, 12.0, 1.0, 10.0, 0.31),
            row(null, null, 15.0, 1.0, 13.0, 0.39),
            row(null, null, 18.0, 1.0, 16.0, 0.48),
            row(null, null, 22.0, 1.0, 20.0, 0.59),
            row(null, null, 28.0, 1.5, 25.0, 1.12),
            row(null, null, 35.0, 1.5, 32.0, 1.41),
            row(null, null, 42.0, 1.5, 39.0, 1.71),
            row(null, null, 54.0, 2.0, 50.0, 2.93),
        ),
    ),
    PipeTable(
        id = "din-en-10220-siederohr",
        reference = "1.54",
        title = "Siederohr nahtlos / Seamless steel pipe",
        standard = "DIN EN 10220",
        material = PipeTableMaterial.STEEL,
        source = SUISSETEC_TABLES_SOURCE,
        note = "Inside diameters as printed; several are 0.2 mm below OD − 2·s.",
        rows = listOf(
            row(32, null, 38.0, 2.6, 32.8, 2.27),
            row(32, "1 1/4", 42.4, 2.6, 37.0, 2.55),
            row(40, null, 44.5, 2.6, 39.1, 2.69),
            row(40, "1 1/2", 48.3, 2.6, 42.9, 2.93),
            row(50, null, 57.0, 2.9, 51.2, 3.87),
            row(50, "2", 60.3, 2.9, 54.5, 4.11),
            row(65, "2 1/2", 76.1, 2.9, 70.3, 5.24),
            row(80, "3", 88.9, 3.2, 82.5, 6.76),
            row(100, null, 108.0, 3.6, 100.8, 9.27),
            row(100, "4", 114.0, 3.6, 106.8, 9.83),
        ),
    ),
    PipeTable(
        id = "din-16893-pe-x",
        reference = "1.55",
        title = "Kunststoffrohr PE-X / PE-X pipe",
        standard = "DIN 16893",
        material = PipeTableMaterial.PLASTIC,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 16.0, 2.7, 10.6, 0.112),
            row(null, null, 20.0, 3.4, 13.2, 0.176),
            row(null, null, 25.0, 4.2, 16.6, 0.270),
            row(null, null, 32.0, 5.4, 21.2, 0.444),
            row(null, null, 40.0, 6.7, 26.6, 0.686),
            row(null, null, 50.0, 8.4, 33.6, 1.037),
            row(null, null, 63.0, 10.5, 42.0, 1.689),
        ),
    ),
    PipeTable(
        id = "nussbaum-optiflex",
        reference = "1.56",
        title = "Optiflex (Nussbaum) PE-Xc/PE-RT/PB",
        standard = "Nussbaum Optiflex",
        material = PipeTableMaterial.PLASTIC,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 16.0, 3.8, 8.4, 0.138),
            row(null, null, 16.0, 2.2, 11.6, 0.130),
            row(null, null, 20.0, 2.8, 14.4, 0.187),
            row(null, null, 25.0, 2.7, 19.6, 0.295),
            row(null, null, 32.0, 3.2, 25.6, 0.380),
            row(null, null, 40.0, 3.5, 33.0, 0.525),
            row(null, null, 50.0, 4.0, 42.0, 0.738),
            row(null, null, 63.0, 4.5, 52.0, 1.062),
        ),
    ),
    PipeTable(
        id = "jrg-sanipex-mt",
        reference = "1.57",
        title = "JRG Sanipex MT PE-X/Al/PE-X",
        standard = "JRG Sanipex MT",
        material = PipeTableMaterial.MULTILAYER,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 16.0, 2.25, 11.5, 0.134),
            row(null, null, 20.0, 2.5, 15.0, 0.177),
            row(null, null, 26.0, 3.0, 20.0, 0.314),
            row(null, null, 32.0, 3.2, 26.0, 0.393),
            row(null, null, 40.0, 3.5, 33.0, 0.605),
            row(null, null, 50.0, 4.0, 42.0, 0.886),
            row(null, null, 63.0, 4.5, 54.0, 1.265),
        ),
    ),
    PipeTable(
        id = "geberit-pushfit",
        reference = "1.58",
        title = "Geberit PushFit PE-Xb/Al/PE-RT",
        standard = "Geberit PushFit",
        material = PipeTableMaterial.MULTILAYER,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 16.0, 2.0, 12.0, 0.099),
            row(null, null, 20.0, 2.0, 16.0, 0.137),
            row(null, null, 25.0, 2.5, 20.0, 0.212),
        ),
    ),
    PipeTable(
        id = "en-12201-pe100-pn16",
        reference = "1.59",
        title = "Druckrohr PE 100 PN 16 / PE 100 pressure pipe",
        standard = "EN 12201",
        material = PipeTableMaterial.PLASTIC,
        source = SUISSETEC_TABLES_SOURCE,
        note = "Lieferlänge / supply length 10 m",
        rows = listOf(
            row(null, null, 20.0, 2.0, 16.0, 0.116),
            row(null, null, 25.0, 2.3, 20.4, 0.169),
            row(null, null, 32.0, 2.9, 26.2, 0.276),
            row(null, null, 40.0, 3.7, 32.6, 0.425),
            row(null, null, 50.0, 4.6, 40.8, 0.659),
            row(null, null, 63.0, 5.8, 51.4, 1.06),
            row(null, null, 75.0, 6.8, 61.4, 1.48),
            row(null, null, 90.0, 8.2, 73.6, 2.14),
            row(null, null, 110.0, 10.0, 90.0, 3.18),
            row(null, null, 125.0, 11.4, 102.2, 4.12),
            row(null, null, 140.0, 12.7, 114.6, 5.13),
            row(null, null, 160.0, 14.6, 130.8, 6.73),
            row(null, null, 180.0, 16.4, 147.2, 8.51),
            row(null, null, 200.0, 18.2, 163.6, 10.49),
            row(null, null, 225.0, 20.5, 184.0, 13.27),
            row(null, null, 250.0, 22.7, 204.6, 16.32),
        ),
    ),
    PipeTable(
        id = "metallverbund-pn10-pe-c-al-pe",
        reference = "1.60",
        title = "Metallverbundrohr PN 10 PE-C/Al/PE",
        standard = "Multilayer PN 10",
        material = PipeTableMaterial.MULTILAYER,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(null, null, 16.0, 2.25, 11.5, 0.125),
            row(null, null, 20.0, 2.5, 15.0, 0.185),
            row(null, null, 26.0, 3.0, 20.0, 0.300),
            row(null, null, 32.0, 3.0, 26.0, 0.415),
            row(null, null, 40.0, 3.5, 33.0, 0.595),
            row(null, null, 50.0, 4.0, 42.0, 0.840),
            row(null, null, 63.0, 4.5, 54.0, 1.0),
        ),
    ),
    PipeTable(
        id = "geberit-pe-hd-abwasser",
        reference = "1.61",
        title = "PE-HD Abwasserrohr Geberit / PE-HD drainage",
        standard = "Geberit PE-HD",
        material = PipeTableMaterial.PLASTIC,
        source = SUISSETEC_TABLES_SOURCE,
        rows = listOf(
            row(50, null, 50.0, 3.0, 44.0, 0.46),
            row(56, null, 56.0, 3.0, 50.0, 0.48),
            row(60, null, 63.0, 3.0, 57.0, 0.61),
            row(70, null, 75.0, 3.0, 69.0, 0.73),
            row(90, null, 90.0, 3.5, 83.0, 0.96),
            row(100, null, 110.0, 4.3, 101.4, 1.49),
            row(125, null, 125.0, 4.9, 115.2, 1.90),
            row(150, null, 160.0, 6.2, 147.6, 3.00),
        ),
    ),
    PipeTable(
        id = "press-stainless-en10312",
        reference = "+",
        title = "Pressrohr Edelstahl / Stainless press pipe 1.4401",
        standard = "EN 10312",
        material = PipeTableMaterial.STAINLESS,
        source = PRESS_SYSTEM_SERIES_SOURCE,
        note = "Mass is theoretical (7 950 kg/m³).",
        rows = listOf(
            series(12.0, 1.0, 7_950.0), series(15.0, 1.0, 7_950.0), series(18.0, 1.0, 7_950.0),
            series(22.0, 1.2, 7_950.0), series(28.0, 1.2, 7_950.0), series(35.0, 1.5, 7_950.0),
            series(42.0, 1.5, 7_950.0), series(54.0, 1.5, 7_950.0), series(76.1, 2.0, 7_950.0),
            series(88.9, 2.0, 7_950.0), series(108.0, 2.0, 7_950.0),
        ),
    ),
    PipeTable(
        id = "press-carbon-en10305-3",
        reference = "+",
        title = "Pressrohr C-Stahl / Carbon steel press pipe",
        standard = "EN 10305-3",
        material = PipeTableMaterial.STEEL,
        source = PRESS_SYSTEM_SERIES_SOURCE,
        note = "Mass is theoretical (7 850 kg/m³).",
        rows = listOf(
            series(12.0, 1.2, 7_850.0), series(15.0, 1.2, 7_850.0), series(18.0, 1.2, 7_850.0),
            series(22.0, 1.5, 7_850.0), series(28.0, 1.5, 7_850.0), series(35.0, 1.5, 7_850.0),
            series(42.0, 1.5, 7_850.0), series(54.0, 1.5, 7_850.0), series(76.1, 2.0, 7_850.0),
            series(88.9, 2.0, 7_850.0), series(108.0, 2.0, 7_850.0),
        ),
    ),
    PipeTable(
        id = "ppr-pn20-sdr6",
        reference = "+",
        title = "PP-R PN 20 (SDR 6)",
        standard = "EN ISO 15874",
        material = PipeTableMaterial.PLASTIC,
        source = PLASTIC_SERIES_SOURCE,
        note = "Mass is theoretical (900 kg/m³).",
        rows = listOf(
            series(20.0, 3.4, 900.0), series(25.0, 4.2, 900.0), series(32.0, 5.4, 900.0),
            series(40.0, 6.7, 900.0), series(50.0, 8.3, 900.0), series(63.0, 10.5, 900.0),
            series(75.0, 12.5, 900.0), series(90.0, 15.0, 900.0), series(110.0, 18.3, 900.0),
        ),
    ),
    PipeTable(
        id = "ppr-pn10-sdr11",
        reference = "+",
        title = "PP-R PN 10 (SDR 11)",
        standard = "EN ISO 15874",
        material = PipeTableMaterial.PLASTIC,
        source = PLASTIC_SERIES_SOURCE,
        note = "Mass is theoretical (900 kg/m³).",
        rows = listOf(
            series(20.0, 1.9, 900.0), series(25.0, 2.3, 900.0), series(32.0, 2.9, 900.0),
            series(40.0, 3.7, 900.0), series(50.0, 4.6, 900.0), series(63.0, 5.8, 900.0),
            series(75.0, 6.8, 900.0), series(90.0, 8.2, 900.0), series(110.0, 10.0, 900.0),
        ),
    ),
    PipeTable(
        id = "pp-drainage-en1451",
        reference = "+",
        title = "PP HT Abwasser / PP drainage",
        standard = "EN 1451",
        material = PipeTableMaterial.PLASTIC,
        source = PLASTIC_SERIES_SOURCE,
        note = "Mass is theoretical (900 kg/m³).",
        rows = listOf(
            series(32.0, 1.8, 900.0, 30), series(40.0, 1.8, 900.0, 40), series(50.0, 1.8, 900.0, 50),
            series(75.0, 1.9, 900.0, 70), series(110.0, 2.7, 900.0, 100), series(125.0, 3.1, 900.0, 125),
            series(160.0, 3.9, 900.0, 150),
        ),
    ),
)

/** Looks a reference table up by its stable id. */
fun pipeTableById(id: String): PipeTable? = PIPE_REFERENCE_TABLES.firstOrNull { it.id == id }

package com.planruler.pipecalculator

/** Case/decimal-insensitive search shared by the catalog and the plan's pipe picker. */
fun catalogMatches(query: String, vararg fields: String): Boolean {
    fun normalize(value: String) = value.lowercase().replace(',', '.').replace(Regex("dn\\s+"), "dn")
    val searchable = normalize(fields.joinToString(" "))
    return normalize(query).trim().split(Regex("\\s+")).all { it in searchable }
}

val PipeCatalogEntry.sizeLabel: String get() = "Ø${outsideDiameterMm} × ${wallThicknessMm} mm"

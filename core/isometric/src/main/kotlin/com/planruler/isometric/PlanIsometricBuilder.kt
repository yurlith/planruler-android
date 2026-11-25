package com.planruler.isometric

import com.planruler.model.Calibration
import com.planruler.model.IsoNode
import com.planruler.model.IsoNodeId
import com.planruler.model.IsoPipe
import com.planruler.model.IsoPipeId
import com.planruler.model.IsoPipeSpec
import com.planruler.model.IsoPipeSystem
import com.planruler.model.IsometricSheet
import com.planruler.model.Measurement
import com.planruler.model.MeasurementId
import com.planruler.model.MeasurementType
import com.planruler.model.TradeCategory
import kotlin.math.abs

data class PlanIsometricOptions(
    /** Only runs of this plan page; pages are usually different floors or zones. */
    val pageIndex: Int? = null,
    /** Run ends closer than this in the model are one joint (plan points are rarely exact). */
    val mergeToleranceMm: Double = 30.0,
    val categories: Set<TradeCategory> = setOf(TradeCategory.HEATING, TradeCategory.PLUMBING),
    /** Level of runs that carry no route data, millimetres. */
    val defaultElevationMm: Double = 0.0,
)

data class PlanIsometricResult(
    val sheet: IsometricSheet,
    val sourceRunCount: Int,
    val teeJunctions: Int,
)

/**
 * Builds the axonometric scheme of the pipe runs measured on a plan: plan X/Y become model
 * X/Y (north up), stored risers and drops become vertical runs, touching ends become joints
 * and an end that lands on another run becomes a tee.
 */
object PlanIsometricBuilder {
    fun isPipeRun(measurement: Measurement, categories: Set<TradeCategory>): Boolean =
        (measurement.type == MeasurementType.POLYLINE || measurement.type == MeasurementType.DISTANCE) &&
            measurement.points.size >= 2 &&
            (measurement.route != null || measurement.takeoff.category in categories)

    fun build(
        base: IsometricSheet,
        measurements: List<Measurement>,
        calibration: Calibration?,
        options: PlanIsometricOptions = PlanIsometricOptions(),
        idGenerator: () -> String,
    ): IsoResult<PlanIsometricResult> {
        if (calibration == null) return IsoResult.Error(IsoError.Invalid("The plan is not calibrated"))
        val mmPerUnit = calibration.metersPerDocumentUnit * 1_000.0
        val runs = measurements.filter { measurement ->
            isPipeRun(measurement, options.categories) &&
                (options.pageIndex == null || measurement.pageIndex == options.pageIndex)
        }
        if (runs.isEmpty()) return IsoResult.Error(IsoError.Invalid("No pipe runs on the plan"))

        val positions = mutableListOf<Vec3>()
        val links = mutableListOf<Link>()
        fun node(point: Vec3): Int {
            positions += point
            return positions.lastIndex
        }
        runs.forEach { measurement ->
            val spec = specOf(measurement)
            var elevation = measurement.route?.startElevationMm ?: options.defaultElevationMm
            var previous: Int? = null
            measurement.points.forEachIndexed { index, point ->
                // Plan Y grows downwards; model Y is north.
                val arrival = node(Vec3(point.x * mmPerUnit, -point.y * mmPerUnit, elevation))
                previous?.let { links += Link(it, arrival, spec, measurement.id) }
                val rise = measurement.route?.riseAt(index) ?: 0.0
                previous = if (abs(rise) > IsoNetwork.MIN_LENGTH_MM) {
                    elevation += rise
                    node(Vec3(point.x * mmPerUnit, -point.y * mmPerUnit, elevation)).also { departure ->
                        links += Link(arrival, departure, spec, measurement.id)
                    }
                } else {
                    arrival
                }
            }
        }

        // Union of joints that coincide within the tolerance; the first point keeps its exact place.
        val parent = IntArray(positions.size) { it }
        fun root(i: Int): Int {
            var r = i
            while (parent[r] != r) r = parent[r]
            var c = i
            while (parent[c] != r) {
                val next = parent[c]
                parent[c] = r
                c = next
            }
            return r
        }
        for (i in positions.indices) for (j in 0 until i) {
            if (positions[i].distanceTo(positions[j]) <= options.mergeToleranceMm) {
                val a = root(i)
                val b = root(j)
                if (a != b) parent[maxOf(a, b)] = minOf(a, b)
            }
        }
        val merged = positions.indices.map(::root)
        val place = positions.toMutableList()
        var edges = links
            .map { it.copy(a = merged[it.a], b = merged[it.b]) }
            .filter { it.a != it.b && place[it.a].distanceTo(place[it.b]) > IsoNetwork.MIN_LENGTH_MM }
            .distinctBy { setOf(it.a, it.b) }

        // An end that lands on another run is a tee: split that run and move the end onto it.
        var tees = 0
        var changed = true
        var guard = 0
        while (changed && guard++ < 10_000) {
            changed = false
            val degree = edges.flatMap { listOf(it.a, it.b) }.groupingBy { it }.eachCount()
            for ((end, count) in degree) {
                if (count != 1) continue
                val host = edges
                    .filter { it.a != end && it.b != end }
                    .mapNotNull { edge ->
                        val (distance, t) = distanceToSegment(place[end], place[edge.a], place[edge.b])
                        val length = place[edge.a].distanceTo(place[edge.b])
                        if (distance <= options.mergeToleranceMm && t * length > options.mergeToleranceMm &&
                            (1 - t) * length > options.mergeToleranceMm
                        ) {
                            Triple(edge, distance, t)
                        } else {
                            null
                        }
                    }
                    .minByOrNull { it.second }
                    ?: continue
                val (edge, _, t) = host
                place[end] = place[edge.a] + (place[edge.b] - place[edge.a]) * t
                edges = edges.filterNot { it === edge } +
                    edge.copy(b = end) +
                    edge.copy(a = end)
                tees++
                changed = true
                break
            }
        }

        val used = edges.flatMap { listOf(it.a, it.b) }.distinct().sorted()
        val ids = used.associateWith { IsoNodeId(idGenerator()) }
        val nodes = used.map { IsoNode(ids.getValue(it), place[it].toIsoPoint()) }
        val pipes = edges.map { IsoPipe(IsoPipeId(idGenerator()), ids.getValue(it.a), ids.getValue(it.b), it.spec, sourceMeasurementId = it.source) }
        return IsoResult.Ok(
            PlanIsometricResult(
                sheet = base.copy(nodes = nodes, pipes = pipes, components = emptyList()),
                sourceRunCount = runs.size,
                teeJunctions = tees,
            ),
        )
    }

    fun specOf(measurement: Measurement) = IsoPipeSpec(
        system = systemOf(measurement),
        nominalDiameter = nominalDiameterOf(measurement.takeoff.diameter) ?: nominalDiameterOf(measurement.takeoff.size),
        material = measurement.takeoff.material?.trim()?.takeIf(String::isNotEmpty),
        colorArgb = measurement.style.colorArgb,
    )

    /** Service of a measured run from its starter template or, failing that, from its wording. */
    fun systemOf(measurement: Measurement): IsoPipeSystem {
        val template = measurement.templateId.orEmpty()
        when {
            template.startsWith("heating-supply") -> return IsoPipeSystem.HEATING_SUPPLY
            template.startsWith("heating-return") -> return IsoPipeSystem.HEATING_RETURN
            template.startsWith("plumbing-cold") -> return IsoPipeSystem.COLD_WATER
            template.startsWith("plumbing-hot") -> return IsoPipeSystem.HOT_WATER
            template.startsWith("plumbing-sewer") -> return IsoPipeSystem.SEWER
        }
        val words = listOfNotNull(measurement.takeoff.material, measurement.label, measurement.takeoff.subcategory)
            .joinToString(" ")
            .lowercase()
        fun has(vararg keys: String) = keys.any { it in words }
        return when {
            has("циркул", "circulation", "cyrkul", "zirkulation", "ricircolo", "pwh-c") -> IsoPipeSystem.HOT_WATER_CIRCULATION
            has("обрат", "return", "powr", "rücklauf", "rucklauf", "ritorno", "retour") -> IsoPipeSystem.HEATING_RETURN
            has("подач", "supply", "zasil", "vorlauf", "mandata", "départ", "depart", "flow") -> IsoPipeSystem.HEATING_SUPPLY
            has("гвс", "горяч", "hot", "ciepł", "cwu", "c.w.u", "warmwasser", "pwh", "calda", "chaude") -> IsoPipeSystem.HOT_WATER
            has("хвс", "холод", "cold", "zimn", "kaltwasser", "pwc", "fredda", "froide") -> IsoPipeSystem.COLD_WATER
            has("канал", "sewer", "waste", "kanaliz", "abwasser", "scarico", "évacuation", "evacuation") -> IsoPipeSystem.SEWER
            has("газ", "gas", "gaz") -> IsoPipeSystem.GAS
            else -> IsoPipeSystem.OTHER
        }
    }

    /** "DN20", "Ду 20", "Ø160", "d110", "20x2.8" or a bare number; inch fractions are not guessed. */
    fun nominalDiameterOf(text: String?): Int? {
        val value = text?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if ('/' in value || '"' in value || '″' in value) return null
        val tagged = TAGGED_SIZE.find(value)?.groupValues?.get(1)
        val leading = OUTSIDE_BY_WALL.find(value)?.groupValues?.get(1)
        val bare = BARE_SIZE.find(value)?.groupValues?.get(1)
        return (tagged ?: leading ?: bare)?.toIntOrNull()?.takeIf { it > 0 }
    }

    private val TAGGED_SIZE = Regex("(?iu)(?:dn|ду|ø|⌀|d)\\s*(\\d{1,4})")
    private val OUTSIDE_BY_WALL = Regex("^(\\d{1,4})\\s*[xх×*]")
    private val BARE_SIZE = Regex("^(\\d{1,4})$")

    private data class Link(val a: Int, val b: Int, val spec: IsoPipeSpec, val source: MeasurementId)
}

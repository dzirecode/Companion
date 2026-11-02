package com.dzire.dzirecompanion.insights

import com.dzire.dzirecompanion.data.database.LifeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Phase 6: learns a neutral, user-specific daily state model.
 *
 * No category is considered good/bad and no wellness meaning is assigned.
 * A state is simply a recurring statistical signature across the categories
 * and measurements that the user actually records.
 */
data class StateModelConfig(
    val minimumHistoryDays: Int = 21,
    val minimumObservedDays: Int = 14,
    val maximumStates: Int = 5,
    val minimumStateDays: Int = 3,
    val transitionWindowDays: Int = 1,
    val maximumTransitions: Int = 12
)

data class StateDimension(
    val category: String,
    val normalizedValue: Double,
    val observationCount: Int
)

data class DailyState(
    val date: LocalDate,
    val stateId: Int,
    val dimensions: List<StateDimension>,
    val observationCount: Int,
    val distanceToCentroid: Double
)

data class StateCluster(
    val stateId: Int,
    val days: Int,
    val frequency: Double,
    val centroid: List<StateDimension>,
    val representativeDays: List<LocalDate>,
    val description: String
)

data class StateTransition(
    val fromStateId: Int,
    val toStateId: Int,
    val occurrences: Int,
    val probability: Double,
    val averageGapDays: Double
)

data class PersonalStateReport(
    val observedDays: Int,
    val states: List<StateCluster>,
    val dailyStates: List<DailyState>,
    val transitions: List<StateTransition>
)

object PersonalStateAnalyzer {
    fun analyze(events: List<LifeEvent>, config: StateModelConfig = StateModelConfig()): PersonalStateReport {
        if (events.isEmpty()) return PersonalStateReport(0, emptyList(), emptyList(), emptyList())

        val observations = events.mapNotNull { event ->
            val value = event.numericValue ?: event.rating ?: event.parameter1?.toDoubleOrNull() ?: return@mapNotNull null
            Observation(dayOf(event.timestamp), event.category.trim().ifBlank { "Uncategorized" }, value)
        }
        if (observations.isEmpty()) return PersonalStateReport(0, emptyList(), emptyList(), emptyList())

        val byDay = observations.groupBy { it.day }
        if (byDay.size < config.minimumHistoryDays) return PersonalStateReport(byDay.size, emptyList(), emptyList(), emptyList())

        val stats = observations.groupBy { it.category }.mapValues { (_, values) ->
            val mean = values.map { it.value }.average()
            val sd = standardDeviation(values.map { it.value }, mean)
            mean to if (sd == 0.0) 1.0 else sd
        }

        val vectors = byDay.entries.sortedBy { it.key }.map { (day, dayObs) ->
            val dimensions = dayObs.groupBy { it.category }.map { (category, values) ->
                val (mean, sd) = stats.getValue(category)
                val daily = values.map { it.value }.average()
                StateDimension(category, ((daily - mean) / sd).coerceIn(-4.0, 4.0), values.size)
            }.sortedBy { it.category }
            DayVector(day, dimensions, dayObs.size)
        }

        val effectiveStates = chooseStateCount(vectors.size, config.maximumStates, config.minimumStateDays)
        if (effectiveStates < 1 || vectors.size < config.minimumObservedDays) {
            return PersonalStateReport(byDay.size, emptyList(), emptyList(), emptyList())
        }

        val centroids = initializeCentroids(vectors, effectiveStates)
        val assignments = IntArray(vectors.size)
        repeat(12) {
            var changed = false
            for (i in vectors.indices) {
                val nearest = nearestCentroid(vectors[i], centroids)
                if (assignments[i] != nearest || it == 0) changed = true
                assignments[i] = nearest
            }
            for (cluster in 0 until effectiveStates) {
                val members = vectors.indices.filter { assignments[it] == cluster }
                if (members.isNotEmpty()) {
                    centroids[cluster] = averageVector(members.map { vectors[it] })
                }
            }
            if (!changed) return@repeat
        }

        val clusters = mutableListOf<StateCluster>()
        val dailyStates = vectors.mapIndexed { index, vector ->
            val cluster = assignments[index]
            val centroid = centroids[cluster]
            val distance = distance(vector, centroid)
            DailyState(vector.day, cluster + 1, vector.dimensions, vector.observationCount, distance)
        }

        for (cluster in 0 until effectiveStates) {
            val members = dailyStates.filter { it.stateId == cluster + 1 }
            if (members.size < config.minimumStateDays) continue
            val centroidDimensions = centroids[cluster].dimensions
                .sortedByDescending { abs(it.normalizedValue) }
                .take(4)
            clusters += StateCluster(
                stateId = cluster + 1,
                days = members.size,
                frequency = members.size.toDouble() / dailyStates.size,
                centroid = centroidDimensions,
                representativeDays = members.sortedBy { it.distanceToCentroid }.take(3).map { it.date },
                description = describeState(centroidDimensions)
            )
        }

        val validStateIds = clusters.map { it.stateId }.toSet()
        val filteredDaily = dailyStates.filter { it.stateId in validStateIds }
        val transitions = findTransitions(filteredDaily, config.transitionWindowDays)
            .filter { it.fromStateId in validStateIds && it.toStateId in validStateIds }
            .sortedWith(compareByDescending<StateTransition> { it.occurrences }.thenByDescending { it.probability })
            .take(config.maximumTransitions)

        return PersonalStateReport(
            observedDays = dailyStates.size,
            states = clusters.sortedByDescending { it.days },
            dailyStates = filteredDaily.sortedByDescending { it.date },
            transitions = transitions
        )
    }

    private data class Observation(val day: LocalDate, val category: String, val value: Double)
    private data class DayVector(val day: LocalDate, val dimensions: List<StateDimension>, val observationCount: Int)

    private fun chooseStateCount(days: Int, maxStates: Int, minStateDays: Int): Int =
        (days / minStateDays).coerceIn(1, maxStates.coerceAtLeast(1))

    private fun initializeCentroids(vectors: List<DayVector>, count: Int): MutableList<DayVector> {
        if (count == 1) return mutableListOf(averageVector(vectors))
        val sorted = vectors.sortedBy { it.day }
        return (0 until count).map { i ->
            val index = ((i.toDouble() / (count - 1)) * (sorted.lastIndex)).toInt().coerceIn(0, sorted.lastIndex)
            sorted[index]
        }.toMutableList()
    }

    private fun nearestCentroid(vector: DayVector, centroids: List<DayVector>): Int =
        centroids.indices.minByOrNull { distance(vector, centroids[it]) } ?: 0

    private fun averageVector(vectors: List<DayVector>): DayVector {
        val categories = vectors.flatMap { it.dimensions.map(StateDimension::category) }.distinct()
        val dimensions = categories.map { category ->
            val values = vectors.flatMap { vector -> vector.dimensions.filter { it.category == category }.map { it.normalizedValue } }
            StateDimension(category, values.averageOrZero(), values.size)
        }.sortedBy { it.category }
        return DayVector(vectors.firstOrNull()?.day ?: LocalDate.MIN, dimensions, vectors.sumOf { it.observationCount })
    }

    private fun distance(a: DayVector, b: DayVector): Double {
        val bMap = b.dimensions.associateBy { it.category }
        val categories = (a.dimensions.map { it.category } + b.dimensions.map { it.category }).distinct()
        if (categories.isEmpty()) return 0.0
        return sqrt(categories.sumOf { category ->
            val x = a.dimensions.firstOrNull { it.category == category }?.normalizedValue ?: 0.0
            val y = bMap[category]?.normalizedValue ?: 0.0
            (x - y) * (x - y)
        } / categories.size)
    }

    private fun findTransitions(states: List<DailyState>, maxGapDays: Int): List<StateTransition> {
        val sorted = states.sortedBy { it.date }
        val counts = mutableMapOf<Pair<Int, Int>, MutableList<Int>>()
        for (i in 0 until sorted.lastIndex) {
            val a = sorted[i]
            val b = sorted[i + 1]
            val gap = java.time.temporal.ChronoUnit.DAYS.between(a.date, b.date).toInt()
            if (gap in 1..maxGapDays && a.stateId != b.stateId) {
                counts.getOrPut(a.stateId to b.stateId) { mutableListOf() }.add(gap)
            }
        }
        val outgoing = counts.entries.groupBy { it.key.first }
        return counts.map { (pair, gaps) ->
            val total = outgoing[pair.first]?.sumOf { it.value.size } ?: gaps.size
            StateTransition(pair.first, pair.second, gaps.size, gaps.size.toDouble() / total.coerceAtLeast(1), gaps.average())
        }
    }

    private fun describeState(dimensions: List<StateDimension>): String {
        if (dimensions.isEmpty()) return "A recurring state in your recorded history."
        val meaningful = dimensions.filter { abs(it.normalizedValue) >= 0.45 }.take(3)
        if (meaningful.isEmpty()) return "A state where recorded dimensions are close to their personal averages."
        return meaningful.joinToString(" • ") { d ->
            val direction = if (d.normalizedValue > 0) "higher" else "lower"
            "$direction ${pretty(d.category)}"
        }.replaceFirstChar { it.uppercase() }
    }

    private fun pretty(value: String) = value.replace('_', ' ').lowercase()
    private fun dayOf(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun List<Double>.averageOrZero() = if (isEmpty()) 0.0 else average()
    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }
}

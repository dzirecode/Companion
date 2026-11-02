package com.dzire.dzirecompanion.insights

import com.dzire.dzirecompanion.data.database.LifeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Learns each user's own baseline from their history. No population health
 * thresholds or domain-specific assumptions are used.
 */
data class BaselineConfig(
    val minimumHistoryDays: Int = 21,
    val baselineDays: Int = 21,
    val recentDays: Int = 7,
    val minimumObservations: Int = 10,
    val anomalyZScore: Double = 2.0,
    val minimumAnomalyScore: Double = 0.55,
    val maximumAnomalies: Int = 8,
    val maximumBaselineFeatures: Int = 20
)

data class PersonalBaseline(
    val feature: String,
    val unit: String?,
    val baselineMean: Double,
    val baselineMedian: Double,
    val baselineStdDev: Double,
    val baselineDays: Int,
    val recentMean: Double?,
    val recentDays: Int,
    val deviationPercent: Double?,
    val status: Status
) {
    enum class Status { WITHIN_BASELINE, ABOVE_BASELINE, BELOW_BASELINE }
}

data class AnomalyInsight(
    val feature: String,
    val value: Double,
    val expectedValue: Double,
    val unit: String?,
    val date: LocalDate,
    val deviationScore: Double,
    val description: String
)

data class BaselineReport(
    val baselines: List<PersonalBaseline>,
    val anomalies: List<AnomalyInsight>
)

object BaselineAnalyzer {
    fun analyze(events: List<LifeEvent>, config: BaselineConfig = BaselineConfig()): BaselineReport {
        if (events.isEmpty()) return BaselineReport(emptyList(), emptyList())

        val byDay = events.groupBy { dayOf(it.timestamp) }
        val days = byDay.keys.sorted()
        if (days.size < config.minimumHistoryDays) return BaselineReport(emptyList(), emptyList())

        val latestDay = days.last()
        val recentStart = latestDay.minusDays(config.recentDays.toLong() - 1)
        val baselineEnd = recentStart.minusDays(1)
        val baselineStart = baselineEnd.minusDays(config.baselineDays.toLong() - 1)

        val numeric = events.mapNotNull { event ->
            val value = event.numericValue ?: event.parameter1?.toDoubleOrNull() ?: return@mapNotNull null
            NumericObservation(featureKey(event), dayOf(event.timestamp), value, event.unit)
        }

        val features = numeric.groupBy { it.feature }
        val baselines = mutableListOf<PersonalBaseline>()
        val anomalies = mutableListOf<AnomalyInsight>()

        for ((feature, observations) in features) {
            val baselineObs = observations.filter { it.day in baselineStart..baselineEnd }
            val recentObs = observations.filter { it.day >= recentStart && it.day <= latestDay }
            if (baselineObs.size < config.minimumObservations) continue

            val baselineValues = dailyAverage(baselineObs).values.toList()
            if (baselineValues.size < config.minimumObservations.coerceAtMost(config.baselineDays)) continue

            val robust = removeOutliers(baselineValues)
            val mean = robust.average()
            val median = median(robust)
            val std = standardDeviation(robust, mean)
            val recentMean = recentObs.takeIf { it.isNotEmpty() }?.let { dailyAverage(it).values.average() }
            val deviationPercent = recentMean?.let { if (mean == 0.0) null else (it - mean) / abs(mean) }
            val status = when {
                recentMean == null -> PersonalBaseline.Status.WITHIN_BASELINE
                std == 0.0 && abs(recentMean - mean) < 1e-9 -> PersonalBaseline.Status.WITHIN_BASELINE
                recentMean > mean + std -> PersonalBaseline.Status.ABOVE_BASELINE
                recentMean < mean - std -> PersonalBaseline.Status.BELOW_BASELINE
                else -> PersonalBaseline.Status.WITHIN_BASELINE
            }

            baselines += PersonalBaseline(
                feature = feature,
                unit = observations.firstOrNull()?.unit,
                baselineMean = mean,
                baselineMedian = median,
                baselineStdDev = std,
                baselineDays = baselineObs.map { it.day }.distinct().size,
                recentMean = recentMean,
                recentDays = recentObs.map { it.day }.distinct().size,
                deviationPercent = deviationPercent,
                status = status
            )

            if (std > 0.0) {
                recentObs.groupBy { it.day }.forEach { (day, dayObs) ->
                    val value = dayObs.map { it.value }.average()
                    val score = abs(value - mean) / std
                    if (score >= config.anomalyZScore) {
                        val anomalyScore = (score / (score + 2.0)).coerceIn(0.0, 1.0)
                        if (anomalyScore >= config.minimumAnomalyScore) {
                            val direction = if (value > mean) "above" else "below"
                            anomalies += AnomalyInsight(
                                feature = feature,
                                value = value,
                                expectedValue = mean,
                                unit = observations.firstOrNull()?.unit,
                                date = day,
                                deviationScore = anomalyScore,
                                description = "${displayName(feature)} was $direction your personal baseline on $day. Recorded value ${format(value)}${observations.firstOrNull()?.unit?.let { " $it" } ?: ""}; baseline average ${format(mean)}${observations.firstOrNull()?.unit?.let { " $it" } ?: ""}. This is unusual for your own history, not a judgment against a general standard."
                            )
                        }
                    }
                }
            }
        }

        return BaselineReport(
            baselines = baselines.sortedByDescending { abs(it.deviationPercent ?: 0.0) }.take(config.maximumBaselineFeatures),
            anomalies = anomalies.sortedWith(compareByDescending<AnomalyInsight> { it.deviationScore }.thenByDescending { it.date }).take(config.maximumAnomalies)
        )
    }

    private data class NumericObservation(val feature: String, val day: LocalDate, val value: Double, val unit: String?)

    private fun dailyAverage(observations: List<NumericObservation>): Map<LocalDate, Double> =
        observations.groupBy { it.day }.mapValues { (_, values) -> values.map { it.value }.average() }

    private fun removeOutliers(values: List<Double>): List<Double> {
        if (values.size < 5) return values
        val sorted = values.sorted()
        val q1 = percentile(sorted, 0.25)
        val q3 = percentile(sorted, 0.75)
        val iqr = q3 - q1
        if (iqr == 0.0) return values
        val lower = q1 - 1.5 * iqr
        val upper = q3 + 1.5 * iqr
        return values.filter { it in lower..upper }.ifEmpty { values }
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val position = (sorted.size - 1) * p
        val lower = position.toInt()
        val upper = (lower + 1).coerceAtMost(sorted.lastIndex)
        val fraction = position - lower
        return sorted[lower] + (sorted[upper] - sorted[lower]) * fraction
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        if (sorted.isEmpty()) return 0.0
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2.0 else sorted[middle]
    }

    private fun standardDeviation(values: List<Double>, mean: Double): Double {
        if (values.size < 2) return 0.0
        return sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    }

    private fun featureKey(event: LifeEvent) = "${event.category.trim()}/${event.type.trim()}"

    private fun displayName(key: String): String = key.substringAfterLast('/').replace('_', ' ').lowercase()

    private fun dayOf(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun format(value: Double) = "%.2f".format(java.util.Locale.US, value)
}

package com.dzire.dzirecompanion.insights

import com.dzire.dzirecompanion.data.database.LifeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.sign

/** Statistical configuration only. No wellness/domain assumptions live here. */
data class InsightConfig(
    val minimumDistinctDays: Int = 14,
    val minimumFeatureDays: Int = 7,
    val minimumPairedDays: Int = 14,
    val minimumTrendDays: Int = 14,
    val minimumAbsoluteCorrelation: Double = 0.50,
    val minimumLift: Double = 1.50,
    val minimumRelativeChange: Double = 0.15,
    val minimumCoverage: Double = 0.55,
    val minimumConfidence: Double = 0.55,
    val rollingWindowDays: Int = 7,
    val maximumInsights: Int = 10
)

data class DiscoveredInsight(
    val title: String,
    val description: String,
    val strength: Double,
    val evidenceDays: Int,
    val confidence: Double,
    val kind: Kind
) {
    enum class Kind { TREND, CHANGE, NUMERIC_CORRELATION, CATEGORICAL_ASSOCIATION }
}

/**
 * Discovers patterns from the user's recorded observations.
 *
 * The engine knows nothing about what is "good", "bad", healthy, spiritual,
 * productive, etc. It only performs generic statistical analysis on the data.
 */
object InsightEngine {
    fun analyze(events: List<LifeEvent>, config: InsightConfig = InsightConfig()): List<DiscoveredInsight> {
        if (events.isEmpty()) return emptyList()

        val daily = buildDailyDataset(events)
        if (daily.days.size < config.minimumDistinctDays) return emptyList()

        val numeric = extractNumericSeries(events)
        val categorical = extractCategoricalSeries(events)

        return buildList {
            addAll(findNumericCorrelations(numeric, config))
            addAll(findNumericTrends(numeric, daily.days, config))
            addAll(findRecentChanges(numeric, daily.days, config))
            addAll(findCategoricalAssociations(categorical, daily.days, config))
        }
            .distinctBy { normalizedKey(it) }
            .sortedWith(compareByDescending<DiscoveredInsight> { it.strength }.thenByDescending { it.evidenceDays })
            .take(config.maximumInsights)
    }

    private data class NumericSeries(val key: String, val valuesByDay: Map<LocalDate, Double>, val unit: String?)
    private data class CategoricalFeature(val key: String, val days: Set<LocalDate>)
    private data class DailyDataset(val days: List<LocalDate>)

    private fun buildDailyDataset(events: List<LifeEvent>): DailyDataset =
        DailyDataset(events.map { dayOf(it.timestamp) }.distinct().sorted())

    private fun extractNumericSeries(events: List<LifeEvent>): List<NumericSeries> {
        return events
            .mapNotNull { event ->
                val value = event.numericValue ?: event.parameter1?.toDoubleOrNull() ?: return@mapNotNull null
                val key = featureKey(event)
                key to dayOf(event.timestamp) to value
            }
            .groupBy({ it.first.first }, { it.first.second to it.second })
            .map { (key, values) ->
                NumericSeries(
                    key = key,
                    valuesByDay = values.groupBy({ it.first }, { it.second }).mapValues { (_, dayValues) -> dayValues.average() },
                    unit = events.firstOrNull { featureKey(it) == key }?.unit
                )
            }
    }

    private fun extractCategoricalSeries(events: List<LifeEvent>): List<CategoricalFeature> {
        return events
            .mapNotNull { event ->
                val value = event.parameter1?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                "${event.category.trim()}/${event.type.trim()}=$value" to dayOf(event.timestamp)
            }
            .groupBy({ it.first }, { it.second })
            .map { (key, days) -> CategoricalFeature(key, days.toSet()) }
    }

    private fun qualityScore(evidenceDays: Int, totalDays: Int, coverage: Double, stability: Double = 1.0, config: InsightConfig): Double {
        val evidenceScore = (evidenceDays.toDouble() / (config.minimumPairedDays * 2.0)).coerceIn(0.0, 1.0)
        return (0.30 * evidenceScore + 0.30 * coverage.coerceIn(0.0, 1.0) + 0.40 * stability.coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
    }

    private fun confidenceLabel(score: Double): String = when {
        score >= 0.80 -> "High"
        score >= 0.65 -> "Moderate"
        else -> "Early"
    }

    private fun robustValues(values: List<Double>): List<Double> {
        if (values.size < 5) return values
        val sorted = values.sorted()
        val q1 = sorted[sorted.size / 4]
        val q3 = sorted[(sorted.size * 3) / 4]
        val iqr = q3 - q1
        if (iqr == 0.0) return values
        val lower = q1 - 1.5 * iqr
        val upper = q3 + 1.5 * iqr
        return values.filter { it in lower..upper }
    }

    private fun findNumericCorrelations(
        series: List<NumericSeries>,
        config: InsightConfig
    ): List<DiscoveredInsight> {
        val result = mutableListOf<DiscoveredInsight>()
        for (i in 0 until series.size) {
            for (j in i + 1 until series.size) {
                val a = series[i]
                val b = series[j]
                if (sameCategory(a.key, b.key)) continue

                val sharedDays = a.valuesByDay.keys.intersect(b.valuesByDay.keys).sorted()
                if (sharedDays.size < config.minimumPairedDays) continue

                val paired = sharedDays.map { it to (a.valuesByDay.getValue(it) to b.valuesByDay.getValue(it)) }
                val xRaw = paired.map { it.second.first }
                val yRaw = paired.map { it.second.second }
                val xSorted = xRaw.sorted()
                val ySorted = yRaw.sorted()
                val xQ1 = xSorted[xSorted.size / 4]
                val xQ3 = xSorted[(xSorted.size * 3) / 4]
                val yQ1 = ySorted[ySorted.size / 4]
                val yQ3 = ySorted[(ySorted.size * 3) / 4]
                val xIqr = xQ3 - xQ1
                val yIqr = yQ3 - yQ1
                val filtered = paired.filter { (_, values) ->
                    val xOk = xIqr == 0.0 || values.first in (xQ1 - 1.5 * xIqr)..(xQ3 + 1.5 * xIqr)
                    val yOk = yIqr == 0.0 || values.second in (yQ1 - 1.5 * yIqr)..(yQ3 + 1.5 * yIqr)
                    xOk && yOk
                }
                if (filtered.size < config.minimumPairedDays) continue
                val x = filtered.map { it.second.first }
                val y = filtered.map { it.second.second }
                val correlation = pearson(x, y) ?: continue
                val strength = abs(correlation)
                val coverage = sharedDays.size.toDouble() / max(1, maxOf(a.valuesByDay.size, b.valuesByDay.size))
                if (strength < config.minimumAbsoluteCorrelation || coverage < config.minimumCoverage) continue
                val stability = correlationStability(a, b, sharedDays, config)
                val confidence = qualityScore(sharedDays.size, 0, coverage, stability, config)
                if (confidence < config.minimumConfidence) continue
                val direction = if (correlation > 0) "tend to increase together" else "tend to move in opposite directions"
                result += DiscoveredInsight(
                    title = "${displayName(a.key)} ↔ ${displayName(b.key)}",
                    description = "Across ${sharedDays.size} shared days, these measurements $direction (correlation ${format(correlation)}). ${confidenceLabel(confidence)} confidence based on coverage and consistency. This is an association, not proof of causation.",
                    strength = strength,
                    evidenceDays = sharedDays.size,
                    confidence = confidence,
                    kind = DiscoveredInsight.Kind.NUMERIC_CORRELATION
                )
            }
        }
        return result
    }

    private fun findNumericTrends(
        series: List<NumericSeries>,
        allDays: List<LocalDate>,
        config: InsightConfig
    ): List<DiscoveredInsight> {
        val result = mutableListOf<DiscoveredInsight>()
        for (item in series) {
            val points = allDays.mapNotNull { day -> item.valuesByDay[day]?.let { day to it } }
            if (points.size < config.minimumTrendDays) continue

            val cleaned = robustValues(points.map { it.second })
            if (cleaned.size < config.minimumTrendDays) continue
            val slope = regressionSlope(cleaned.mapIndexed { index, value -> index.toDouble() to value })
            val mean = cleaned.average()
            if (mean == 0.0) continue
            val normalizedSlope = slope / abs(mean)
            val strength = minMax(abs(normalizedSlope) * cleaned.size / 2.0, 0.0, 1.0)
            val coverage = points.size.toDouble() / allDays.size
            val stability = trendStability(cleaned)
            val confidence = qualityScore(points.size, allDays.size, coverage, stability, config)
            if (strength < 0.30 || coverage < config.minimumCoverage || confidence < config.minimumConfidence) continue
            val direction = if (slope > 0) "increasing" else "decreasing"
            result += DiscoveredInsight(
                title = "${displayName(item.key)} is $direction",
                description = "The recorded values show a statistical $direction trend across ${points.size} observed days. Average value: ${format(mean)}${item.unit?.let { " $it" } ?: ""}. ${confidenceLabel(confidence)} confidence after accounting for coverage and outliers. A trend describes the data; it does not explain why it changed.",
                strength = strength,
                evidenceDays = points.size,
                confidence = confidence,
                kind = DiscoveredInsight.Kind.TREND
            )
        }
        return result
    }

    private fun findRecentChanges(
        series: List<NumericSeries>,
        allDays: List<LocalDate>,
        config: InsightConfig
    ): List<DiscoveredInsight> {
        val result = mutableListOf<DiscoveredInsight>()
        for (item in series) {
            val points = allDays.mapNotNull { day -> item.valuesByDay[day] }
            if (points.size < config.minimumTrendDays * 2) continue

            val split = points.size / 2
            val previous = points.take(split).average()
            val recent = points.drop(split).average()
            if (previous == 0.0) continue

            val relativeChange = (recent - previous) / abs(previous)
            if (abs(relativeChange) < config.minimumRelativeChange) continue

            val strength = minMax(abs(relativeChange), 0.0, 1.0)
            val coverage = points.size.toDouble() / allDays.size
            val stability = splitStability(points, split)
            val confidence = qualityScore(points.size, allDays.size, coverage, stability, config)
            if (confidence < config.minimumConfidence || coverage < config.minimumCoverage) continue
            val direction = if (relativeChange > 0) "higher" else "lower"
            result += DiscoveredInsight(
                title = "${displayName(item.key)} changed",
                description = "The average in the more recent half of the available history is ${formatPercent(abs(relativeChange))} $direction than the earlier half (${format(previous)} → ${format(recent)}${item.unit?.let { " $it" } ?: ""}). ${confidenceLabel(confidence)} confidence based on history coverage and consistency.",
                strength = strength,
                evidenceDays = points.size,
                confidence = confidence,
                kind = DiscoveredInsight.Kind.CHANGE
            )
        }
        return result
    }

    private fun findCategoricalAssociations(
        features: List<CategoricalFeature>,
        allDays: List<LocalDate>,
        config: InsightConfig
    ): List<DiscoveredInsight> {
        val result = mutableListOf<DiscoveredInsight>()
        if (allDays.size < config.minimumDistinctDays) return result

        for (i in 0 until features.size) {
            for (j in i + 1 until features.size) {
                val a = features[i]
                val b = features[j]
                if (sameCategory(a.key, b.key)) continue

                val overlap = a.days.intersect(b.days).size
                if (a.days.size < config.minimumFeatureDays || b.days.size < config.minimumFeatureDays) continue
                if (overlap < config.minimumFeatureDays) continue

                val pB = b.days.size.toDouble() / allDays.size
                val pBGivenA = overlap.toDouble() / a.days.size
                val lift = if (pB > 0) pBGivenA / pB else 0.0
                if (lift < config.minimumLift) continue

                val strength = minMax((lift - 1.0) / lift, 0.0, 1.0)
                val coverage = overlap.toDouble() / allDays.size
                val confidence = qualityScore(overlap, allDays.size, coverage, minMax(overlap / 20.0, 0.0, 1.0), config)
                if (confidence < config.minimumConfidence) continue
                result += DiscoveredInsight(
                    title = "${displayName(a.key)} ↔ ${displayName(b.key)}",
                    description = "These two recorded categories appeared together on ${overlap} days. The second was ${format(lift)}× as common on days containing the first compared with its overall frequency. ${confidenceLabel(confidence)} confidence based on evidence volume and baseline frequency. This is an association, not proof of causation.",
                    strength = strength,
                    evidenceDays = overlap,
                    confidence = confidence,
                    kind = DiscoveredInsight.Kind.CATEGORICAL_ASSOCIATION
                )
            }
        }
        return result
    }

    private fun correlationStability(a: NumericSeries, b: NumericSeries, days: List<LocalDate>, config: InsightConfig): Double {
        if (days.size < config.rollingWindowDays * 2) return 0.5
        val windows = days.chunked(config.rollingWindowDays).filter { it.size >= config.rollingWindowDays }
        if (windows.size < 2) return 0.5
        val signs = windows.mapNotNull { window ->
            pearson(window.map { a.valuesByDay.getValue(it) }, window.map { b.valuesByDay.getValue(it) })?.let { if (it >= 0) 1 else -1 }
        }
        if (signs.isEmpty()) return 0.5
        return maxOf(signs.count { it == signs.first() }.toDouble() / signs.size, 0.5)
    }

    private fun trendStability(values: List<Double>): Double {
        if (values.size < 3) return 0.5
        val slopes = values.windowed(3).map { regressionSlope(it.mapIndexed { i, v -> i.toDouble() to v }) }
        if (slopes.isEmpty()) return 0.5
        val overall = regressionSlope(values.mapIndexed { i, v -> i.toDouble() to v })
        val aligned = slopes.count { it == 0.0 || it.sign == overall.sign }
        return aligned.toDouble() / slopes.size
    }

    private fun splitStability(values: List<Double>, split: Int): Double {
        if (split < 2 || values.size - split < 2) return 0.5
        val first = values.take(split).average()
        val second = values.drop(split).average()
        val firstVar = values.take(split).map { (it - first) * (it - first) }.average()
        val secondVar = values.drop(split).map { (it - second) * (it - second) }.average()
        val pooled = sqrt((firstVar + secondVar) / 2.0)
        return (1.0 / (1.0 + pooled / max(abs(first), 1e-9))).coerceIn(0.0, 1.0)
    }

    private fun featureKey(event: LifeEvent) = "${event.category.trim()}/${event.type.trim()}"

    private fun displayName(key: String): String = key.substringAfterLast('/').replace('_', ' ').lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun sameCategory(a: String, b: String): Boolean =
        a.substringBefore('/').equals(b.substringBefore('/'), ignoreCase = true)

    private fun dayOf(timestamp: Long): LocalDate =
        Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 2) return null
        val meanX = x.average()
        val meanY = y.average()
        var numerator = 0.0
        var denomX = 0.0
        var denomY = 0.0
        for (i in x.indices) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY
            numerator += dx * dy
            denomX += dx * dx
            denomY += dy * dy
        }
        val denominator = sqrt(denomX * denomY)
        return if (denominator == 0.0) null else numerator / denominator
    }

    private fun regressionSlope(points: List<Pair<Double, Double>>): Double {
        if (points.size < 2) return 0.0
        val meanX = points.map { it.first }.average()
        val meanY = points.map { it.second }.average()
        var numerator = 0.0
        var denominator = 0.0
        points.forEach { (x, y) ->
            numerator += (x - meanX) * (y - meanY)
            denominator += (x - meanX) * (x - meanX)
        }
        return if (denominator == 0.0) 0.0 else numerator / denominator
    }

    private fun normalizedKey(insight: DiscoveredInsight): String = insight.title + insight.kind

    private fun minMax(value: Double, min: Double, max: Double) = value.coerceIn(min, max)
    private fun format(value: Double) = "%.2f".format(java.util.Locale.US, value)
    private fun formatPercent(value: Double) = "%.0f%%".format(java.util.Locale.US, value * 100)
}

package com.dzire.dzirecompanion.insights

import com.dzire.dzirecompanion.data.database.LifeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/** A chronological, neutral representation of recorded life events. */
data class TimelineEntry(
    val eventId: Long,
    val timestamp: Long,
    val date: LocalDate,
    val timeLabel: String,
    val category: String,
    val type: String,
    val content: String,
    val numericValue: Double?,
    val unit: String?,
    val rating: Double?,
    val durationMs: Long?,
    val source: String
)

/** A time-aware statistical association. It does not imply causation. */
data class TemporalInsight(
    val title: String,
    val description: String,
    val lagHours: Int,
    val evidencePairs: Int,
    val correlation: Double,
    val confidence: Double
)

object TemporalAnalyzer {
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun buildTimeline(events: List<LifeEvent>): List<TimelineEntry> = events
        .sortedByDescending { it.timestamp }
        .map { event ->
            val dateTime = Instant.ofEpochMilli(event.timestamp).atZone(ZoneId.systemDefault())
            TimelineEntry(
                eventId = event.id,
                timestamp = event.timestamp,
                date = dateTime.toLocalDate(),
                timeLabel = dateTime.format(timeFormatter),
                category = event.category,
                type = event.type,
                content = event.content,
                numericValue = event.numericValue,
                unit = event.unit,
                rating = event.rating,
                durationMs = event.durationMs,
                source = event.source
            )
        }

    /**
     * Searches for lagged numeric associations without knowing what a feature means.
     * For every occurrence of feature A, the engine looks for the first observation
     * of feature B in the configured future window. The result is explicitly an
     * association; it is never presented as a cause/effect conclusion.
     */
    fun findLaggedAssociations(
        events: List<LifeEvent>,
        maxLagHours: Int = 24,
        stepHours: Int = 6,
        minimumPairs: Int = 10,
        minimumAbsoluteCorrelation: Double = 0.50,
        minimumConfidence: Double = 0.55,
        maximumResults: Int = 8
    ): List<TemporalInsight> {
        val numeric = events.mapNotNull { event ->
            val value = event.numericValue ?: event.parameter1?.toDoubleOrNull() ?: return@mapNotNull null
            NumericEvent(event, featureKey(event), value)
        }
        val features = numeric.map { it.key }.distinct()
        if (features.size < 2) return emptyList()

        val results = mutableListOf<TemporalInsight>()
        for (source in features) {
            for (target in features) {
                if (source == target || sameCategory(source, target)) continue
                for (lag in (stepHours..maxLagHours step stepHours)) {
                    val pairs = pairAtLag(numeric, source, target, lag)
                    if (pairs.size < minimumPairs) continue
                    val correlation = pearson(pairs.map { it.first }, pairs.map { it.second }) ?: continue
                    val strength = abs(correlation)
                    if (strength < minimumAbsoluteCorrelation) continue

                    val coverage = pairs.size.toDouble() / max(1, numeric.count { it.key == source })
                    val stability = lagStability(pairs)
                    val evidenceScore = (pairs.size.toDouble() / (minimumPairs * 3.0)).coerceIn(0.0, 1.0)
                    val confidence = (0.35 * evidenceScore + 0.30 * coverage + 0.35 * stability).coerceIn(0.0, 1.0)
                    if (confidence < minimumConfidence) continue

                    val direction = if (correlation > 0) "tend to increase together" else "tend to move in opposite directions"
                    val sourceName = displayName(source)
                    val targetName = displayName(target)
                    results += TemporalInsight(
                        title = "$sourceName → $targetName",
                        description = "When $sourceName was recorded, a $targetName observation within the next $lag hours tended to $direction (r=${format(correlation)}). This association was supported by ${pairs.size} time-linked observations. It does not establish causation.",
                        lagHours = lag,
                        evidencePairs = pairs.size,
                        correlation = correlation,
                        confidence = confidence
                    )
                }
            }
        }

        return results
            .distinctBy { "${it.title}|${it.lagHours}" }
            .sortedByDescending { it.confidence * abs(it.correlation) }
            .take(maximumResults)
    }

    private data class NumericEvent(val event: LifeEvent, val key: String, val value: Double)

    private fun pairAtLag(events: List<NumericEvent>, source: String, target: String, lagHours: Int): List<Pair<Double, Double>> {
        val sourceEvents = events.filter { it.key == source }.sortedBy { it.event.timestamp }
        val targetEvents = events.filter { it.key == target }.sortedBy { it.event.timestamp }
        val window = lagHours * 60L * 60L * 1000L
        val pairs = mutableListOf<Pair<Double, Double>>()
        for (s in sourceEvents) {
            val targetEvent = targetEvents.firstOrNull {
                it.event.timestamp > s.event.timestamp && it.event.timestamp - s.event.timestamp <= window
            } ?: continue
            pairs += s.value to targetEvent.value
        }
        return pairs
    }

    private fun lagStability(pairs: List<Pair<Double, Double>>): Double {
        if (pairs.size < 12) return 0.5
        val windows = pairs.chunked(max(4, pairs.size / 4))
        val signs = windows.mapNotNull { pearson(it.map { p -> p.first }, it.map { p -> p.second })?.let { r -> if (r >= 0) 1 else -1 } }
        if (signs.isEmpty()) return 0.5
        return signs.count { it == signs.first() }.toDouble() / signs.size
    }

    private fun featureKey(event: LifeEvent) = "${event.category.trim()}/${event.type.trim()}"
    private fun displayName(key: String) = key.substringAfterLast('/').replace('_', ' ').lowercase()
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    private fun sameCategory(a: String, b: String) = a.substringBefore('/').equals(b.substringBefore('/'), ignoreCase = true)
    private fun format(v: Double) = "%.2f".format(java.util.Locale.US, v)

    private fun pearson(x: List<Double>, y: List<Double>): Double? {
        if (x.size != y.size || x.size < 2) return null
        val mx = x.average(); val my = y.average()
        var n = 0.0; var dx = 0.0; var dy = 0.0
        for (i in x.indices) {
            val a = x[i] - mx; val b = y[i] - my
            n += a * b; dx += a * a; dy += b * b
        }
        val d = sqrt(dx * dy)
        return if (d == 0.0) null else n / d
    }
}

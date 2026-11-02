package com.dzire.dzirecompanion.insights

import com.dzire.dzirecompanion.data.database.LifeEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Phase 7: converts validated analytical outputs into an evidence-backed,
 * neutral narrative. It does not invent causes, diagnoses, goals, or advice.
 */
data class NarrativeConfig(
    val recentDays: Int = 14,
    val minimumConfidence: Double = 0.60,
    val maximumFindings: Int = 6,
    val maximumAnomalies: Int = 3,
    val maximumTransitions: Int = 3
)

enum class NarrativeEvidenceType { CHANGE, ANOMALY, STATE, TRANSITION, ASSOCIATION, TEMPORAL }

data class NarrativeEvidence(
    val type: NarrativeEvidenceType,
    val title: String,
    val statement: String,
    val confidence: Double,
    val evidenceCount: Int,
    val date: LocalDate? = null
)

data class NarrativeReport(
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val observedDays: Int,
    val recordedEvents: Int,
    val headline: String,
    val summary: String,
    val evidence: List<NarrativeEvidence>,
    val limitations: List<String>
)

object PersonalNarrativeEngine {
    fun build(
        events: List<LifeEvent>,
        baseline: BaselineReport,
        states: PersonalStateReport,
        statistical: List<DiscoveredInsight>,
        temporal: List<TemporalInsight>,
        config: NarrativeConfig = NarrativeConfig()
    ): NarrativeReport {
        if (events.isEmpty()) {
            return NarrativeReport(null, null, 0, 0,
                "Not enough history yet",
                "Record more structured events to build an evidence-backed personal narrative.",
                emptyList(),
                listOf("The narrative is based only on data recorded in Dzire."))
        }

        val dates = events.map { dayOf(it.timestamp) }.distinct().sorted()
        val end = dates.last()
        val start = end.minusDays(config.recentDays.toLong() - 1)
        val recentEvents = events.filter { dayOf(it.timestamp) in start..end }

        val evidence = mutableListOf<NarrativeEvidence>()
        evidence += recentChanges(baseline)
        evidence += recentAnomalies(baseline, config)
        evidence += currentState(states, end)
        evidence += stateTransitions(states, config)
        evidence += strongStatistical(statistical, config)
        evidence += strongTemporal(temporal, config)

        val selected = evidence
            .distinctBy { "${it.type}|${it.title}|${it.date}" }
            .sortedWith(compareByDescending<NarrativeEvidence> { it.confidence }.thenByDescending { it.evidenceCount })
            .take(config.maximumFindings)

        val headline = makeHeadline(selected)
        val summary = makeSummary(selected, recentEvents.size, start, end)
        val limitations = buildList {
            add("This narrative describes statistical evidence in your recorded history; it does not establish cause and effect.")
            add("Missing or inconsistent records can change the findings.")
            if (dates.size < config.recentDays) add("The recent period contains fewer observed days than the configured window.")
        }

        return NarrativeReport(start, end, dates.size, events.size, headline, summary, selected, limitations)
    }

    private fun recentChanges(report: BaselineReport): List<NarrativeEvidence> =
        report.baselines.filter { it.recentMean != null && it.deviationPercent != null }
            .sortedByDescending { abs(it.deviationPercent ?: 0.0) }
            .take(3)
            .map { baseline ->
                val direction = if ((baseline.deviationPercent ?: 0.0) >= 0) "higher" else "lower"
                val pct = abs((baseline.deviationPercent ?: 0.0) * 100)
                NarrativeEvidence(
                    NarrativeEvidenceType.CHANGE,
                    pretty(baseline.feature),
                    "Recent recorded values were ${format(pct)}% $direction than the personal baseline average.",
                    changeConfidence(baseline),
                    baseline.baselineDays
                )
            }

    private fun recentAnomalies(report: BaselineReport, config: NarrativeConfig): List<NarrativeEvidence> =
        report.anomalies.take(config.maximumAnomalies).map { anomaly ->
            NarrativeEvidence(
                NarrativeEvidenceType.ANOMALY,
                "Unusual: ${pretty(anomaly.feature)}",
                anomaly.description,
                anomaly.deviationScore.coerceIn(0.0, 1.0),
                1,
                anomaly.date
            )
        }

    private fun currentState(report: PersonalStateReport, end: LocalDate): List<NarrativeEvidence> {
        val latest = report.dailyStates.firstOrNull { it.date == end } ?: report.dailyStates.firstOrNull() ?: return emptyList()
        val cluster = report.states.firstOrNull { it.stateId == latest.stateId } ?: return emptyList()
        return listOf(
            NarrativeEvidence(
                NarrativeEvidenceType.STATE,
                "Recent personal state",
                "The latest observed day most closely matched a recurring personal state observed on ${cluster.days} recorded days (${format(cluster.frequency * 100)}% of learned state days). ${cluster.description}.",
                stateConfidence(report, cluster),
                cluster.days,
                latest.date
            )
        )
    }

    private fun stateTransitions(report: PersonalStateReport, config: NarrativeConfig): List<NarrativeEvidence> =
        report.transitions.take(config.maximumTransitions).map { transition ->
            NarrativeEvidence(
                NarrativeEvidenceType.TRANSITION,
                "State ${transition.fromStateId} → State ${transition.toStateId}",
                "This transition appeared ${transition.occurrences} times and represented ${format(transition.probability * 100)}% of observed outgoing transitions from State ${transition.fromStateId}. Average gap: ${format(transition.averageGapDays)} day(s).",
                transitionConfidence(transition),
                transition.occurrences
            )
        }

    private fun strongStatistical(insights: List<DiscoveredInsight>, config: NarrativeConfig): List<NarrativeEvidence> =
        insights.filter { it.confidence >= config.minimumConfidence }
            .take(2)
            .map {
                NarrativeEvidence(
                    NarrativeEvidenceType.ASSOCIATION,
                    it.title,
                    it.description,
                    it.confidence,
                    it.evidenceDays
                )
            }

    private fun strongTemporal(insights: List<TemporalInsight>, config: NarrativeConfig): List<NarrativeEvidence> =
        insights.filter { it.confidence >= config.minimumConfidence }
            .take(2)
            .map {
                NarrativeEvidence(
                    NarrativeEvidenceType.TEMPORAL,
                    it.title,
                    it.description,
                    it.confidence,
                    it.evidencePairs
                )
            }

    private fun makeHeadline(evidence: List<NarrativeEvidence>): String {
        if (evidence.isEmpty()) return "Your recent history is still taking shape"
        val strongest = evidence.maxByOrNull { it.confidence } ?: return "Your recent history is still taking shape"
        return when (strongest.type) {
            NarrativeEvidenceType.CHANGE -> "Your recent pattern differs from your baseline"
            NarrativeEvidenceType.ANOMALY -> "Your recent history contains an unusual observation"
            NarrativeEvidenceType.STATE -> "Your recent days resemble a recurring personal state"
            NarrativeEvidenceType.TRANSITION -> "Your history shows a recurring state transition"
            NarrativeEvidenceType.ASSOCIATION -> "Your data shows a recurring statistical association"
            NarrativeEvidenceType.TEMPORAL -> "Your data shows a time-linked association"
        }
    }

    private fun makeSummary(evidence: List<NarrativeEvidence>, eventCount: Int, start: LocalDate, end: LocalDate): String {
        if (evidence.isEmpty()) return "From $start to $end, Dzire recorded $eventCount events in the selected period. There is not yet enough validated evidence to describe a stronger pattern."
        val high = evidence.count { it.confidence >= 0.80 }
        val moderate = evidence.count { it.confidence >= 0.60 && it.confidence < 0.80 }
        return "From $start to $end, Dzire recorded $eventCount events. The analysis found ${evidence.size} evidence-backed finding(s): $high high-confidence and $moderate moderate-confidence finding(s). The findings describe your recorded history without assuming that one event caused another."
    }

    private fun changeConfidence(baseline: PersonalBaseline): Double {
        val coverage = (baseline.baselineDays / 21.0).coerceIn(0.0, 1.0)
        val magnitude = ((abs(baseline.deviationPercent ?: 0.0) / 0.50)).coerceIn(0.0, 1.0)
        return (0.55 * coverage + 0.45 * magnitude).coerceIn(0.0, 1.0)
    }

    private fun stateConfidence(report: PersonalStateReport, state: StateCluster): Double {
        val frequency = state.frequency.coerceIn(0.0, 1.0)
        val history = (report.observedDays / 60.0).coerceIn(0.0, 1.0)
        return (0.60 * frequency + 0.40 * history).coerceIn(0.0, 1.0)
    }

    private fun transitionConfidence(transition: StateTransition): Double =
        (0.60 * transition.probability + 0.40 * (transition.occurrences / 10.0).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)

    private fun dayOf(timestamp: Long): LocalDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
    private fun pretty(value: String): String = value.substringAfterLast('/').replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
    private fun format(value: Double): String = "%.1f".format(java.util.Locale.US, value)
}

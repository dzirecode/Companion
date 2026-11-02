package com.dzire.dzirecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.insights.DiscoveredInsight
import com.dzire.dzirecompanion.insights.BaselineAnalyzer
import com.dzire.dzirecompanion.insights.BaselineConfig
import com.dzire.dzirecompanion.insights.PersonalBaseline
import com.dzire.dzirecompanion.insights.AnomalyInsight
import com.dzire.dzirecompanion.insights.InsightConfig
import com.dzire.dzirecompanion.insights.InsightEngine
import com.dzire.dzirecompanion.insights.TemporalAnalyzer
import com.dzire.dzirecompanion.insights.TemporalInsight
import com.dzire.dzirecompanion.insights.TimelineEntry
import com.dzire.dzirecompanion.insights.PersonalStateAnalyzer
import com.dzire.dzirecompanion.insights.PersonalStateReport
import com.dzire.dzirecompanion.insights.StateCluster
import com.dzire.dzirecompanion.insights.StateTransition
import com.dzire.dzirecompanion.insights.PersonalNarrativeEngine
import com.dzire.dzirecompanion.insights.NarrativeReport
import com.dzire.dzirecompanion.insights.NarrativeEvidence
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme

class InsightsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                InsightsScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val events by db.lifeEventDao().getAllEvents().collectAsState(initial = emptyList())

    val timeline = remember(events) { TemporalAnalyzer.buildTimeline(events) }
    val temporalInsights = remember(events) {
        TemporalAnalyzer.findLaggedAssociations(events)
    }

    val baselineReport = remember(events) {
        BaselineAnalyzer.analyze(events, BaselineConfig())
    }

    val stateReport = remember(events) {
        PersonalStateAnalyzer.analyze(events)
    }

    val insights = remember(events) {
        InsightEngine.analyze(
            events = events,
            config = InsightConfig()
        )
    }

    val narrative = remember(events) {
        PersonalNarrativeEngine.build(
            events = events,
            baseline = baselineReport,
            states = stateReport,
            statistical = insights,
            temporal = temporalInsights
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Data Insights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoGraph,
                            contentDescription = null,
                            modifier = Modifier.size(42.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Patterns from your data", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Relationships are discovered from your recorded events rather than predefined life rules.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            item {
                NarrativeCard(narrative)
            }

            item {
                Text(
                    "Temporal patterns",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Time-linked associations are discovered from the order and timing of your own observations.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (temporalInsights.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "Not enough time-linked evidence yet. Keep recording events with timestamps.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(temporalInsights) { TemporalInsightCard(it) }
            }

            item {
                Text(
                    "Your personal states",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dzire groups recurring daily signatures from your own recorded data. State labels describe statistical differences, not good or bad states.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (stateReport.states.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "Not enough consistent multi-day data to learn recurring personal states yet.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(stateReport.states) { state -> StateClusterCard(state) }
                if (stateReport.transitions.isNotEmpty()) {
                    item {
                        Text(
                            "State transitions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "These are recurring changes between learned states on consecutive observed days. They are descriptive, not causal.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    items(stateReport.transitions) { transition -> StateTransitionCard(transition) }
                }
            }

            item {
                Text(
                    "Personal timeline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            itemsIndexed(timeline.take(30)) { index, entry ->
                TimelineCard(entry, showDate = index == 0 || timeline.take(30)[index - 1].date != entry.date)
            }

            item {
                Text(
                    "Your personal baseline",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dzire compares recent observations with your own historical pattern. It does not compare you with population targets.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (baselineReport.baselines.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                        Text(
                            "Not enough consistent history to establish a personal baseline yet. Keep recording structured measurements over time.",
                            modifier = Modifier.padding(20.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                items(baselineReport.baselines) { baseline ->
                    BaselineCard(baseline)
                }

                if (baselineReport.anomalies.isNotEmpty()) {
                    item {
                        Text(
                            "Unusual for you",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    items(baselineReport.anomalies) { anomaly ->
                        AnomalyCard(anomaly)
                    }
                }
            }

            if (insights.isEmpty()) {
                item {
                    EmptyInsightsCard(events.size)
                }
            } else {
                item {
                    Text(
                        "Statistical patterns",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(insights) { insight ->
                    InsightCard(insight)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "These results show statistical associations in your history. They do not establish cause and effect. More consistent data generally produces more reliable patterns.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun NarrativeCard(report: NarrativeReport) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Your recent story", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(report.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(report.summary, style = MaterialTheme.typography.bodyMedium)

            if (report.evidence.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                report.evidence.forEach { evidence ->
                    NarrativeEvidenceRow(evidence)
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            report.limitations.forEach { limitation ->
                Text("• $limitation", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun NarrativeEvidenceRow(evidence: NarrativeEvidence) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(evidence.title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(evidence.statement, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "Evidence: ${evidence.evidenceCount} • Confidence: ${(evidence.confidence * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BaselineCard(baseline: PersonalBaseline) {
    val status = when (baseline.status) {
        PersonalBaseline.Status.ABOVE_BASELINE -> "Above your baseline"
        PersonalBaseline.Status.BELOW_BASELINE -> "Below your baseline"
        PersonalBaseline.Status.WITHIN_BASELINE -> "Within your baseline"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(baseline.feature.substringAfterLast('/').replace('_', ' '), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(status, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Personal baseline: ${"%.2f".format(java.util.Locale.US, baseline.baselineMean)}${baseline.unit?.let { " $it" } ?: ""} • ${baseline.baselineDays} days",
                style = MaterialTheme.typography.bodyMedium
            )
            baseline.recentMean?.let { recent ->
                Text(
                    "Recent average: ${"%.2f".format(java.util.Locale.US, recent)}${baseline.unit?.let { " $it" } ?: ""} • ${baseline.recentDays} days",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            baseline.deviationPercent?.let { deviation ->
                Text(
                    "Change from baseline: ${if (deviation >= 0) "+" else ""}${"%.1f".format(java.util.Locale.US, deviation * 100)}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AnomalyCard(anomaly: AnomalyInsight) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(anomaly.feature.substringAfterLast('/').replace('_', ' '), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(anomaly.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "Unusualness: ${(anomaly.deviationScore * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InsightCard(insight: DiscoveredInsight) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                insight.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(insight.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { insight.strength.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Signal: ${(insight.strength * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Evidence: ${insight.evidenceDays} days",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Confidence: ${when { insight.confidence >= 0.80 -> "High"; insight.confidence >= 0.65 -> "Moderate"; else -> "Early" }} (${(insight.confidence * 100).toInt()}%)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TemporalInsightCard(insight: TemporalInsight) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(insight.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(insight.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                "Lag: ${insight.lagHours}h  •  Evidence: ${insight.evidencePairs} pairs  •  Confidence: ${(insight.confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TimelineCard(entry: TimelineEntry, showDate: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDate) {
            Text(
                entry.date.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(entry.type.replace('_', ' '), fontWeight = FontWeight.SemiBold)
                    Text(entry.timeLabel, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(6.dp))
                Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                val measurement = entry.numericValue?.let { value ->
                    "${"%.2f".format(java.util.Locale.US, value)}${entry.unit?.let { " $it" } ?: ""}"
                }
                if (measurement != null || entry.rating != null || entry.durationMs != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        listOfNotNull(
                            measurement,
                            entry.rating?.let { "rating ${"%.1f".format(java.util.Locale.US, it)}" },
                            entry.durationMs?.let { "duration ${it / 60000} min" }
                        ).joinToString("  •  "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "${entry.category}  •  ${entry.source}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StateClusterCard(state: StateCluster) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("State ${state.stateId}", fontWeight = FontWeight.Bold)
                Text("${(state.frequency * 100).toInt()}% of observed days", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.height(8.dp))
            Text(state.description, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("Observed on ${state.days} days", style = MaterialTheme.typography.labelMedium)
            if (state.representativeDays.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Representative days: ${state.representativeDays.joinToString()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StateTransitionCard(transition: StateTransition) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("State ${transition.fromStateId} → State ${transition.toStateId}", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Observed ${transition.occurrences} times. Among observed transitions from State ${transition.fromStateId}, this transition occurred ${(transition.probability * 100).toInt()}% of the time.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Average gap: ${"%.1f".format(java.util.Locale.US, transition.averageGapDays)} day(s)",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyInsightsCard(eventCount: Int) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Not enough evidence yet", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "The engine currently has $eventCount recorded events. Keep recording consistently across multiple days; patterns are shown only when the statistics meet the configured evidence requirements.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

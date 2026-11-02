package com.dzire.dzirecompanion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.data.database.LifeEvent
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme
import kotlinx.coroutines.launch

class MentalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                MentalScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MentalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mental Clarity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { MentalHeader() }

            item {
                MentalSectionLabel("Mental Load Tracker")
                MentalLoadCard(onSave = { load ->
                    scope.launch {
                        val event = LifeEvent(
                            category = "MENTAL",
                            type = "MENTAL_LOAD",
                            content = "User reported a mental load of ${(load * 100).toInt()}%",
                            parameter1 = load.toString(),
                            numericValue = load.toDouble(),
                            unit = "ratio"
                        )
                        db.lifeEventDao().insert(event)
                        Toast.makeText(context, "Mental state saved", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            item {
                MentalSectionLabel("Content Awareness")
                ContentConsumptionCard(onSave = { type, impact ->
                    scope.launch {
                        db.lifeEventDao().insert(LifeEvent(
                            category = "MENTAL",
                            type = "CONTENT_CONSUMPTION",
                            content = "Consumed $type content. Perceived impact: $impact",
                            parameter1 = type,
                            parameter2 = impact
                        ))
                        Toast.makeText(context, "Observation recorded", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            item {
                MentalSectionLabel("Power of Mind")
                MindPowerCard()
            }
        }
    }
}

@Composable
fun MentalHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SelfImprovement, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Inner Focus", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Master your thoughts, master your life.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun MentalSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun MentalLoadCard(onSave: (Float) -> Unit) {
    var loadLevel by remember { mutableFloatStateOf(0.5f) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Current Mental Load", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Slider(
                value = loadLevel,
                onValueChange = { loadLevel = it },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                )
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Calm", style = MaterialTheme.typography.labelSmall)
                Text("Extorted", style = MaterialTheme.typography.labelSmall)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { onSave(loadLevel) },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save State")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentConsumptionCard(onSave: (String, String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Track Content Impact", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                }
            }
            
            AnimatedVisibility(visible = expanded) {
                Column {
                    Text("Watching content is not bad, but let's observe how it affects your peace.", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Text("Type of content:", style = MaterialTheme.typography.labelMedium)
                    val types = listOf("Educational", "Entertainment", "Social Media", "Intense/Vulgar")
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        types.forEach { type ->
                            SuggestionChip(onClick = { onSave(type, "Unknown") }, label = { Text(type) })
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Perceived Impact:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSave("General", "Positive") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))) { Text("Positive") }
                        Button(onClick = { onSave("General", "Negative") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))) { Text("Negative") }
                    }
                }
            }
        }
    }
}

@Composable
fun AwarenessSolutionCard(issue: String, description: String, solution: String, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(issue, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text("Solution:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            Text(solution, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onAction, modifier = Modifier.align(Alignment.End)) {
                Text("I will try this")
            }
        }
    }
}

@Composable
fun MindPowerCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Affirmation of the Day", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "\"I am the architect of my thoughts. I choose clarity over chaos.\"",
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }
    }
}

@Composable
private fun Icon(icon: ImageVector, contentDescription: String?, size: androidx.compose.ui.unit.Dp) {
    androidx.compose.material3.Icon(icon, contentDescription, modifier = Modifier.size(size))
}

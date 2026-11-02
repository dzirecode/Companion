package com.dzire.dzirecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.data.database.LifeEvent
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PhysicalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                PhysicalScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicalScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    val physicalEvents by db.lifeEventDao().getEventsByCategory("PHYSICAL").collectAsState(initial = emptyList())
    
    var steps by remember { mutableIntStateOf(6432) }
    var water by remember { mutableFloatStateOf(1.5f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Physical Well-being", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PhysicalOverviewCard()
            }

            item {
                Text("Daily Stats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                PhysicalStatRow(
                    label = "Steps",
                    value = "$steps",
                    goal = "10,000",
                    icon = Icons.Default.DirectionsRun,
                    progress = steps / 10000f,
                    onAdd = {
                        steps += 500
                        scope.launch {
                            db.lifeEventDao().insert(LifeEvent(
                                category = "PHYSICAL",
                                type = "STEPS_UPDATE",
                                content = "Steps updated to $steps",
                                parameter1 = steps.toString(),
                                numericValue = steps.toDouble(),
                                unit = "steps"
                            ))
                        }
                    }
                )
            }

            item {
                PhysicalStatRow(
                    label = "Water",
                    value = "${"%.1f".format(water)}L",
                    goal = "3.0L",
                    icon = Icons.Default.WaterDrop,
                    progress = water / 3.0f,
                    onAdd = {
                        water += 0.25f
                        scope.launch {
                            db.lifeEventDao().insert(LifeEvent(
                                category = "PHYSICAL",
                                type = "WATER_UPDATE",
                                content = "Water intake updated to $water L",
                                parameter1 = water.toString(),
                                numericValue = water.toDouble(),
                                unit = "L"
                            ))
                        }
                    }
                )
            }

            item {
                Text("Activity Logs", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (physicalEvents.isEmpty()) {
                item {
                    Text("No activity logs yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(physicalEvents.take(10)) { event ->
                    PhysicalLogItem(event)
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Body Wisdom", fontWeight = FontWeight.Bold)
                        Text(
                            "Your physical health is the container for your consciousness. Honor it with movement and hydration.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhysicalLogItem(event: LifeEvent) {
    val date = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(event.timestamp))
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (event.type == "STEPS_UPDATE") Icons.Default.DirectionsRun else Icons.Default.WaterDrop,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = event.content, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.weight(1f))
            Text(text = date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PhysicalOverviewCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FitnessCenter, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Health Foundation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Building a resilient body.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun PhysicalStatRow(
    label: String, 
    value: String, 
    goal: String, 
    icon: ImageVector, 
    progress: Float,
    onAdd: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(label, fontWeight = FontWeight.Bold)
                    Text("$value / $goal", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    }
}

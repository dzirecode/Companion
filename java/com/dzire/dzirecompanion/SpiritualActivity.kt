package com.dzire.dzirecompanion

import android.os.Bundle
import android.widget.Toast
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
import androidx.compose.ui.graphics.Color
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

class SpiritualActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                SpiritualScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpiritualScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    val reflections by db.lifeEventDao().getEventsByCategory("SPIRITUAL").collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spiritual Evolution", fontWeight = FontWeight.Bold) },
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
            item { SpiritualHeader() }

            item {
                SpiritualSectionLabel("Moral Compass")
                MoralCompassCard(onAction = { alignment ->
                    scope.launch {
                        val event = LifeEvent(
                            category = "SPIRITUAL",
                            type = "MORAL_ALIGNMENT",
                            content = "User felt their actions were $alignment with their values.",
                            parameter1 = alignment
                        )
                        db.lifeEventDao().insert(event)
                        Toast.makeText(context, "Reflection saved to your journey", Toast.LENGTH_SHORT).show()
                    }
                })
            }

            item {
                SpiritualSectionLabel("Practices")
                PracticeCard(
                    title = "The Power of Silence",
                    description = "Spend 5 minutes in complete stillness to reconnect with your inner self.",
                    icon = Icons.Default.Timer
                )
            }

            item {
                SpiritualSectionLabel("Reflection History")
            }

            if (reflections.isEmpty()) {
                item {
                    Text("No reflections recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(reflections) { reflection ->
                    ReflectionHistoryItem(reflection)
                }
            }

            item {
                SpiritualSectionLabel("Spiritual Guidance")
                SpiritualGuidanceCard(
                    "On Right & Wrong",
                    "Right is what brings you and others closer to peace. Wrong is what creates a wall of ego or suffering. Trust your inner vibration."
                )
            }
        }
    }
}

@Composable
fun ReflectionHistoryItem(event: LifeEvent) {
    val date = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(event.timestamp))
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (event.parameter1 == "ALIGNED") Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (event.parameter1 == "ALIGNED") Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (event.parameter1 == "ALIGNED") "Aligned with Values" else "Misaligned with Values",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(text = date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SpiritualHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Soul Evolution", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Your journey toward the infinite self.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun SpiritualSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun MoralCompassCard(onAction: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Daily Reflection: Intentions", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Did my actions today align with my highest values?", style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onAction("ALIGNED") },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                ) {
                    Text("Aligned")
                }
                OutlinedButton(
                    onClick = { onAction("MISALIGNED") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Misaligned")
                }
            }
        }
    }
}

@Composable
fun PracticeCard(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun SpiritualGuidanceCard(title: String, description: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

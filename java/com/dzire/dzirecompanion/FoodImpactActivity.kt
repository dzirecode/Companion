package com.dzire.dzirecompanion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.data.database.LifeEvent
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme
import kotlinx.coroutines.launch

class FoodImpactActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                FoodImpactScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodImpactScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Food & Vitality", fontWeight = FontWeight.Bold) },
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
            item { FoodHeader() }

            item {
                FoodSectionLabel("Daily Intake Impact")
                FoodIntakeCard(onFeelSelected = { feel ->
                    scope.launch {
                        val event = LifeEvent(
                            category = "FOOD",
                            type = "MEAL_FEELING",
                            content = "User felt $feel after their meal.",
                            parameter1 = feel
                        )
                        db.lifeEventDao().insert(event)
                        Toast.makeText(context, "Nutritional reflection saved", Toast.LENGTH_SHORT).show()
                    }
                })
            }


        }
    }
}

@Composable
fun FoodHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Restaurant, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Nutritional Path", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Fueling your physical and soul journey.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun FoodSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun FoodIntakeCard(onFeelSelected: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("How did your last meal make you feel?", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealFeelButton("Energetic", Color(0xFF4CAF50), Modifier.weight(1f), onClick = { onFeelSelected("Energetic") })
                MealFeelButton("Drowsy", Color(0xFFFF9800), Modifier.weight(1f), onClick = { onFeelSelected("Drowsy") })
                MealFeelButton("Heavy", Color(0xFFF44336), Modifier.weight(1f), onClick = { onFeelSelected("Heavy") })
            }
        }
    }
}

@Composable
fun MealFeelButton(label: String, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.15f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = color,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}


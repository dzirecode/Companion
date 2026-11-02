package com.dzire.dzirecompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme

class DiscoveryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                DiscoveryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(onBack: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val insights = listOf(
        InsightQuestion("How are you feeling today?", listOf("Energetic", "Calm", "Tired", "Anxious")),
        InsightQuestion("What's your primary goal this week?", listOf("Focus", "Rest", "Learning", "Socializing")),
        InsightQuestion("Which area needs more attention?", listOf("Mind", "Body", "Spirit", "Work")),
        InsightQuestion("How did you handle your last challenge?", listOf("With Grace", "Stressed", "Avoided it", "Asked for help")),
        InsightQuestion("What are you most grateful for right now?", listOf("Family", "Health", "Growth", "Silence"))
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Self-Discovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (step < insights.size) {
                val current = insights[step]
                
                LinearProgressIndicator(
                    progress = { (step + 1).toFloat() / insights.size },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Icon(
                    Icons.Default.Lightbulb, 
                    contentDescription = null, 
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = current.question, 
                    style = MaterialTheme.typography.headlineSmall, 
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    current.options.forEach { option ->
                        Button(
                            onClick = { step++ },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text(option, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            } else {
                Text(
                    "Insight Complete", 
                    style = MaterialTheme.typography.headlineMedium, 
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Your companion is now better equipped to support your unique journey.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Return to Dashboard")
                }
            }
        }
    }
}

data class InsightQuestion(val question: String, val options: List<String>)

package com.dzire.dzirecompanion.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.*
import com.dzire.dzirecompanion.content.DynamicContent
import com.dzire.dzirecompanion.content.DynamicContentRepository
import com.dzire.dzirecompanion.billing.PlayBillingManager
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.data.database.LifeEvent
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme
import kotlinx.coroutines.launch

@Composable
fun CompanionDashboard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    val contentRepository = remember { DynamicContentRepository() }
    val billingManager = remember { PlayBillingManager(context) }
    val billingState by billingManager.state.collectAsState()
    val events by db.lifeEventDao().getAllEvents().collectAsState(initial = emptyList())
    val latest = events.firstOrNull()
    val contentTags = remember(latest) {
        buildSet {
            latest?.category?.uppercase()?.let(::add)
            latest?.type?.uppercase()?.let(::add)
            latest?.parameter1?.uppercase()?.let(::add)
        }
    }
    val quoteFlow = remember(contentTags, billingState.isPremium) {
        contentRepository.observeQuotes(contentTags, billingState.isPremium)
    }
    val adviceFlow = remember(contentTags) {
        contentRepository.observeAdvice(contentTags, true)
    }
    val quote by quoteFlow.collectAsState(initial = null)
    val advice by adviceFlow.collectAsState(initial = null)
    var showMoodDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Companion", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("Lifelong Journey of Growth", style = MaterialTheme.typography.bodyMedium)
                    }
                },
                actions = {
                    IconButton(onClick = { /* Settings */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { RemoteWisdomCard(quote) }

            item {
                if (billingState.isPremium) {
                    RemoteAdviceCard(advice)
                } else {
                    PremiumTeaserCard(
                        onClick = { context.startActivity(Intent(context, SubscriptionActivity::class.java)) }
                    )
                }
            }

            item {
                SectionHeader("Life Pillars")
                LifePillarsGrid(
                    onPillarClick = { label ->
                        val intent = when (label) {
                            "Psychology" -> Intent(context, PsychologyActivity::class.java)
                            "Physical" -> Intent(context, PhysicalActivity::class.java)
                            "Mental" -> Intent(context, MentalActivity::class.java)
                            "Spiritual" -> Intent(context, SpiritualActivity::class.java)
                            "Food" -> Intent(context, FoodImpactActivity::class.java)
                            "Education" -> Intent(context, EducationActivity::class.java)
                            else -> null
                        }
                        intent?.let { context.startActivity(it) }
                    }
                )
            }

            item {
                SectionHeader("Daily Reflection")
                DailyReflectionRow(
                    onReflectionClick = { type ->
                        when (type) {
                            "Mood" -> showMoodDialog = true
                            "Activity" -> context.startActivity(Intent(context, PhysicalActivity::class.java))
                            "Thoughts" -> context.startActivity(Intent(context, PsychologyActivity::class.java))
                        }
                    }
                )
            }

            item {
                SectionHeader("Discovery & Insights")
                DiscoveryInsightCard()
            }

            item {
                SectionHeader("Impact & Awareness")
                AwarenessCard(
                    title = "Activity Impact",
                    description = "Analyze how your current actions affect your long-term peace and goals.",
                    icon = Icons.Default.BarChart
                )
            }

            item {
                SectionHeader("Daily Habits")
            }

            items(sampleHabits) { habit ->
                HabitItem(habit)
            }
        }
    }

    if (showMoodDialog) {
        MoodEntryDialog(
            onDismiss = { showMoodDialog = false },
            onMoodSelected = { mood ->
                scope.launch {
                    db.lifeEventDao().insert(LifeEvent(
                        category = "MOOD",
                        type = "DAILY_COLLECTION",
                        content = "User reported feeling $mood",
                        parameter1 = mood
                    ))
                    showMoodDialog = false
                    Toast.makeText(context, "Mood captured", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun MoodEntryDialog(onDismiss: () -> Unit, onMoodSelected: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How are you feeling?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Happy", "Calm", "Anxious", "Sad", "Angry").forEach { mood ->
                    Button(
                        onClick = { onMoodSelected(mood) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(mood)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun LifePillarsGrid(onPillarClick: (String) -> Unit) {
    val pillars = listOf(
        Pillar("Psychology", Icons.Default.Psychology, Color(0xFFE1BEE7)),
        Pillar("Physical", Icons.Default.FitnessCenter, Color(0xFFC8E6C9)),
        Pillar("Mental", Icons.Default.SelfImprovement, Color(0xFFBBDEFB)),
        Pillar("Spiritual", Icons.Default.AutoAwesome, Color(0xFFFFF9C4)),
        Pillar("Food", Icons.Default.Restaurant, Color(0xFFF8BBD0)),
        Pillar("Education", Icons.Default.School, Color(0xFFFFE0B2))
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        pillars.chunked(3).forEach { rowPillars ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowPillars.forEach { pillar ->
                    PillarCard(pillar, Modifier.weight(1f), onClick = { onPillarClick(pillar.label) })
                }
            }
        }
    }
}

data class Pillar(val label: String, val icon: ImageVector, val color: Color)

@Composable
fun PillarCard(pillar: Pillar, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = pillar.color.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(pillar.icon, contentDescription = null, modifier = Modifier.size(28.dp), tint = pillar.color)
            Spacer(modifier = Modifier.height(8.dp))
            Text(pillar.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DailyReflectionRow(onReflectionClick: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReflectionItem("Mood", Icons.Default.Mood, Modifier.weight(1f), onClick = { onReflectionClick("Mood") })
        ReflectionItem("Activity", Icons.AutoMirrored.Filled.ListAlt, Modifier.weight(1f), onClick = { onReflectionClick("Activity") })
        ReflectionItem("Thoughts", Icons.Default.ChatBubbleOutline, Modifier.weight(1f), onClick = { onReflectionClick("Thoughts") })
    }
}

@Composable
fun ReflectionItem(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedCard(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun RemoteWisdomCard(content: DynamicContent?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(12.dp))
            if (content == null) {
                Text("Your daily wisdom is loading…", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Content is managed remotely and can change without an app update.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(content.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                if (content.author.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("- ${content.author}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
                }
                if (content.title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(content.title, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun RemoteAdviceCard(content: DynamicContent?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Advice for you", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
            Text(content?.text ?: "No new advice is available right now.", style = MaterialTheme.typography.bodyLarge)
            if (!content?.title.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(content!!.title, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
fun PremiumTeaserCard(onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Dzire Premium", fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text("Unlock remotely managed personalized advice and deeper companion guidance.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onClick) {
                Text("View Premium")
                Spacer(Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryInsightCard() {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    val insights = listOf(
        InsightQuestion("How are you feeling today?", listOf("Energetic", "Calm", "Tired", "Anxious")),
        InsightQuestion("What's your primary goal this week?", listOf("Focus", "Rest", "Learning", "Socializing")),
        InsightQuestion("Which area needs more attention?", listOf("Mind", "Body", "Spirit", "Work"))
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Self-Discovery", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.weight(1f))
                Text("${if (step < insights.size) step + 1 else insights.size}/${insights.size}", style = MaterialTheme.typography.labelSmall)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (step < insights.size) {
                val current = insights[step]
                Text(current.question, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    current.options.forEach { option ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    if (step < insights.size) step++ 
                                    
                                    // The selected option is stored as user data by the next event capture.
                                    // Content selection itself is remote and data-driven; no quote/advice mapping is hardcoded here.
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            } else {
                Text("Information gathered. Let's analyze your path.", style = MaterialTheme.typography.bodyMedium)
                
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { 
                            val intent = Intent(context, InsightsActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Get Insights")
                    }
                    
                    OutlinedButton(
                        onClick = { 
                            val intent = Intent(context, DiscoveryActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Restart")
                    }
                }
            }
        }
    }
}

data class InsightQuestion(val question: String, val options: List<String>)

@Composable
fun AwarenessCard(title: String, description: String, icon: ImageVector) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

data class Habit(val name: String, val progress: Float, val icon: ImageVector)

val sampleHabits = listOf(
    Habit("Meditation", 0.7f, Icons.Default.SelfImprovement),
    Habit("Reflection", 0.4f, Icons.Default.EditNote),
    Habit("Healthy Eating", 0.6f, Icons.Default.Restaurant)
)

@Composable
fun HabitItem(habit: Habit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(habit.icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(habit.name, fontWeight = FontWeight.Medium)
            LinearProgressIndicator(
                progress = { habit.progress },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text("${(habit.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
    }
}

@Preview(showBackground = true)
@Composable
fun CompanionDashboardPreview() {
    DzireCompanionTheme {
        CompanionDashboard()
    }
}

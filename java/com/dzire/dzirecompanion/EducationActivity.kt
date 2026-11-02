package com.dzire.dzirecompanion

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dzire.dzirecompanion.data.database.AppDatabase
import com.dzire.dzirecompanion.data.database.LifeEvent
import com.dzire.dzirecompanion.ui.theme.DzireCompanionTheme
import kotlinx.coroutines.launch

class EducationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DzireCompanionTheme {
                EducationScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EducationScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getDatabase(context) }
    
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lifelong Education", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Learning")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { EducationHeader() }

            item {
                EducationSectionLabel("Learning Path")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Knowledge is Power", fontWeight = FontWeight.Bold)
                        Text("Every skill you acquire opens a new door in your lifelong journey.")
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        LearningEntryDialog(
            onDismiss = { showAddDialog = false },
            onSave = { topic ->
                scope.launch {
                    db.lifeEventDao().insert(LifeEvent(
                        category = "EDUCATION",
                        type = "NEW_SKILL",
                        content = "User started learning: $topic",
                        parameter1 = topic,
                        source = "USER"
                    ))
                    showAddDialog = false
                    Toast.makeText(context, "Skill added to your path", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
fun EducationHeader() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("Continuous Learning", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Expand your mind, forever.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun EducationSectionLabel(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
fun LearningEntryDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What are you learning?") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Meditation, Coding, Ethics...") }
            )
        },
        confirmButton = {
            Button(onClick = { if (text.isNotBlank()) onSave(text) }) {
                Text("Add to Path")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

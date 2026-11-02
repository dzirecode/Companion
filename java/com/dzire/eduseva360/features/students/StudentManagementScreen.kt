package com.dzire.eduseva360.features.students

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentManagementScreen(
    onBack: () -> Unit,
    viewModel: StudentViewModel = hiltViewModel()
) {
    val students by viewModel.students.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val operationMessage by viewModel.operationMessage.collectAsState()
    var statusFilter by remember { mutableStateOf("ALL") }
    var query by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Student?>(null) }
    var selected by remember { mutableStateOf<Student?>(null) }

    val filtered = students.filter {
        query.isBlank() || it.fullName.contains(query, true) ||
            it.admissionNumber.contains(query, true) || it.className.contains(query, true) ||
            it.guardianName.contains(query, true)
    }.filter { statusFilter == "ALL" || it.status.name == statusFilter }
    val context = LocalContext.current
    val studentCsvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("Student ID,Full Name,Admission Number,Class,Section,Guardian Name,Guardian Phone,Status\r\n")
                    filtered.forEach { student ->
                        val cells = listOf(student.id, student.fullName, student.admissionNumber, student.className, student.section, student.guardianName, student.guardianPhone, student.status.name)
                        writer.write(cells.joinToString(",") { studentCsvCell(it) } + "\r\n")
                    }
                } ?: error("Unable to open the selected file.")
            }.onFailure { android.widget.Toast.makeText(context, "Student export failed: ${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
             .onSuccess { android.widget.Toast.makeText(context, "Exported ${filtered.size} student record(s).", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Student Management") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = null; showForm = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add student")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = null; showForm = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add student") }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(8.dp))
            Text("${students.size} students registered", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { viewModel.refresh() }, contentPadding = PaddingValues(0.dp)) {
                    Text("Refresh from Firebase")
                }
                TextButton(onClick = { studentCsvLauncher.launch("eduseva_students.csv") }, enabled = filtered.isNotEmpty()) {
                    Text("Export CSV (${filtered.size})")
                }
            }
            operationMessage?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.clearOperationMessage() }) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text("Search name, admission no., class or guardian") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "ACTIVE" to "Active", "INACTIVE" to "Inactive").forEach { (value, label) ->
                    FilterChip(selected = statusFilter == value, onClick = { statusFilter = value }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            if (filtered.isEmpty()) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No students found", style = MaterialTheme.typography.titleMedium)
                        Text(if (students.isEmpty()) "Add a student or refresh records from Firebase." else "Try another search or status filter.")
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filtered, key = { it.id }) { student ->
                        Card(Modifier.fillMaxWidth().clickable { selected = student }) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(student.fullName, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(3.dp))
                                    Text("${student.admissionNumber} • ${student.className} ${student.section}")
                                    Spacer(Modifier.height(5.dp))
                                    AssistChip(onClick = {}, label = { Text(student.status.name) })
                                }
                                TextButton(onClick = { editing = student; showForm = true }) { Text("Edit") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        StudentFormDialog(
            existing = editing,
            onDismiss = { showForm = false },
            onSave = { viewModel.save(it); showForm = false }
        )
    }

    selected?.let { student ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(student.fullName) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Admission No.: ${student.admissionNumber}")
                    Text("Class: ${student.className} - ${student.section}")
                    Text("Guardian: ${student.guardianName}")
                    Text("Phone: ${student.guardianPhone}")
                    Text("Status: ${student.status.name}")
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null; editing = student; showForm = true }) { Text("Edit") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentFormDialog(
    existing: Student?,
    onDismiss: () -> Unit,
    onSave: (Student) -> Unit
) {
    var name by remember(existing) { mutableStateOf(existing?.fullName.orEmpty()) }
    var admission by remember(existing) { mutableStateOf(existing?.admissionNumber.orEmpty()) }
    var className by remember(existing) { mutableStateOf(existing?.className.orEmpty()) }
    var section by remember(existing) { mutableStateOf(existing?.section.orEmpty()) }
    var guardian by remember(existing) { mutableStateOf(existing?.guardianName.orEmpty()) }
    var phone by remember(existing) { mutableStateOf(existing?.guardianPhone.orEmpty()) }
    var status by remember(existing) { mutableStateOf(existing?.status ?: StudentStatus.ACTIVE) }
    var statusMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add student" else "Edit student") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Full name *") }, singleLine = true)
                OutlinedTextField(admission, { admission = it; error = null }, label = { Text("Admission number *") }, singleLine = true)
                OutlinedTextField(className, { className = it }, label = { Text("Class *") }, singleLine = true)
                OutlinedTextField(section, { section = it }, label = { Text("Section") }, singleLine = true)
                OutlinedTextField(guardian, { guardian = it }, label = { Text("Guardian name") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Guardian phone") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = statusMenuExpanded, onExpandedChange = { statusMenuExpanded = !statusMenuExpanded }) {
                    OutlinedTextField(
                        value = status.name, onValueChange = {}, readOnly = true,
                        label = { Text("Student status") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = statusMenuExpanded, onDismissRequest = { statusMenuExpanded = false }) {
                        StudentStatus.values().forEach { option ->
                            DropdownMenuItem(text = { Text(option.name) }, onClick = { status = option; statusMenuExpanded = false })
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || admission.isBlank() || className.isBlank()) {
                    error = "Please fill in name, admission number and class."
                } else {
                    onSave(
                        Student(
                            id = existing?.id ?: "STU-${System.currentTimeMillis()}",
                            fullName = name.trim(),
                            admissionNumber = admission.trim(),
                            className = className.trim(),
                            section = section.trim().ifBlank { "-" },
                            guardianName = guardian.trim().ifBlank { "Not provided" },
                            guardianPhone = phone.trim().ifBlank { "Not provided" },
                            status = status
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}


private fun studentCsvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

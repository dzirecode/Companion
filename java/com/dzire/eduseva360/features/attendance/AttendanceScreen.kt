package com.dzire.eduseva360.features.attendance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dzire.eduseva360.features.students.StudentViewModel

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    onBack: () -> Unit,
    studentViewModel: StudentViewModel = hiltViewModel(),
    viewModel: AttendanceViewModel = hiltViewModel()
) {
    val allStudents by studentViewModel.students.collectAsState()
    val students = allStudents.filter { it.status.name == "ACTIVE" }
    val records by viewModel.records.collectAsState()
    val message by viewModel.message.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val selected = remember(records) { mutableStateMapOf<String, AttendanceStatus>().apply { records.forEach { row -> runCatching { put(row.studentId, AttendanceStatus.valueOf(row.status)) } } } }
    Scaffold(topBar = {
        TopAppBar(title = { Text("Daily Attendance") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh attendance") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Date: ${viewModel.attendanceDate}", style = MaterialTheme.typography.titleMedium)
            Text("Choose a status for each student, then save. Unmarked students are left unchanged.", style = MaterialTheme.typography.bodySmall)
            message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(12.dp))
            if (students.isEmpty()) {
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) { Text("No active student records available"); Text("Load student records before taking attendance.") } }
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(students, key = { it.id }) { student ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(student.fullName, fontWeight = FontWeight.SemiBold)
                                Text("${student.admissionNumber} • ${student.className} ${student.section}", style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    AttendanceStatus.values().forEach { status ->
                                        FilterChip(selected = selected[student.id] == status, onClick = { selected[student.id] = status }, label = { Text(status.name) })
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.save(students, selected.toMap()) }, enabled = !saving && selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                    if (saving) CircularProgressIndicator() else Text("Save attendance (${selected.size})")
                }
            }
        }
    }
}

package com.dzire.eduseva360.features.hostel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.dzire.eduseva360.features.students.Student
import com.dzire.eduseva360.features.students.StudentViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class HostelAssignment(val studentId: String, val studentName: String, val hostel: String, val room: String, val bed: String, val status: String)

@HiltViewModel
class WardenHostelViewModel @Inject constructor(private val auth: FirebaseAuth, private val firestore: FirebaseFirestore) : ViewModel() {
    var assignments by mutableStateOf<List<HostelAssignment>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    private var institutionId: String = ""
    private var uid: String = ""

    fun refresh() = viewModelScope.launch {
        loading = true
        try {
            val user = auth.currentUser ?: error("Please sign in again."); uid = user.uid
            val profile = firestore.collection("users").document(uid).get().await()
            check(profile.getBoolean("active") == true) { "Your account is missing or inactive." }
            val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>()?.map { it.uppercase() }.orEmpty()
            check(roles.any { it in setOf("WARDEN", "ADMIN", "INSTITUTION_ADMIN", "SUPER_ADMIN", "PRINCIPAL") }) { "Hostel management requires a warden or institution administrator account." }
            institutionId = profile.getString("institutionId").orEmpty().trim(); check(institutionId.isNotBlank()) { "Your account is not assigned to an institution." }
            val snapshot = firestore.collection("institutions").document(institutionId).collection("hostelAssignments").get().await()
            assignments = snapshot.documents.mapNotNull { d ->
                val id = d.getString("studentId") ?: d.id
                val name = d.getString("studentName") ?: return@mapNotNull null
                HostelAssignment(id, name, d.getString("hostelName").orEmpty(), d.getString("roomNumber").orEmpty(), d.getString("bedNumber").orEmpty(), d.getString("status") ?: "ACTIVE")
            }.sortedBy { it.studentName.lowercase() }
            message = "Loaded ${assignments.size} hostel assignment(s)."
        } catch (e: Exception) { message = e.message ?: "Unable to load hostel assignments." }
        finally { loading = false }
    }

    fun save(student: Student, hostel: String, room: String, bed: String, status: String) = viewModelScope.launch {
        try {
            check(institutionId.isNotBlank() && uid.isNotBlank()) { "Refresh account context first." }
            require(hostel.isNotBlank() && room.isNotBlank()) { "Hostel name and room number are required." }
            val payload = mapOf("studentId" to student.id, "studentName" to student.fullName, "institutionId" to institutionId,
                "hostelName" to hostel.trim(), "roomNumber" to room.trim(), "bedNumber" to bed.trim(), "status" to status,
                "updatedAt" to FieldValue.serverTimestamp(), "updatedBy" to uid)
            firestore.collection("institutions").document(institutionId).collection("hostelAssignments").document(student.id).set(payload).await()
            message = "Hostel assignment saved for ${student.fullName}."; refresh()
        } catch (e: Exception) { message = e.message ?: "Unable to save assignment. Check Firestore rules." }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WardenHostelScreen(onBack: () -> Unit, studentViewModel: StudentViewModel = hiltViewModel(), viewModel: WardenHostelViewModel = hiltViewModel()) {
    val students by studentViewModel.students.collectAsState()
    val assignments = viewModel.assignments; val message = viewModel.message; val loading = viewModel.loading
    var selectedId by remember { mutableStateOf("") }; var hostel by remember { mutableStateOf("") }; var room by remember { mutableStateOf("") }; var bed by remember { mutableStateOf("") }; var status by remember { mutableStateOf("ACTIVE") }
    LaunchedEffect(Unit) { viewModel.refresh(); studentViewModel.refresh() }
    val selectedStudent = students.firstOrNull { it.id == selectedId }
    Scaffold(topBar = { TopAppBar(title = { Text("Warden • Hostel Management") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Assign a student to a hostel and room", style = MaterialTheme.typography.titleMedium)
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(value = selectedStudent?.let { "${it.fullName} • ${it.admissionNumber}" } ?: "Select student", onValueChange = {}, readOnly = true, label = { Text("Student") }, modifier = Modifier.fillMaxWidth().menuAnchor())
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    students.filter { it.status.name == "ACTIVE" }.forEach { student -> DropdownMenuItem(text = { Text("${student.fullName} • ${student.admissionNumber}") }, onClick = { selectedId = student.id; expanded = false }) }
                }
            }
            OutlinedTextField(hostel, { hostel = it }, label = { Text("Hostel name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(room, { room = it }, label = { Text("Room number") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(bed, { bed = it }, label = { Text("Bed (optional)") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = status == "ACTIVE", onClick = { status = "ACTIVE" }, label = { Text("Active") })
                FilterChip(selected = status == "VACATED", onClick = { status = "VACATED" }, label = { Text("Vacated") })
            }
            Button(onClick = { selectedStudent?.let { viewModel.save(it, hostel, room, bed, status) } }, enabled = selectedStudent != null && hostel.isNotBlank() && room.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save hostel assignment") }
            Divider()
            Text("Current assignments (${assignments.size})", style = MaterialTheme.typography.titleMedium)
            if (loading) CircularProgressIndicator()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(assignments, key = { it.studentId }) { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(item.studentName, fontWeight = FontWeight.SemiBold)
                    Text("${item.hostel} • Room ${item.room}" + if (item.bed.isBlank()) "" else " • Bed ${item.bed}")
                    Text("Status: ${item.status}", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { selectedId = item.studentId; hostel = item.hostel; room = item.room; bed = item.bed; status = item.status }) { Text("Edit assignment") }
                } } }
            }
        }
    }
}

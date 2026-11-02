package com.dzire.eduseva360.features.parents

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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ParentStudentSummary(
    val id: String, val name: String, val admission: String, val className: String,
    val attendanceSummary: String
)

@HiltViewModel
class ParentLinkedStudentsViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {
    var students by mutableStateOf<List<ParentStudentSummary>>(emptyList())
        private set
    var message by mutableStateOf("Load linked student records to begin.")
        private set
    var loading by mutableStateOf(false)
        private set

    fun refresh() = viewModelScope.launch {
        loading = true
        try {
            val user = auth.currentUser ?: error("Please sign in again.")
            val profile = firestore.collection("users").document(user.uid).get().await()
            check(profile.getBoolean("active") == true) { "Your account is missing or inactive." }
            val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>()?.map { it.uppercase() }.orEmpty()
            check("PARENT" in roles) { "This screen is available only to parent accounts." }
            val institutionId = profile.getString("institutionId").orEmpty().trim()
            check(institutionId.isNotBlank()) { "Your parent account is not assigned to an institution." }
            val linkedIds = (profile.get("linkedStudentIds") as? List<*>)?.filterIsInstance<String>().orEmpty().distinct()
            check(linkedIds.isNotEmpty()) { "No students are linked to this parent account yet. Ask the institution administrator to link the student IDs." }
            val result = mutableListOf<ParentStudentSummary>()
            linkedIds.forEach { studentId ->
                val doc = firestore.collection("institutions").document(institutionId).collection("students").document(studentId).get().await()
                if (doc.exists()) {
                    val attendance = firestore.collection("institutions").document(institutionId).collection("attendance")
                        .whereEqualTo("studentId", studentId).get().await().documents
                    val present = attendance.count { it.getString("status") == "PRESENT" }
                    val absent = attendance.count { it.getString("status") == "ABSENT" }
                    result += ParentStudentSummary(studentId, doc.getString("fullName").orEmpty(), doc.getString("admissionNumber").orEmpty(),
                        listOf(doc.getString("className").orEmpty(), doc.getString("section").orEmpty()).filter { it.isNotBlank() }.joinToString(" - "),
                        "Attendance records: ${attendance.size} • Present: $present • Absent: $absent")
                }
            }
            students = result
            message = if (result.isEmpty()) "Linked student IDs were not found in this institution." else "Showing ${result.size} linked student(s)."
        } catch (e: Exception) { message = e.message ?: "Unable to load linked students. Check account links and Firestore rules." }
        finally { loading = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentLinkedStudentsScreen(onBack: () -> Unit, viewModel: ParentLinkedStudentsViewModel = hiltViewModel()) {
    val students = viewModel.students
    val message = viewModel.message
    val loading = viewModel.loading
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(topBar = { TopAppBar(title = { Text("Parent Portal") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { IconButton(onClick = { viewModel.refresh() }) { Icon(Icons.Default.Refresh, "Refresh") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Linked student overview", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp)); Text(message, style = MaterialTheme.typography.bodyMedium)
            if (loading) { Spacer(Modifier.height(12.dp)); CircularProgressIndicator() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
                items(students, key = { it.id }) { student ->
                    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(student.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                        Text("Admission: ${student.admission}")
                        Text("Class: ${student.className}")
                        Text(student.attendanceSummary, style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
        }
    }
}

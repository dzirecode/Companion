package com.dzire.eduseva360.features.academics

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
import com.dzire.eduseva360.core.model.UserRole
import com.dzire.eduseva360.features.auth.AuthState
import com.dzire.eduseva360.features.auth.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.DateFormat
import java.util.Date

private data class TimetableItem(val id: String, val day: String, val className: String, val subject: String, val teacher: String, val start: String, val end: String, val room: String)
private data class ExamItem(val id: String, val title: String, val className: String, val subject: String, val date: String, val maxMarks: Long)
private data class ResultItem(val id: String, val studentId: String, val examTitle: String, val subject: String, val marks: Double, val maxMarks: Double, val published: Boolean, val updatedAt: Long)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcademicRecordsScreen(onBack: () -> Unit, authViewModel: AuthViewModel) {
    val authState by authViewModel.state.collectAsState()
    val session = (authState as? AuthState.LoggedIn)?.session
    val institutionId = session?.institutionId.orEmpty()
    val roles = session?.roles.orEmpty()
    val canManage = roles.any { it in setOf(UserRole.SUPER_ADMIN, UserRole.INSTITUTION_ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER) }
    val isParent = UserRole.PARENT in roles
    val isStudent = UserRole.STUDENT in roles
    val db = remember { FirebaseFirestore.getInstance() }
    var tab by remember { mutableIntStateOf(0) }
    var timetable by remember { mutableStateOf<List<TimetableItem>>(emptyList()) }
    var exams by remember { mutableStateOf<List<ExamItem>>(emptyList()) }
    var results by remember { mutableStateOf<List<ResultItem>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var day by remember { mutableStateOf("Monday") }
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("09:45") }
    var room by remember { mutableStateOf("") }
    var examTitle by remember { mutableStateOf("") }
    var examDate by remember { mutableStateOf("") }
    var maxMarks by remember { mutableStateOf("100") }
    var studentId by remember { mutableStateOf("") }
    var resultExam by remember { mutableStateOf("") }
    var resultSubject by remember { mutableStateOf("") }
    var marks by remember { mutableStateOf("") }
    var resultMaxMarks by remember { mutableStateOf("100") }

    fun refresh() {
        if (institutionId.isBlank()) { message = "Your account is not assigned to an institution."; return }
        loading = true
        val base = db.collection("institutions").document(institutionId)
        base.collection("timetable").orderBy("dayOrder").get()
            .addOnSuccessListener { snap -> timetable = snap.documents.map { d -> TimetableItem(d.id, d.getString("day").orEmpty(), d.getString("className").orEmpty(), d.getString("subject").orEmpty(), d.getString("teacher").orEmpty(), d.getString("startTime").orEmpty(), d.getString("endTime").orEmpty(), d.getString("room").orEmpty()) } }
            .addOnFailureListener { message = "Timetable load failed: ${it.localizedMessage ?: "check Firestore rules"}" }
        base.collection("exams").orderBy("examDate").get()
            .addOnSuccessListener { snap -> exams = snap.documents.map { d -> ExamItem(d.id, d.getString("title").orEmpty(), d.getString("className").orEmpty(), d.getString("subject").orEmpty(), d.getString("examDate").orEmpty(), d.getLong("maxMarks") ?: 100L) } }
            .addOnFailureListener { message = "Exam schedule load failed: ${it.localizedMessage ?: "check Firestore rules"}" }
        val resultCollection = base.collection("academicResults")
        fun applyResultSnapshot(snapshot: com.google.firebase.firestore.QuerySnapshot) {
            results = snapshot.documents.mapNotNull { d ->
                ResultItem(d.id, d.getString("studentId").orEmpty(), d.getString("examTitle").orEmpty(), d.getString("subject").orEmpty(), d.getDouble("marks") ?: 0.0, d.getDouble("maxMarks") ?: 100.0, d.getBoolean("published") ?: false, d.getLong("updatedAt") ?: 0L)
            }
            loading = false
        }
        if (canManage) {
            resultCollection.orderBy("updatedAt", Query.Direction.DESCENDING).get()
                .addOnSuccessListener { applyResultSnapshot(it) }
                .addOnFailureListener { loading = false; message = "Results load failed: ${it.localizedMessage ?: "check Firestore rules"}" }
        } else if (isParent || isStudent) {
            db.collection("users").document(session?.userId.orEmpty()).get()
                .addOnSuccessListener { profile ->
                    val linkedIds = if (isParent) (profile.get("linkedStudentIds") as? List<*>)?.filterIsInstance<String>().orEmpty() else listOfNotNull(profile.getString("studentId"))
                    if (linkedIds.isEmpty()) {
                        results = emptyList(); loading = false
                        message = if (isParent) "No linked students found on this parent profile. Ask an administrator to configure linkedStudentIds." else "Your profile is not linked to a student record yet. Ask an administrator to configure studentId."
                    } else {
                        resultCollection.whereEqualTo("published", true).whereIn("studentId", linkedIds.take(30)).get()
                            .addOnSuccessListener { applyResultSnapshot(it) }
                            .addOnFailureListener { loading = false; message = "Published results load failed: ${it.localizedMessage ?: "check Firestore rules and student linkage"}" }
                    }
                }
                .addOnFailureListener { loading = false; message = "Unable to read your student linkage: ${it.localizedMessage ?: "check user profile"}" }
        } else {
            results = emptyList(); loading = false
        }
    }

    LaunchedEffect(institutionId, session?.userId) { refresh() }
    Scaffold(topBar = { TopAppBar(title = { Text("Timetable & Academics") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, actions = { IconButton(onClick = { refresh() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                listOf("Timetable", "Exams", "Results").forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
            }
            LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tab == 0) {
                    if (canManage) item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add timetable period", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            var dayExpanded by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = it }) {
                                OutlinedTextField(value = day, onValueChange = {}, readOnly = true, label = { Text("Day") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
                                ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) { listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { day = option; dayExpanded = false }) } }
                            }
                            OutlinedTextField(className, { className = it.take(40) }, label = { Text("Class / section (e.g. 8-A)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(subject, { subject = it.take(60) }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(teacher, { teacher = it.take(80) }, label = { Text("Teacher") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(start, { start = it.take(5) }, label = { Text("Start HH:mm") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(end, { end = it.take(5) }, label = { Text("End HH:mm") }, modifier = Modifier.weight(1f), singleLine = true) }
                            OutlinedTextField(room, { room = it.take(40) }, label = { Text("Room (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(enabled = !loading && className.isNotBlank() && subject.isNotBlank() && teacher.isNotBlank() && start.matches(Regex("\\d{2}:\\d{2}")) && end.matches(Regex("\\d{2}:\\d{2}")), onClick = {
                                val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday").indexOf(day)
                                val payload = mapOf("day" to day, "dayOrder" to dayOrder, "className" to className.trim(), "subject" to subject.trim(), "teacher" to teacher.trim(), "startTime" to start, "endTime" to end, "room" to room.trim(), "institutionId" to institutionId, "createdBy" to session?.userId.orEmpty(), "updatedAt" to System.currentTimeMillis())
                                loading = true
                                db.collection("institutions").document(institutionId).collection("timetable").add(payload).addOnSuccessListener { className = ""; subject = ""; teacher = ""; room = ""; message = "Timetable period saved."; refresh() }.addOnFailureListener { loading = false; message = "Save failed: ${it.localizedMessage ?: "check rules"}" }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Save timetable period") }
                        } }
                    }
                    item { Text("Weekly timetable", style = MaterialTheme.typography.titleLarge) }
                    if (timetable.isEmpty()) item { Text("No timetable periods found yet.") }
                    items(timetable, key = { it.id }) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text("${row.day} • ${row.start}–${row.end}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary); Text(row.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("Class ${row.className} • ${row.teacher}"); if (row.room.isNotBlank()) Text("Room: ${row.room}") } } }
                }
                if (tab == 1) {
                    if (canManage) item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Add examination", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(examTitle, { examTitle = it.take(100) }, label = { Text("Exam name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(className, { className = it.take(40) }, label = { Text("Class / section") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(subject, { subject = it.take(60) }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(examDate, { examDate = it.take(10) }, label = { Text("Exam date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(maxMarks, { maxMarks = it.filter(Char::isDigit).take(5) }, label = { Text("Maximum marks") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Button(enabled = examTitle.isNotBlank() && className.isNotBlank() && subject.isNotBlank() && examDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && (maxMarks.toLongOrNull() ?: 0) > 0, onClick = {
                                val payload = mapOf("title" to examTitle.trim(), "className" to className.trim(), "subject" to subject.trim(), "examDate" to examDate, "maxMarks" to (maxMarks.toLongOrNull() ?: 100L), "institutionId" to institutionId, "createdBy" to session?.userId.orEmpty(), "createdAt" to System.currentTimeMillis())
                                loading = true
                                db.collection("institutions").document(institutionId).collection("exams").add(payload).addOnSuccessListener { examTitle = ""; examDate = ""; message = "Exam saved."; refresh() }.addOnFailureListener { loading = false; message = "Save failed: ${it.localizedMessage ?: "check rules"}" }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Save examination") }
                        } }
                    }
                    item { Text("Examination schedule", style = MaterialTheme.typography.titleLarge) }
                    if (exams.isEmpty()) item { Text("No examinations scheduled yet.") }
                    items(exams, key = { it.id }) { exam -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(exam.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${exam.subject} • Class ${exam.className}"); Text("Date: ${exam.date} • Maximum marks: ${exam.maxMarks}") } } }
                }
                if (tab == 2) {
                    if (canManage) item {
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Enter student result", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("Use the exact student document ID from the institution's students collection. Results remain unpublished until reviewed.", style = MaterialTheme.typography.bodySmall)
                            OutlinedTextField(studentId, { studentId = it.take(128) }, label = { Text("Student document ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(resultExam, { resultExam = it.take(100) }, label = { Text("Exam name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(resultSubject, { resultSubject = it.take(60) }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedTextField(marks, { marks = it.take(8) }, label = { Text("Marks earned") }, modifier = Modifier.weight(1f), singleLine = true); OutlinedTextField(resultMaxMarks, { resultMaxMarks = it.take(8) }, label = { Text("Max marks") }, modifier = Modifier.weight(1f), singleLine = true) }
                            Button(enabled = studentId.isNotBlank() && resultExam.isNotBlank() && resultSubject.isNotBlank() && (marks.toDoubleOrNull() ?: -1.0) >= 0 && (resultMaxMarks.toDoubleOrNull() ?: 0.0) > 0 && (marks.toDoubleOrNull() ?: 0.0) <= (resultMaxMarks.toDoubleOrNull() ?: 0.0), onClick = {
                                val sid = studentId.trim()
                                val payload = mapOf("studentId" to sid, "examTitle" to resultExam.trim(), "subject" to resultSubject.trim(), "marks" to (marks.toDoubleOrNull() ?: 0.0), "maxMarks" to (resultMaxMarks.toDoubleOrNull() ?: 100.0), "published" to false, "institutionId" to institutionId, "updatedBy" to session?.userId.orEmpty(), "updatedAt" to System.currentTimeMillis())
                                loading = true
                                db.collection("institutions").document(institutionId).collection("academicResults").add(payload).addOnSuccessListener { studentId = ""; resultExam = ""; resultSubject = ""; marks = ""; message = "Result saved as a draft. Review it, then use Publish result to make it visible to linked accounts."; refresh() }.addOnFailureListener { loading = false; message = "Save failed: ${it.localizedMessage ?: "check rules"}" }
                            }, modifier = Modifier.fillMaxWidth()) { Text("Save result as unpublished") }
                        } }
                    }
                    item { Text(if (canManage) "Recent results" else "Published results", style = MaterialTheme.typography.titleLarge) }
                    if (results.isEmpty()) item { Text("No visible results found. Students and parents can see published results only when their profile is linked to the correct student ID.") }
                    items(results, key = { it.id }) { result -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { Text(result.examTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); Text("${result.subject} • ${result.marks}/${result.maxMarks}"); Text("Student: ${result.studentId} • ${if (result.published) "Published" else "Draft"}", style = MaterialTheme.typography.labelSmall); if (result.updatedAt > 0) Text("Updated ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.updatedAt))}", style = MaterialTheme.typography.labelSmall); if (canManage && !result.published) OutlinedButton(onClick = { db.collection("institutions").document(institutionId).collection("academicResults").document(result.id).update(mapOf("published" to true, "updatedAt" to System.currentTimeMillis(), "updatedBy" to session?.userId.orEmpty())).addOnSuccessListener { message = "Result published."; refresh() }.addOnFailureListener { message = "Publish failed: ${it.localizedMessage ?: "check rules"}" } }, modifier = Modifier.fillMaxWidth()) { Text("Publish result") } } } }
                }
                if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                message?.let { text -> item { Text(text, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) } }
            }
        }
    }
}

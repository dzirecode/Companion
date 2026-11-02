package com.dzire.eduseva360.features.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import java.io.OutputStream
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AttendanceReportRow(val date: String, val studentName: String, val className: String, val status: String)

@HiltViewModel
class AttendanceReportsViewModel @Inject constructor(private val auth: FirebaseAuth, private val firestore: FirebaseFirestore) : ViewModel() {
    var startDate by mutableStateOf(java.time.LocalDate.now().withDayOfMonth(1).toString())
    var endDate by mutableStateOf(java.time.LocalDate.now().toString())
    var rows by mutableStateOf<List<AttendanceReportRow>>(emptyList())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var loading by mutableStateOf(false)
        private set

    fun setStart(value: String) { startDate = value }
    fun setEnd(value: String) { endDate = value }
    fun load() = viewModelScope.launch {
        loading = true; message = null
        try {
            require(startDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && endDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Enter dates as YYYY-MM-DD." }
            require(startDate <= endDate) { "Start date must be on or before end date." }
            val user = auth.currentUser ?: error("Please sign in again.")
            val profile = firestore.collection("users").document(user.uid).get().await()
            check(profile.getBoolean("active") == true) { "Your account is missing or inactive." }
            val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>()?.map { it.uppercase() }.orEmpty()
            check(roles.any { it in setOf("SUPER_ADMIN", "INSTITUTION_ADMIN", "ADMIN", "PRINCIPAL", "TEACHER", "WARDEN") }) { "Attendance reports are available to authorized institution staff." }
            val institution = profile.getString("institutionId").orEmpty().trim(); check(institution.isNotBlank()) { "Your account is not assigned to an institution." }
            val snapshot = firestore.collection("institutions").document(institution).collection("attendance")
                .whereGreaterThanOrEqualTo("attendanceDate", startDate).whereLessThanOrEqualTo("attendanceDate", endDate).get().await()
            rows = snapshot.documents.mapNotNull { d ->
                val date = d.getString("attendanceDate") ?: return@mapNotNull null
                val name = d.getString("studentName") ?: return@mapNotNull null
                AttendanceReportRow(date, name, d.getString("className").orEmpty(), d.getString("status").orEmpty())
            }.sortedWith(compareBy<AttendanceReportRow> { it.date }.thenBy { it.studentName.lowercase() })
            message = "Loaded ${rows.size} attendance record(s) from Firebase."
        } catch (e: Exception) { message = e.message ?: "Unable to load report. Check connectivity and Firestore rules." }
        finally { loading = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceReportsScreen(onBack: () -> Unit, viewModel: AttendanceReportsViewModel = hiltViewModel()) {
    val rows = viewModel.rows; val message = viewModel.message; val loading = viewModel.loading
    val context = LocalContext.current
    val present = rows.count { it.status.equals("PRESENT", true) }; val absent = rows.count { it.status.equals("ABSENT", true) }; val excused = rows.count { it.status.equals("EXCUSED", true) }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("Date,Student,Class,Status\r\n")
                    rows.forEach { row -> writer.write(listOf(row.date, row.studentName, row.className, row.status).joinToString(",") { csvCell(it) } + "\r\n") }
                } ?: error("Unable to open the selected file.")
            }.onFailure { android.widget.Toast.makeText(context, "CSV export failed: ${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
             .onSuccess { android.widget.Toast.makeText(context, "Attendance CSV exported.", android.widget.Toast.LENGTH_SHORT).show() }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri != null) runCatching { writeAttendancePdf(context.contentResolver.openOutputStream(uri) ?: error("Unable to open the selected file."), rows, viewModel.startDate, viewModel.endDate, present, absent, excused) }
            .onFailure { android.widget.Toast.makeText(context, "PDF export failed: ${it.localizedMessage}", android.widget.Toast.LENGTH_LONG).show() }
            .onSuccess { android.widget.Toast.makeText(context, "Attendance PDF exported.", android.widget.Toast.LENGTH_SHORT).show() }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Attendance Reports") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }, actions = { IconButton(onClick = { viewModel.load() }) { Icon(Icons.Default.Refresh, "Refresh report") } }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Date range report", style = MaterialTheme.typography.titleLarge)
            Text("Use YYYY-MM-DD. Reports are read from institution-scoped Firebase attendance records.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = viewModel.startDate, onValueChange = viewModel::setStart, label = { Text("Start date") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(value = viewModel.endDate, onValueChange = viewModel::setEnd, label = { Text("End date") }, modifier = Modifier.weight(1f), singleLine = true)
            }
            Button(onClick = { viewModel.load() }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text(if (loading) "Loading…" else "Generate report") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { csvLauncher.launch("attendance_${viewModel.startDate}_${viewModel.endDate}.csv") }, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Export CSV") }
                OutlinedButton(onClick = { pdfLauncher.launch("attendance_${viewModel.startDate}_${viewModel.endDate}.pdf") }, enabled = rows.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Export PDF") }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("Present", present.toString(), Modifier.weight(1f))
                SummaryCard("Absent", absent.toString(), Modifier.weight(1f))
                SummaryCard("Excused", excused.toString(), Modifier.weight(1f))
            }
            Text("Records: ${rows.size}", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(rows) { row -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(row.studentName, style = MaterialTheme.typography.titleSmall)
                    Text("${row.date} • ${row.className} • ${row.status}", style = MaterialTheme.typography.bodySmall)
                } } }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(12.dp)) { Text(label, style = MaterialTheme.typography.labelMedium); Text(value, style = MaterialTheme.typography.headlineSmall) } }
}


private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""

private fun writeAttendancePdf(
    output: OutputStream,
    rows: List<AttendanceReportRow>,
    startDate: String,
    endDate: String,
    present: Int,
    absent: Int,
    excused: Int
) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 36f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f }
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var canvas: android.graphics.Canvas? = null
    var y = 0f
    fun newPage() {
        page?.let { document.finishPage(it) }
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        canvas = page!!.canvas
        y = 42f
        paint.textSize = 16f
        paint.isFakeBoldText = true
        canvas!!.drawText("EduSeva 360 - Attendance Report", margin, y, paint)
        paint.isFakeBoldText = false
        y += 22f
        paint.textSize = 9f
        canvas!!.drawText("Period: $startDate to $endDate", margin, y, paint)
        y += 16f
        canvas!!.drawText("Present: $present    Absent: $absent    Excused: $excused    Total: ${rows.size}", margin, y, paint)
        y += 22f
        paint.isFakeBoldText = true
        canvas!!.drawText("Date", margin, y, paint)
        canvas!!.drawText("Student", margin + 78f, y, paint)
        canvas!!.drawText("Class", margin + 335f, y, paint)
        canvas!!.drawText("Status", margin + 425f, y, paint)
        paint.isFakeBoldText = false
        y += 8f
        canvas!!.drawLine(margin, y, pageWidth - margin, y, paint)
        y += 15f
    }
    try {
        newPage()
        rows.forEach { row ->
            if (y > pageHeight - 42f) newPage()
            paint.textSize = 8.5f
            canvas!!.drawText(row.date.take(10), margin, y, paint)
            canvas!!.drawText(row.studentName.take(38), margin + 78f, y, paint)
            canvas!!.drawText(row.className.take(13), margin + 335f, y, paint)
            canvas!!.drawText(row.status.take(12), margin + 425f, y, paint)
            y += 16f
        }
        page?.let { document.finishPage(it); page = null }
        document.writeTo(output)
    } finally {
        page?.let { runCatching { document.finishPage(it) } }
        document.close()
        output.close()
    }
}

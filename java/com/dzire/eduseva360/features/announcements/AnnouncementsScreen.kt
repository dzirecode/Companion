package com.dzire.eduseva360.features.announcements

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dzire.eduseva360.features.auth.AuthState
import com.dzire.eduseva360.features.auth.AuthViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.messaging.FirebaseMessaging
import java.text.DateFormat
import java.util.Date

private data class Announcement(
    val id: String,
    val title: String,
    val body: String,
    val audience: String,
    val authorName: String,
    val createdAt: Long
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnouncementsScreen(onBack: () -> Unit, authViewModel: AuthViewModel) {
    val authState by authViewModel.state.collectAsState()
    val session = (authState as? AuthState.LoggedIn)?.session
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("ALL") }
    var rows by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var fcmToken by remember { mutableStateOf("") }
    val canPublish = session?.roles?.any { it.name in setOf("SUPER_ADMIN", "INSTITUTION_ADMIN", "ADMIN", "PRINCIPAL", "TEACHER", "WARDEN") } == true

    fun loadAnnouncements() {
        val s = session ?: return
        if (s.institutionId.isBlank()) { message = "Your account is not assigned to an institution."; return }
        FirebaseFirestore.getInstance()
            .collection("institutions").document(s.institutionId)
            .collection("announcements")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(100)
            .get()
            .addOnSuccessListener { snapshot ->
                rows = snapshot.documents.mapNotNull { doc ->
                    val a = Announcement(doc.id, doc.getString("title").orEmpty(), doc.getString("body").orEmpty(), doc.getString("audience") ?: "ALL", doc.getString("authorName").orEmpty(), doc.getLong("createdAt") ?: 0L)
                    if (a.audience == "ALL" || a.audience == "${s.roles.firstOrNull()?.name ?: "STUDENT"}S" || a.audience == "STAFF" && canPublish) a else null
                }
                message = if (rows.isEmpty()) "No announcements yet." else null
            }
            .addOnFailureListener { message = "Unable to load announcements: ${it.localizedMessage ?: "check Firestore rules and connection"}" }
    }

    LaunchedEffect(session?.userId, session?.institutionId) {
        loadAnnouncements()
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { fcmToken = it }
            .addOnFailureListener { message = "Push token unavailable. ${it.localizedMessage ?: ""}" }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        message = if (granted) "Notifications permission enabled." else "Notifications are disabled for this app in Android settings."
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Notifications & Announcements") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }, actions = { IconButton(onClick = { loadAnnouncements() }) { Icon(Icons.Default.Refresh, contentDescription = "Refresh") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Device notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Push messages can be received from Firebase Cloud Messaging. Publishing an announcement below saves it in Firestore; it does not automatically send a push notification.", style = MaterialTheme.typography.bodySmall)
                        if (Build.VERSION.SDK_INT >= 33) {
                            OutlinedButton(onClick = {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) message = "Notifications permission is already enabled."
                                else permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }, modifier = Modifier.fillMaxWidth()) { Text("Enable device notifications") }
                        }
                        if (fcmToken.isNotBlank()) {
                            Text("FCM device token available", style = MaterialTheme.typography.labelMedium)
                            OutlinedButton(onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("FCM token", fcmToken))
                                message = "FCM token copied. Use it only in Firebase Console for a test message; do not share it publicly."
                            }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.ContentCopy, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Copy FCM token for test message") }
                        }
                    }
                }
            }
            if (canPublish) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Publish an announcement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = title, onValueChange = { title = it.take(100) }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = body, onValueChange = { body = it.take(2000) }, label = { Text("Message") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                        Text("Audience", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL", "PARENTS", "STUDENTS", "STAFF").forEach { option ->
                                FilterChip(selected = audience == option, onClick = { audience = option }, label = { Text(option) })
                            }
                        }
                        Button(enabled = !saving && title.isNotBlank() && body.isNotBlank() && session?.institutionId?.isNotBlank() == true, onClick = {
                            val s = session ?: return@Button
                            saving = true
                            val now = System.currentTimeMillis()
                            val payload = mapOf("title" to title.trim(), "body" to body.trim(), "audience" to audience, "authorUid" to s.userId, "authorName" to s.displayName, "createdAt" to now, "institutionId" to s.institutionId)
                            FirebaseFirestore.getInstance().collection("institutions").document(s.institutionId).collection("announcements")
                                .add(payload)
                                .addOnSuccessListener { title = ""; body = ""; saving = false; message = "Announcement published."; loadAnnouncements() }
                                .addOnFailureListener { saving = false; message = "Publish failed: ${it.localizedMessage ?: "check Firestore rules"}" }
                        }, modifier = Modifier.fillMaxWidth()) { if (saving) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Publish announcement") }
                    }
                }
            }
            item { Text("Recent announcements", style = MaterialTheme.typography.titleLarge) }
            if (rows.isEmpty()) item { Text(message ?: "No announcements yet.", style = MaterialTheme.typography.bodyMedium) }
            items(rows, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(item.body, style = MaterialTheme.typography.bodyMedium)
                        Text("${item.audience} • ${item.authorName} • ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAt))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            if (message != null) item { Text(message!!, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

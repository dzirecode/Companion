package com.dzire.eduseva360.features.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dzire.eduseva360.core.model.UserRole
import com.dzire.eduseva360.features.auth.AuthState
import com.dzire.eduseva360.features.auth.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: AuthViewModel,
    onLogout: () -> Unit,
    onStudentsClick: () -> Unit,
    onUsersClick: () -> Unit,
    onAttendanceClick: () -> Unit,
    onParentPortalClick: () -> Unit,
    onHostelClick: () -> Unit,
    onAttendanceReportsClick: () -> Unit,
    onAnnouncementsClick: () -> Unit,
    onAcademicsClick: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val session = (state as? AuthState.LoggedIn)?.session ?: return
    val role = session.roles.firstOrNull { it !in setOf(UserRole.STUDENT, UserRole.PARENT) }
        ?: session.roles.firstOrNull() ?: UserRole.STUDENT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EduSeva 360") },
                actions = {
                    IconButton(onClick = {
                        viewModel.logout()
                        onLogout()
                    }) {
                        Icon(Icons.Default.Logout, contentDescription = "Logout")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column {
                    Text("Welcome, ${session.displayName}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(role.name.replace('_', ' '), style = MaterialTheme.typography.labelLarge)

                }
            }
            if (role in setOf(UserRole.SUPER_ADMIN, UserRole.INSTITUTION_ADMIN, UserRole.PRINCIPAL, UserRole.TEACHER, UserRole.WARDEN)) {
                item(span = { GridItemSpan(2) }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Quick actions", style = MaterialTheme.typography.titleLarge)
                            Button(onClick = onAnnouncementsClick, modifier = Modifier.fillMaxWidth()) { Text("Notifications & Announcements") }
                            Button(onClick = onAcademicsClick, modifier = Modifier.fillMaxWidth()) { Text("Timetable & Academic Records") }
                            Button(onClick = onStudentsClick, modifier = Modifier.fillMaxWidth()) { Text("Student Management") }
                            Button(onClick = onAttendanceClick, modifier = Modifier.fillMaxWidth()) { Text("Daily Attendance") }
                            Button(onClick = onAttendanceReportsClick, modifier = Modifier.fillMaxWidth()) { Text("Attendance Reports") }
                            if (role in setOf(UserRole.WARDEN, UserRole.SUPER_ADMIN, UserRole.INSTITUTION_ADMIN, UserRole.PRINCIPAL)) {
                                OutlinedButton(onClick = onHostelClick, modifier = Modifier.fillMaxWidth()) { Text("Hostel Management") }
                            }
                            if (role in setOf(UserRole.SUPER_ADMIN, UserRole.INSTITUTION_ADMIN)) {
                                OutlinedButton(onClick = onUsersClick, modifier = Modifier.fillMaxWidth()) { Text("User Account Setup") }
                            }
                        }
                    }
                }
            } else {
                item(span = { GridItemSpan(2) }) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(if (role == UserRole.PARENT) "Parent Portal" else "Student Portal", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(8.dp))
                            Text("Your account is signed in. View announcements and relevant student information here.")
                            Button(onClick = onAnnouncementsClick, modifier = Modifier.fillMaxWidth()) { Text("Notifications & Announcements") }
                            Button(onClick = onAcademicsClick, modifier = Modifier.fillMaxWidth()) { Text("Timetable & Academic Records") }
                            if (role == UserRole.PARENT) {
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onParentPortalClick, modifier = Modifier.fillMaxWidth()) { Text("My Linked Students") }
                            }
                        }
                    }
                }
            }
            item(span = { GridItemSpan(2) }) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Workspace status", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("Attendance and student records are available from Quick actions. Dashboard totals will appear here once live reporting is connected.")
                    }
                }
            }
        }
    }
}

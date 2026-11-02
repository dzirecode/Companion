package com.dzire.eduseva360.features.users

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("User Accounts — No-Blaze Setup") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } }
        )
    }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Create ordinary accounts", style = MaterialTheme.typography.titleMedium)
            Text("Users can create their own STUDENT account from the sign-in screen using ‘New user? Create a student account’. Self-registration does not assign an institution or staff permissions.")
            HorizontalDivider()
            Text("Create a teacher, warden, or institution administrator", style = MaterialTheme.typography.titleMedium)
            Text("Without Cloud Functions, create staff accounts manually in Firebase Console. This keeps privileged role assignment outside the Android app.")
            Text("1. Open Firebase Console → Authentication → Users → Add user. Create the account with the user's email and a temporary password, then arrange a password reset if needed.")
            Text("2. Copy the new account's UID. Open Firestore Database → Data → users. Create a document whose document ID is exactly that UID.")
            Text("3. Add fields: uid (string), email (string), displayName (string), institutionId (string), roles (array), active (boolean true). Use only the role you have authorized. For staff, institutionId must match the institution path used by student records.")
            Text("4. Publish the Firestore rules from firebase/firestore.rules. Do not allow users to change their own roles or institutionId.")
            Button(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://console.firebase.google.com/")))
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Firebase Console") }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "No Cloud Functions are required for this workflow. The app's self-registration creates only a STUDENT profile. Privileged accounts are created and assigned manually by a trusted administrator.",
                    modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

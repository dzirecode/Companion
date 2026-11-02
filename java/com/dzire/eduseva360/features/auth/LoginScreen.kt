package com.dzire.eduseva360.features.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.dzire.eduseva360.core.ui.components.EduErrorState

@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onLoggedIn: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is AuthState.LoggedIn) onLoggedIn()
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("EduSeva 360", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("One platform for smarter educational institutions", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(24.dp))
            Text(if (isRegister) "Create student account" else "Sign in", style = MaterialTheme.typography.titleLarge)

            if (isRegister) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it; error = null },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Full name") }, singleLine = true,
                    enabled = !busy
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = email, onValueChange = { email = it.trim(); error = null; notice = null },
                modifier = Modifier.fillMaxWidth(), label = { Text("Email") }, singleLine = true, enabled = !busy
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password, onValueChange = { password = it; error = null },
                modifier = Modifier.fillMaxWidth(), label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(), singleLine = true, enabled = !busy
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    error = null; notice = null; busy = true
                    if (isRegister) {
                        viewModel.registerStudent(email, password, displayName) { message -> error = message; busy = false }
                    } else {
                        viewModel.login(email, password) { message -> error = message; busy = false }
                    }
                }, modifier = Modifier.fillMaxWidth(), enabled = !busy
            ) { if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (isRegister) "Create student account" else "Sign in") }

            if (!isRegister) {
                TextButton(onClick = {
                    if (email.isBlank()) { error = "Enter your email address first." }
                    else viewModel.sendPasswordReset(email) { notice = it }
                }, enabled = !busy) { Text("Forgot password?") }
            }
            TextButton(onClick = { isRegister = !isRegister; error = null; notice = null }) {
                Text(if (isRegister) "Already registered? Sign in" else "New user? Create a student account")
            }
            if (isRegister) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Self-registration creates a STUDENT profile with no institution assigned. An authorized administrator must approve and assign institution access. Staff roles cannot be selected here.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            notice?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            error?.let { Spacer(Modifier.height(12.dp)); EduErrorState(it) }
        }
    }
}

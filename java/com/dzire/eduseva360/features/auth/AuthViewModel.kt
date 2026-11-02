package com.dzire.eduseva360.features.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dzire.eduseva360.core.model.Session
import com.dzire.eduseva360.core.security.SessionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface AuthState {
    data object Loading : AuthState
    data object LoggedOut : AuthState
    data class LoggedIn(val session: Session) : AuthState
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    sessionStore: SessionStore
) : ViewModel() {

    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            sessionStore.session.collect { session ->
                _state.value = if (session == null) AuthState.LoggedOut
                else AuthState.LoggedIn(session)
            }
        }
    }

    fun login(email: String, password: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.login(email, password)
                .onFailure { onError(it.message ?: "Unable to login") }
        }
    }

    fun registerStudent(email: String, password: String, displayName: String, onError: (String) -> Unit) {
        viewModelScope.launch {
            repository.createStudentAccount(email, password, displayName)
                .onFailure { onError(it.message ?: "Unable to create account.") }
                .onSuccess {
                    repository.login(email, password)
                        .onFailure { onError(it.message ?: "Account created. Please sign in.") }
                }
        }
    }

    fun sendPasswordReset(email: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            repository.sendPasswordReset(email)
                .onSuccess { onResult("Password reset email sent. Check your inbox.") }
                .onFailure { onResult(it.message ?: "Unable to send password reset email.") }
        }
    }

    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}

package com.dzire.eduseva360.core.network

import com.dzire.eduseva360.core.model.LoginRequest
import com.dzire.eduseva360.core.model.LoginResponse
import com.dzire.eduseva360.core.model.Session
import com.dzire.eduseva360.core.model.UserRole
import javax.inject.Inject

class MockAuthService @Inject constructor() {

    suspend fun login(request: LoginRequest): LoginResponse {
        val email = request.email.trim().lowercase()

        val role = when {
            email.startsWith("admin") -> UserRole.INSTITUTION_ADMIN
            email.startsWith("principal") -> UserRole.PRINCIPAL
            email.startsWith("teacher") -> UserRole.TEACHER
            email.startsWith("warden") -> UserRole.WARDEN
            email.startsWith("student") -> UserRole.STUDENT
            email.startsWith("parent") -> UserRole.PARENT
            else -> UserRole.INSTITUTION_ADMIN
        }

        return LoginResponse(
            success = true,
            session = Session(
                userId = "demo-user-001",
                institutionId = "demo-institution-001",
                displayName = role.name.replace('_', ' ').lowercase()
                    .replaceFirstChar { it.uppercase() },
                email = email.ifBlank { "admin@eduseva360.demo" },
                roles = listOf(role),
                accessToken = "demo-access-token",
                refreshToken = "demo-refresh-token",
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 3600
            )
        )
    }
}

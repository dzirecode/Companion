package com.dzire.eduseva360.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole {
    SUPER_ADMIN,
    INSTITUTION_ADMIN,
    PRINCIPAL,
    TEACHER,
    WARDEN,
    STUDENT,
    PARENT
}

@Serializable
data class Session(
    val userId: String,
    val institutionId: String,
    val displayName: String,
    val email: String,
    val roles: List<UserRole>,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long = 0L
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val success: Boolean,
    val session: Session? = null,
    val message: String? = null
)

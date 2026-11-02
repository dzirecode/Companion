package com.dzire.eduseva360.features.auth

import com.dzire.eduseva360.core.model.LoginResponse
import com.dzire.eduseva360.core.model.Session
import com.dzire.eduseva360.core.model.UserRole
import com.dzire.eduseva360.core.security.SessionStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase Authentication adapter. Privileged roles must be assigned by a trusted admin/backend. */
@Singleton
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val sessionStore: SessionStore
) {
    suspend fun login(email: String, password: String): Result<LoginResponse> = runCatching {
        require(email.isNotBlank()) { "Enter email" }
        require(password.isNotBlank()) { "Enter password" }

        val result = auth.signInWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Firebase did not return a signed-in user")
        val profile = runCatching {
            firestore.collection("users").document(user.uid).get().await()
        }.getOrNull()

        val roles = profile?.get("roles") as? List<*>
        val safeRoles = roles.orEmpty()
            .mapNotNull { raw -> (raw as? String)?.uppercase()?.let { value ->
                when (value) {
                    "ADMIN", "INSTITUTION_ADMIN" -> UserRole.INSTITUTION_ADMIN
                    "SUPER_ADMIN" -> UserRole.SUPER_ADMIN
                    "PRINCIPAL" -> UserRole.PRINCIPAL
                    "TEACHER" -> UserRole.TEACHER
                    "WARDEN" -> UserRole.WARDEN
                    "STUDENT" -> UserRole.STUDENT
                    "PARENT" -> UserRole.PARENT
                    else -> null
                }
            } }
            .distinct()
            .ifEmpty { listOf(UserRole.STUDENT) }

        val session = Session(
            userId = user.uid,
            institutionId = profile?.getString("institutionId").orEmpty(),
            displayName = profile?.getString("displayName")
                ?.takeIf { it.isNotBlank() }
                ?: user.displayName?.takeIf { it.isNotBlank() }
                ?: user.email?.substringBefore('@').orEmpty(),
            email = user.email ?: email.trim(),
            roles = safeRoles,
            // Firebase SDK manages its own credentials; do not persist raw passwords or ID tokens.
            accessToken = "",
            refreshToken = null,
            expiresAtEpochSeconds = 0L
        )
        sessionStore.save(session)
        LoginResponse(success = true, session = session)
    }.recoverCatching { throwable ->
        throw IllegalStateException(friendlyMessage(throwable), throwable)
    }

    suspend fun createStudentAccount(
        email: String,
        password: String,
        displayName: String
    ): Result<String> = runCatching {
        require(email.isNotBlank()) { "Enter email" }
        require(password.length >= 6) { "Password must contain at least 6 characters" }
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val user = result.user ?: error("Firebase did not return the created user")
        val cleanName = displayName.trim()
        if (cleanName.isNotEmpty()) {
            val request = UserProfileChangeRequest.Builder()
                .setDisplayName(cleanName)
                .build()
            user.updateProfile(request).await()
        }
        // Only create a low-privilege self-profile. Institution and privileged roles must be assigned
        // through a trusted administrative process, not from client-provided values.
        firestore.collection("users").document(user.uid).set(
            mapOf(
                "uid" to user.uid,
                "email" to (user.email ?: email.trim()),
                "displayName" to cleanName,
                "institutionId" to "",
                "roles" to listOf("STUDENT"),
                "active" to true
            )
        ).await()
        user.uid
    }.recoverCatching { throwable ->
        throw IllegalStateException(friendlyMessage(throwable), throwable)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        require(email.isNotBlank()) { "Enter your email address first" }
        auth.sendPasswordResetEmail(email.trim()).await()
        Unit
    }.recoverCatching { throwable ->
        throw IllegalStateException(friendlyMessage(throwable), throwable)
    }

    suspend fun logout() {
        auth.signOut()
        sessionStore.clear()
    }

    fun currentUser() = auth.currentUser

    private fun friendlyMessage(error: Throwable): String {
        val message = error.message.orEmpty()
        return when {
            message.contains("password", ignoreCase = true) && message.contains("invalid", ignoreCase = true) ->
                "Email or password is incorrect."
            message.contains("no user record", ignoreCase = true) ->
                "No account was found for this email."
            message.contains("network", ignoreCase = true) ->
                "Network error. Check your internet connection and try again."
            message.contains("permission", ignoreCase = true) ->
                "Access was denied by Firebase security rules. Check your Firestore rules and user profile permissions."
            else -> message.ifBlank { "Authentication failed. Please try again." }
        }
    }
}

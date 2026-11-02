package com.dzire.eduseva360.features.students

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StudentRepository @Inject constructor(
    private val studentDao: StudentDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    val students: Flow<List<Student>> = studentDao.observeAll().map { rows ->
        rows.map { it.toStudent() }
    }

    private val _syncStatus = MutableStateFlow("Connecting to student records…")
    val syncStatus = _syncStatus.asStateFlow()

    private data class StaffContext(val uid: String, val institutionId: String)

    /**
     * Merges institution records from Firestore into Room.
     * Never clears the local table during refresh: an empty or partial cloud result must not
     * erase locally saved student records.
     */
    suspend fun refreshFromCloud() {
        try {
            val staff = requireStaffContext()
            val snapshot = firestore.collection("institutions")
                .document(staff.institutionId)
                .collection("students")
                .get()
                .await()

            val cloudRows = snapshot.documents.mapNotNull { doc ->
                val name = doc.getString("fullName") ?: return@mapNotNull null
                val admission = doc.getString("admissionNumber") ?: return@mapNotNull null
                StudentEntity(
                    id = doc.id,
                    fullName = name,
                    admissionNumber = admission,
                    className = doc.getString("className").orEmpty(),
                    section = doc.getString("section").orEmpty(),
                    guardianName = doc.getString("guardianName").orEmpty(),
                    guardianPhone = doc.getString("guardianPhone").orEmpty(),
                    status = doc.getString("status") ?: StudentStatus.ACTIVE.name
                )
            }

            val localCountBefore = studentDao.count()
            if (cloudRows.isEmpty() && localCountBefore > 0) {
                _syncStatus.value =
                    "Firebase returned no student records. Kept $localCountBefore local student(s) to prevent data loss."
                return
            }

            if (cloudRows.isNotEmpty()) {
                studentDao.upsertAll(cloudRows)
            }
            val localCountAfter = studentDao.count()
            _syncStatus.value = if (cloudRows.isEmpty()) {
                "Firebase sync complete • No cloud students found."
            } else {
                "Merged ${cloudRows.size} Firebase student(s) • $localCountAfter local student(s) retained."
            }
        } catch (error: Exception) {
            _syncStatus.value = error.message?.takeIf { it.isNotBlank() }
                ?.let { "Cloud sync unavailable: $it" }
                ?: "Cloud sync unavailable. Check your connection and account permissions."
        }
    }

    suspend fun save(student: Student) {
        try {
            val staff = requireStaffContext()
            val normalizedAdmission = student.admissionNumber.trim()
            require(normalizedAdmission.isNotBlank()) { "Admission number is required." }
            require(student.fullName.isNotBlank()) { "Student name is required." }
            require(student.className.isNotBlank()) { "Class is required." }
            require(studentDao.countOtherWithAdmissionNumber(normalizedAdmission, student.id) == 0) {
                "An admission number already exists in this device's student records."
            }
            val studentsRef = firestore.collection("institutions")
                .document(staff.institutionId)
                .collection("students")

            val duplicate = studentsRef
                .whereEqualTo("admissionNumber", normalizedAdmission)
                .get()
                .await()
                .documents
                .any { it.id != student.id }
            require(!duplicate) { "An admission number already exists in this institution." }

            val payload = mapOf(
                "id" to student.id,
                "institutionId" to staff.institutionId,
                "fullName" to student.fullName.trim(),
                "admissionNumber" to normalizedAdmission,
                "className" to student.className.trim(),
                "section" to student.section.trim(),
                "guardianName" to student.guardianName.trim(),
                "guardianPhone" to student.guardianPhone.trim(),
                "status" to student.status.name,
                "updatedAt" to FieldValue.serverTimestamp(),
                "updatedBy" to staff.uid
            )
            studentsRef.document(student.id).set(payload).await()
            studentDao.upsert(student.toEntity())
            _syncStatus.value = "Saved to Firebase successfully."
        } catch (error: Exception) {
            _syncStatus.value = error.message?.takeIf { it.isNotBlank() }
                ?.let { "Save failed: $it" }
                ?: "Save failed. Check your connection and account permissions."
            throw error
        }
    }

    private suspend fun requireStaffContext(): StaffContext {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in again.")
        val profile = firestore.collection("users").document(user.uid).get().await()
        if (!profile.exists()) {
            throw IllegalStateException("Your user profile is missing. Ask your institution administrator to configure your account.")
        }
        if (profile.getBoolean("active") != true) {
            throw IllegalStateException("This account is not active. Contact your institution administrator.")
        }
        val institutionId = profile.getString("institutionId").orEmpty().trim()
        if (institutionId.isBlank()) {
            throw IllegalStateException("Your account is not assigned to an institution yet. Ask an administrator to assign your institution.")
        }
        val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>()?.map { it.uppercase() }.orEmpty()
        if (roles.none { it in setOf("SUPER_ADMIN", "INSTITUTION_ADMIN", "ADMIN", "PRINCIPAL", "TEACHER", "WARDEN") }) {
            throw IllegalStateException("Student management is available only to authorized institution staff.")
        }
        return StaffContext(user.uid, institutionId)
    }
}

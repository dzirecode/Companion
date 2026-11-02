package com.dzire.eduseva360.features.attendance

import com.dzire.eduseva360.features.students.Student
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AttendanceRepository @Inject constructor(
    private val dao: AttendanceDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    fun observeForDate(date: String): Flow<List<AttendanceRecord>> = dao.observeForDate(date)

    suspend fun refreshFromCloud(date: String) {
        val staff = requireStaffContext()
        val collection = firestore.collection("institutions").document(staff.institutionId).collection("attendance")
        val snapshot = collection.whereEqualTo("attendanceDate", date).get().await()
        val cloudIds = snapshot.documents.map { it.id }.toSet()
        // Retry uploading local-only marks when connectivity/rules are available again.
        dao.getForDate(date).filter { it.id !in cloudIds }.forEach { local ->
            collection.document(local.id).set(
                mapOf(
                    "id" to local.id, "studentId" to local.studentId, "studentName" to local.studentName,
                    "className" to local.className, "attendanceDate" to local.attendanceDate,
                    "status" to local.status, "institutionId" to staff.institutionId,
                    "markedBy" to staff.uid, "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }
        val rows = snapshot.documents.mapNotNull { doc ->
            val studentId = doc.getString("studentId") ?: return@mapNotNull null
            val studentName = doc.getString("studentName") ?: return@mapNotNull null
            AttendanceRecord(
                id = doc.id, studentId = studentId, studentName = studentName,
                className = doc.getString("className").orEmpty(), attendanceDate = date,
                status = doc.getString("status") ?: return@mapNotNull null,
                institutionId = staff.institutionId, markedBy = doc.getString("markedBy").orEmpty(),
                updatedAtMillis = doc.getTimestamp("updatedAt")?.toDate()?.time ?: System.currentTimeMillis()
            )
        }
        if (rows.isNotEmpty()) dao.upsertAll(rows)
    }

    suspend fun save(student: Student, date: String, status: AttendanceStatus) {
        val staff = requireStaffContext()
        val id = "$date-${student.id}"
        val record = AttendanceRecord(
            id = id, studentId = student.id, studentName = student.fullName,
            className = listOf(student.className, student.section).filter { it.isNotBlank() && it != "-" }.joinToString("-"),
            attendanceDate = date, status = status.name, institutionId = staff.institutionId,
            markedBy = staff.uid, updatedAtMillis = System.currentTimeMillis()
        )
        // Save locally first. If the network fails, the mark remains on-device for a later retry.
        dao.upsert(record)
        try {
            firestore.collection("institutions").document(staff.institutionId).collection("attendance")
                .document(id).set(
                    mapOf(
                        "id" to id, "studentId" to student.id, "studentName" to student.fullName,
                        "className" to record.className, "attendanceDate" to date, "status" to status.name,
                        "institutionId" to staff.institutionId, "markedBy" to staff.uid,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
        } catch (error: Exception) {
            throw IllegalStateException("Attendance saved on this device but could not sync to Firebase: ${error.message ?: "check connection and rules"}", error)
        }
    }

    private data class StaffContext(val uid: String, val institutionId: String)

    private suspend fun requireStaffContext(): StaffContext {
        val user = auth.currentUser ?: throw IllegalStateException("Please sign in again.")
        val profile = firestore.collection("users").document(user.uid).get().await()
        if (!profile.exists() || profile.getBoolean("active") != true) throw IllegalStateException("Your account profile is missing or inactive.")
        val institutionId = profile.getString("institutionId").orEmpty().trim()
        if (institutionId.isBlank()) throw IllegalStateException("Your account is not assigned to an institution.")
        val roles = (profile.get("roles") as? List<*>)?.filterIsInstance<String>()?.map { it.uppercase() }.orEmpty()
        if (roles.none { it in setOf("SUPER_ADMIN", "INSTITUTION_ADMIN", "ADMIN", "PRINCIPAL", "TEACHER", "WARDEN") }) {
            throw IllegalStateException("Attendance is available only to authorized institution staff.")
        }
        return StaffContext(user.uid, institutionId)
    }
}

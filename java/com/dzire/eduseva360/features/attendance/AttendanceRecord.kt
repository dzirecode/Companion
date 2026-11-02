package com.dzire.eduseva360.features.attendance

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "attendance_records",
    indices = [Index(value = ["studentId", "attendanceDate"], unique = true), Index(value = ["attendanceDate"])]
)
data class AttendanceRecord(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val className: String,
    val attendanceDate: String,
    val status: String,
    val institutionId: String,
    val markedBy: String,
    val updatedAtMillis: Long
)

enum class AttendanceStatus { PRESENT, ABSENT, EXCUSED }

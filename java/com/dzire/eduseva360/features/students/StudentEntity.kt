package com.dzire.eduseva360.features.students

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "students",
    indices = [Index(value = ["admissionNumber"], unique = true)]
)
data class StudentEntity(
    @PrimaryKey val id: String,
    val fullName: String,
    val admissionNumber: String,
    val className: String,
    val section: String,
    val guardianName: String,
    val guardianPhone: String,
    val status: String = "ACTIVE"
)

fun StudentEntity.toStudent() = Student(
    id = id,
    fullName = fullName,
    admissionNumber = admissionNumber,
    className = className,
    section = section,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    status = runCatching { StudentStatus.valueOf(status) }.getOrDefault(StudentStatus.ACTIVE)
)

fun Student.toEntity() = StudentEntity(
    id = id,
    fullName = fullName,
    admissionNumber = admissionNumber,
    className = className,
    section = section,
    guardianName = guardianName,
    guardianPhone = guardianPhone,
    status = status.name
)

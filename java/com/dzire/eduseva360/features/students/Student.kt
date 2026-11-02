package com.dzire.eduseva360.features.students

data class Student(
    val id: String,
    val fullName: String,
    val admissionNumber: String,
    val className: String,
    val section: String,
    val guardianName: String,
    val guardianPhone: String,
    val status: StudentStatus = StudentStatus.ACTIVE
)

enum class StudentStatus { ACTIVE, INACTIVE }

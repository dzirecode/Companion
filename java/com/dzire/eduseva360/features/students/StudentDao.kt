package com.dzire.eduseva360.features.students

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT COUNT(*) FROM students")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM students WHERE admissionNumber = :admissionNumber AND id != :studentId")
    suspend fun countOtherWithAdmissionNumber(admissionNumber: String, studentId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(student: StudentEntity)

    /** Merge cloud records without deleting local-only rows. */
    @Upsert
    suspend fun upsertAll(students: List<StudentEntity>)
}

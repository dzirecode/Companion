package com.dzire.eduseva360.features.attendance

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance_records WHERE attendanceDate = :date ORDER BY studentName COLLATE NOCASE")
    fun observeForDate(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE attendanceDate = :date")
    suspend fun getForDate(date: String): List<AttendanceRecord>

    @Upsert
    suspend fun upsert(record: AttendanceRecord)

    @Upsert
    suspend fun upsertAll(records: List<AttendanceRecord>)
}

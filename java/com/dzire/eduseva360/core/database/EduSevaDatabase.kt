package com.dzire.eduseva360.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dzire.eduseva360.features.attendance.AttendanceDao
import com.dzire.eduseva360.features.attendance.AttendanceRecord
import com.dzire.eduseva360.features.students.StudentDao
import com.dzire.eduseva360.features.students.StudentEntity

@Database(
    entities = [StudentEntity::class, AttendanceRecord::class],
    version = 2,
    exportSchema = false
)
abstract class EduSevaDatabase : RoomDatabase() {
    abstract fun studentDao(): StudentDao
    abstract fun attendanceDao(): AttendanceDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS attendance_records (id TEXT NOT NULL, studentId TEXT NOT NULL, studentName TEXT NOT NULL, className TEXT NOT NULL, attendanceDate TEXT NOT NULL, status TEXT NOT NULL, institutionId TEXT NOT NULL, markedBy TEXT NOT NULL, updatedAtMillis INTEGER NOT NULL, PRIMARY KEY(id))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_attendance_records_studentId_attendanceDate ON attendance_records(studentId, attendanceDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_attendance_records_attendanceDate ON attendance_records(attendanceDate)")
            }
        }
    }
}

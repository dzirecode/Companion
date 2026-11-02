package com.dzire.eduseva360.core.di

import android.content.Context
import androidx.room.Room
import com.dzire.eduseva360.core.database.EduSevaDatabase
import com.dzire.eduseva360.features.students.StudentDao
import com.dzire.eduseva360.features.attendance.AttendanceDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EduSevaDatabase =
        Room.databaseBuilder(
            context,
            EduSevaDatabase::class.java,
            "eduseva360.db"
        ).addMigrations(EduSevaDatabase.MIGRATION_1_2).build()

    @Provides
    fun provideStudentDao(database: EduSevaDatabase): StudentDao = database.studentDao()

    @Provides
    fun provideAttendanceDao(database: EduSevaDatabase): AttendanceDao = database.attendanceDao()
}

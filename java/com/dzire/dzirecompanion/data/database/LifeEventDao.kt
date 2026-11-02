package com.dzire.dzirecompanion.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LifeEventDao {
    @Insert
    suspend fun insert(event: LifeEvent)

    @Query("SELECT * FROM life_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<LifeEvent>>

    @Query("SELECT * FROM life_events WHERE category = :category ORDER BY timestamp DESC")
    fun getEventsByCategory(category: String): Flow<List<LifeEvent>>
    
    @Query("SELECT * FROM life_events ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEvent(): LifeEvent?
}

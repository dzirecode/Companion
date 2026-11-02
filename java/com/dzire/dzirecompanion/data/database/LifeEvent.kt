package com.dzire.dzirecompanion.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A normalized observation captured from the user's life.
 *
 * The model deliberately stores measurements separately from display text so
 * analytics never has to parse sentences such as "Water intake is 1.5 L".
 */
@Entity(tableName = "life_events")
data class LifeEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val category: String,
    val type: String,
    val content: String,
    val parameter1: String? = null,
    val parameter2: String? = null,
    val numericValue: Double? = null,
    val unit: String? = null,
    val rating: Double? = null,
    val durationMs: Long? = null,
    val source: String = "USER"
)

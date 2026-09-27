package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertEventDao {
    @Query("SELECT * FROM alert_events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<AlertEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: AlertEvent): Long

    @Query("DELETE FROM alert_events")
    suspend fun clearAll()

    @Query("DELETE FROM alert_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

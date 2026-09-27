package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "alert_events")
data class AlertEvent(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val source: String,
    val status: String,
    val userName: String,
    val contactPhone: String,
    val latitude: Double,
    val longitude: Double,
    val addressText: String,
    val spokenMessage: String
)

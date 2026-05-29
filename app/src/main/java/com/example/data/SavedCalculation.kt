package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_calculations")
data class SavedCalculation(
    @PrimaryKey val id: Long, // timestamp
    val title: String,
    val timestamp: Long,
    val totalAmount: Double
)

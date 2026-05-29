package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "materials")
data class Material(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pricePerUnit: Double,
    val unit: String, // "kg" lub "g"
    val category: String, // "Metale kolorowe", "Metale szlachetne", etc.
    val isDefault: Boolean = false
)

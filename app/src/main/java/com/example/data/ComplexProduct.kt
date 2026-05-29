package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "complex_products")
data class ComplexProduct(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String = "",
    val isDefault: Boolean = false
)

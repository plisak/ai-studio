package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calculation_items")
data class CalculationItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val calculationId: Long, // 0 for the live/draft calculation, or timestamp for saved calculation
    val materialId: Int,
    val materialName: String,
    val materialPrice: Double,
    val materialUnit: String,
    val quantity: Double
) {
    val totalCost: Double
        get() = materialPrice * quantity
}

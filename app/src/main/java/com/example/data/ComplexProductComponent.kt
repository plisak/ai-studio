package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "complex_product_components")
data class ComplexProductComponent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,      // references ComplexProduct.id
    val materialId: Int,     // references Material.id
    val quantity: Double     // amount of material (e.g. 0.15 for 0.15g gold or 5.0 for 5kg steel)
)

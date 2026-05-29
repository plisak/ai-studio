package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ScrapDao {

    // --- MATERIALS ---
    @Query("SELECT * FROM materials ORDER BY category ASC, name ASC")
    fun getAllMaterials(): Flow<List<Material>>

    @Query("SELECT COUNT(*) FROM materials")
    suspend fun getMaterialsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterial(material: Material)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaterials(materials: List<Material>)

    @Update
    suspend fun updateMaterial(material: Material)

    @Query("DELETE FROM materials WHERE id = :id")
    suspend fun deleteMaterialById(id: Int)


    // --- CALCULATION ITEMS ---
    @Query("SELECT * FROM calculation_items ORDER BY id ASC")
    fun getAllCalculationItems(): Flow<List<CalculationItem>>

    @Query("SELECT * FROM calculation_items WHERE calculationId = 0 ORDER BY id ASC")
    fun getDraftItems(): Flow<List<CalculationItem>>

    @Query("SELECT * FROM calculation_items WHERE calculationId = :calcId ORDER BY id ASC")
    fun getItemsForCalculation(calcId: Long): Flow<List<CalculationItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalculationItem(item: CalculationItem)

    @Query("DELETE FROM calculation_items WHERE id = :id")
    suspend fun deleteCalculationItemById(id: Int)

    @Query("DELETE FROM calculation_items WHERE calculationId = 0")
    suspend fun clearDraft()

    @Query("DELETE FROM calculation_items WHERE calculationId = :calcId")
    suspend fun deleteItemsForCalculation(calcId: Long)

    @Query("UPDATE calculation_items SET calculationId = :newCalcId WHERE calculationId = 0")
    suspend fun convertDraftToSaved(newCalcId: Long)


    // --- SAVED CALCULATIONS ---
    @Query("SELECT * FROM saved_calculations ORDER BY timestamp DESC")
    fun getAllSavedCalculations(): Flow<List<SavedCalculation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedCalculation(calculation: SavedCalculation)

    @Query("DELETE FROM saved_calculations WHERE id = :id")
    suspend fun deleteSavedCalculationById(id: Long)

    // --- COMPLEX PRODUCTS & COMPONENTS ---
    @Query("SELECT * FROM complex_products ORDER BY name ASC")
    fun getAllComplexProducts(): Flow<List<ComplexProduct>>

    @Query("SELECT * FROM complex_product_components")
    fun getAllComplexProductComponents(): Flow<List<ComplexProductComponent>>

    @Query("SELECT COUNT(*) FROM complex_products")
    suspend fun getComplexProductsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplexProduct(product: ComplexProduct): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComplexProductComponents(components: List<ComplexProductComponent>)

    @Query("DELETE FROM complex_products WHERE id = :id")
    suspend fun deleteComplexProductById(id: Int)

    @Query("DELETE FROM complex_product_components WHERE productId = :productId")
    suspend fun deleteComponentsForProduct(productId: Int)
}

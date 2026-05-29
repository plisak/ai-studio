package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ScrapViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ScrapRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ScrapRepository(database.scrapDao())
        
        // Feed sample data if empty
        viewModelScope.launch {
            repository.checkAndSeedDefaults()
        }
    }

    // --- STATES ---
    val materials: StateFlow<List<Material>> = repository.allMaterials
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val draftItems: StateFlow<List<CalculationItem>> = repository.draftItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val savedCalculations: StateFlow<List<SavedCalculation>> = repository.savedCalculations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allCalculationItems: StateFlow<List<CalculationItem>> = repository.allCalculationItems
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val complexProductsWithDetails: StateFlow<List<ComplexProductWithDetails>> = combine(
        repository.allComplexProducts,
        repository.allComplexProductComponents,
        materials
    ) { products, components, mats ->
        val materialMap = mats.associateBy { it.id }
        val componentWithMaterialList = components.mapNotNull { comp ->
            val mat = materialMap[comp.materialId] ?: return@mapNotNull null
            ComponentWithMaterial(comp, mat)
        }
        val componentsByProduct = componentWithMaterialList.groupBy { it.component.productId }

        products.map { prod ->
            ComplexProductWithDetails(
                product = prod,
                components = componentsByProduct[prod.id] ?: emptyList()
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun calculateLivePrice(
        item: CalculationItem,
        mats: List<Material>,
        complexProds: List<ComplexProductWithDetails>
    ): Double {
        val isComplex = item.materialId < 0
        if (isComplex) {
            val prod = complexProds.find { it.id == -item.materialId } ?: return item.materialPrice
            val standardPricePerPiece = prod.totalPrice
            val weightInKg = prod.components.sumOf { comp ->
                val compQty = comp.component.quantity
                if (comp.material.unit.lowercase() == "g") compQty / 1000.0 else compQty
            }.takeIf { it > 0.0 } ?: 1.0

            return when (item.materialUnit.lowercase()) {
                "szt." -> standardPricePerPiece
                "kg" -> standardPricePerPiece / weightInKg
                "g", "gramy" -> (standardPricePerPiece / weightInKg) / 1000.0
                else -> item.materialPrice
            }
        } else {
            val mat = mats.find { it.id == item.materialId } ?: return item.materialPrice
            val standardPricePerBaseUnit = mat.pricePerUnit
            val baseUnit = mat.unit.lowercase()

            return when (item.materialUnit.lowercase()) {
                "kg" -> {
                    if (baseUnit == "kg") standardPricePerBaseUnit
                    else standardPricePerBaseUnit * 1000.0
                }
                "g", "gramy" -> {
                    if (baseUnit == "g") standardPricePerBaseUnit
                    else standardPricePerBaseUnit / 1000.0
                }
                "szt." -> {
                    val oldBasePrice = mat.pricePerUnit
                    val weightFactor = if (oldBasePrice > 0) item.materialPrice / oldBasePrice else 1.0
                    standardPricePerBaseUnit * weightFactor
                }
                else -> item.materialPrice
            }
        }
    }

    // Derived states
    val draftTotal: StateFlow<Double> = combine(
        draftItems,
        materials,
        complexProductsWithDetails
    ) { items, mats, complexProds ->
        items.sumOf { item ->
            val livePrice = calculateLivePrice(item, mats, complexProds)
            item.quantity * livePrice
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0.0
    )

    val dynamicSavedCalculations: StateFlow<List<SavedCalculationWithLiveTotal>> = combine(
        savedCalculations,
        allCalculationItems,
        materials,
        complexProductsWithDetails
    ) { calculations, allItems, mats, complexProds ->
        val itemsByCalcId = allItems.groupBy { it.calculationId }

        calculations.map { calc ->
            val items = itemsByCalcId[calc.id] ?: emptyList()
            val total = items.sumOf { item ->
                val livePrice = calculateLivePrice(item, mats, complexProds)
                item.quantity * livePrice
            }
            SavedCalculationWithLiveTotal(calc, total)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedCalculationId = MutableStateFlow<Long?>(null)
    val selectedCalculationId: StateFlow<Long?> = _selectedCalculationId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedCalculationItems: StateFlow<List<CalculationItem>> = _selectedCalculationId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList())
            else repository.getItemsForCalculation(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val liveSelectedCalculationItems: StateFlow<List<CalculationItem>> = combine(
        selectedCalculationItems,
        materials,
        complexProductsWithDetails
    ) { items, mats, complexProds ->
        items.map { item ->
            val livePrice = calculateLivePrice(item, mats, complexProds)
            item.copy(materialPrice = livePrice)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- ACTIONS ---

    // Materials
    fun saveMaterial(name: String, price: Double, unit: String, category: String, id: Int = 0) {
        viewModelScope.launch {
            repository.saveMaterial(
                Material(
                    id = id,
                    name = name,
                    pricePerUnit = price,
                    unit = unit,
                    category = category
                )
            )
        }
    }

    fun deleteMaterial(id: Int) {
        viewModelScope.launch {
            repository.deleteMaterial(id)
        }
    }

    // --- Complex Products ---
    fun saveComplexProduct(name: String, description: String, components: List<ComplexProductComponent>, id: Int = 0) {
        viewModelScope.launch {
            repository.saveComplexProduct(
                ComplexProduct(id = id, name = name, description = description),
                components
            )
        }
    }

    fun deleteComplexProduct(id: Int) {
        viewModelScope.launch {
            repository.deleteComplexProduct(id)
        }
    }

    fun addComplexProductToDraft(product: ComplexProduct, pricePerUnit: Double, quantity: Double) {
        viewModelScope.launch {
            repository.addComplexProductToDraft(product, pricePerUnit, quantity)
        }
    }

    fun addCustomDraftItem(materialId: Int, name: String, price: Double, unit: String, quantity: Double) {
        viewModelScope.launch {
            repository.addCustomDraftItem(materialId, name, price, unit, quantity)
        }
    }

    // Calculation Draft
    fun addDraftItem(material: Material, quantity: Double) {
        viewModelScope.launch {
            repository.addDraftItem(material, quantity)
        }
    }

    fun deleteDraftItem(id: Int) {
        viewModelScope.launch {
            repository.deleteDraftItem(id)
        }
    }

    fun clearDraft() {
        viewModelScope.launch {
            repository.clearDraft()
        }
    }

    fun saveCurrentCalculation(title: String) {
        viewModelScope.launch {
            val titleToSave = title.ifBlank { "Kalkulacja bez nazwy" }
            val total = draftTotal.value
            repository.saveCurrentCalculation(titleToSave, total)
        }
    }

    // Historical
    fun selectCalculation(id: Long?) {
        _selectedCalculationId.value = id
    }

    fun deleteSavedCalculation(id: Long) {
        viewModelScope.launch {
            repository.deleteSavedCalculation(id)
            if (_selectedCalculationId.value == id) {
                _selectedCalculationId.value = null
            }
        }
    }

    // ViewModel Factory
    class Factory(private val application: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ScrapViewModel::class.java)) {
                return ScrapViewModel(application) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

data class SavedCalculationWithLiveTotal(
    val calculation: SavedCalculation,
    val liveTotal: Double
)

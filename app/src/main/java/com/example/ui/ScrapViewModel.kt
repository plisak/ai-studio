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

    // --- COOP / BACKUP & CLEAR ACTIONS ---
    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _selectedCalculationId.value = null
        }
    }

    fun clearPriceList() {
        viewModelScope.launch {
            repository.clearPriceList()
        }
    }

    data class ImportResult(val success: Boolean, val message: String)

    suspend fun exportPriceListJson(): String {
        val materialsList = repository.allMaterials.first()
        val complexList = repository.allComplexProducts.first()
        val componentsList = repository.allComplexProductComponents.first()
        
        val root = org.json.JSONObject()
        
        // 1. Materials
        val materialsArray = org.json.JSONArray()
        for (m in materialsList) {
            val matObj = org.json.JSONObject()
            matObj.put("name", m.name)
            matObj.put("pricePerUnit", m.pricePerUnit)
            matObj.put("unit", m.unit)
            matObj.put("category", m.category)
            matObj.put("isDefault", m.isDefault)
            matObj.put("deleted", false)
            materialsArray.put(matObj)
        }
        root.put("materials", materialsArray)
        
        // 2. Complex products
        val complexArray = org.json.JSONArray()
        for (cp in complexList) {
            val cpObj = org.json.JSONObject()
            cpObj.put("name", cp.name)
            cpObj.put("description", cp.description)
            cpObj.put("isDefault", cp.isDefault)
            cpObj.put("deleted", false)
            
            val compArray = org.json.JSONArray()
            val matchComps = componentsList.filter { it.productId == cp.id }
            for (comp in matchComps) {
                val matName = materialsList.find { it.id == comp.materialId }?.name ?: ""
                if (matName.isNotEmpty()) {
                    val compObj = org.json.JSONObject()
                    compObj.put("materialName", matName)
                    compObj.put("quantity", comp.quantity)
                    compArray.put(compObj)
                }
            }
            cpObj.put("components", compArray)
            complexArray.put(cpObj)
        }
        root.put("complexProducts", complexArray)
        
        return root.toString(4) // 4 spaces indentation indent
    }

    suspend fun importPriceListJson(jsonString: String): ImportResult {
        return try {
            val root = org.json.JSONObject(jsonString)
            
            // 1. Materials
            val materialsArray = root.optJSONArray("materials")
            var materialsCount = 0
            var materialsDeletedCount = 0
            
            if (materialsArray != null) {
                for (i in 0 until materialsArray.length()) {
                    val matObj = materialsArray.getJSONObject(i)
                    val name = matObj.getString("name")
                    val isDeleted = matObj.optBoolean("deleted", false)
                    
                    val existingList = repository.allMaterials.first()
                    val existing = existingList.find { it.name.trim().lowercase() == name.trim().lowercase() }
                    
                    if (isDeleted) {
                        if (existing != null) {
                            repository.deleteMaterial(existing.id)
                            materialsDeletedCount++
                        }
                    } else {
                        val pricePerUnit = matObj.getDouble("pricePerUnit")
                        val unit = matObj.getString("unit")
                        val category = matObj.getString("category")
                        val isDefault = matObj.optBoolean("isDefault", false)
                        
                        if (existing != null) {
                            val updated = existing.copy(
                                pricePerUnit = pricePerUnit,
                                unit = unit,
                                category = category,
                                isDefault = isDefault
                            )
                            repository.saveMaterial(updated)
                        } else {
                            val newMat = Material(
                                name = name,
                                pricePerUnit = pricePerUnit,
                                unit = unit,
                                category = category,
                                isDefault = isDefault
                            )
                            repository.saveMaterial(newMat)
                        }
                        materialsCount++
                    }
                }
            }
            
            // 2. Complex products
            val complexArray = root.optJSONArray("complexProducts")
            var complexCount = 0
            var complexDeletedCount = 0
            
            if (complexArray != null) {
                for (i in 0 until complexArray.length()) {
                    val cpObj = complexArray.getJSONObject(i)
                    val name = cpObj.getString("name")
                    val isDeleted = cpObj.optBoolean("deleted", false)
                    
                    val existingComplexList = repository.allComplexProducts.first()
                    val existing = existingComplexList.find { it.name.trim().lowercase() == name.trim().lowercase() }
                    
                    if (isDeleted) {
                        if (existing != null) {
                            repository.deleteComplexProduct(existing.id)
                            complexDeletedCount++
                        }
                    } else {
                        val description = cpObj.optString("description", "")
                        val isDefault = cpObj.optBoolean("isDefault", false)
                        
                        val freshMaterials = repository.allMaterials.first()
                        val componentsJson = cpObj.optJSONArray("components")
                        val parsedComponents = mutableListOf<ComplexProductComponent>()
                        
                        if (componentsJson != null) {
                            for (j in 0 until componentsJson.length()) {
                                val compObj = componentsJson.getJSONObject(j)
                                val matName = compObj.getString("materialName")
                                val quantity = compObj.getDouble("quantity")
                                
                                val matchedMat = freshMaterials.find { it.name.trim().lowercase() == matName.trim().lowercase() }
                                if (matchedMat != null) {
                                    parsedComponents.add(
                                        ComplexProductComponent(
                                            productId = existing?.id ?: 0,
                                            materialId = matchedMat.id,
                                            quantity = quantity
                                        )
                                    )
                                }
                            }
                        }
                        
                        if (existing != null) {
                            repository.saveComplexProduct(
                                existing.copy(description = description, isDefault = isDefault),
                                parsedComponents
                            )
                        } else {
                            repository.saveComplexProduct(
                                ComplexProduct(name = name, description = description, isDefault = isDefault),
                                parsedComponents
                            )
                        }
                        complexCount++
                    }
                }
            }
            
            val summaryParts = mutableListOf<String>()
            if (materialsCount > 0) summaryParts.add("dodano/zaktualizowano $materialsCount prostych")
            if (materialsDeletedCount > 0) summaryParts.add("usunięto $materialsDeletedCount prostych")
            if (complexCount > 0) summaryParts.add("dodano/zaktualizowano $complexCount złożonych")
            if (complexDeletedCount > 0) summaryParts.add("usunięto $complexDeletedCount złożonych")
            
            val summary = if (summaryParts.isEmpty()) "brak zmian" else summaryParts.joinToString(", ")
            ImportResult(true, summary)
        } catch (e: Exception) {
            ImportResult(false, e.message ?: "Błąd parsowania pliku JSON")
        }
    }

    private fun escapeCsv(value: String): String {
        val containsSemicolon = value.contains(";")
        val containsQuote = value.contains("\"")
        val containsNewLine = value.contains("\n") || value.contains("\r")
        if (containsSemicolon || containsQuote || containsNewLine) {
            val escaped = value.replace("\"", "\"\"")
            return "\"$escaped\""
        }
        return value
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    cur.append('"')
                    i++
                } else {
                    inQuotes = !inQuotes
                }
            } else if (c == ';' && !inQuotes) {
                result.add(cur.toString())
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        result.add(cur.toString())
        return result
    }

    suspend fun exportPriceListCsv(): String {
        val materialsList = repository.allMaterials.first()
        val complexList = repository.allComplexProducts.first()
        val componentsList = repository.allComplexProductComponents.first()

        val sb = StringBuilder()
        sb.append("# Instrukcja: Typ to METAL (prosty materiał) lub ZESTAW (złożony produkt). Separator kolumn to średnik (;)\n")
        sb.append("# Dla METAL uzupełnij: Nazwa, Cena, Jednostka (np. kg, g, szt.), Kategoria (np. Metale Kolorowe, Metale Szlachetne, Stal i Żeliwo, Inne)\n")
        sb.append("# Dla ZESTAW uzupełnij: Nazwa, Opis, Skład w formacie NazwaMateriału:Masa, oddzielone przecinkiem (np. Miedź Świecąca:0.7, Cynk:0.3)\n")
        sb.append("Typ;Nazwa;Cena;Jednostka;Kategoria;Opis;Sklad\n")

        // 1. Materials
        for (m in materialsList) {
            val typ = "METAL"
            val nazwa = escapeCsv(m.name)
            val cena = m.pricePerUnit.toString().replace(".", ",") // Excel decimal point in Europe (Poland)
            val jednostka = escapeCsv(m.unit)
            val kategoria = escapeCsv(m.category)
            val opis = ""
            val sklad = ""
            sb.append("$typ;$nazwa;$cena;$jednostka;$kategoria;$opis;$sklad\n")
        }

        // 2. Complex products
        for (cp in complexList) {
            val typ = "ZESTAW"
            val nazwa = escapeCsv(cp.name)
            val cena = ""
            val jednostka = ""
            val kategoria = ""
            val opis = escapeCsv(cp.description)
            
            val matchComps = componentsList.filter { it.productId == cp.id }
            val compPairs = mutableListOf<String>()
            for (comp in matchComps) {
                val matName = materialsList.find { it.id == comp.materialId }?.name ?: ""
                if (matName.isNotEmpty()) {
                    compPairs.add("$matName:${comp.quantity}")
                }
            }
            val sklad = escapeCsv(compPairs.joinToString(", "))
            sb.append("$typ;$nazwa;$cena;$jednostka;$kategoria;$opis;$sklad\n")
        }

        return sb.toString()
    }

    suspend fun importPriceListCsv(csvString: String): ImportResult {
        return try {
            val lines = csvString.split(Regex("\\r?\\n"))
            var materialsCount = 0
            var complexCount = 0

            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue
                }
                
                val parts = splitCsvLine(trimmed)
                if (parts.size < 2) continue
                
                val typ = parts[0].trim().uppercase()
                // If it's a header line, skip it
                if (typ == "TYP" && parts[1].trim().uppercase() == "NAZWA") {
                    continue
                }

                val name = parts[1].trim()
                if (name.isEmpty()) continue

                if (typ == "METAL" || typ == "PROSTY" || typ == "MATERIAL") {
                    // It's a simple material
                    val cenaStr = parts.getOrNull(2)?.trim()?.replace(",", ".") ?: "0.0"
                    val cena = cenaStr.toDoubleOrNull() ?: 0.0
                    val jednostka = parts.getOrNull(3)?.trim()?.ifBlank { "kg" } ?: "kg"
                    val kategoria = parts.getOrNull(4)?.trim()?.ifBlank { "Metale Kolorowe" } ?: "Metale Kolorowe"

                    val existingList = repository.allMaterials.first()
                    val existing = existingList.find { it.name.trim().lowercase() == name.lowercase() }

                    if (existing != null) {
                        val updated = existing.copy(
                            pricePerUnit = cena,
                            unit = jednostka,
                            category = kategoria
                        )
                        repository.saveMaterial(updated)
                    } else {
                        val newMat = Material(
                            name = name,
                            pricePerUnit = cena,
                            unit = jednostka,
                            category = kategoria,
                            isDefault = false
                        )
                        repository.saveMaterial(newMat)
                    }
                    materialsCount++
                } else if (typ == "ZESTAW" || typ == "ZLOZONY" || typ == "PRODUKT_ZLOZONY") {
                    // It's a complex product
                    val opis = parts.getOrNull(5)?.trim() ?: ""
                    val skladStr = parts.getOrNull(6)?.trim() ?: ""

                    val existingComplexList = repository.allComplexProducts.first()
                    val existing = existingComplexList.find { it.name.trim().lowercase() == name.lowercase() }

                    val freshMaterials = repository.allMaterials.first()
                    val parsedComponents = mutableListOf<ComplexProductComponent>()

                    if (skladStr.isNotEmpty()) {
                        // Sklad format: "Miedź:0.5, Cynk:0.5"
                        val compParts = skladStr.split(Regex("[,|]"))
                        for (cpPart in compParts) {
                            val singleComp = cpPart.trim()
                            if (singleComp.isEmpty()) continue
                            val colonIdx = singleComp.lastIndexOf(':')
                            if (colonIdx != -1) {
                                val matName = singleComp.substring(0, colonIdx).trim()
                                val qtyStr = singleComp.substring(colonIdx + 1).trim().replace(",", ".")
                                val qty = qtyStr.toDoubleOrNull() ?: 0.0
                                
                                val matchedMat = freshMaterials.find { it.name.trim().lowercase() == matName.lowercase() }
                                if (matchedMat != null) {
                                    parsedComponents.add(
                                        ComplexProductComponent(
                                            productId = existing?.id ?: 0,
                                            materialId = matchedMat.id,
                                            quantity = qty
                                        )
                                    )
                                }
                            }
                        }
                    }

                    if (existing != null) {
                        repository.saveComplexProduct(
                            existing.copy(description = opis),
                            parsedComponents
                        )
                    } else {
                        repository.saveComplexProduct(
                            ComplexProduct(name = name, description = opis, isDefault = false),
                            parsedComponents
                        )
                    }
                    complexCount++
                }
            }

            val summaryParts = mutableListOf<String>()
            if (materialsCount > 0) summaryParts.add("dodano/zaktualizowano $materialsCount prostych")
            if (complexCount > 0) summaryParts.add("dodano/zaktualizowano $complexCount złożonych")
            
            val summary = if (summaryParts.isEmpty()) "brak zmian" else summaryParts.joinToString(", ")
            ImportResult(true, summary)
        } catch (e: Exception) {
            ImportResult(false, e.message ?: "Błąd parsowania pliku CSV")
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

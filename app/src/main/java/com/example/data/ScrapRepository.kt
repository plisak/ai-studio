package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ScrapRepository(private val scrapDao: ScrapDao) {

    val allMaterials: Flow<List<Material>> = scrapDao.getAllMaterials()
    val draftItems: Flow<List<CalculationItem>> = scrapDao.getDraftItems()
    val allCalculationItems: Flow<List<CalculationItem>> = scrapDao.getAllCalculationItems()
    val savedCalculations: Flow<List<SavedCalculation>> = scrapDao.getAllSavedCalculations()
    val allComplexProducts: Flow<List<ComplexProduct>> = scrapDao.getAllComplexProducts()
    val allComplexProductComponents: Flow<List<ComplexProductComponent>> = scrapDao.getAllComplexProductComponents()

    suspend fun checkAndSeedDefaults() {
        if (scrapDao.getMaterialsCount() == 0) {
            val defaults = listOf(
                Material(name = "Złoto próby 585 (14K)", pricePerUnit = 180.00, unit = "g", category = "Metale Szlachetne", isDefault = true),
                Material(name = "Złoto próby 333 (8K)", pricePerUnit = 95.00, unit = "g", category = "Metale Szlachetne", isDefault = true),
                Material(name = "Srebro próby 925", pricePerUnit = 3.20, unit = "g", category = "Metale Szlachetne", isDefault = true),
                Material(name = "Srebro próby 800", pricePerUnit = 2.70, unit = "g", category = "Metale Szlachetne", isDefault = true),
                Material(name = "Miedź Millbera", pricePerUnit = 32.50, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Miedź Kawałkowa", pricePerUnit = 30.00, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Miedź Piecyki / Izolowana", pricePerUnit = 22.00, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Mosiądz (żółty/mieszany)", pricePerUnit = 18.50, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Aluminium (czyste/sektor)", pricePerUnit = 8.50, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Aluminium puszki", pricePerUnit = 6.00, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Ołów czysty", pricePerUnit = 7.20, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Akumulatory ołowiowe", pricePerUnit = 3.50, unit = "kg", category = "Metale Kolorowe", isDefault = true),
                Material(name = "Stal gruba wsadowa", pricePerUnit = 1.10, unit = "kg", category = "Stal i Żeliwo", isDefault = true),
                Material(name = "Stal cienka wióry", pricePerUnit = 0.90, unit = "kg", category = "Stal i Żeliwo", isDefault = true),
                Material(name = "Żeliwo", pricePerUnit = 1.25, unit = "kg", category = "Stal i Żeliwo", isDefault = true)
            )
            scrapDao.insertMaterials(defaults)
        }

        if (scrapDao.getComplexProductsCount() == 0) {
            // Let's retrieve materials to match the exact database IDs after insertion
            val materialsList = scrapDao.getAllMaterials().first()
            val gold585 = materialsList.find { it.name.contains("Złoto próby 585") }?.id ?: 1
            val silver925 = materialsList.find { it.name.contains("Srebro próby 925") }?.id ?: 3
            val copperKawalkowa = materialsList.find { it.name.contains("Miedź Kawałkowa") }?.id ?: 6
            val copperMillbera = materialsList.find { it.name.contains("Miedź Millbera") }?.id ?: 5
            val alumCzyste = materialsList.find { it.name.contains("Aluminium (czyste") }?.id ?: 9
            val steelCienka = materialsList.find { it.name.contains("Stal cienka") }?.id ?: 14

            val hddId = scrapDao.insertComplexProduct(
                ComplexProduct(
                    name = "Dysk twardy HDD (sztuka)",
                    description = "Uśredniony odzysk metali z jednego dysku komputerowego 3.5\"",
                    isDefault = true
                )
            ).toInt()

            val boardId = scrapDao.insertComplexProduct(
                ComplexProduct(
                    name = "Płytka PCB Klasa A (kg)",
                    description = "Płyty główne z elektroniki premium o wysokim zagęszczeniu metali",
                    isDefault = true
                )
            ).toInt()

            val hddComponents = listOf(
                ComplexProductComponent(productId = hddId, materialId = gold585, quantity = 0.12),        // 0.12g gold
                ComplexProductComponent(productId = hddId, materialId = copperKawalkowa, quantity = 0.08), // 0.08kg copper
                ComplexProductComponent(productId = hddId, materialId = alumCzyste, quantity = 0.25),      // 0.25kg alum
                ComplexProductComponent(productId = hddId, materialId = steelCienka, quantity = 0.45)      // 0.45kg steel
            )

            val boardComponents = listOf(
                ComplexProductComponent(productId = boardId, materialId = gold585, quantity = 0.28),        // 0.28g gold
                ComplexProductComponent(productId = boardId, materialId = silver925, quantity = 1.50),       // 1.50g silver
                ComplexProductComponent(productId = boardId, materialId = copperMillbera, quantity = 0.30)    // 0.30kg copper
            )

            scrapDao.insertComplexProductComponents(hddComponents + boardComponents)
        }
    }

    suspend fun saveComplexProduct(product: ComplexProduct, components: List<ComplexProductComponent>) {
        if (product.id != 0) {
            scrapDao.deleteComponentsForProduct(product.id)
            scrapDao.insertComplexProduct(product)
            val finalComps = components.map { it.copy(productId = product.id) }
            scrapDao.insertComplexProductComponents(finalComps)
        } else {
            val newId = scrapDao.insertComplexProduct(product).toInt()
            val finalComps = components.map { it.copy(productId = newId) }
            scrapDao.insertComplexProductComponents(finalComps)
        }
    }

    suspend fun deleteComplexProduct(id: Int) {
        val allProducts = allComplexProducts.first()
        val prod = allProducts.find { it.id == id }
        if (prod != null && prod.isDefault) {
             return
        }
        scrapDao.deleteComplexProductById(id)
        scrapDao.deleteComponentsForProduct(id)
    }

    suspend fun addComplexProductToDraft(product: ComplexProduct, pricePerUnit: Double, quantity: Double) {
        val existing = scrapDao.getDraftItems().first()
        val alreadyAdded = existing.find { it.materialId == -product.id }

        if (alreadyAdded != null) {
            val updated = alreadyAdded.copy(
                quantity = alreadyAdded.quantity + quantity,
                materialPrice = pricePerUnit,
                materialName = product.name,
                materialUnit = "szt."
            )
            scrapDao.insertCalculationItem(updated)
        } else {
            val newItem = CalculationItem(
                calculationId = 0,
                materialId = -product.id,
                materialName = product.name,
                materialPrice = pricePerUnit,
                materialUnit = "szt.",
                quantity = quantity
            )
            scrapDao.insertCalculationItem(newItem)
        }
    }

    suspend fun saveMaterial(material: Material) {
        if (material.id == 0) {
            scrapDao.insertMaterial(material)
        } else {
            scrapDao.updateMaterial(material)
        }
    }

    suspend fun deleteMaterial(id: Int) {
        val allMaterialsList = allMaterials.first()
        val mat = allMaterialsList.find { it.id == id }
        if (mat != null && mat.isDefault) {
             return
        }
        scrapDao.deleteMaterialById(id)
    }

    suspend fun addCustomDraftItem(materialId: Int, name: String, price: Double, unit: String, quantity: Double) {
        val existing = scrapDao.getDraftItems().first()
        val alreadyAdded = existing.find { it.materialId == materialId && it.materialUnit == unit }

        if (alreadyAdded != null) {
            val updated = alreadyAdded.copy(
                quantity = alreadyAdded.quantity + quantity,
                materialPrice = price,
                materialName = name
            )
            scrapDao.insertCalculationItem(updated)
        } else {
            val newItem = CalculationItem(
                calculationId = 0,
                materialId = materialId,
                materialName = name,
                materialPrice = price,
                materialUnit = unit,
                quantity = quantity
            )
            scrapDao.insertCalculationItem(newItem)
        }
    }

    suspend fun addDraftItem(material: Material, quantity: Double) {
        val existing = scrapDao.getDraftItems().first()
        val alreadyAdded = existing.find { it.materialId == material.id }

        if (alreadyAdded != null) {
            // Update quantity
            val updated = alreadyAdded.copy(
                quantity = alreadyAdded.quantity + quantity,
                materialPrice = material.pricePerUnit, // update to latest price
                materialName = material.name,
                materialUnit = material.unit
            )
            scrapDao.insertCalculationItem(updated)
        } else {
            // Insert new item
            val newItem = CalculationItem(
                calculationId = 0,
                materialId = material.id,
                materialName = material.name,
                materialPrice = material.pricePerUnit,
                materialUnit = material.unit,
                quantity = quantity
            )
            scrapDao.insertCalculationItem(newItem)
        }
    }

    suspend fun deleteDraftItem(id: Int) {
        scrapDao.deleteCalculationItemById(id)
    }

    suspend fun clearDraft() {
        scrapDao.clearDraft()
    }

    suspend fun saveCurrentCalculation(title: String, total: Double) {
        val timestamp = System.currentTimeMillis()
        val header = SavedCalculation(
            id = timestamp,
            title = title,
            timestamp = timestamp,
            totalAmount = total
        )
        // Insert header
        scrapDao.insertSavedCalculation(header)
        // Convert live draft items to historic items mapped to this timestamp
        scrapDao.convertDraftToSaved(timestamp)
    }

    suspend fun deleteSavedCalculation(id: Long) {
        scrapDao.deleteSavedCalculationById(id)
        scrapDao.deleteItemsForCalculation(id)
    }

    fun getItemsForCalculation(id: Long): Flow<List<CalculationItem>> {
        return scrapDao.getItemsForCalculation(id)
    }
}

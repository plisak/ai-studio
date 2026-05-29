package com.example.data

data class ComplexProductWithDetails(
    val product: ComplexProduct,
    val components: List<ComponentWithMaterial>
) {
    val id: Int get() = product.id
    val name: String get() = product.name
    val description: String get() = product.description

    // Computes dynamic total price based on current prices of standard metals in database
    val totalPrice: Double
        get() = components.sumOf { it.totalCost }
}

data class ComponentWithMaterial(
    val component: ComplexProductComponent,
    val material: Material
) {
    val totalCost: Double
        get() = component.quantity * material.pricePerUnit
}

package io.embrace.android.exampleapp.paradigms.data

data class Product(
    val id: String,
    val categoryId: String,
    val title: String,
    val priceCents: Long,
    val rating: Double,
    val reviewCount: Int,
    val description: String,
)

data class ProductCategory(
    val id: String,
    val title: String,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
)

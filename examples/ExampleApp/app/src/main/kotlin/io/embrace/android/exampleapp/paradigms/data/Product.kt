package io.embrace.android.exampleapp.paradigms.data

import kotlinx.serialization.Serializable

@Serializable
data class Product(
    val id: String,
    val categoryId: String,
    val title: String,
    val priceCents: Long,
    val rating: Double,
    val reviewCount: Int,
    val description: String,
    val thumbnail: ImageSource? = null,
    val gallery: List<ImageSource> = emptyList(),
    val bullets: List<String> = emptyList(),
    val specifications: List<Specification> = emptyList(),
    val deliveryEta: String = "",
    val isPrime: Boolean = false,
    val originalPriceCents: Long? = null,
    val brand: String = "",
)

@Serializable
data class ProductCategory(
    val id: String,
    val title: String,
    val accentSeed: Long = 0L,
)

@Serializable
data class Specification(
    val key: String,
    val value: String,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
)

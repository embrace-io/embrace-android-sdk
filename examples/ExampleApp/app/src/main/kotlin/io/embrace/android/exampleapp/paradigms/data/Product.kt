package io.embrace.android.exampleapp.paradigms.data

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
    val specifications: List<Pair<String, String>> = emptyList(),
    val deliveryEta: String = "",
    val isPrime: Boolean = false,
    val originalPriceCents: Long? = null,
    val brand: String = "",
)

data class ProductCategory(
    val id: String,
    val title: String,
    val accentSeed: Long = 0L,
)

data class CartLine(
    val product: Product,
    val quantity: Int,
)

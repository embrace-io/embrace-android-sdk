package io.embrace.android.exampleapp.paradigms.ecommerce

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import io.embrace.android.exampleapp.paradigms.data.CartLine
import io.embrace.android.exampleapp.paradigms.data.Product

object EcommerceCartStore {

    private val backing: SnapshotStateList<CartLine> = mutableStateListOf()

    val items: List<CartLine> get() = backing
    val itemCount: Int get() = backing.sumOf { it.quantity }
    val totalCents: Long get() = backing.sumOf { it.product.priceCents * it.quantity }

    fun add(product: Product) {
        val existingIndex = backing.indexOfFirst { it.product.id == product.id }
        if (existingIndex >= 0) {
            val current = backing[existingIndex]
            backing[existingIndex] = current.copy(quantity = current.quantity + 1)
        } else {
            backing.add(CartLine(product = product, quantity = 1))
        }
    }

    fun remove(productId: String) {
        backing.removeAll { it.product.id == productId }
    }

    fun clear() {
        backing.clear()
    }
}

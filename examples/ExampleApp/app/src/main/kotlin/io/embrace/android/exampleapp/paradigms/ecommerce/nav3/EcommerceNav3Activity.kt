package io.embrace.android.exampleapp.paradigms.ecommerce.nav3

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.ecommerce.EcommerceCartStore
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCartUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCategoriesUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductDetailUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceNav3Activity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val backStack = remember { mutableStateListOf<EcommerceNav3Key>(EcommerceNav3Key.Categories) }
                var orderPlacedTotalCents by remember { mutableStateOf<Long?>(null) }
                NavDisplay(
                    backStack = backStack,
                    onBack = { backStack.removeLastOrNull() },
                    entryProvider = { key ->
                        when (key) {
                            is EcommerceNav3Key.Categories -> NavEntry(key) {
                                EcommerceCategoriesUi(
                                    title = "Categories (Nav3)",
                                    categories = SampleData.productCategories,
                                    onCategoryClick = { id ->
                                        backStack.add(EcommerceNav3Key.ProductList(id))
                                    },
                                    cartItemCount = EcommerceCartStore.itemCount,
                                    onCartClick = { backStack.add(EcommerceNav3Key.Cart) },
                                    orderPlacedTotalCents = orderPlacedTotalCents,
                                )
                            }
                            is EcommerceNav3Key.ProductList -> NavEntry(key) {
                                val category = SampleData.category(key.categoryId)
                                if (category == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    EcommerceProductListUi(
                                        categoryTitle = category.title,
                                        products = SampleData.productsIn(category.id),
                                        onProductClick = { id ->
                                            backStack.add(EcommerceNav3Key.ProductDetail(id))
                                        },
                                        onBack = { backStack.removeLastOrNull() },
                                    )
                                }
                            }
                            is EcommerceNav3Key.ProductDetail -> NavEntry(key) {
                                val product = SampleData.product(key.productId)
                                if (product == null) {
                                    backStack.removeLastOrNull()
                                } else {
                                    EcommerceProductDetailUi(
                                        product = product,
                                        onBack = { backStack.removeLastOrNull() },
                                        onAddToCart = { EcommerceCartStore.add(product) },
                                    )
                                }
                            }
                            is EcommerceNav3Key.Cart -> NavEntry(key) {
                                EcommerceCartUi(
                                    items = EcommerceCartStore.items,
                                    totalCents = EcommerceCartStore.totalCents,
                                    onRemove = { id -> EcommerceCartStore.remove(id) },
                                    onPlaceOrder = {
                                        orderPlacedTotalCents = EcommerceCartStore.totalCents
                                        EcommerceCartStore.clear()
                                        backStack.removeLastOrNull()
                                    },
                                    onBack = { backStack.removeLastOrNull() },
                                )
                            }
                        }
                    },
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, EcommerceNav3Activity::class.java)
    }
}

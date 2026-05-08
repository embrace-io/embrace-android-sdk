package io.embrace.android.exampleapp.paradigms.ecommerce.navcompose

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCartUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCategoriesUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductDetailUi
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceNavComposeActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val graph = appGraph()
                val sampleData = graph.sampleData
                val cartStore = graph.cartStore
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = EcommerceRoute.Categories,
                ) {
                    composable<EcommerceRoute.Categories> { entry ->
                        val savedHandle = entry.savedStateHandle
                        val orderPlacedTotal by savedHandle
                            .getStateFlow<Long?>(SAVED_STATE_ORDER_PLACED_TOTAL_CENTS, null)
                            .collectAsState()
                        EcommerceCategoriesUi(
                            title = "Categories (Nav-Compose)",
                            categories = sampleData.productCategories,
                            onCategoryClick = { id ->
                                navController.navigate(EcommerceRoute.ProductList(id))
                            },
                            cartItemCount = cartStore.itemCount,
                            onCartClick = {
                                navController.navigate(EcommerceRoute.Cart)
                            },
                            orderPlacedTotalCents = orderPlacedTotal,
                        )
                    }
                    composable<EcommerceRoute.ProductList> { entry ->
                        val route: EcommerceRoute.ProductList = entry.toRoute()
                        val category = sampleData.category(route.categoryId)
                        if (category == null) {
                            navController.popBackStack()
                        } else {
                            EcommerceProductListUi(
                                categoryTitle = category.title,
                                products = sampleData.productsIn(category.id),
                                onProductClick = { id ->
                                    navController.navigate(EcommerceRoute.ProductDetail(id))
                                },
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable<EcommerceRoute.ProductDetail> { entry ->
                        val route: EcommerceRoute.ProductDetail = entry.toRoute()
                        val product = sampleData.product(route.productId)
                        if (product == null) {
                            navController.popBackStack()
                        } else {
                            EcommerceProductDetailUi(
                                product = product,
                                onBack = { navController.popBackStack() },
                                onAddToCart = { cartStore.add(product) },
                            )
                        }
                    }
                    composable<EcommerceRoute.Cart> {
                        EcommerceCartUi(
                            items = cartStore.items,
                            totalCents = cartStore.totalCents,
                            onRemove = { id -> cartStore.remove(id) },
                            onPlaceOrder = {
                                val total = cartStore.totalCents
                                cartStore.clear()
                                navController.previousBackStackEntry
                                    ?.savedStateHandle
                                    ?.set(SAVED_STATE_ORDER_PLACED_TOTAL_CENTS, total)
                                navController.popBackStack()
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, EcommerceNavComposeActivity::class.java)
    }
}

package io.embrace.android.exampleapp.paradigms.ecommerce.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.ProductCategory
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcommerceCategoriesUi(
    title: String,
    categories: List<ProductCategory>,
    onCategoryClick: (categoryId: String) -> Unit,
    cartItemCount: Int = 0,
    onCartClick: (() -> Unit)? = null,
    orderPlacedTotalCents: Long? = null,
) {
    val sampleData = appGraph().sampleData
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(orderPlacedTotalCents) {
        if (orderPlacedTotalCents != null) {
            snackbarHostState.showSnackbar("Order placed: ${formatPrice(orderPlacedTotalCents)}")
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(title) },
                colors = appBarColors(),
                actions = {
                    if (onCartClick != null) {
                        IconButton(onClick = onCartClick) {
                            BadgedBox(
                                badge = {
                                    if (cartItemCount > 0) {
                                        Badge { Text(cartItemCount.toString()) }
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = "Cart",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            items(items = categories, key = { it.id }) { category ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(category.id) },
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                        Text(text = category.title, style = MaterialTheme.typography.titleMedium)
                        val count = sampleData.productsIn(category.id).size
                        Text(
                            text = "$count items",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

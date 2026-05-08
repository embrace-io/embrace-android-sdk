package io.embrace.android.exampleapp.paradigms.ecommerce.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.CartLine
import io.embrace.android.exampleapp.ui.appBarColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcommerceCartUi(
    items: List<CartLine>,
    totalCents: Long,
    onRemove: (productId: String) -> Unit,
    onPlaceOrder: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Cart") },
                colors = appBarColors(),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Your cart is empty",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = items, key = { it.product.id }) { line ->
                        CartRow(line = line, onRemove = onRemove)
                        HorizontalDivider()
                    }
                }
                CheckoutFooter(totalCents = totalCents, onPlaceOrder = onPlaceOrder)
            }
        }
    }
}

@Composable
private fun CartRow(line: CartLine, onRemove: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = line.product.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${line.quantity} × ${formatPrice(line.product.priceCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatPrice(line.product.priceCents * line.quantity),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(end = 12.dp),
        )
        IconButton(onClick = { onRemove(line.product.id) }) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${line.product.title}",
            )
        }
    }
}

@Composable
private fun CheckoutFooter(totalCents: Long, onPlaceOrder: () -> Unit) {
    HorizontalDivider()
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Total", style = MaterialTheme.typography.labelMedium)
            Text(text = formatPrice(totalCents), style = MaterialTheme.typography.headlineSmall)
        }
        Button(onClick = onPlaceOrder) {
            Text("Place Order")
        }
    }
}

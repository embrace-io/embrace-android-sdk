package io.embrace.android.exampleapp.paradigms.ecommerce.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.embrace.android.exampleapp.paradigms.data.Product
import io.embrace.android.exampleapp.paradigms.ui.ImageItem
import io.embrace.android.exampleapp.ui.appBarColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EcommerceProductDetailUi(
    product: Product,
    onBack: () -> Unit,
    onAddToCart: (() -> Unit)? = null,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Product") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            ProductGallery(product = product)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                if (product.brand.isNotEmpty()) {
                    Text(
                        text = "Visit the ${product.brand} store",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(text = product.title, style = MaterialTheme.typography.headlineSmall)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarRow(rating = product.rating)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${product.reviewCount} ratings",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                PriceBlock(product = product)
                Spacer(modifier = Modifier.height(12.dp))
                DeliveryBlock(product = product)
                Spacer(modifier = Modifier.height(16.dp))
                if (onAddToCart != null) {
                    PurchaseButtons(
                        onAddToCart = {
                            onAddToCart()
                            scope.launch {
                                snackbarHostState.showSnackbar("Added to cart: ${product.title}")
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }
                BulletList(bullets = product.bullets)
                Spacer(modifier = Modifier.height(20.dp))
                Text(text = "Description", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(6.dp))
                Text(text = product.description, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(20.dp))
                if (product.specifications.isNotEmpty()) {
                    SpecificationsTable(specifications = product.specifications)
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ProductGallery(product: Product) {
    val images = product.gallery.ifEmpty { listOfNotNull(product.thumbnail) }
    if (images.isEmpty()) return
    Column {
        Box(modifier = Modifier.fillMaxWidth()) {
            ImageItem(
                source = images.first(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (images.size > 1) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(items = images, key = { it.hashCode() }) { source ->
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    ) {
                        ImageItem(
                            source = source,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceBlock(product: Product) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = formatPrice(product.priceCents), style = MaterialTheme.typography.headlineSmall)
        if (product.originalPriceCents != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = formatPrice(product.originalPriceCents),
                style = MaterialTheme.typography.bodyMedium.copy(textDecoration = TextDecoration.LineThrough),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (product.isPrime) {
        Spacer(modifier = Modifier.height(6.dp))
        PrimeBadge()
    }
}

@Composable
private fun DeliveryBlock(product: Product) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = product.deliveryEta,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "In stock",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2E7D32),
                )
            }
        }
    }
}

@Composable
private fun PurchaseButtons(onAddToCart: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = onAddToCart,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Add to Cart")
        }
        OutlinedButton(
            onClick = {},
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Buy Now")
        }
    }
}

@Composable
private fun BulletList(bullets: List<String>) {
    if (bullets.isEmpty()) return
    Text(text = "About this item", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        bullets.forEach { bullet ->
            Row(verticalAlignment = Alignment.Top) {
                Text(text = "• ", style = MaterialTheme.typography.bodyMedium)
                Text(text = bullet, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SpecificationsTable(specifications: List<Pair<String, String>>) {
    Text(text = "Specifications", style = MaterialTheme.typography.titleSmall)
    Spacer(modifier = Modifier.height(6.dp))
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            specifications.forEachIndexed { idx, (key, value) ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(
                        text = key,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(120.dp),
                    )
                    Text(text = value, style = MaterialTheme.typography.bodyMedium)
                }
                if (idx < specifications.lastIndex) {
                    HorizontalDivider()
                }
            }
        }
    }
}

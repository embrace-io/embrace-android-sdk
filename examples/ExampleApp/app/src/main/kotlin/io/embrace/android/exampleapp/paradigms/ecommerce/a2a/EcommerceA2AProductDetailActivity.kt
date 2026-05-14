package io.embrace.android.exampleapp.paradigms.ecommerce.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductDetailUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceA2AProductDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = appGraph()
        val sampleData = graph.sampleData
        val cartStore = graph.cartStore
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID)
        val product = productId?.let(sampleData::product)
        if (product == null) {
            Toast.makeText(this, "Unknown product", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        setContent {
            ExampleAppTheme {
                EcommerceProductDetailUi(
                    product = product,
                    onBack = { finish() },
                    onAddToCart = { cartStore.add(product) },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PRODUCT_ID = "product_id"

        fun newIntent(context: Context, productId: String): Intent =
            Intent(context, EcommerceA2AProductDetailActivity::class.java)
                .putExtra(EXTRA_PRODUCT_ID, productId)
    }
}

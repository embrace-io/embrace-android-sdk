package io.embrace.android.exampleapp.paradigms.ecommerce.a2a

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.di.appGraph
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCartUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceA2ACartActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                val cartStore = appGraph().cartStore
                EcommerceCartUi(
                    items = cartStore.items,
                    totalCents = cartStore.totalCents,
                    onRemove = { id -> cartStore.remove(id) },
                    onPlaceOrder = {
                        val total = cartStore.totalCents
                        cartStore.clear()
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_ORDER_TOTAL_CENTS, total),
                        )
                        finish()
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_ORDER_TOTAL_CENTS: String = "order_total_cents"

        fun newIntent(context: Context): Intent =
            Intent(context, EcommerceA2ACartActivity::class.java)
    }
}

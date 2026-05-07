package io.embrace.android.exampleapp.paradigms.ecommerce.a2a

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.ecommerce.EcommerceCartStore
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceCategoriesUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceA2ACategoriesActivity : ComponentActivity() {

    private var orderPlacedTotalCents by mutableStateOf<Long?>(null)

    private val cartLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            orderPlacedTotalCents = result.data
                ?.getLongExtra(EcommerceA2ACartActivity.EXTRA_ORDER_TOTAL_CENTS, 0L)
                ?.takeIf { it >= 0 }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExampleAppTheme {
                EcommerceCategoriesUi(
                    title = "Categories (A2A)",
                    categories = SampleData.productCategories,
                    onCategoryClick = { id ->
                        startActivity(EcommerceA2AProductListActivity.newIntent(this, id))
                    },
                    cartItemCount = EcommerceCartStore.itemCount,
                    onCartClick = {
                        cartLauncher.launch(EcommerceA2ACartActivity.newIntent(this))
                    },
                    orderPlacedTotalCents = orderPlacedTotalCents,
                )
            }
        }
    }

    companion object {
        fun newIntent(context: Context): Intent =
            Intent(context, EcommerceA2ACategoriesActivity::class.java)
    }
}

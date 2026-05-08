package io.embrace.android.exampleapp.paradigms.ecommerce.a2a

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import io.embrace.android.exampleapp.paradigms.data.SampleData
import io.embrace.android.exampleapp.paradigms.ecommerce.ui.EcommerceProductListUi
import io.embrace.android.exampleapp.ui.theme.ExampleAppTheme

class EcommerceA2AProductListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID)
        val category = categoryId?.let(SampleData::category)
        if (category == null) {
            Toast.makeText(this, "Unknown category", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val products = SampleData.productsIn(category.id)
        setContent {
            ExampleAppTheme {
                EcommerceProductListUi(
                    categoryTitle = category.title,
                    products = products,
                    onProductClick = { id ->
                        startActivity(EcommerceA2AProductDetailActivity.newIntent(this, id))
                    },
                    onBack = { finish() },
                )
            }
        }
    }

    companion object {
        private const val EXTRA_CATEGORY_ID = "category_id"

        fun newIntent(context: Context, categoryId: String): Intent =
            Intent(context, EcommerceA2AProductListActivity::class.java)
                .putExtra(EXTRA_CATEGORY_ID, categoryId)
    }
}

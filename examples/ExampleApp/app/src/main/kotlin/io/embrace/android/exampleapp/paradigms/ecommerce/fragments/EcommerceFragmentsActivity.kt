package io.embrace.android.exampleapp.paradigms.ecommerce.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentActivity
import androidx.navigation.createGraph
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.fragment.fragment

class EcommerceFragmentsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val containerId = View.generateViewId()
        setContentView(
            FrameLayout(this).apply {
                id = containerId
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            },
        )
        if (savedInstanceState != null) {
            return
        }
        val navHostFragment = NavHostFragment()
        supportFragmentManager.beginTransaction()
            .replace(containerId, navHostFragment)
            .commitNow()

        val navController = navHostFragment.navController
        navController.graph = navController.createGraph(startDestination = ROUTE_CATEGORIES) {
            fragment<EcommerceCategoriesFragment>(ROUTE_CATEGORIES)
            fragment<EcommerceProductListFragment>("category/{$ARG_CATEGORY_ID}")
            fragment<EcommerceProductDetailFragment>("product/{$ARG_PRODUCT_ID}")
            fragment<EcommerceCartFragment>(ROUTE_CART)
        }
    }

    companion object {
        internal const val ROUTE_CATEGORIES: String = "categories"
        internal const val ROUTE_CART: String = "cart"
        internal const val ARG_CATEGORY_ID: String = "categoryId"
        internal const val ARG_PRODUCT_ID: String = "productId"
        internal const val FRAGMENT_RESULT_ORDER_PLACED: String = "ecommerce_fragments_order_placed"
        internal const val FRAGMENT_RESULT_KEY_TOTAL_CENTS: String = "totalCents"

        fun newIntent(context: Context): Intent =
            Intent(context, EcommerceFragmentsActivity::class.java)
    }
}

package io.embrace.android.exampleapp.paradigms.ecommerce.navcompose

import kotlinx.serialization.Serializable

internal sealed interface EcommerceRoute {

    @Serializable
    object Categories : EcommerceRoute

    @Serializable
    data class ProductList(val categoryId: String) : EcommerceRoute

    @Serializable
    data class ProductDetail(val productId: String) : EcommerceRoute

    @Serializable
    object Cart : EcommerceRoute
}

internal const val SAVED_STATE_ORDER_PLACED_TOTAL_CENTS: String = "ecommerce_navcompose_order_placed_total_cents"

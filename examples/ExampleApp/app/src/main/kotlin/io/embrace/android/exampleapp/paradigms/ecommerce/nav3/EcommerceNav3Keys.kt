package io.embrace.android.exampleapp.paradigms.ecommerce.nav3

internal sealed interface EcommerceNav3Key {
    data object Categories : EcommerceNav3Key
    data class ProductList(val categoryId: String) : EcommerceNav3Key
    data class ProductDetail(val productId: String) : EcommerceNav3Key
    data object Cart : EcommerceNav3Key
}

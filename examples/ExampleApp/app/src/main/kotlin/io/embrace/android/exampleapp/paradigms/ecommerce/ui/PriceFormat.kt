package io.embrace.android.exampleapp.paradigms.ecommerce.ui

internal fun formatPrice(priceCents: Long): String {
    val dollars = priceCents / 100
    val cents = priceCents % 100
    return "$%d.%02d".format(dollars, cents)
}

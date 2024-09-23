package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DeliveryModule2]. Matches the signature of the constructor
 * for [DeliveryModule2Impl]
 */
typealias DeliveryModule2Supplier = () -> DeliveryModule2

fun createDeliveryModule2(): DeliveryModule2 = DeliveryModule2Impl()

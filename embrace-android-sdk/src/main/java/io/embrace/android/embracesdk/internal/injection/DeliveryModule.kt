package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService

internal interface DeliveryModule {
    val deliveryService: DeliveryService
}

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService

interface DeliveryModule {
    val deliveryService: DeliveryService
}

package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeDeliveryService
import io.embrace.android.embracesdk.internal.injection.DeliveryModule

class FakeDeliveryModule(
    override val deliveryService: FakeDeliveryService = FakeDeliveryService()
) : DeliveryModule

package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.FakeDeliveryService
import io.embrace.android.embracesdk.injection.DeliveryModule

internal class FakeDeliveryModule(
    override val deliveryService: FakeDeliveryService = FakeDeliveryService()
) : DeliveryModule

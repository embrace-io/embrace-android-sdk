package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.NoopDeliveryService

internal class DeliveryModuleImpl(
    initModule: InitModule,
    storageModule: StorageModule,
    apiService: ApiService?
) : DeliveryModule {

    override val deliveryService: DeliveryService by singleton {
        if (apiService == null) {
            NoopDeliveryService()
        } else {
            EmbraceDeliveryService(
                storageModule.deliveryCacheManager,
                apiService,
                initModule.jsonSerializer,
                initModule.logger
            )
        }
    }
}

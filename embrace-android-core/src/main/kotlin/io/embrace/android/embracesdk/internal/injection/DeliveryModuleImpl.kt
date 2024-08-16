package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiService
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.NoopDeliveryService
import io.embrace.android.embracesdk.internal.worker.WorkerName

internal class DeliveryModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
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
                workerThreadModule.backgroundWorker(WorkerName.DELIVERY_CACHE),
                initModule.jsonSerializer,
                initModule.logger
            )
        }
    }
}

package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.comms.delivery.NoopDeliveryService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface DeliveryModule {
    val deliveryService: DeliveryService
}

internal class DeliveryModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
) : DeliveryModule {

    override val deliveryService: DeliveryService by singleton {
        val apiService = essentialServiceModule.apiService
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

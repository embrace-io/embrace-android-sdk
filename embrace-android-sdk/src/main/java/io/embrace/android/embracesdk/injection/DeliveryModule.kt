package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface DeliveryModule {
    val deliveryService: DeliveryService
}

internal class DeliveryModuleImpl(
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule
) : DeliveryModule {

    override val deliveryService: DeliveryService by singleton {
        EmbraceDeliveryService(
            essentialServiceModule.deliveryCacheManager,
            essentialServiceModule.apiService,
            essentialServiceModule.gatingService,
            workerThreadModule.backgroundWorker(WorkerName.DELIVERY_CACHE),
            coreModule.jsonSerializer,
            coreModule.logger
        )
    }
}

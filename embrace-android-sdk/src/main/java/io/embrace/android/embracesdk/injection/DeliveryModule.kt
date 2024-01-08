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
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule
) : DeliveryModule {

    private val cachedSessionsWorker = workerThreadModule.backgroundWorker(WorkerName.CACHED_SESSIONS)

    override val deliveryService: DeliveryService by singleton {
        EmbraceDeliveryService(
            storageModule.deliveryCacheManager,
            essentialServiceModule.apiService,
            essentialServiceModule.gatingService,
            cachedSessionsWorker,
            coreModule.jsonSerializer,
            coreModule.logger
        )
    }
}

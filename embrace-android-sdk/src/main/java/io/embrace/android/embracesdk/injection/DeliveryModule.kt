package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface DeliveryModule {
    val deliveryService: DeliveryService
}

internal class DeliveryModuleImpl(
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule
) : DeliveryModule {

    private val cachedSessionsExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.CACHED_SESSIONS)
    private val sendSessionsExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.SEND_SESSIONS)

    override val deliveryService: DeliveryService by singleton {
        EmbraceDeliveryService(
            essentialServiceModule.deliveryCacheManager,
            essentialServiceModule.apiService,
            essentialServiceModule.gatingService,
            cachedSessionsExecutorService,
            sendSessionsExecutorService,
            coreModule.jsonSerializer,
            coreModule.logger
        )
    }
}

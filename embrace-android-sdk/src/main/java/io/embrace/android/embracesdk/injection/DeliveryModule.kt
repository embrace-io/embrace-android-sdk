package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.DeliveryNetworkManager
import io.embrace.android.embracesdk.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

internal interface DeliveryModule {
    val cacheService: CacheService
    val deliveryCacheManager: DeliveryCacheManager
    val deliveryNetworkManager: DeliveryNetworkManager
    val deliveryService: DeliveryService
}

internal class DeliveryModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    workerThreadModule: WorkerThreadModule
) : DeliveryModule {

    private val cachedSessionsExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.CACHED_SESSIONS)
    private val sendSessionsExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.SEND_SESSIONS)
    private val deliveryCacheExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.DELIVERY_CACHE)
    private val apiRetryExecutor = workerThreadModule.scheduledExecutor(ExecutorName.API_RETRY)

    override val cacheService: CacheService by singleton {
        EmbraceCacheService(coreModule.context, coreModule.jsonSerializer, coreModule.logger)
    }

    override val deliveryCacheManager: DeliveryCacheManager by singleton {
        DeliveryCacheManager(
            cacheService,
            deliveryCacheExecutorService,
            coreModule.logger,
            initModule.clock,
            coreModule.jsonSerializer
        )
    }

    override val deliveryNetworkManager: DeliveryNetworkManager by singleton {
        DeliveryNetworkManager(
            essentialServiceModule.metadataService,
            essentialServiceModule.urlBuilder,
            essentialServiceModule.apiClient,
            deliveryCacheManager,
            coreModule.logger,
            essentialServiceModule.configService,
            apiRetryExecutor,
            dataCaptureServiceModule.networkConnectivityService,
            coreModule.jsonSerializer,
            essentialServiceModule.userService
        )
    }

    override val deliveryService: DeliveryService by singleton {
        EmbraceDeliveryService(
            deliveryCacheManager,
            deliveryNetworkManager,
            cachedSessionsExecutorService,
            sendSessionsExecutorService,
            coreModule.logger,
            essentialServiceModule.configService
        )
    }
}

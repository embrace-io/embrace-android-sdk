package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.storage.EmbraceStorageService
import io.embrace.android.embracesdk.storage.StorageService
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Contains dependencies that are used to store data in the device's storage.
 */
internal interface StorageModule {
    val storageService: StorageService
    val cache: ApiResponseCache
    val cacheService: CacheService
    val deliveryCacheManager: DeliveryCacheManager
}

internal class StorageModuleImpl(
    workerThreadModule: WorkerThreadModule,
    initModule: InitModule,
    coreModule: CoreModule,
) : StorageModule {

    override val storageService: StorageService by singleton {
        EmbraceStorageService(
            coreModule.context,
            initModule.telemetryService,
            workerThreadModule.scheduledWorker(WorkerName.PERIODIC_CACHE)
        )
    }

    override val cache by singleton {
        ApiResponseCache(
            coreModule.jsonSerializer,
            storageService,
        )
    }

    override val cacheService: CacheService by singleton {
        EmbraceCacheService(storageService, coreModule.jsonSerializer, coreModule.logger)
    }

    override val deliveryCacheManager: DeliveryCacheManager by singleton {
        EmbraceDeliveryCacheManager(
            cacheService,
            workerThreadModule.backgroundWorker(WorkerName.DELIVERY_CACHE),
            coreModule.logger,
            initModule.clock,
            coreModule.jsonSerializer
        )
    }
}

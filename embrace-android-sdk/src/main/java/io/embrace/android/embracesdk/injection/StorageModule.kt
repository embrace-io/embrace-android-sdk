package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.comms.delivery.CacheService
import io.embrace.android.embracesdk.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.storage.EmbraceStorageManager
import io.embrace.android.embracesdk.storage.StorageManager
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import java.io.File

internal interface StorageModule {
    val storageManager: StorageManager
    val cache: ApiResponseCache
    val cacheService: CacheService
    val deliveryCacheManager: DeliveryCacheManager
}

internal class StorageModuleImpl(
    workerThreadModule: WorkerThreadModule,
    initModule: InitModule,
    coreModule: CoreModule,
) : StorageModule {

    private val deliveryCacheExecutorService =
        workerThreadModule.backgroundExecutor(ExecutorName.DELIVERY_CACHE)

    override val storageManager: StorageManager by singleton {
        EmbraceStorageManager(coreModule)
    }

    override val cache by singleton {
        ApiResponseCache(
            coreModule.jsonSerializer,
            { File(storageManager.cacheDirectory.value, "emb_config_cache") }
        )
    }

    override val cacheService: CacheService by singleton {
        EmbraceCacheService(storageManager, coreModule.jsonSerializer, coreModule.logger)
    }

    override val deliveryCacheManager: DeliveryCacheManager by singleton {
        EmbraceDeliveryCacheManager(
            cacheService,
            deliveryCacheExecutorService,
            coreModule.logger,
            initModule.clock,
            coreModule.jsonSerializer
        )
    }
}


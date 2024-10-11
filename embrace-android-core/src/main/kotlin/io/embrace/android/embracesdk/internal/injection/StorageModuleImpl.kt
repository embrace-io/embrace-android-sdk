package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.api.ApiResponseCache
import io.embrace.android.embracesdk.internal.comms.delivery.CacheService
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryCacheManager
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceCacheService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryCacheManager
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.TimeUnit

internal class StorageModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) : StorageModule {

    override val storageService: StorageService by singleton {
        EmbraceStorageService(
            coreModule.context,
            initModule.telemetryService,
            StatFsAvailabilityChecker(coreModule.context)
        )
    }

    override val cache by singleton {
        ApiResponseCache(
            initModule.jsonSerializer,
            storageService,
            initModule.logger
        )
    }

    override val cacheService: CacheService by singleton {
        EmbraceCacheService(
            storageService,
            initModule.jsonSerializer,
            initModule.logger
        )
    }

    override val deliveryCacheManager: DeliveryCacheManager by singleton {
        EmbraceDeliveryCacheManager(
            cacheService,
            workerThreadModule.priorityWorker(Worker.Priority.DeliveryCacheWorker),
            initModule.logger
        )
    }

    init {
        workerThreadModule
            .backgroundWorker(Worker.Background.IoRegWorker)
            .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
    }
}

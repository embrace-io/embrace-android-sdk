package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.NoopDeliveryService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionServiceImpl
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionService
import io.embrace.android.embracesdk.internal.delivery.resurrection.PayloadResurrectionServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.NoopSchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V1PayloadStore
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DeliveryModuleImpl(
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule
) : DeliveryModule {

    override val payloadStore: PayloadStore by singleton {
        V1PayloadStore(deliveryService)
    }

    override val deliveryService: DeliveryService by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            NoopDeliveryService()
        } else {
            EmbraceDeliveryService(
                storageModule.deliveryCacheManager,
                checkNotNull(essentialServiceModule.apiService),
                initModule.jsonSerializer,
                initModule.logger
            )
        }
    }

    override val intakeService: IntakeService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        IntakeServiceImpl(
            checkNotNull(schedulingService),
            checkNotNull(payloadStorageService),
            initModule.internalErrorService::handleInternalError,
            initModule.jsonSerializer,
            workerThreadModule.priorityWorker(Worker.Priority.FileCacheWorker)
        )
    }

    override val payloadResurrectionService: PayloadResurrectionService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        PayloadResurrectionServiceImpl(checkNotNull(intakeService))
    }

    override val payloadCachingService: PayloadCachingService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        PayloadCachingServiceImpl()
    }

    override val payloadStorageService: PayloadStorageService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        PayloadStorageServiceImpl(
            PayloadStorageServiceImpl.createOutputDir(
                coreModule.context,
                initModule.internalErrorService::handleInternalError
            ),
            initModule.internalErrorService::handleInternalError
        )
    }

    override val requestExecutionService: RequestExecutionService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        RequestExecutionServiceImpl()
    }

    override val schedulingService: SchedulingService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            return@singleton null
        }
        NoopSchedulingService()
    }
}

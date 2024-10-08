package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.NoopDeliveryService
import io.embrace.android.embracesdk.internal.delivery.caching.NoopPayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.intake.NoopIntakeService
import io.embrace.android.embracesdk.internal.delivery.scheduling.NoopSchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.NoopPayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.orchestrator.NoopPayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V1PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V2PayloadStore
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DeliveryModuleImpl(
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    requestExecutionServiceProvider: Provider<RequestExecutionService>,
    deliveryServiceProvider: () -> DeliveryService = {
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
) : DeliveryModule {

    override val payloadStore: PayloadStore by singleton {
        val configService = configModule.configService
        if (configService.isOnlyUsingOtelExporters()) {
            NoopPayloadStore()
        } else {
            if (configService.autoDataCaptureBehavior.isV2StorageEnabled()) {
                V2PayloadStore(intakeService, deliveryService, initModule.clock)
            } else {
                V1PayloadStore(deliveryService)
            }
        }
    }

    override val deliveryService: DeliveryService by singleton {
        deliveryServiceProvider()
    }

    override val intakeService: IntakeService by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            NoopIntakeService()
        } else {
            IntakeServiceImpl(
                schedulingService,
                payloadStorageService,
                initModule.logger,
                initModule.jsonSerializer,
                workerThreadModule.priorityWorker(Worker.Priority.FileCacheWorker)
            )
        }
    }

    private val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(
            workerThreadModule.backgroundWorker(Worker.Background.PeriodicCacheWorker),
            initModule.logger
        )
    }

    override val payloadCachingService: PayloadCachingService by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            NoopPayloadCachingService()
        } else {
            PayloadCachingServiceImpl(
                periodicSessionCacher,
                initModule.clock,
                essentialServiceModule.sessionIdTracker,
                payloadStore
            )
        }
    }

    override val payloadStorageService: PayloadStorageService by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            NoopPayloadStorageService()
        } else {
            PayloadStorageServiceImpl(
                PayloadStorageServiceImpl.createOutputDir(
                    coreModule.context,
                    initModule.logger
                ),
                initModule.logger
            )
        }
    }

    override val requestExecutionService: RequestExecutionService by singleton {
        requestExecutionServiceProvider()
    }

    override val schedulingService: SchedulingService by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            NoopSchedulingService()
        } else {
            SchedulingServiceImpl(
                payloadStorageService,
                requestExecutionService,
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
                workerThreadModule.backgroundWorker(Worker.Background.DeliveryWorker),
                initModule.clock,
                initModule.logger
            )
        }
    }
}

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.comms.delivery.DeliveryService
import io.embrace.android.embracesdk.internal.comms.delivery.EmbraceDeliveryService
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.HttpUrlConnectionRequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.OkHttpRequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V1PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.V2PayloadStore
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DeliveryModuleImpl(
    configModule: ConfigModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    deliveryServiceProvider: Provider<DeliveryService>?,
    override val deliveryTracer: DeliveryTracer? = null,
) : DeliveryModule {

    private val processIdProvider = { otelModule.openTelemetryConfiguration.processIdentifier }

    override val payloadStore: PayloadStore? by singleton {
        val configService = configModule.configService
        if (configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val deliveryService = deliveryService ?: return@singleton null
            val intakeService = intakeService ?: return@singleton null
            if (configService.autoDataCaptureBehavior.isV2StorageEnabled()) {
                V2PayloadStore(intakeService, initModule.clock, processIdProvider)
            } else {
                val worker = workerThreadModule.backgroundWorker(Worker.Background.LogMessageWorker)
                V1PayloadStore(worker, deliveryService)
            }
        }
    }

    override val deliveryService: DeliveryService? by singleton {
        deliveryServiceProvider?.invoke() ?: if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            EmbraceDeliveryService(
                storageModule.deliveryCacheManager,
                essentialServiceModule.apiService ?: return@singleton null,
                initModule.jsonSerializer
            )
        }
    }

    private val dataPersistenceWorker: PriorityWorker<StoredTelemetryMetadata> by singleton {
        workerThreadModule.priorityWorker(Worker.Priority.DataPersistenceWorker)
    }

    override val intakeService: IntakeService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val payloadStorageService = payloadStorageService ?: return@singleton null
            val schedulingService = schedulingService ?: return@singleton null
            val cacheStorageService = cacheStorageService ?: return@singleton null
            IntakeServiceImpl(
                schedulingService,
                payloadStorageService,
                cacheStorageService,
                initModule.logger,
                initModule.jsonSerializer,
                dataPersistenceWorker,
                deliveryTracer
            )
        }
    }

    private val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(
            workerThreadModule.backgroundWorker(Worker.Background.PeriodicCacheWorker),
            initModule.logger
        )
    }

    override val payloadCachingService: PayloadCachingService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val payloadStore = payloadStore ?: return@singleton null
            PayloadCachingServiceImpl(
                periodicSessionCacher,
                initModule.clock,
                essentialServiceModule.sessionIdTracker,
                payloadStore,
                deliveryTracer
            )
        }
    }

    override val payloadStorageService: PayloadStorageService? by singleton {
        payloadStorageServiceProvider?.invoke() ?: if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val location = StorageLocation.PAYLOAD.asFile(coreModule.context, initModule.logger)
            PayloadStorageServiceImpl(
                location,
                dataPersistenceWorker,
                processIdProvider,
                initModule.logger,
                deliveryTracer
            )
        }
    }

    override val cacheStorageService: PayloadStorageService? by singleton {
        cacheStorageServiceProvider?.invoke() ?: if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val location = StorageLocation.CACHE.asFile(coreModule.context, initModule.logger)
            PayloadStorageServiceImpl(
                location,
                dataPersistenceWorker,
                processIdProvider,
                initModule.logger,
                deliveryTracer
            )
        }
    }

    override val requestExecutionService: RequestExecutionService? by singleton {
        requestExecutionServiceProvider?.invoke() ?: if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val appId = configModule.configService.appId ?: return@singleton null
            val coreBaseUrl = configModule.urlBuilder?.baseDataUrl ?: return@singleton null
            val lazyDeviceId = lazy(androidServicesModule.preferencesService::deviceIdentifier)
            if (configModule.configService.autoDataCaptureBehavior.shouldUseOkHttp()) {
                OkHttpRequestExecutionService(
                    configModule.okHttpClient,
                    coreBaseUrl,
                    lazyDeviceId,
                    appId,
                    BuildConfig.VERSION_NAME,
                    initModule.logger,
                    deliveryTracer
                )
            } else {
                HttpUrlConnectionRequestExecutionService(
                    coreBaseUrl,
                    lazyDeviceId,
                    appId,
                    BuildConfig.VERSION_NAME,
                    initModule.logger,
                )
            }
        }
    }

    override val schedulingService: SchedulingService? by singleton {
        if (configModule.configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            val payloadStorageService = payloadStorageService ?: return@singleton null
            val requestExecutionService = requestExecutionService ?: return@singleton null
            SchedulingServiceImpl(
                payloadStorageService,
                requestExecutionService,
                workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                workerThreadModule.backgroundWorker(Worker.Background.HttpRequestWorker),
                initModule.clock,
                initModule.logger
            )
        }
    }
}

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.OkHttpRequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingService
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStoreImpl
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionCacher
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStoreImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker

class DeliveryModuleImpl(
    configService: ConfigService,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    override val deliveryTracer: DeliveryTracer? = null,
) : DeliveryModule {

    private val processIdProvider = { otelModule.otelSdkConfig.processIdentifier }

    override val payloadStore: PayloadStore by singleton {
        PayloadStoreImpl(intakeService, initModule.clock, processIdProvider)
    }

    private val dataPersistenceWorker: PriorityWorker<StoredTelemetryMetadata> by singleton {
        workerThreadModule.priorityWorker(Worker.Priority.DataPersistenceWorker)
    }

    override val intakeService: IntakeService by singleton {
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

    private val periodicSessionCacher: PeriodicSessionCacher by singleton {
        PeriodicSessionCacher(
            workerThreadModule.backgroundWorker(Worker.Background.PeriodicCacheWorker),
            initModule.logger
        )
    }

    override val payloadCachingService: PayloadCachingService by singleton {
        PayloadCachingServiceImpl(
            periodicSessionCacher,
            initModule.clock,
            essentialServiceModule.sessionTracker,
            payloadStore,
            deliveryTracer
        )
    }

    private val payloadStorageService: PayloadStorageService by singleton {
        payloadStorageServiceProvider?.invoke() ?: run {
            val location = StorageLocation.PAYLOAD.asFile(
                logger = initModule.logger,
                rootDirSupplier = { coreModule.context.filesDir },
                fallbackDirSupplier = { coreModule.context.cacheDir }
            )
            PayloadStorageServiceImpl(
                location,
                dataPersistenceWorker,
                processIdProvider,
                initModule.logger,
                deliveryTracer
            )
        }
    }

    override val cacheStorageService: PayloadStorageService by singleton {
        cacheStorageServiceProvider?.invoke() ?: run {
            val location = StorageLocation.CACHE.asFile(
                logger = initModule.logger,
                rootDirSupplier = { coreModule.context.filesDir },
                fallbackDirSupplier = { coreModule.context.cacheDir }
            )
            PayloadStorageServiceImpl(
                location,
                dataPersistenceWorker,
                processIdProvider,
                initModule.logger,
                deliveryTracer
            )
        }
    }

    override val cachedLogEnvelopeStore: CachedLogEnvelopeStore by singleton {
        val location = StorageLocation.ENVELOPE.asFile(
            logger = initModule.logger,
            rootDirSupplier = { coreModule.context.filesDir },
            fallbackDirSupplier = { coreModule.context.cacheDir }
        )
        CachedLogEnvelopeStoreImpl(
            outputDir = location,
            worker = dataPersistenceWorker,
            logger = initModule.logger,
            serializer = initModule.jsonSerializer
        )
    }

    private val requestExecutionService: RequestExecutionService by singleton {
        requestExecutionServiceProvider?.invoke() ?: run {
            val appId = checkNotNull(configService.appId)
            val coreBaseUrl = initModule.instrumentedConfig.baseUrls.getData() ?: "https://a-$appId.data.emb-api.com"
            val url = "$coreBaseUrl/${Endpoint.SESSIONS.version}/"

            val lazyDeviceId = lazy(configService::deviceId)
            OkHttpRequestExecutionService(
                initModule.okHttpClient,
                url,
                lazyDeviceId,
                appId,
                BuildConfig.VERSION_NAME,
                initModule.logger,
                deliveryTracer
            )
        }
    }

    override val schedulingService: SchedulingService by singleton {
        SchedulingServiceImpl(
            payloadStorageService,
            requestExecutionService,
            workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
            workerThreadModule.backgroundWorker(Worker.Background.HttpRequestWorker),
            initModule.clock,
            initModule.logger,
            deliveryTracer
        )
    }
}

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
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStoreImpl
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker

class DeliveryModuleImpl(
    configService: ConfigService,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadPool: WorkerThreadPool,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    requestExecutionServiceProvider: Provider<RequestExecutionService>?,
    payloadStorageServiceProvider: Provider<PayloadStorageService>?,
    cacheStorageServiceProvider: Provider<PayloadStorageService>?,
    override val deliveryTracer: DeliveryTracer? = null,
    payloadStorageLimit: Int = PayloadStorageServiceImpl.DEFAULT_STORAGE_LIMIT,
) : DeliveryModule {

    private val processIdProvider = { otelModule.otelSdkConfig.processIdentifier }

    private val rootDirSupplier = { coreModule.context.filesDir }
    private val fallbackDirSupplier = { coreModule.context.cacheDir }

    private val dataPersistenceWorker: PriorityWorker<StoredTelemetryMetadata> =
        workerThreadPool.priorityWorker(Worker.Priority.DataPersistenceWorker)

    override val payloadStorageService: PayloadStorageService =
        payloadStorageServiceProvider?.invoke() ?: PayloadStorageServiceImpl(
            StorageLocation.PAYLOAD.asFile(
                errorHandler = initModule.errorHandler,
                rootDirSupplier = rootDirSupplier,
                fallbackDirSupplier = fallbackDirSupplier,
            ),
            dataPersistenceWorker,
            processIdProvider,
            initModule.errorHandler,
            initModule.clock,
            deliveryTracer,
            payloadStorageLimit,
        )

    override val cacheStorageService: PayloadStorageService =
        cacheStorageServiceProvider?.invoke() ?: PayloadStorageServiceImpl(
            StorageLocation.CACHE.asFile(
                errorHandler = initModule.errorHandler,
                rootDirSupplier = rootDirSupplier,
                fallbackDirSupplier = fallbackDirSupplier,
            ),
            dataPersistenceWorker,
            processIdProvider,
            initModule.errorHandler,
            initModule.clock,
            deliveryTracer,
        )

    private val requestExecutionService: RequestExecutionService =
        requestExecutionServiceProvider?.invoke() ?: run {
            val appId = checkNotNull(configService.appId)
            val coreBaseUrl = initModule.instrumentedConfig.baseUrls.getData() ?: "https://a-$appId.data.emb-api.com"
            val url = "$coreBaseUrl/${Endpoint.SESSIONS.version}/"

            OkHttpRequestExecutionService(
                initModule.okHttpClient,
                url,
                lazy(configService::deviceId),
                appId,
                BuildConfig.VERSION_NAME,
                initModule.errorHandler,
                deliveryTracer,
            )
        }

    override val schedulingService: SchedulingService = SchedulingServiceImpl(
        payloadStorageService,
        requestExecutionService,
        workerThreadPool.backgroundWorker(Worker.Background.DeliverySchedulingWorker),
        workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker),
        initModule.clock,
        initModule.errorHandler,
        deliveryTracer,
    )

    override val intakeService: IntakeService = IntakeServiceImpl(
        schedulingService,
        payloadStorageService,
        cacheStorageService,
        initModule.errorHandler,
        initModule.jsonSerializer,
        dataPersistenceWorker,
        deliveryTracer,
    )

    override val payloadStore: PayloadStore = PayloadStoreImpl(
        intakeService,
        initModule.clock,
        processIdProvider,
        initModule.uuidSource,
        essentialServiceModule.sessionIdsProvider::getActiveSessionIds,
    )

    private val partCacher: PeriodicSessionPartCacher = PeriodicSessionPartCacher(
        workerThreadPool.backgroundWorker(Worker.Background.PeriodicCacheWorker),
        initModule.errorHandler,
        configService.otelBehavior.getPeriodicCacheIntervalMs(),
    )

    override val payloadCachingService: PayloadCachingService = PayloadCachingServiceImpl(
        partCacher,
        initModule.clock,
        essentialServiceModule.sessionIdsProvider,
        payloadStore,
        deliveryTracer,
    )

    override val cachedLogEnvelopeStore: CachedLogEnvelopeStore = CachedLogEnvelopeStoreImpl(
        outputDir = StorageLocation.ENVELOPE.asFile(
            errorHandler = initModule.errorHandler,
            rootDirSupplier = rootDirSupplier,
            fallbackDirSupplier = fallbackDirSupplier,
        ),
        worker = dataPersistenceWorker,
        errorHandler = initModule.errorHandler,
        serializer = initModule.jsonSerializer,
        clock = initModule.clock,
    )
}

package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.comms.api.Endpoint
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.config.instrumented.schema.InstrumentedConfig
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingService
import io.embrace.android.embracesdk.internal.delivery.caching.PayloadCachingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.execution.OkHttpRequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.execution.RequestExecutionService
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeService
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.delivery.storage.CachedLogEnvelopeStoreImpl
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageService
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer
import io.embrace.android.embracesdk.internal.session.caching.PeriodicSessionPartCacher
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionIdsSnapshot
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStore
import io.embrace.android.embracesdk.internal.session.orchestrator.PayloadStoreImpl
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.UuidSource
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import okhttp3.OkHttpClient

fun createPayloadStorageService(
    location: StorageLocation,
    context: Context,
    worker: PriorityWorker<StoredTelemetryMetadata>,
    processIdProvider: () -> String,
    errorHandler: ErrorHandler,
    clock: Clock,
    deliveryTracer: DeliveryTracer?,
    limit: Int = PayloadStorageServiceImpl.DEFAULT_STORAGE_LIMIT,
): PayloadStorageService = PayloadStorageServiceImpl(
    location.asFile(
        errorHandler = errorHandler,
        rootDirSupplier = { context.filesDir },
        fallbackDirSupplier = { context.cacheDir },
    ),
    worker,
    processIdProvider,
    errorHandler,
    clock,
    deliveryTracer,
    limit,
)

fun createRequestExecutionService(
    configService: ConfigService,
    instrumentedConfig: InstrumentedConfig,
    okHttpClient: Lazy<OkHttpClient>,
    errorHandler: ErrorHandler,
    deliveryTracer: DeliveryTracer?,
): RequestExecutionService {
    val appId = checkNotNull(configService.appId)
    val coreBaseUrl = instrumentedConfig.baseUrls.getData() ?: "https://a-$appId.data.emb-api.com"
    val url = "$coreBaseUrl/${Endpoint.SESSIONS.version}/"

    return OkHttpRequestExecutionService(
        okHttpClient,
        url,
        lazy(configService::deviceId),
        appId,
        BuildConfig.VERSION_NAME,
        errorHandler,
        deliveryTracer,
    )
}

fun createPayloadStore(
    intakeService: IntakeService,
    clock: Clock,
    processIdProvider: () -> String,
    uuidSource: UuidSource,
    sessionIdsProvider: () -> SessionIdsSnapshot,
): PayloadStore = PayloadStoreImpl(intakeService, clock, processIdProvider, uuidSource, sessionIdsProvider)

fun createPayloadCachingService(
    worker: BackgroundWorker,
    errorHandler: ErrorHandler,
    periodicCacheIntervalMs: Long,
    clock: Clock,
    sessionIdsProvider: SessionIdsProvider,
    payloadStore: PayloadStore,
    deliveryTracer: DeliveryTracer?,
): PayloadCachingService = PayloadCachingServiceImpl(
    EmbTrace.trace("part-cacher-init") { PeriodicSessionPartCacher(worker, errorHandler, periodicCacheIntervalMs) },
    clock,
    sessionIdsProvider,
    payloadStore,
    deliveryTracer,
)

fun createCachedLogEnvelopeStore(
    context: Context,
    worker: PriorityWorker<StoredTelemetryMetadata>,
    errorHandler: ErrorHandler,
    serializer: PlatformSerializer,
    clock: Clock,
): CachedLogEnvelopeStore = CachedLogEnvelopeStoreImpl(
    outputDir = StorageLocation.ENVELOPE.asFile(
        errorHandler = errorHandler,
        rootDirSupplier = { context.filesDir },
        fallbackDirSupplier = { context.cacheDir },
    ),
    worker = worker,
    errorHandler = errorHandler,
    serializer = serializer,
    clock = clock,
)

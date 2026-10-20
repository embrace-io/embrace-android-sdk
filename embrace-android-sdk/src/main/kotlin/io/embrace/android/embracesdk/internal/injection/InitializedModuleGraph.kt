package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModule
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.DataCaptureServiceModuleSupplier
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogLimitingServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.storage.StorageService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.concurrent.TimeUnit

/**
 * Constructs objects used by the initialized SDK. Only constructed once [ConfigLoadServices] has
 * confirmed the SDK is not disabled. Construction has no side effects: [start] must be called afterwards.
 *
 * Each supplier is a test seam and is null in production. Where it is null the real construction
 * below runs instead; because [init] is inlined that costs no lambda allocation on the default path.
 */
internal class InitializedModuleGraph(
    context: Context,
    preStartServices: PreStartServices,
    configLoadServices: ConfigLoadServices,
    val sdkStartTimeMs: Long,
    val sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    versionChecker: VersionChecker = BuildVersionChecker,
    overrides: SdkOverrides? = null,
    private val initModuleSupplier: InitModuleSupplier? = null,
    private val openTelemetryModuleSupplier: OpenTelemetryModuleSupplier? = null,
    private val coreModuleSupplier: CoreModuleSupplier? = null,
    private val storageServiceSupplier: StorageServiceSupplier? = null,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier? = null,
    private val instrumentationModuleSupplier: InstrumentationModuleSupplier? = null,
    private val dataCaptureServiceModuleSupplier: DataCaptureServiceModuleSupplier? = null,
    private val deliveryModuleSupplier: DeliveryModuleSupplier? = null,
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier? = null,
    private val userSessionOrchestrationModuleSupplier: UserSessionOrchestrationModuleSupplier? = null,
    private val payloadSourceModuleSupplier: PayloadSourceModuleSupplier? = null,
) {

    val initModule: InitModule = init(name = "init-module", recordDuration = true) {
        initModuleSupplier?.invoke(preStartServices) ?: InitModuleImpl(
            preStartServices = preStartServices,
            uuidSource = configLoadServices.uuidSource,
            instrumentedConfig = configLoadServices.instrumentedConfig,
            jsonSerializer = configLoadServices.jsonSerializer,
            okHttpClient = configLoadServices.okHttpClient,
        )
    }

    val workerThreadPool: WorkerThreadPool = init("worker-thread-pool") { configLoadServices.workerThreadPool }
    val configService: ConfigService = init("config-service") { configLoadServices.configService }

    private val otelSignalContext: OtelSignalContext = init("otel-signal-context") {
        OtelSignalContext(
            sessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider },
            userServiceProvider = { essentialServiceModule.userService },
            sessionPartTrackerProvider = { essentialServiceModule.sessionPartTracker },
            processStateTrackerProvider = { essentialServiceModule.processStateTracker },
            experimentTrackingServiceProvider = { essentialServiceModule.experimentTrackingService },
            userSessionPropertiesServiceProvider = { essentialServiceModule.userSessionPropertiesService },
            instrumentationRegistryProvider = { instrumentationModule.instrumentationRegistry },
        )
    }

    val openTelemetryModule: OpenTelemetryModule = init("open-telemetry-module") {
        openTelemetryModuleSupplier?.invoke(
            initModule,
            configService,
            preStartServices.externalOtelConfig,
            otelSignalContext::getSessionIdsProvider,
            otelSignalContext::getUserId,
            otelSignalContext::getEventMetadata,
        ) ?: OpenTelemetryModuleImpl(
            initModule = initModule,
            configService = configService,
            externalOtelConfig = preStartServices.externalOtelConfig,
            sessionIdsProvider = otelSignalContext::getSessionIdsProvider,
            userIdProvider = otelSignalContext::getUserId,
            eventMetadataProvider = otelSignalContext::getEventMetadata,
        )
    }

    val coreModule: CoreModule = init("core-module") {
        coreModuleSupplier?.invoke(context, initModule, configLoadServices.keyValueStore)
            ?: CoreModuleImpl(context, initModule, configLoadServices.keyValueStore)
    }

    val essentialServiceModule: EssentialServiceModule =
        init(name = "essential-service-module", recordDuration = true) {
            val lifecycleTrackerProvider: Provider<LifecycleTracker?> = { null }
            val networkConnectivityServiceProvider: Provider<NetworkConnectivityService?> = { null }
            val sessionOrchestratorProvider = { userSessionOrchestrationModule.sessionOrchestrator }

            essentialServiceModuleSupplier?.invoke(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadPool,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            ) ?: EssentialServiceModuleImpl(
                initModule,
                configService,
                openTelemetryModule,
                coreModule,
                workerThreadPool,
                context,
                lifecycleTrackerProvider,
                networkConnectivityServiceProvider,
                sessionOrchestratorProvider,
            )
        }

    val storageService: StorageService = init("storage-service") {
        storageServiceSupplier?.invoke(initModule, coreModule, workerThreadPool)
            ?: EmbraceStorageService(
                coreModule.context,
                initModule.telemetryService,
                StatFsAvailabilityChecker(coreModule.context),
            )
    }

    val instrumentationModule: InstrumentationModule = init(name = "instrumentation-module", recordDuration = true) {
        // Forward references: break the instrumentation <-> userSessionOrchestration cycle.
        val userSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getCurrentUserSessionId() }
        val activeSessionIdsProvider = { userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds() }

        instrumentationModuleSupplier?.invoke(
            initModule,
            openTelemetryModule,
            workerThreadPool,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        ) ?: InstrumentationModuleImpl(
            initModule,
            openTelemetryModule,
            workerThreadPool,
            configService,
            essentialServiceModule,
            coreModule,
            storageService,
            userSessionIdsProvider,
            activeSessionIdsProvider,
        )
    }

    val breadcrumbDataSource: Lazy<BreadcrumbDataSource?> = lazy {
        instrumentationModule.instrumentationRegistry.add {
            BreadcrumbDataSource(instrumentationModule.instrumentationArgs)
        }
    }

    val crashMarker: CrashFileMarker = init("crash-marker") {
        CrashFileMarkerImpl(
            lazy { storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME) },
        )
    }

    val lastRunCrashVerifier: LastRunCrashVerifier = init("last-run-crash-verifier") {
        LastRunCrashVerifier(crashMarker)
    }

    val dataCaptureServiceModule: DataCaptureServiceModule = init("data-capture-service-module") {
        val instrumentationArgs = instrumentationModule.instrumentationArgs
        val destination = instrumentationArgs.destination
        val activityManager = instrumentationArgs.systemService<ActivityManager>(Context.ACTIVITY_SERVICE)
        val backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
        dataCaptureServiceModuleSupplier?.invoke(
            initModule.clock,
            initModule.errorHandler,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
            activityManager,
            backgroundWorker,
        ) ?: DataCaptureServiceModuleImpl(
            initModule.clock,
            initModule.errorHandler,
            destination,
            configService,
            { coreModule.appVersionStartupCounter },
            initModule.startupClassifier,
            versionChecker,
            activityManager,
            backgroundWorker,
        )
    }

    val deliveryModule: DeliveryModule? = init(name = "delivery-module", recordDuration = true) {
        if (configService.isOnlyUsingOtelExporters()) {
            null
        } else {
            deliveryModuleSupplier?.invoke(
                configService,
                initModule,
                openTelemetryModule,
                workerThreadPool,
                coreModule,
                essentialServiceModule,
                null,
                null,
                null,
                null,
            ) ?: DeliveryModuleImpl(
                configService = configService,
                initModule = initModule,
                otelModule = openTelemetryModule,
                workerThreadPool = workerThreadPool,
                coreModule = coreModule,
                essentialServiceModule = essentialServiceModule,
                requestExecutionServiceProvider = null,
                payloadStorageServiceProvider = null,
                cacheStorageServiceProvider = null,
            )
        }
    }

    val threadBlockageService: ThreadBlockageService? = init("thread-blockage-service") {
        val args = instrumentationModule.instrumentationArgs
        // Only create the thread blockage service if the provider is null, not when the provider returns null
        when (threadBlockageServiceSupplier) {
            null -> createThreadBlockageService(args)
            else -> threadBlockageServiceSupplier.invoke(args)
        }
    }

    val payloadSourceModule: PayloadSourceModule = init(name = "payload-source-module", recordDuration = true) {
        payloadSourceModuleSupplier?.invoke(
            initModule,
            coreModule,
            workerThreadPool,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        ) ?: PayloadSourceModuleImpl(
            initModule,
            coreModule,
            workerThreadPool,
            essentialServiceModule,
            configService,
            openTelemetryModule,
            threadBlockageService,
            deliveryModule,
        )
    }

    val logLimitingService: LogLimitingService = init("log-limiting-service") {
        LogLimitingServiceImpl(configService)
    }

    val logOrchestrator: LogOrchestrator = init("log-orchestrator") {
        createLogOrchestrator(
            worker = overrides?.logOrchestratorWorker.resolve {
                workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
            },
            clock = initModule.clock,
            sink = openTelemetryModule.logSink,
            payloadStore = deliveryModule?.payloadStore,
            logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
        )
    }

    val attachmentService: AttachmentService = init("attachment-service") { AttachmentService() }

    /**
     * Deferred: only reached via the public logging API, never during SDK init.
     */
    val logService: Lazy<LogService> = lazy(LazyThreadSafetyMode.PUBLICATION) {
        LogServiceImpl(
            essentialServiceModule.telemetryDestination,
            configService,
            logLimitingService,
            initModule.telemetryService,
        )
    }

    val userSessionOrchestrationModule: UserSessionOrchestrationModule =
        init(name = "user-session-orchestration-module", recordDuration = true) {
            val startupDurationProvider = dataCaptureServiceModule.startupService::getSdkStartupDuration
            val appVersionStartupCounterProvider = dataCaptureServiceModule.startupService::getAppVersionStartupCounter
            userSessionOrchestrationModuleSupplier?.invoke(
                initModule,
                openTelemetryModule,
                coreModule,
                essentialServiceModule,
                configService,
                deliveryModule,
                instrumentationModule,
                payloadSourceModule,
                startupDurationProvider,
                appVersionStartupCounterProvider,
                logLimitingService,
                workerThreadPool,
            ) ?: UserSessionOrchestrationModuleImpl(
                initModule,
                openTelemetryModule,
                coreModule,
                essentialServiceModule,
                configService,
                deliveryModule,
                instrumentationModule,
                payloadSourceModule,
                startupDurationProvider,
                appVersionStartupCounterProvider,
                logLimitingService,
                workerThreadPool,
            )
        }

    /**
     * Performs the side effects of initialization. Must be called once, after construction.
     */
    fun start() {
        EmbTrace.trace(sectionName = "span-service-init", recordDuration = true) {
            openTelemetryModule.currentSessionPartSpan.initializeService(sdkStartTimeMs)
        }
        EmbTrace.trace("storage-telemetry-schedule") {
            workerThreadPool
                .backgroundWorker(Worker.Background.IoRegWorker)
                .schedule<Unit>({ storageService.logStorageTelemetry() }, 1, TimeUnit.MINUTES)
        }
        EmbTrace.trace("otel-signal-context-mark-ready") {
            otelSignalContext.markReady()
        }
    }

    private inline fun <T> init(name: String, recordDuration: Boolean = false, supplier: () -> T): T =
        EmbTrace.trace("$name-init", recordDuration) { supplier() }
}

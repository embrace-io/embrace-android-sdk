package io.embrace.android.embracesdk.internal.injection

import android.app.ActivityManager
import android.content.Context
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.capture.connectivity.NetworkConnectivityService
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarker
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.createAppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.createUiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageService
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.ThreadBlockageServiceSupplier
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logs.LogLimitingService
import io.embrace.android.embracesdk.internal.logs.LogLimitingServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogOrchestrator
import io.embrace.android.embracesdk.internal.logs.LogService
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.resurrection.SessionPartReader
import io.embrace.android.embracesdk.internal.session.lifecycle.LifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
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
    overrides: SdkOverrides? = null,
    private val initModuleSupplier: InitModuleSupplier? = null,
    private val openTelemetryModuleSupplier: OpenTelemetryModuleSupplier? = null,
    private val coreModuleSupplier: CoreModuleSupplier? = null,
    private val storageServiceSupplier: StorageServiceSupplier? = null,
    private val essentialServiceModuleSupplier: EssentialServiceModuleSupplier? = null,
    private val deliveryModuleSupplier: DeliveryModuleSupplier? = null,
    private val threadBlockageServiceSupplier: ThreadBlockageServiceSupplier? = null,
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
            sessionIdsProvider = { essentialServiceModule.sessionIdsProvider },
            userServiceProvider = { essentialServiceModule.userService },
            sessionPartTrackerProvider = { essentialServiceModule.sessionPartTracker },
            processStateTrackerProvider = { essentialServiceModule.processStateTracker },
            experimentTrackingServiceProvider = { essentialServiceModule.experimentTrackingService },
            userSessionPropertiesServiceProvider = { essentialServiceModule.userSessionPropertiesService },
            instrumentationRegistryProvider = { instrumentationRegistry },
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
            val sessionOrchestratorProvider = { sessionOrchestrator }

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

    val instrumentationRegistry: InstrumentationRegistry = init("instrumentation-registry") {
        overrides?.instrumentationRegistry.resolve { InstrumentationRegistryImpl(initModule.errorHandler) }
    }

    val instrumentationArgs: InstrumentationArgs = init(name = "instrumentation-module", recordDuration = true) {
        createInstrumentationArgs(
            configService = configService,
            destination = essentialServiceModule.telemetryDestination,
            errorHandler = initModule.errorHandler,
            clock = initModule.clock,
            context = coreModule.context,
            application = coreModule.application,
            storeProvider = { coreModule.store },
            serializer = initModule.jsonSerializer,
            uuidSource = initModule.uuidSource,
            ordinalStore = coreModule.ordinalStore,
            processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
            processStateTracker = essentialServiceModule.processStateTracker,
            navigationTrackingService = essentialServiceModule.navigationTrackingService,
            telemetryService = initModule.telemetryService,
            workerThreadPool = workerThreadPool,
            sessionPartTracker = essentialServiceModule.sessionPartTracker,
            userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
            userSessionIdsProvider = { essentialServiceModule.sessionIdsProvider.getCurrentUserSessionId() },
            activeSessionIdsProvider = { essentialServiceModule.sessionIdsProvider.getActiveSessionIds() },
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            eventBus = coreModule.eventBus,
        )
    }

    val breadcrumbDataSource: Lazy<BreadcrumbDataSource?> = lazy {
        instrumentationRegistry.add {
            BreadcrumbDataSource(instrumentationArgs)
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

    private val versionChecker: VersionChecker = overrides?.versionChecker.resolve { BuildVersionChecker }

    val startupService: StartupService = init("startup-service") {
        createStartupService(
            destination = instrumentationArgs.destination,
            appVersionStartupCounterProvider = { coreModule.appVersionStartupCounter },
        )
    }

    val appStartupDataCollector: AppStartupDataCollector = init("app-startup-data-collector") {
        val instrumentationArgs = instrumentationArgs
        createAppStartupDataCollector(
            clock = initModule.clock,
            errorHandler = initModule.errorHandler,
            destination = instrumentationArgs.destination,
            configService = configService,
            startupService = startupService,
            startupClassifier = initModule.startupClassifier,
            versionChecker = versionChecker,
            activityManager = instrumentationArgs.systemService<ActivityManager>(Context.ACTIVITY_SERVICE),
            backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    val uiLoadDataListener: UiLoadDataListener? = init("ui-load-data-listener") {
        createUiLoadDataListener(
            destination = instrumentationArgs.destination,
            configService = configService,
            versionChecker = versionChecker,
        )
    }

    val startupTracker: StartupTracker = init("startup-tracker") {
        createStartupTracker(
            clock = initModule.clock,
            errorHandler = initModule.errorHandler,
            configService = configService,
            appStartupDataCollector = appStartupDataCollector,
            uiLoadDataListener = uiLoadDataListener,
            versionChecker = versionChecker,
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
        val args = instrumentationArgs
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

    val sessionPartReader: SessionPartReader? = init("session-part-reader") {
        deliveryModule?.let { delivery ->
            createSessionPartReader(
                context = coreModule.context,
                clock = initModule.clock,
                errorHandler = initModule.errorHandler,
                configService = configService,
                sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                intakeService = delivery.intakeService,
                cachedLogEnvelopeStore = delivery.cachedLogEnvelopeStore,
                nativeCrashServiceProvider = {
                    instrumentationRegistry.findByType(NativeCrashDataSource::class)
                },
                restoreDecisionProvider = { sessionOrchestrator.userSessionRestoreDecision },
                processIdProvider = { openTelemetryModule.otelSdkConfig.processIdentifier },
            )
        }
    }

    val sessionOrchestrator: SessionOrchestrator =
        init(name = "user-session-orchestration-module", recordDuration = true) {
            overrides?.sessionOrchestrator.resolve {
                createSessionOrchestrator(
                    clock = initModule.clock,
                    errorHandler = initModule.errorHandler,
                    uuidSource = initModule.uuidSource,
                    startupClassifier = initModule.startupClassifier,
                    telemetryService = initModule.telemetryService,
                    configService = configService,
                    processStateTracker = essentialServiceModule.processStateTracker,
                    sessionPartTracker = essentialServiceModule.sessionPartTracker,
                    sessionIdsProvider = essentialServiceModule.sessionIdsProvider,
                    userService = essentialServiceModule.userService,
                    userSessionPropertiesService = essentialServiceModule.userSessionPropertiesService,
                    telemetryDestination = essentialServiceModule.telemetryDestination,
                    experimentRecordsProvider = { essentialServiceModule.experimentTrackingService.getRecords() },
                    currentSessionPartSpan = openTelemetryModule.currentSessionPartSpan,
                    spanRepository = openTelemetryModule.spanRepository,
                    processIdentifier = openTelemetryModule.otelSdkConfig.processIdentifier,
                    sessionPartEnvelopeSource = payloadSourceModule.sessionPartEnvelopeSource,
                    logEnvelopeSource = payloadSourceModule.logEnvelopeSource,
                    metadataService = payloadSourceModule.metadataService,
                    resourceSource = payloadSourceModule.resourceSource,
                    envelopeMetadataSource = payloadSourceModule.envelopeMetadataSource,
                    payloadStore = deliveryModule?.payloadStore,
                    payloadCachingService = deliveryModule?.payloadCachingService,
                    instrumentationRegistry = instrumentationRegistry,
                    startupDurationProvider = startupService::getSdkStartupDuration,
                    appVersionStartupCounterProvider = startupService::getAppVersionStartupCounter,
                    logLimitingService = logLimitingService,
                    ordinalStore = coreModule.ordinalStore,
                    store = coreModule.store,
                    backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                    sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                    sessionReadWorker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
                    sessionPartReader = sessionPartReader,
                )
            }
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

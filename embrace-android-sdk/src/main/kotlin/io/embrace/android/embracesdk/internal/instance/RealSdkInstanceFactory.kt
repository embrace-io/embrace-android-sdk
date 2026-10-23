package io.embrace.android.embracesdk.internal.instance

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ExperimentApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.InstrumentationApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserSessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistry
import io.embrace.android.embracesdk.internal.arch.InstrumentationRegistryImpl
import io.embrace.android.embracesdk.internal.arch.destination.TelemetryDestinationImpl
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifierImpl
import io.embrace.android.embracesdk.internal.arch.state.ProcessStateTracker
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbDataSource
import io.embrace.android.embracesdk.internal.capture.experiment.ExperimentTrackingService
import io.embrace.android.embracesdk.internal.capture.session.UserSessionPropertiesService
import io.embrace.android.embracesdk.internal.capture.user.UserService
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.delivery.StoredTelemetryMetadata
import io.embrace.android.embracesdk.internal.delivery.debug.DeliveryTracer
import io.embrace.android.embracesdk.internal.delivery.intake.IntakeServiceImpl
import io.embrace.android.embracesdk.internal.delivery.scheduling.SchedulingServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.PayloadStorageServiceImpl
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.injection.ConfigLoadServices
import io.embrace.android.embracesdk.internal.injection.OtelSignalContext
import io.embrace.android.embracesdk.internal.injection.PreStartServices
import io.embrace.android.embracesdk.internal.injection.SdkOverrides
import io.embrace.android.embracesdk.internal.injection.applicationContextOrSelf
import io.embrace.android.embracesdk.internal.injection.createCachedLogEnvelopeStore
import io.embrace.android.embracesdk.internal.injection.createCurrentSessionPartSpan
import io.embrace.android.embracesdk.internal.injection.createEnvelopeResourceSource
import io.embrace.android.embracesdk.internal.injection.createExperimentTrackingService
import io.embrace.android.embracesdk.internal.injection.createHostedSdkVersionInfo
import io.embrace.android.embracesdk.internal.injection.createInstrumentationArgs
import io.embrace.android.embracesdk.internal.injection.createLogEnvelopeSource
import io.embrace.android.embracesdk.internal.injection.createLogOrchestrator
import io.embrace.android.embracesdk.internal.injection.createMetadataService
import io.embrace.android.embracesdk.internal.injection.createNavigationTrackingService
import io.embrace.android.embracesdk.internal.injection.createNetworkConnectivityService
import io.embrace.android.embracesdk.internal.injection.createOtelSdkConfig
import io.embrace.android.embracesdk.internal.injection.createPayloadCachingService
import io.embrace.android.embracesdk.internal.injection.createPayloadResurrectionService
import io.embrace.android.embracesdk.internal.injection.createPayloadStorageService
import io.embrace.android.embracesdk.internal.injection.createPayloadStore
import io.embrace.android.embracesdk.internal.injection.createProcessStateTracker
import io.embrace.android.embracesdk.internal.injection.createRedactionFunction
import io.embrace.android.embracesdk.internal.injection.createRequestExecutionService
import io.embrace.android.embracesdk.internal.injection.createRnBundleIdTracker
import io.embrace.android.embracesdk.internal.injection.createSessionIdsProvider
import io.embrace.android.embracesdk.internal.injection.createSessionOrchestrator
import io.embrace.android.embracesdk.internal.injection.createSessionPartEnvelopeSource
import io.embrace.android.embracesdk.internal.injection.createSessionPartReader
import io.embrace.android.embracesdk.internal.injection.createSessionPartTracker
import io.embrace.android.embracesdk.internal.injection.createUserService
import io.embrace.android.embracesdk.internal.injection.createUserSessionPropertiesService
import io.embrace.android.embracesdk.internal.injection.resolve
import io.embrace.android.embracesdk.internal.instrumentation.crash.CrashFileMarkerImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.LastRunCrashVerifier
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.SdkInitResourceUsageTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.createAppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.createStartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.createUiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.thread.blockage.createThreadBlockageService
import io.embrace.android.embracesdk.internal.logs.LogLimitingServiceImpl
import io.embrace.android.embracesdk.internal.logs.LogServiceImpl
import io.embrace.android.embracesdk.internal.logs.attachments.AttachmentService
import io.embrace.android.embracesdk.internal.otel.impl.EmbClock
import io.embrace.android.embracesdk.internal.otel.logs.LogSinkImpl
import io.embrace.android.embracesdk.internal.otel.sdk.DataValidator
import io.embrace.android.embracesdk.internal.otel.sdk.OtelSdkWrapper
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactory
import io.embrace.android.embracesdk.internal.otel.spans.EmbraceSpanFactoryImpl
import io.embrace.android.embracesdk.internal.otel.spans.SpanRepository
import io.embrace.android.embracesdk.internal.otel.spans.SpanService
import io.embrace.android.embracesdk.internal.otel.spans.SpanServiceImpl
import io.embrace.android.embracesdk.internal.session.id.SessionIdsProvider
import io.embrace.android.embracesdk.internal.session.id.SessionPartTracker
import io.embrace.android.embracesdk.internal.session.lifecycle.createLifecycleTracker
import io.embrace.android.embracesdk.internal.session.orchestrator.SessionOrchestrator
import io.embrace.android.embracesdk.internal.spans.TracingApiDelegate
import io.embrace.android.embracesdk.internal.storage.EmbraceStorageService
import io.embrace.android.embracesdk.internal.storage.StatFsAvailabilityChecker
import io.embrace.android.embracesdk.internal.store.KeyValueStore
import io.embrace.android.embracesdk.internal.store.Ordinal
import io.embrace.android.embracesdk.internal.store.OrdinalStore
import io.embrace.android.embracesdk.internal.store.OrdinalStoreImpl
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.event.EventBus
import io.embrace.android.embracesdk.internal.worker.PriorityWorker
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi
import java.util.concurrent.TimeUnit

/**
 * Constructs every service used by the initialized SDK, performs the side effects of initialization, and assembles
 * the [RealSdkInstance] that the public API delegates to. Only called once [ConfigLoadServices] has confirmed the
 * SDK is not disabled.
 *
 * [SdkOverrides] are test seams and are null in production. Where they are null the real construction
 * below runs instead; because [resolve] and [init] are inlined that costs no lambda allocation on the default path.
 *
 * Services that are referenced before they are constructed, to break dependency cycles, are declared up front as
 * `lateinit` and captured by the lambdas that need them.
 */
@Suppress("CyclomaticComplexMethod")
internal fun createRealSdkInstance(
    context: Context,
    preStartServices: PreStartServices,
    configLoadServices: ConfigLoadServices,
    sdkStartTimeMs: Long,
    sdkInitResourceUsageTracker: SdkInitResourceUsageTracker,
    overrides: SdkOverrides? = null,
): RealSdkInstance {
    val clock = preStartServices.clock
    val errorHandler = preStartServices.errorHandler
    val systemInfo = preStartServices.systemInfo
    val telemetryService = preStartServices.telemetryService
    val jsonSerializer = configLoadServices.jsonSerializer
    val instrumentedConfig = configLoadServices.instrumentedConfig
    val okHttpClient = configLoadServices.okHttpClient
    val uuidSource = configLoadServices.uuidSource

    // forward references that break dependency cycles
    lateinit var sessionIdsProvider: SessionIdsProvider
    lateinit var userService: Lazy<UserService>
    lateinit var sessionPartTracker: SessionPartTracker
    lateinit var processStateTracker: ProcessStateTracker
    lateinit var experimentTrackingService: Lazy<ExperimentTrackingService>
    lateinit var userSessionPropertiesService: UserSessionPropertiesService
    lateinit var instrumentationRegistry: InstrumentationRegistry
    lateinit var spanService: Lazy<SpanService>
    lateinit var embraceSpanFactory: EmbraceSpanFactory
    lateinit var sessionOrchestrator: SessionOrchestrator

    val startupClassifier = init(name = "startup-classifier", recordDuration = true) {
        StartupClassifierImpl()
    }

    val workerThreadPool = init("worker-thread-pool") { configLoadServices.workerThreadPool }
    val configService = init("config-service") { configLoadServices.configService }

    val otelSignalContext = init("otel-signal-context") {
        OtelSignalContext(
            sessionIdsProvider = { sessionIdsProvider },
            userServiceProvider = { userService.value },
            sessionPartTrackerProvider = { sessionPartTracker },
            processStateTrackerProvider = { processStateTracker },
            experimentTrackingServiceProvider = { experimentTrackingService.value },
            userSessionPropertiesServiceProvider = { userSessionPropertiesService },
            instrumentationRegistryProvider = { instrumentationRegistry },
        )
    }

    val openTelemetryClock = init("open-telemetry-clock") { EmbClock(clock) }

    val sensitiveKeysBehavior = init("sensitive-keys-behavior") {
        SensitiveKeysBehavior(configService.config.sensitiveKeys)
    }

    val spanRepository = init("span-repository") {
        overrides?.spanRepository.resolve { SpanRepository(errorHandler) }
    }

    val logSink = init("log-sink") { LogSinkImpl() }

    val otelSdkConfig = lazy {
        EmbTrace.trace("otel-sdk-config-init") {
            overrides?.otelSdkConfig.resolve {
                createOtelSdkConfig(
                    spanRepository = spanRepository,
                    logSink = logSink,
                    instrumentedConfig = instrumentedConfig,
                    systemInfo = systemInfo,
                    uuidSource = uuidSource,
                    errorHandler = errorHandler,
                    externalOtelConfig = preStartServices.externalOtelConfig,
                    sessionIdsProvider = otelSignalContext::getSessionIdsProvider,
                    userIdProvider = otelSignalContext::getUserId,
                    eventMetadataProvider = otelSignalContext::getEventMetadata,
                )
            }
        }
    }

    val otelSdkWrapper = lazy {
        EmbTrace.trace("otel-sdk-wrapper-init") {
            overrides?.otelSdkWrapper.resolve {
                OtelSdkWrapper(
                    otelClock = openTelemetryClock,
                    configuration = otelSdkConfig.value,
                    spanServiceProvider = { spanService.value },
                    errorHandler = errorHandler,
                    useKotlinSdk = configService.otelBehavior.shouldUseKotlinSdk(),
                )
            }
        }
    }

    val dataValidator = init("data-validator") {
        DataValidator(
            bypassValidation = configService::isOnlyUsingOtelExporters,
            telemetryService = telemetryService,
        )
    }

    val currentSessionPartSpan = init("current-session-part-span") {
        createCurrentSessionPartSpan(
            openTelemetryClock = openTelemetryClock,
            telemetryService = telemetryService,
            spanRepository = spanRepository,
            tracerSupplier = { otelSdkWrapper.value.sdkTracer },
            openTelemetrySupplier = { otelSdkWrapper.value.openTelemetryKotlin },
            embraceSpanFactorySupplier = { embraceSpanFactory },
            uuidSource = uuidSource,
            otelBehaviorSupplier = { configService.otelBehavior },
            customBreadcrumbLimitSupplier = { configService.config.breadcrumb.customLimit },
        )
    }

    embraceSpanFactory = init("embrace-span-factory") {
        EmbraceSpanFactoryImpl(
            openTelemetryClock = openTelemetryClock,
            spanRepository = spanRepository,
            dataValidator = dataValidator,
            stopCallback = currentSessionPartSpan::spanStopCallback,
            redactionFunction = createRedactionFunction(sensitiveKeysBehavior),
            telemetryService = telemetryService,
            errorHandler = errorHandler,
        )
    }

    spanService = lazy {
        EmbTrace.trace("span-service-init") {
            SpanServiceImpl(
                spanRepository = spanRepository,
                canStartNewSpan = currentSessionPartSpan::canStartNewSpan,
                dataValidator = dataValidator,
                embraceSpanFactory = embraceSpanFactory,
                tracer = otelSdkWrapper.value.sdkTracer,
                openTelemetry = otelSdkWrapper.value.openTelemetryKotlin,
            )
        }
    }

    val tracingApi: TracingApi by lazy {
        EmbTrace.trace("tracing-api-init") { TracingApiDelegate(spanService.value) }
    }

    val appContext = init("app-context") {
        overrides?.appContext.resolve { context.applicationContextOrSelf() }
    }

    val store: KeyValueStore by configLoadServices.keyValueStore

    val ordinalStore: OrdinalStore by lazy {
        EmbTrace.trace("ordinal-store-init") { OrdinalStoreImpl(store) }
    }

    val eventBus: EventBus by lazy {
        EmbTrace.trace("event-bus-init") { overrides?.eventBus.resolve { EventBus(errorHandler) } }
    }

    val appVersionStartupCounter: Int by lazy {
        EmbTrace.trace("app-version-startup-counter-init") {
            ordinalStore.incrementAndGet(
                ordinal = Ordinal.APP_VERSION_STARTUP,
                scope = instrumentedConfig.project.getVersionName() ?: "UNKNOWN",
            )
        }
    }

    processStateTracker = init(name = "process-state-tracker", recordDuration = true) {
        createProcessStateTracker(
            errorHandler = errorHandler,
            lifecycleTracker = overrides?.lifecycleTracker.resolve {
                createLifecycleTracker(configService, appContext as Application, context)
            },
        )
    }

    val navigationTrackingService = init("navigation-tracking-service") {
        createNavigationTrackingService()
    }

    val networkConnectivityService = init("network-connectivity-service") {
        overrides?.networkConnectivityService.resolve {
            createNetworkConnectivityService(
                context = appContext,
                configService = configService,
                worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                errorHandler = errorHandler,
            )
        }
    }

    sessionPartTracker = init("session-part-tracker") {
        createSessionPartTracker(appContext, errorHandler)
    }

    sessionIdsProvider = init("session-ids-provider") {
        overrides?.sessionIdsProvider.resolve { createSessionIdsProvider({ sessionOrchestrator }, sessionPartTracker) }
    }

    val telemetryDestination = init("telemetry-destination") {
        TelemetryDestinationImpl(
            clock = clock,
            spanService = spanService.value,
            loggerSupplier = { otelSdkWrapper.value.sdkLogger },
            currentSessionPartSpan = currentSessionPartSpan,
        )
    }

    userSessionPropertiesService = init("user-session-properties-service") {
        createUserSessionPropertiesService(
            store = lazy { store },
            configService = configService,
            destination = telemetryDestination,
            telemetryService = telemetryService,
        )
    }

    userService = lazy {
        EmbTrace.trace("user-service-init") {
            createUserService(store, clock, errorHandler)
        }
    }

    experimentTrackingService = lazy {
        EmbTrace.trace("experiment-tracking-service-init") {
            overrides?.experimentTrackingService.resolve {
                createExperimentTrackingService(configService, telemetryService, telemetryDestination)
            }
        }
    }

    val storageService = init("storage-service") {
        EmbraceStorageService(
            appContext,
            telemetryService,
            StatFsAvailabilityChecker(appContext),
        )
    }

    instrumentationRegistry = init("instrumentation-registry") {
        overrides?.instrumentationRegistry.resolve { InstrumentationRegistryImpl(errorHandler) }
    }

    val instrumentationArgs = init(name = "instrumentation-args", recordDuration = true) {
        createInstrumentationArgs(
            configService = configService,
            destination = telemetryDestination,
            errorHandler = errorHandler,
            clock = clock,
            context = appContext,
            application = appContext as Application,
            storeProvider = { store },
            serializer = jsonSerializer,
            uuidSource = uuidSource,
            ordinalStore = ordinalStore,
            processIdentifier = otelSdkConfig.value.processIdentifier,
            processStateTracker = processStateTracker,
            navigationTrackingService = navigationTrackingService,
            telemetryService = telemetryService,
            workerThreadPool = workerThreadPool,
            sessionPartTracker = sessionPartTracker,
            userSessionPropertiesService = userSessionPropertiesService,
            userSessionIdsProvider = { sessionIdsProvider.getCurrentUserSessionId() },
            activeSessionIdsProvider = { sessionIdsProvider.getActiveSessionIds() },
            crashMarkerFileProvider = { storageService.getFileForWrite("embrace_crash_marker") },
            eventBus = eventBus,
        )
    }

    val breadcrumbDataSource: Lazy<BreadcrumbDataSource?> = lazy {
        instrumentationRegistry.add {
            BreadcrumbDataSource(instrumentationArgs)
        }
    }

    val crashMarker = init("crash-marker") {
        CrashFileMarkerImpl(
            lazy { storageService.getFileForWrite(CrashFileMarkerImpl.CRASH_MARKER_FILE_NAME) },
        )
    }

    val lastRunCrashVerifier = init("last-run-crash-verifier") {
        LastRunCrashVerifier(crashMarker)
    }

    val versionChecker = overrides?.versionChecker.resolve { BuildVersionChecker }

    val startupService = init("startup-service") {
        createStartupService(
            destination = instrumentationArgs.destination,
            errorHandler = errorHandler,
            appVersionStartupCounterProvider = { appVersionStartupCounter },
        )
    }

    val appStartupDataCollector = init("app-startup-data-collector") {
        createAppStartupDataCollector(
            clock = clock,
            errorHandler = errorHandler,
            destination = instrumentationArgs.destination,
            configService = configService,
            startupService = startupService,
            startupClassifier = startupClassifier,
            versionChecker = versionChecker,
            activityManager = instrumentationArgs.systemService<ActivityManager>(Context.ACTIVITY_SERVICE),
            backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    val uiLoadDataListener = init("ui-load-data-listener") {
        createUiLoadDataListener(
            destination = instrumentationArgs.destination,
            configService = configService,
            versionChecker = versionChecker,
        )
    }

    val startupTracker = init("startup-tracker") {
        createStartupTracker(
            clock = clock,
            errorHandler = errorHandler,
            configService = configService,
            appStartupDataCollector = appStartupDataCollector,
            uiLoadDataListener = uiLoadDataListener,
            versionChecker = versionChecker,
        )
    }

    val deliveryTracer: DeliveryTracer? = overrides?.deliveryTracer.resolve { null }

    val processIdProvider: () -> String = { otelSdkConfig.value.processIdentifier }

    val dataPersistenceWorker: PriorityWorker<StoredTelemetryMetadata>? = init("data-persistence-worker") {
        when {
            !configService.isOnlyUsingOtelExporters() -> workerThreadPool.priorityWorker(Worker.Priority.DataPersistenceWorker)
            else -> null
        }
    }

    val payloadStorageService = init(name = "payload-storage-service", recordDuration = true) {
        dataPersistenceWorker?.let { worker ->
            overrides?.payloadStorageService.resolve {
                createPayloadStorageService(
                    location = StorageLocation.PAYLOAD,
                    context = appContext,
                    worker = worker,
                    processIdProvider = processIdProvider,
                    errorHandler = errorHandler,
                    clock = clock,
                    deliveryTracer = deliveryTracer,
                    limit = overrides?.payloadStorageLimit.resolve { PayloadStorageServiceImpl.DEFAULT_STORAGE_LIMIT },
                )
            }
        }
    }

    val cacheStorageService = init("cache-storage-service") {
        dataPersistenceWorker?.let { worker ->
            overrides?.cacheStorageService.resolve {
                createPayloadStorageService(
                    location = StorageLocation.CACHE,
                    context = appContext,
                    worker = worker,
                    processIdProvider = processIdProvider,
                    errorHandler = errorHandler,
                    clock = clock,
                    deliveryTracer = deliveryTracer,
                )
            }
        }
    }

    val requestExecutionService = init("request-execution-service") {
        if (dataPersistenceWorker != null) {
            overrides?.requestExecutionService.resolve {
                createRequestExecutionService(
                    configService = configService,
                    instrumentedConfig = instrumentedConfig,
                    okHttpClient = okHttpClient,
                    errorHandler = errorHandler,
                    deliveryTracer = deliveryTracer,
                )
            }
        } else {
            null
        }
    }

    val schedulingService = init("scheduling-service") {
        if (payloadStorageService != null && requestExecutionService != null) {
            SchedulingServiceImpl(
                payloadStorageService,
                requestExecutionService,
                workerThreadPool.backgroundWorker(Worker.Background.DeliverySchedulingWorker),
                workerThreadPool.backgroundWorker(Worker.Background.HttpRequestWorker),
                clock,
                errorHandler,
                deliveryTracer,
            )
        } else {
            null
        }
    }

    val intakeService = init("intake-service") {
        dataPersistenceWorker?.let { worker ->
            if (schedulingService != null && payloadStorageService != null && cacheStorageService != null) {
                overrides?.intakeService.resolve {
                    IntakeServiceImpl(
                        schedulingService,
                        payloadStorageService,
                        cacheStorageService,
                        errorHandler,
                        jsonSerializer,
                        worker,
                        deliveryTracer,
                    )
                }
            } else {
                null
            }
        }
    }

    val payloadStore = init("payload-store") {
        intakeService?.let {
            createPayloadStore(
                intakeService = it,
                clock = clock,
                processIdProvider = processIdProvider,
                uuidSource = uuidSource,
                sessionIdsProvider = sessionIdsProvider::getActiveSessionIds,
            )
        }
    }

    val payloadCachingService = init("payload-caching-service") {
        payloadStore?.let {
            createPayloadCachingService(
                worker = workerThreadPool.backgroundWorker(Worker.Background.PeriodicCacheWorker),
                errorHandler = errorHandler,
                periodicCacheIntervalMs = configService.otelBehavior.getPeriodicCacheIntervalMs(),
                clock = clock,
                sessionIdsProvider = sessionIdsProvider,
                payloadStore = it,
                deliveryTracer = deliveryTracer,
            )
        }
    }

    val cachedLogEnvelopeStore = init("cached-log-envelope-store") {
        dataPersistenceWorker?.let { worker ->
            createCachedLogEnvelopeStore(
                context = appContext,
                worker = worker,
                errorHandler = errorHandler,
                serializer = jsonSerializer,
                clock = clock,
            )
        }
    }

    val threadBlockageService = init("thread-blockage-service") {
        // An override that returns null disables the service: it is not replaced by the real construction
        overrides?.threadBlockageService.resolve { createThreadBlockageService(instrumentationArgs) }
    }

    val rnBundleIdTracker = lazy {
        EmbTrace.trace("rn-bundle-id-tracker-init") {
            createRnBundleIdTracker(
                context = appContext,
                configService = configService,
                store = store,
                worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            )
        }
    }

    val hostedSdkVersionInfo = init("hosted-sdk-version-info") {
        createHostedSdkVersionInfo(configService.appFramework, store)
    }

    val resourceSource = init(name = "resource-source", recordDuration = true) {
        createEnvelopeResourceSource(
            context = appContext,
            configService = configService,
            worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
            systemInfo = systemInfo,
            errorHandler = errorHandler,
            hostedSdkVersionInfo = hostedSdkVersionInfo,
            rnBundleIdTracker = rnBundleIdTracker,
            useKotlinSdkProvider = { otelSdkWrapper.value.useKotlinSdk },
        )
    }

    val envelopeMetadataSource = init("envelope-metadata-source") {
        EnvelopeMetadataSourceImpl { userService.value.getUserInfo() }
    }

    val sessionPartEnvelopeSource = init("session-part-envelope-source") {
        createSessionPartEnvelopeSource(
            symbolMap = configService.nativeSymbolMap,
            currentSessionPartSpan = currentSessionPartSpan,
            spanRepository = spanRepository,
            otelPayloadMapper = threadBlockageService,
            processStateTracker = processStateTracker,
            clock = clock,
            errorHandler = errorHandler,
            envelopeMetadataSource = envelopeMetadataSource,
            resourceSource = resourceSource,
        )
    }

    val logEnvelopeSource = init("log-envelope-source") {
        createLogEnvelopeSource(
            logSink = logSink,
            envelopeMetadataSource = envelopeMetadataSource,
            resourceSource = resourceSource,
            cachedLogEnvelopeStore = cachedLogEnvelopeStore,
        )
    }

    val metadataService = init("metadata-service") {
        createMetadataService(
            resourceSource = resourceSource,
            context = appContext,
            configService = configService,
            store = store,
            clock = clock,
            worker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
        )
    }

    val payloadResurrectionService = init("payload-resurrection-service") {
        intakeService?.let { intake ->
            if (payloadStorageService != null && cacheStorageService != null && cachedLogEnvelopeStore != null) {
                createPayloadResurrectionService(
                    intakeService = intake,
                    payloadStorageService = payloadStorageService,
                    cacheStorageService = cacheStorageService,
                    cachedLogEnvelopeStore = cachedLogEnvelopeStore,
                    errorHandler = errorHandler,
                    serializer = jsonSerializer,
                )
            } else {
                null
            }
        }
    }

    val logLimitingService = init("log-limiting-service") {
        LogLimitingServiceImpl(configService)
    }

    val logOrchestrator = init("log-orchestrator") {
        createLogOrchestrator(
            worker = overrides?.logOrchestratorWorker.resolve {
                workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker)
            },
            clock = clock,
            sink = logSink,
            payloadStore = payloadStore,
            logEnvelopeSource = logEnvelopeSource,
            errorHandler = errorHandler,
        )
    }

    val attachmentService = init("attachment-service") { AttachmentService() }

    // Deferred: only reached via the public logging API, never during SDK init.
    val logService = lazy(LazyThreadSafetyMode.PUBLICATION) {
        LogServiceImpl(
            telemetryDestination,
            configService,
            logLimitingService,
            telemetryService,
        )
    }

    val sessionPartReader = init("session-part-reader") {
        if (intakeService != null && cachedLogEnvelopeStore != null) {
            createSessionPartReader(
                context = appContext,
                clock = clock,
                errorHandler = errorHandler,
                configService = configService,
                sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                intakeService = intakeService,
                cachedLogEnvelopeStore = cachedLogEnvelopeStore,
                nativeCrashServiceProvider = {
                    instrumentationRegistry.findByType(NativeCrashDataSource::class)
                },
                restoreDecisionProvider = { sessionOrchestrator.userSessionRestoreDecision },
                processIdProvider = processIdProvider,
            )
        } else {
            null
        }
    }

    sessionOrchestrator = init(name = "session-orchestrator", recordDuration = true) {
        overrides?.sessionOrchestrator.resolve {
            createSessionOrchestrator(
                clock = clock,
                errorHandler = errorHandler,
                uuidSource = uuidSource,
                startupClassifier = startupClassifier,
                telemetryService = telemetryService,
                configService = configService,
                processStateTracker = processStateTracker,
                sessionPartTracker = sessionPartTracker,
                sessionIdsProvider = sessionIdsProvider,
                userService = userService.value,
                userSessionPropertiesService = userSessionPropertiesService,
                telemetryDestination = telemetryDestination,
                experimentRecordsProvider = { experimentTrackingService.value.getRecords() },
                currentSessionPartSpan = currentSessionPartSpan,
                spanRepository = spanRepository,
                processIdentifier = otelSdkConfig.value.processIdentifier,
                sessionPartEnvelopeSource = sessionPartEnvelopeSource,
                logEnvelopeSource = logEnvelopeSource,
                metadataService = metadataService,
                resourceSource = resourceSource,
                envelopeMetadataSource = envelopeMetadataSource,
                payloadStore = payloadStore,
                payloadCachingService = payloadCachingService,
                instrumentationRegistry = instrumentationRegistry,
                startupDurationProvider = startupService::getSdkStartupDuration,
                appVersionStartupCounterProvider = startupService::getAppVersionStartupCounter,
                logLimitingService = logLimitingService,
                ordinalStore = ordinalStore,
                store = store,
                backgroundWorker = workerThreadPool.backgroundWorker(Worker.Background.NonIoRegWorker),
                sessionPersistenceWorker = workerThreadPool.backgroundWorker(Worker.Background.SessionPersistenceWorker),
                sessionReadWorker = workerThreadPool.backgroundWorker(Worker.Background.IoRegWorker),
                sessionPartReader = sessionPartReader,
            )
        }
    }

    // the side effects of initialization, once every service is constructed
    EmbTrace.trace("sdk-services-start") {
        EmbTrace.trace(sectionName = "current-session-part-span-initialize-service", recordDuration = true) {
            currentSessionPartSpan.initializeService(sdkStartTimeMs)
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

    return EmbTrace.trace("real-sdk-instance-init") {
        val logsApiDelegate = LogsApiDelegate(
            logService = logService,
            attachmentService = attachmentService,
            errorHandler = errorHandler,
            payloadStore = payloadStore,
            serializer = jsonSerializer,
            telemetryService = telemetryService,
        )
        RealSdkInstance(
            clock = clock,
            errorHandler = errorHandler,
            primaryAbi = systemInfo.primaryAbi,
            configService = configService,
            workerThreadPool = workerThreadPool,
            okHttpClient = okHttpClient,
            eventBus = eventBus,
            appContext = appContext,
            spanRepository = spanRepository,
            logSink = logSink,
            otelSdkConfig = otelSdkConfig,
            processStateTracker = processStateTracker,
            sessionPartTracker = sessionPartTracker,
            networkConnectivityService = networkConnectivityService,
            telemetryDestination = telemetryDestination,
            instrumentationRegistry = instrumentationRegistry,
            instrumentationArgs = instrumentationArgs,
            sessionOrchestrator = sessionOrchestrator,
            sessionPartReader = sessionPartReader,
            logOrchestrator = logOrchestrator,
            attachmentService = attachmentService,
            logLimitingService = logLimitingService,
            metadataService = metadataService,
            payloadResurrectionService = payloadResurrectionService,
            payloadCachingService = payloadCachingService,
            payloadStore = payloadStore,
            schedulingService = schedulingService,
            cacheStorageService = cacheStorageService,
            threadBlockageService = threadBlockageService,
            lastRunCrashVerifier = lastRunCrashVerifier,
            crashMarker = crashMarker,
            startupTracker = startupTracker,
            startupService = startupService,
            appStartupDataCollector = appStartupDataCollector,
            sdkStartTimeMs = sdkStartTimeMs,
            sdkInitResourceUsageTracker = sdkInitResourceUsageTracker,
            userApiDelegate = UserApiDelegate(
                userService = userService,
            ),
            sessionApiDelegate = UserSessionApiDelegate(
                userSessionPropertiesService = userSessionPropertiesService,
                sessionOrchestrator = sessionOrchestrator,
            ),
            networkRequestApiDelegate = NetworkRequestApiDelegate(
                registry = instrumentationRegistry,
                httpRequestInfoModifierChain = instrumentationArgs.httpRequestInfoModifierChain,
                sessionOrchestrator = sessionOrchestrator,
            ),
            logsApiDelegate = logsApiDelegate,
            hostedSdkInternalInterface = HostedSdkInternalInterfaceImpl(
                logsApi = logsApiDelegate,
                configService = configService,
                resourceSource = resourceSource,
                instrumentationRegistry = instrumentationRegistry,
                serializer = jsonSerializer,
                rnBundleIdTracker = rnBundleIdTracker,
                hostedSdkVersionInfo = hostedSdkVersionInfo,
                errorHandler = errorHandler,
                telemetryService = telemetryService,
            ),
            viewTrackingApiDelegate = ViewTrackingApiDelegate(
                registry = instrumentationRegistry,
            ),
            sdkStateApiDelegate = SdkStateApiDelegate(
                deviceIdentifier = lazy { configService.deviceId },
                sessionIdsProvider = sessionIdsProvider,
                crashVerifier = lastRunCrashVerifier,
            ),
            otelApiDelegate = OTelApiDelegate(
                otelSdkWrapper = otelSdkWrapper,
            ),
            breadcrumbApiDelegate = BreadcrumbApiDelegate(
                clock = clock,
                breadcrumbDataSource = breadcrumbDataSource,
            ),
            instrumentationApiDelegate = InstrumentationApiDelegate(
                clock = clock,
                uiLoadTraceEmitter = uiLoadDataListener,
                appStartupDataCollector = appStartupDataCollector,
                navigationTrackingService = navigationTrackingService,
            ),
            experimentApiDelegate = ExperimentApiDelegate(
                clock = clock,
                experimentTrackingService = experimentTrackingService,
            ),
            tracingApi = tracingApi,
        )
    }
}

private inline fun <T> init(name: String, recordDuration: Boolean = false, supplier: () -> T): T =
    EmbTrace.trace("$name-init", recordDuration) { supplier() }

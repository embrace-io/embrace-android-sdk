package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
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
import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.behavior.SensitiveKeysBehavior
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkCaptureDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkRequestDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.ArtOptimizationState
import io.embrace.android.embracesdk.internal.instrumentation.startup.sdkInitEnvironmentAttributes
import io.embrace.android.embracesdk.internal.instrumentation.startup.toSdkInitDurationAttributes
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbCommonAttributes
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.embrace.android.embracesdk.semconv.ExperimentalSemconv
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import io.opentelemetry.kotlin.semconv.UserAttributes
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private const val WORKER_TERMINATION_TIMEOUT_MS = 2_000L

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
internal class RealSdkInstance(
    private val moduleGraph: InitializedModuleGraph,
    private val clock: Clock,
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(
        userService = lazy { moduleGraph.essentialServiceModule.userService },
    ),
    private val sessionApiDelegate: UserSessionApiDelegate = UserSessionApiDelegate(
        userSessionPropertiesService = moduleGraph.essentialServiceModule.userSessionPropertiesService,
        sessionOrchestrator = moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
    ),
    private val networkRequestApiDelegate: NetworkRequestApiDelegate = NetworkRequestApiDelegate(
        registry = moduleGraph.instrumentationModule.instrumentationRegistry,
        httpRequestInfoModifierChain = moduleGraph.instrumentationModule.instrumentationArgs.httpRequestInfoModifierChain,
        sessionOrchestrator = moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
    ),
    private val logsApiDelegate: LogsApiDelegate = LogsApiDelegate(
        logService = lazy { moduleGraph.logModule.logService },
        attachmentService = moduleGraph.logModule.attachmentService,
        logger = moduleGraph.initModule.logger,
        payloadStore = moduleGraph.deliveryModule?.payloadStore,
        serializer = moduleGraph.initModule.jsonSerializer,
        telemetryService = moduleGraph.initModule.telemetryService,
    ),
    private val hostedSdkInternalInterface: HostedSdkInternalInterfaceImpl = HostedSdkInternalInterfaceImpl(
        logsApi = logsApiDelegate,
        configService = moduleGraph.configService,
        resourceSource = moduleGraph.payloadSourceModule.resourceSource,
        instrumentationRegistry = moduleGraph.instrumentationModule.instrumentationRegistry,
        serializer = moduleGraph.initModule.jsonSerializer,
        rnBundleIdTracker = lazy { moduleGraph.payloadSourceModule.rnBundleIdTracker },
        hostedSdkVersionInfo = moduleGraph.payloadSourceModule.hostedSdkVersionInfo,
        logger = moduleGraph.initModule.logger,
        telemetryService = moduleGraph.initModule.telemetryService,
    ),
    private val viewTrackingApiDelegate: ViewTrackingApiDelegate = ViewTrackingApiDelegate(
        registry = moduleGraph.instrumentationModule.instrumentationRegistry,
    ),
    private val sdkStateApiDelegate: SdkStateApiDelegate = SdkStateApiDelegate(
        deviceIdentifier = lazy { moduleGraph.configService.deviceId },
        sessionIdsProvider = moduleGraph.userSessionOrchestrationModule.sessionIdsProvider,
        crashVerifier = moduleGraph.featureModule.lastRunCrashVerifier,
    ),
    private val otelApiDelegate: OTelApiDelegate = OTelApiDelegate(
        otelSdkWrapper = lazy { moduleGraph.openTelemetryModule.otelSdkWrapper },
    ),
    private val breadcrumbApiDelegate: BreadcrumbApiDelegate = BreadcrumbApiDelegate(
        clock = moduleGraph.initModule.clock,
        breadcrumbDataSource = lazy { moduleGraph.featureModule.breadcrumbDataSource },
    ),
    private val instrumentationApiDelegate: InstrumentationApiDelegate = InstrumentationApiDelegate(
        clock = moduleGraph.initModule.clock,
        uiLoadTraceEmitter = moduleGraph.dataCaptureServiceModule.uiLoadDataListener,
        appStartupDataCollector = moduleGraph.dataCaptureServiceModule.appStartupDataCollector,
        navigationTrackingService = moduleGraph.essentialServiceModule.navigationTrackingService,
    ),
    private val experimentApiDelegate: ExperimentApiDelegate = ExperimentApiDelegate(
        clock = moduleGraph.initModule.clock,
        experimentTrackingService = lazy { moduleGraph.essentialServiceModule.experimentTrackingService },
    ),
) : SdkInstance,
    LogsApi by logsApiDelegate,
    NetworkRequestApi by networkRequestApiDelegate,
    UserSessionApi by sessionApiDelegate,
    UserApi by userApiDelegate,
    SdkStateApi by sdkStateApiDelegate,
    OTelApi by otelApiDelegate,
    ViewTrackingApi by viewTrackingApiDelegate,
    BreadcrumbApi by breadcrumbApiDelegate,
    InstrumentationApi by instrumentationApiDelegate,
    ExperimentApi by experimentApiDelegate,
    InternalInterfaceApi by hostedSdkInternalInterface {

    private val tracingApi: TracingApi get() = moduleGraph.openTelemetryModule.tracingApi

    @Volatile
    private var applicationInitStartMs: Long? = null

    @Volatile
    private var started: Boolean = false

    override val isStarted: Boolean
        get() = started

    override fun getOpenTelemetryKotlin(): OpenTelemetry = if (started) {
        otelApiDelegate.getOpenTelemetryKotlin()
    } else {
        NoopOpenTelemetry
    }

    override fun onPostStart(buffer: BufferingSdkInstance) {
        buffer.drainCompletedSpans(tracingApi)
        applyCustomMetadata {
            buffer.drainExperimentCalls(experimentApiDelegate, experimentApiDelegate::replay)
        }
        // the buffered value was recorded first, so takes precedence
        buffer.applicationInitStartMs?.let { applicationInitStartMs = it }

        // drained last so errors raised by the drains above are captured
        buffer.drainInternalErrors(
            moduleGraph.essentialServiceModule.telemetryDestination,
            moduleGraph.configService.config.dataCaptureEvent.internalExceptionCaptureEnabled,
        )
    }

    override fun start(context: Context) {
        try {
            postInit()

            EmbTrace.trace(sectionName = "post-services-setup", recordDuration = true) {
                // not fully initialized, but the SDK shouldn't catastrophically throw after this point,
                // so we allow external calls.
                started = true
                registerListeners()
                loadInstrumentation()
                postLoadInstrumentation()
                triggerPayloadSend()
            }
            markSdkInitComplete(EmbTrace.durationTracker::flush)
        } catch (ignored: Throwable) {
            Log.w("Embrace", "Failed to initialize Embrace SDK", ignored)
        }
    }

    override fun disable() = close()

    /**
     * Tears down the SDK. Each step tolerates failure independently so that a partially initialized module graph
     * does not prevent the remaining steps from running.
     */
    override fun close() {
        started = false
        runCatching { moduleGraph.openTelemetryModule.otelSdkConfig.disableDataExport() }
        runCatching { moduleGraph.instrumentationModule.instrumentationRegistry.close() }

        val rootDir = runCatching { moduleGraph.coreModule.context.filesDir }.getOrNull()
        val fallbackDir = runCatching { moduleGraph.coreModule.context.cacheDir }.getOrNull()
        val workerThreadModule = runCatching { moduleGraph.workerThreadModule }.getOrNull()
        runCatching { moduleGraph.essentialServiceModule.close() }
        runCatching { moduleGraph.dataCaptureServiceModule.startupTracker.close() }
        runCatching { moduleGraph.openTelemetryModule.otelSdkConfig.shutdownExport() }
        runCatching { workerThreadModule?.close() }

        if (rootDir == null || fallbackDir == null) {
            return
        }
        val executor = runCatching { Executors.newSingleThreadExecutor() }.getOrNull() ?: return
        executor.execute {
            runCatching {
                if (workerThreadModule?.awaitTermination(WORKER_TERMINATION_TIMEOUT_MS) == false) {
                    Log.w("[Embrace]", "Timed out waiting for Embrace workers to finish before deleting data.")
                }
                StorageLocation.entries.map {
                    it.asFile(
                        logger = null,
                        rootDirSupplier = { rootDir },
                        fallbackDirSupplier = { fallbackDir },
                    ).value
                }.forEach {
                    it.deleteRecursively()
                }
            }.onFailure { exception ->
                Log.e("[Embrace]", "An error occurred while trying to disable Embrace SDK.", exception)
            }
        }
        // lets the queued deletion finish, then releases the worker thread
        executor.shutdown()
    }

    override fun createSpan(
        name: String,
        parent: EmbraceSpan?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = tracingApi.createSpan(name, parent, autoTerminationMode)

    override fun startSpan(
        name: String,
        parent: EmbraceSpan?,
        startTimeMs: Long?,
        autoTerminationMode: AutoTerminationMode,
    ): EmbraceSpan = tracingApi.startSpan(name, parent, startTimeMs, autoTerminationMode)

    override fun <T> recordSpan(
        name: String,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
        autoTerminationMode: AutoTerminationMode,
        code: () -> T,
    ): T = tracingApi.recordSpan(name, parent, attributes, events, autoTerminationMode, code)

    override fun getSpan(spanId: String): EmbraceSpan? = tracingApi.getSpan(spanId)

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = tracingApi.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events)

    override fun applicationInitStart() {
        if (applicationInitStartMs == null) {
            applicationInitStartMs = clock.now()
        }
    }

    override fun applicationInitEnd() {
        moduleGraph.dataCaptureServiceModule.appStartupDataCollector.run {
            this@RealSdkInstance.applicationInitStartMs?.let { applicationInitStart(it) }
            applicationInitEnd()
        }
    }

    /**
     * Performs bootstrapping by setting required values where there is an interdependency
     * between modules.
     */
    internal fun postInit() = with(moduleGraph) {
        EmbTrace.trace(sectionName = "post-init", recordDuration = true) {
            openTelemetryModule.setEventMetadataProvider(eventMetadataSupplierProvider())

            // note: otelBehavior is not applied here - it decides which OTel SDK is built, so it is set
            // right after the persisted config is read in InitializedModuleGraph.
            openTelemetryModule.applyConfiguration(
                sensitiveKeysBehavior = SensitiveKeysBehavior(configService.config.sensitiveKeys),
                bypassValidation = configService.isOnlyUsingOtelExporters(),
                otelBehavior = configService.otelBehavior,
                breadcrumbConfig = configService.config.breadcrumb,
            )

            deliveryModule?.payloadCachingService?.run {
                openTelemetryModule.spanRepository.addSpanChangeListener { reportBackgroundActivityStateChange() }
            }

            payloadSourceModule.metadataService.precomputeValues()

            // Start the log orchestrator
            openTelemetryModule.logSink.registerLogStoredCallback {
                logModule.logOrchestrator.onLogsAdded()
            }
            essentialServiceModule.telemetryDestination.sessionUpdateAction =
                userSessionOrchestrationModule.sessionOrchestrator::onSessionDataUpdate
            essentialServiceModule.telemetryDestination.currentStatesProvider =
                instrumentationModule.instrumentationRegistry::getCurrentStates

            openTelemetryModule.setSessionIdsProvider(userSessionOrchestrationModule.sessionIdsProvider)
            openTelemetryModule.setUserIdProvider { essentialServiceModule.userService.getUserInfo().userId }

            // Start the orchestrator and create the first session part once all the module dependencies have been created and wired up
            userSessionOrchestrationModule.sessionOrchestrator.start()
        }
    }

    /**
     * Apply custom metadata by the SDK user through Embrace APIs.
     * Should only be done after the SDK has been started successfully.
     */
    private fun applyCustomMetadata(applyMetadata: () -> Unit) = safeInit {
        applyMetadata()
    }

    /**
     * Registers listeners for various lifecycle/system callbacks.
     */
    private fun registerListeners() = with(moduleGraph) {
        safeInit {
            EmbTrace.trace("service-registration") {
                val ctx = coreModule.application
                dataCaptureServiceModule.startupTracker.register(ctx)

                // periodically fail any in-flight spans that have exceeded their timeout, so leaked spans
                // are terminated and their memory released even during a long-running session.
                val spanRepository = openTelemetryModule.spanRepository
                val clock = initModule.clock
                val logger = initModule.logger
                workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).scheduleWithFixedDelay(
                    {
                        try {
                            spanRepository.stopTimedOutSpans(clock.now())
                        } catch (exc: Throwable) {
                            logger.trackInternalError(InternalErrorType.SpanTimeoutSweepFail, exc)
                        }
                    },
                    SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
                    SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
                    TimeUnit.MILLISECONDS,
                )

                val sessionPartTracker = essentialServiceModule.sessionPartTracker
                val appStateTracker = essentialServiceModule.processStateTracker

                sessionPartTracker.addSessionPartChangeListener {
                    configService.networkBehavior.domainCountLimiter.reset()
                }

                appStateTracker.addListener(dataCaptureServiceModule.appStartupDataCollector)

                threadBlockageService?.let {
                    appStateTracker.addListener(it)
                    sessionPartTracker.addSessionPartChangeListener(it)
                }

                sessionPartTracker.addSessionPartChangeListener(logModule.attachmentService)
                sessionPartTracker.addSessionPartChangeListener(logModule.logLimitingService)
                sessionPartTracker.addSessionPartChangeListener(initModule.internalErrorHandler::resetLimit)
            }
        }
    }

    /**
     * Loads instrumentation via SPI and legacy methods.
     */
    private fun loadInstrumentation() = with(moduleGraph) {
        safeInit {
            EmbTrace.trace(sectionName = "load-instrumentation", recordDuration = true) {
                val registry = instrumentationModule.instrumentationRegistry
                registry.loadInstrumentations(loadInstrumentationProviders(), instrumentationModule.instrumentationArgs)

                threadBlockageService?.startCapture()
                featureModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
                    workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
                )
            }

            initializeHucInstrumentation()
        }
    }

    /**
     * Loads the [InstrumentationProvider] implementations declared via SPI. Before making changes
     * to this function please study R8, as currently it optimizes out lookup and reflection from
     * the startup path.
     *
     * See R8 for further details on how ServiceLoaderRewriter optimizes SPI:
     * https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
     */
    private fun loadInstrumentationProviders(): List<InstrumentationProvider> {
        val providers = mutableListOf<InstrumentationProvider>()
        for (provider in ServiceLoader.load(InstrumentationProvider::class.java, InstrumentationProvider::class.java.classLoader)) {
            providers.add(provider)
        }
        return providers
    }

    /**
     * Initializes HttpUrlConnection instrumentation via reflection, if the module is present and capture is enabled.
     */
    private fun initializeHucInstrumentation() = with(moduleGraph) {
        val networkBehavior = configService.networkBehavior
        try {
            if (networkBehavior.isHttpUrlConnectionCaptureEnabled()) {
                EmbTrace.trace(sectionName = "huc-init", recordDuration = true) {
                    val trackerClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huc.HttpUrlConnectionTracker")
                    val objectField = trackerClass.getDeclaredField("INSTANCE")
                    val trackerObject = objectField.get(null)
                    val initMethod = trackerClass.getDeclaredMethod(
                        "registerUrlStreamHandlerFactory",
                        Boolean::class.java,
                        InstrumentationArgs::class.java,
                        NetworkRequestDataSource::class.java,
                        NetworkCaptureDataSource::class.java,
                    )
                    val module = instrumentationModule
                    initMethod.invoke(
                        trackerObject,
                        networkBehavior.isRequestContentLengthCaptureEnabled(),
                        module.instrumentationArgs,
                        module.instrumentationRegistry.findByType(NetworkRequestDataSource::class),
                        module.instrumentationRegistry.findByType(NetworkCaptureDataSource::class),
                    )
                }
            }
        } catch (t: Throwable) {
            initModule.logger.trackInternalError(InternalErrorType.InstrumentationRegFail, t)
        }
    }

    /**
     * Performs post-load instrumentation tasks such as setting listeners.
     */
    internal fun postLoadInstrumentation() = with(moduleGraph) {
        safeInit {
            // setup crash teardown handlers
            val registry = instrumentationModule.instrumentationRegistry
            registry.findByType(JvmCrashDataSource::class)?.apply {
                threadBlockageService?.let(::addCrashTeardownHandler)
                addCrashTeardownHandler(logModule.logOrchestrator)
                addCrashTeardownHandler(userSessionOrchestrationModule.sessionOrchestrator)
                addCrashTeardownHandler(featureModule.crashMarker)
                deliveryModule?.payloadStore?.let(::addCrashTeardownHandler)
            }
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                with(essentialServiceModule.networkConnectivityService) {
                    registry.findByType(NetworkStatusDataSource::class)?.let(::addNetworkConnectivityListener)
                    registry.findByType(NetworkStateDataSource::class)?.let(::addNetworkConnectivityListener)
                    register()
                }
            }
        }
    }

    /**
     * Trigger sending cached data on disk.
     */
    private fun triggerPayloadSend() = with(moduleGraph) {
        safeInit {
            val worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker)
            worker.submit {
                // Get real network status as soon as we can on the worker.
                // This allows the delivery triggered by resurrection to get the most accurate network status as possible
                // so that it won't waste time trying to send when the device is known to be offline.
                deliveryModule?.schedulingService?.let(
                    essentialServiceModule.networkConnectivityService::addNetworkConnectivityListener,
                )

                // process multi-file session part directories before resurrection can run. The reader resurrects
                // dead parts itself, so a part this pass doesn't reach is still resurrected by a later pass
                userSessionOrchestrationModule.sessionPartReader?.readPersistedSessionParts(true)

                val resurrectionService = payloadSourceModule.payloadResurrectionService
                var resurrectionAttempted = false
                if (resurrectionService != null) {
                    deliveryModule?.schedulingService?.let { scheduler ->
                        resurrectionService.addResurrectionCompleteListener(scheduler::onResurrectionComplete)
                    }
                    resurrectionService.resurrectOldPayloads(
                        nativeCrashServiceProvider = {
                            instrumentationModule.instrumentationRegistry.findByType(NativeCrashDataSource::class)
                        },
                        userSessionRestoreDecisionProvider = {
                            userSessionOrchestrationModule.sessionOrchestrator.userSessionRestoreDecision
                        },
                        pendingSessionPartIdsProvider = {
                            userSessionOrchestrationModule.sessionPartReader?.storedSessionPartIds() ?: emptySet()
                        },
                    )
                    resurrectionAttempted = true
                } else {
                    val payloadCount = deliveryModule?.cacheStorageService?.getUndeliveredPayloads()?.size ?: 0
                    initModule.logger.trackInternalError(
                        type = InternalErrorType.PayloadResurrectionFail,
                        throwable = IllegalStateException(
                            "Resurrection service not found. Undelivered payloads not processed: $payloadCount",
                        ),
                    )
                }

                // Unblock scheduler and attempt to send undelivered payloads if no resurrection was attempted
                if (!resurrectionAttempted) {
                    deliveryModule?.schedulingService?.onResurrectionComplete()
                }
            }
        }
    }

    /**
     * Mark SDK initialization as complete.
     */
    private fun markSdkInitComplete(sdkInitDurationsProvider: () -> Map<String, Long>) = with(moduleGraph) {
        safeInit {
            val startupService = dataCaptureServiceModule.startupService
            EmbTrace.trace("startup-tracking") {
                val sdkInitDurations = sdkInitDurationsProvider()
                val resourceUsageTracker = sdkInitResourceUsageTracker
                resourceUsageTracker.captureEnd()
                startupService.setSdkStartupInfo(
                    startTimeMs = sdkStartTimeMs,
                    endTimeMs = initModule.clock.now(),
                    endState = essentialServiceModule.processStateTracker.getAppState(),
                    threadName = Thread.currentThread().name,
                    attributesProvider = {
                        sdkInitDurations.toSdkInitDurationAttributes() +
                            resourceUsageTracker.buildAttributes() +
                            sdkInitEnvironmentAttributes(
                                nowMs = initModule.clock.now(),
                                logger = initModule.logger,
                                packageInfo = instrumentationModule.instrumentationArgs.packageInfo,
                                powerManagerProvider = {
                                    instrumentationModule.instrumentationArgs.systemService(Context.POWER_SERVICE)
                                },
                                activityManagerProvider = {
                                    instrumentationModule.instrumentationArgs.systemService(Context.ACTIVITY_SERVICE)
                                },
                                prefsFileSizeProvider = { defaultPrefsFile(coreModule.context)?.length() },
                            ) {
                                coreModule.context.applicationInfo?.sourceDir?.let { sourceDir ->
                                    ArtOptimizationState.create(
                                        apkPath = sourceDir,
                                        primaryAbi = initModule.systemInfo.primaryAbi,
                                        logger = initModule.logger,
                                    )
                                }
                            }
                    },
                )
            }
            workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
                startupService.recordSdkInitSpan()
            }
            val appId = configService.appId
            val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" +
                (appId?.let { " for appId = $it" } ?: " without an app ID")
            initModule.logger.logInfo(startMsg)
        }
    }

    @OptIn(ExperimentalSemconv::class)
    private fun eventMetadataSupplierProvider(): Provider<Map<String, String>> = with(moduleGraph) {
        {
            mutableMapOf<String, String>().apply {
                val sessionPart = essentialServiceModule.sessionPartTracker.getActiveSessionPart()
                val sessionState = sessionPart?.processState ?: essentialServiceModule.processStateTracker.getAppState()
                val sessionIds = userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds()

                put(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionIds.sessionPartId)
                put(EmbSessionAttributes.EMB_USER_SESSION_ID, sessionIds.userSessionId)
                put(EmbSessionAttributes.EMB_STATE, sessionState.description)
                essentialServiceModule.userService.getUserInfo().userId?.let {
                    put(UserAttributes.USER_ID, it)
                }
                essentialServiceModule.experimentTrackingService.getRecords()?.let {
                    put(EmbCommonAttributes.EMB_EXPERIMENTS, it)
                }
                putAll(
                    essentialServiceModule.userSessionPropertiesService
                        .getProperties()
                        .mapKeys { property ->
                            property.key.toEmbraceAttributeName()
                        },
                )
                instrumentationModule.instrumentationRegistry.getCurrentStates().forEach {
                    put(it.key, it.value.toString())
                }
            }
        }
    }

    /**
     * Run the given initialization phase and record any failures, but allow execution to continue even if it does.
     */
    private inline fun safeInit(
        init: () -> Unit,
    ) {
        try {
            init()
        } catch (t: Throwable) {
            moduleGraph.initModule.logger.trackInternalError(InternalErrorType.SdkInitPhaseFail, t)
        }
    }

    /**
     * Attempt to identify the size of the host app's default `SharedPreferences` file without actually opening it.
     * This file currently hosts the SDK's key-value store, and the bigger it is, the more impact it will have on
     * SDK init time if the SDK is the thing that opens it first.
     *
     * Returns null if the file is absent, so a missing file is not reported.
     */
    private fun defaultPrefsFile(context: Context): File? =
        File(File(context.applicationInfo.dataDir, "shared_prefs"), "${context.packageName}_preferences.xml")
            .takeIf(File::exists)
}

private const val SPAN_TIMEOUT_SWEEP_INTERVAL_MS = 30_000L

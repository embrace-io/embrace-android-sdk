package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
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
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.ModuleGraph
import io.embrace.android.embracesdk.internal.injection.applyCustomMetadata
import io.embrace.android.embracesdk.internal.injection.loadInstrumentation
import io.embrace.android.embracesdk.internal.injection.markSdkInitComplete
import io.embrace.android.embracesdk.internal.injection.postInit
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
import io.embrace.android.embracesdk.internal.injection.registerListeners
import io.embrace.android.embracesdk.internal.injection.triggerPayloadSend
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.NoopOpenTelemetry
import io.opentelemetry.kotlin.OpenTelemetry
import java.util.concurrent.Executors

private const val WORKER_TERMINATION_TIMEOUT_MS = 2_000L

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
internal class RealSdkInstance(
    private val moduleGraph: ModuleGraph,
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
        moduleGraph.applyCustomMetadata {
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
            moduleGraph.postInit()

            EmbTrace.trace(sectionName = "post-services-setup", recordDuration = true) {
                // not fully initialized, but the SDK shouldn't catastrophically throw after this point,
                // so we allow external calls.
                started = true
                moduleGraph.registerListeners()
                moduleGraph.loadInstrumentation()
                moduleGraph.postLoadInstrumentation()
                moduleGraph.triggerPayloadSend()
            }
            moduleGraph.markSdkInitComplete(EmbTrace.durationTracker::flush)
        } catch (ignored: Throwable) {
            Log.w("Embrace", "Failed to initialize Embrace SDK", ignored)
        }
    }

    override fun disable() {
        try {
            moduleGraph.openTelemetryModule.otelSdkConfig.disableDataExport()
            val rootDir = moduleGraph.coreModule.context.filesDir
            val fallbackDir = moduleGraph.coreModule.context.cacheDir
            val workerThreadModule = moduleGraph.workerThreadModule
            started = false
            moduleGraph.essentialServiceModule.networkConnectivityService.close()
            moduleGraph.openTelemetryModule.otelSdkConfig.shutdownExport()
            workerThreadModule.close()
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
                workerThreadModule.awaitTermination(Worker.Priority.DataPersistenceWorker, WORKER_TERMINATION_TIMEOUT_MS)
                runCatching {
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
        } catch (ignored: Throwable) {
        }
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
}

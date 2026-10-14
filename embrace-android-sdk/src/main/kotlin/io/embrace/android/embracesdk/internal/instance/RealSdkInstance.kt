package io.embrace.android.embracesdk.internal.instance

import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.ExperimentApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.UserSessionApi
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ExperimentApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.InstrumentationApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserSessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.applyCustomMetadata
import io.embrace.android.embracesdk.internal.injection.loadInstrumentation
import io.embrace.android.embracesdk.internal.injection.markSdkInitComplete
import io.embrace.android.embracesdk.internal.injection.postInit
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
import io.embrace.android.embracesdk.internal.injection.registerListeners
import io.embrace.android.embracesdk.internal.injection.triggerPayloadSend
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.AutoTerminationMode
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.OpenTelemetry
import java.util.concurrent.Executors

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
internal class RealSdkInstance(
    lazyBootstrapper: Lazy<ModuleInitBootstrapper>,
    private val hostedSdkApiProvider: Provider<SdkApi>,
    private val sdkCallChecker: SdkCallChecker,
    openTelemetryKotlin: OpenTelemetry,
    private val clock: Clock,
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
    ),
    private val sessionApiDelegate: UserSessionApiDelegate = UserSessionApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
    ),
    private val networkRequestApiDelegate: NetworkRequestApiDelegate =
        NetworkRequestApiDelegate(lazyBootstrapper, sdkCallChecker),
    private val logsApiDelegate: LogsApiDelegate = LogsApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
    ),
    private val viewTrackingApiDelegate: ViewTrackingApiDelegate =
        ViewTrackingApiDelegate(lazyBootstrapper, sdkCallChecker),
    private val sdkStateApiDelegate: SdkStateApiDelegate = SdkStateApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
    ),
    private val otelApiDelegate: OTelApiDelegate =
        OTelApiDelegate(lazyBootstrapper, sdkCallChecker, openTelemetryKotlin),
    private val breadcrumbApiDelegate: BreadcrumbApiDelegate = BreadcrumbApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
    ),
    private val instrumentationApiDelegate: InstrumentationApiDelegate =
        InstrumentationApiDelegate(lazyBootstrapper, sdkCallChecker),
    private val experimentApiDelegate: ExperimentApiDelegate = ExperimentApiDelegate(
        lazyBootstrapper,
        sdkCallChecker,
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
    InternalInterfaceApi {

    private val bootstrapper by lazyBootstrapper
    private val tracingApi: TracingApi get() = bootstrapper.openTelemetryModule.tracingApi

    private var internalInterfaceModule: InternalInterfaceModule? = null

    @Volatile
    private var applicationInitStartMs: Long? = null

    override fun onPreStart(buffer: BufferingSdkInstance) {
        // drain before building OTel SDK
        buffer.drainOTelConfig(otelApiDelegate)
    }

    override fun onPostStart(buffer: BufferingSdkInstance) {
        buffer.drainCompletedSpans(tracingApi)
        bootstrapper.applyCustomMetadata {
            buffer.drainExperimentCalls(experimentApiDelegate, experimentApiDelegate::replay)
        }
        // the buffered value was recorded first, so takes precedence
        buffer.applicationInitStartMs?.let { applicationInitStartMs = it }
    }

    override fun start(context: Context) {
        EmbTrace.trace("sdk-start") {
            try {
                if (!bootstrapper.init(context)) {
                    return
                }
                bootstrapper.postInit()

                EmbTrace.trace(sectionName = "post-services-setup", recordDuration = true) {
                    internalInterfaceModule = InternalInterfaceModuleImpl(
                        bootstrapper.initModule,
                        bootstrapper.configService,
                        bootstrapper.payloadSourceModule,
                        hostedSdkApiProvider(),
                        bootstrapper,
                    )

                    // not fully initialized, but the SDK shouldn't catastrophically throw after this point,
                    // so we allow external calls.
                    sdkCallChecker.started.set(true)
                    bootstrapper.registerListeners()
                    bootstrapper.loadInstrumentation()
                    bootstrapper.postLoadInstrumentation()
                    bootstrapper.triggerPayloadSend()
                }
                bootstrapper.markSdkInitComplete(EmbTrace.durationTracker::flush)
            } catch (ignored: Throwable) {
                Log.w("Embrace", "Failed to initialize Embrace SDK", ignored)
            }
        }
    }

    override fun disable() {
        if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.otelSdkConfig.disableDataExport()
            val rootDir = bootstrapper.coreModule.context.filesDir
            val fallbackDir = bootstrapper.coreModule.context.cacheDir
            sdkCallChecker.started.set(false)
            bootstrapper.stop()
            val executor = Executors.newSingleThreadExecutor()
            executor.execute {
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
        }
    }

    /**
     * Gets the [io.embrace.android.embracesdk.internal.EmbraceInternalInterface] that should be used as the sole source of
     * communication with other Android SDK modules.
     */
    override val internalInterface: EmbraceInternalInterface
        get() {
            return checkNotNull(internalInterfaceModule?.embraceInternalInterface)
        }

    override val reactNativeInternalInterface: ReactNativeInternalInterface
        get() {
            return checkNotNull(internalInterfaceModule?.reactNativeInternalInterface)
        }

    override val unityInternalInterface: UnityInternalInterface
        get() {
            return checkNotNull(internalInterfaceModule?.unityInternalInterface)
        }

    override val flutterInternalInterface: FlutterInternalInterface
        get() {
            return checkNotNull(internalInterfaceModule?.flutterInternalInterface)
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
        if (sdkCallChecker.check("application_init_end", false)) {
            bootstrapper.dataCaptureServiceModule.appStartupDataCollector.run {
                this@RealSdkInstance.applicationInitStartMs?.let { applicationInitStart(it) }
                applicationInitEnd()
            }
        }
    }
}

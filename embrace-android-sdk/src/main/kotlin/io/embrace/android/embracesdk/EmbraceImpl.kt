package io.embrace.android.embracesdk

import android.annotation.SuppressLint
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
import io.embrace.android.embracesdk.internal.instance.BufferingSdkInstance
import io.embrace.android.embracesdk.internal.logging.InternalErrorHandler
import io.embrace.android.embracesdk.internal.telemetry.InternalTelemetryService
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.spans.EmbraceSpan
import io.embrace.android.embracesdk.spans.EmbraceSpanEvent
import io.embrace.android.embracesdk.spans.ErrorCode
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.kotlin.logging.export.LogRecordExporter
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.tracing.export.SpanExporter
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import java.util.concurrent.Executors

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceImpl(
    private val bootstrapper: ModuleInitBootstrapper,
    private val hostedSdkApiProvider: Provider<SdkApi>,
    private val sdkCallChecker: SdkCallChecker =
        SdkCallChecker(bootstrapper.initModule.logger),
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(bootstrapper, sdkCallChecker),
    private val sessionApiDelegate: UserSessionApiDelegate = UserSessionApiDelegate(bootstrapper, sdkCallChecker),
    private val networkRequestApiDelegate: NetworkRequestApiDelegate =
        NetworkRequestApiDelegate(bootstrapper, sdkCallChecker),
    private val logsApiDelegate: LogsApiDelegate = LogsApiDelegate(bootstrapper, sdkCallChecker),
    private val viewTrackingApiDelegate: ViewTrackingApiDelegate =
        ViewTrackingApiDelegate(bootstrapper, sdkCallChecker),
    private val sdkStateApiDelegate: SdkStateApiDelegate = SdkStateApiDelegate(bootstrapper, sdkCallChecker),
    private val otelApiDelegate: OTelApiDelegate = OTelApiDelegate(bootstrapper, sdkCallChecker),
    private val breadcrumbApiDelegate: BreadcrumbApiDelegate = BreadcrumbApiDelegate(bootstrapper, sdkCallChecker),
    private val instrumentationApiDelegate: InstrumentationApiDelegate =
        InstrumentationApiDelegate(bootstrapper, sdkCallChecker),
    private val experimentApiDelegate: ExperimentApiDelegate = ExperimentApiDelegate(bootstrapper, sdkCallChecker),
    private val preStartBuffer: BufferingSdkInstance = BufferingSdkInstance(
        clock = { bootstrapper.initModule.clock.now() },
        logger = bootstrapper.initModule.logger,
    ),
) : SdkApi,
    LogsApi by logsApiDelegate,
    NetworkRequestApi by networkRequestApiDelegate,
    UserSessionApi by sessionApiDelegate,
    UserApi by userApiDelegate,
    TracingApi by bootstrapper.openTelemetryModule.tracingApi,
    SdkStateApi by sdkStateApiDelegate,
    OTelApi by otelApiDelegate,
    ViewTrackingApi by viewTrackingApiDelegate,
    BreadcrumbApi by breadcrumbApiDelegate,
    InstrumentationApi by instrumentationApiDelegate,
    ExperimentApi by preStartBuffer,
    InternalInterfaceApi {

    val telemetryService: InternalTelemetryService get() = bootstrapper.initModule.telemetryService
    val internalErrorHandler: InternalErrorHandler get() = bootstrapper.initModule.logger
    private val startStopLock = Any()

    private var internalInterfaceModule: InternalInterfaceModule? = null

    override fun start(context: Context) {
        EmbTrace.trace("sdk-start") {
            synchronized(startStopLock) {
                try {
                    // drain before building OTel SDK
                    preStartBuffer.drainOTelConfig(otelApiDelegate)
                    if (!bootstrapper.init(context)) {
                        return
                    }
                    // replay spans before the first session part starts
                    preStartBuffer.drainCompletedSpans(bootstrapper.openTelemetryModule.tracingApi)
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
                        bootstrapper.applyCustomMetadata {
                            preStartBuffer.drainExperimentCalls(experimentApiDelegate, experimentApiDelegate::replay)
                        }
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
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        synchronized(startStopLock) {
            sdkCallChecker.started.set(false)
            bootstrapper.stop()
        }
    }

    override fun disable() {
        synchronized(startStopLock) {
            if (sdkCallChecker.started.get()) {
                bootstrapper.openTelemetryModule.otelSdkConfig.disableDataExport()
                val rootDir = bootstrapper.coreModule.context.filesDir
                val fallbackDir = bootstrapper.coreModule.context.cacheDir
                stop()
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
    }

    /**
     * Gets the [EmbraceInternalInterface] that should be used as the sole source of
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

    override fun addSpanExporter(spanExporter: SpanExporter) = preStartBuffer.addSpanExporter(spanExporter)

    override fun addSpanProcessor(spanProcessor: SpanProcessor) = preStartBuffer.addSpanProcessor(spanProcessor)

    override fun addLogRecordExporter(logRecordExporter: LogRecordExporter) =
        preStartBuffer.addLogRecordExporter(logRecordExporter)

    override fun addLogRecordProcessor(logRecordProcessor: LogRecordProcessor) =
        preStartBuffer.addLogRecordProcessor(logRecordProcessor)

    override fun setResourceAttribute(key: String, value: String) = preStartBuffer.setResourceAttribute(key, value)

    override fun recordCompletedSpan(
        name: String,
        startTimeMs: Long,
        endTimeMs: Long,
        errorCode: ErrorCode?,
        parent: EmbraceSpan?,
        attributes: Map<String, String>,
        events: List<EmbraceSpanEvent>,
    ): Boolean = preStartBuffer.recordCompletedSpan(name, startTimeMs, endTimeMs, errorCode, parent, attributes, events)

    override fun applicationInitStart() = preStartBuffer.applicationInitStart()

    override fun applicationInitEnd() {
        if (sdkCallChecker.check("application_init_end", false)) {
            bootstrapper.dataCaptureServiceModule.appStartupDataCollector.run {
                preStartBuffer.applicationInitStartMs?.let { applicationInitStart(it) }
                applicationInitEnd()
            }
        }
    }
}

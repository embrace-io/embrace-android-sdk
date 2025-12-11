package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OTelApi
import io.embrace.android.embracesdk.internal.api.SdkApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.SessionApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.InstrumentationApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.delivery.storage.asFile
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.loadInstrumentation
import io.embrace.android.embracesdk.internal.injection.markSdkInitComplete
import io.embrace.android.embracesdk.internal.injection.postInit
import io.embrace.android.embracesdk.internal.injection.postLoadInstrumentation
import io.embrace.android.embracesdk.internal.injection.registerListeners
import io.embrace.android.embracesdk.internal.injection.triggerPayloadSend
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.EmbTrace.end
import io.embrace.android.embracesdk.internal.utils.EmbTrace.start
import io.embrace.android.embracesdk.spans.TracingApi
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
    private val bootstrapper: ModuleInitBootstrapper = EmbTrace.trace("bootstrapper-init", ::ModuleInitBootstrapper),
    private val sdkCallChecker: SdkCallChecker =
        SdkCallChecker(bootstrapper.initModule.logger, bootstrapper.initModule.telemetryService),
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(bootstrapper, sdkCallChecker),
    private val sessionApiDelegate: SessionApiDelegate = SessionApiDelegate(bootstrapper, sdkCallChecker),
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
) : SdkApi,
    LogsApi by logsApiDelegate,
    NetworkRequestApi by networkRequestApiDelegate,
    SessionApi by sessionApiDelegate,
    UserApi by userApiDelegate,
    TracingApi by bootstrapper.openTelemetryModule.embraceTracer,
    SdkStateApi by sdkStateApiDelegate,
    OTelApi by otelApiDelegate,
    ViewTrackingApi by viewTrackingApiDelegate,
    BreadcrumbApi by breadcrumbApiDelegate,
    InstrumentationApi by instrumentationApiDelegate,
    InternalInterfaceApi {

    init {
        EmbraceInternalApi.internalInterfaceApi = this
        EmbraceInternalApi.isStarted = sdkCallChecker.started::get
    }

    private val logger by lazy { bootstrapper.initModule.logger }
    private val clock by lazy { bootstrapper.initModule.clock }

    @Volatile
    private var applicationInitStartMs: Long? = null

    private var internalInterfaceModule: InternalInterfaceModule? = null

    override fun start(context: Context) {
        try {
            if (!bootstrapper.init(context)) {
                return
            }
            bootstrapper.postInit()

            start("post-services-setup")
            internalInterfaceModule = InternalInterfaceModuleImpl(
                bootstrapper.initModule,
                bootstrapper.configModule,
                bootstrapper.payloadSourceModule,
                bootstrapper.instrumentationModule,
                this,
                bootstrapper
            )

            // not fully initialized, but the SDK shouldn't catastrophically throw after this point,
            // so we allow external calls.
            sdkCallChecker.started.set(true)
            bootstrapper.registerListeners()
            bootstrapper.loadInstrumentation()
            initializeHucInstrumentation(bootstrapper.configModule.configService.networkBehavior)
            bootstrapper.postLoadInstrumentation()
            bootstrapper.triggerPayloadSend()
            bootstrapper.markSdkInitComplete()
            end()
        } catch (ignored: Throwable) {
            Log.w("Embrace", "Failed to initialize Embrace SDK", ignored)
        }
    }

    private fun initializeHucInstrumentation(networkBehavior: NetworkBehavior) {
        try {
            if (networkBehavior.isHttpUrlConnectionCaptureEnabled()) {
                val trackerClass = Class.forName("io.embrace.android.embracesdk.instrumentation.huc.HttpUrlConnectionTracker")
                val objectField = trackerClass.getDeclaredField("INSTANCE")
                val trackerObject = objectField.get(null)
                val initMethod = trackerClass.getDeclaredMethod(
                    "registerUrlStreamHandlerFactory",
                    Boolean::class.java,
                    SdkStateApi::class.java,
                    InstrumentationApi::class.java,
                    NetworkRequestApi::class.java,
                    EmbraceInternalInterface::class.java,
                )
                initMethod.invoke(
                    trackerObject,
                    networkBehavior.isRequestContentLengthCaptureEnabled(),
                    sdkStateApiDelegate,
                    instrumentationApiDelegate,
                    networkRequestApiDelegate,
                    internalInterface,
                )
            }
        } catch (t: Throwable) {
            logger.trackInternalError(InternalErrorType.INSTRUMENTATION_REG_FAIL, t)
        }
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        bootstrapper.stop()
    }

    override fun disable() {
        if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.otelSdkConfig.disableDataExport()
            stop()
            Executors.newSingleThreadExecutor().execute {
                runCatching {
                    StorageLocation.entries.map {
                        it.asFile(
                            logger = logger,
                            rootDirSupplier = { bootstrapper.coreModule.context.filesDir },
                            fallbackDirSupplier = { bootstrapper.coreModule.context.cacheDir }
                        ).value
                    }.forEach {
                        it.deleteRecursively()
                    }
                }.onFailure { exception ->
                    Log.e("[Embrace]", "An error occurred while trying to disable Embrace SDK.", exception)
                }
            }
        }
    }

    fun logMessage(
        severity: Severity,
        message: String,
        attributes: Map<String, Any> = emptyMap(),
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        logExceptionType: LogExceptionType = LogExceptionType.NONE,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
        embraceAttributes: Map<String, String> = emptyMap(),
    ) {
        logsApiDelegate.logMessageImpl(
            severity = severity,
            message = message,
            attributes = attributes,
            stackTraceElements = stackTraceElements,
            customStackTrace = customStackTrace,
            logExceptionType = logExceptionType,
            exceptionName = exceptionName,
            exceptionMessage = exceptionMessage,
            embraceAttributes = embraceAttributes,
        )
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

    override fun applicationInitStart() {
        if (applicationInitStartMs == null) {
            applicationInitStartMs = clock.now()
        }
    }

    override fun applicationInitEnd() {
        if (sdkCallChecker.check("application_init_end", false)) {
            bootstrapper.dataCaptureServiceModule.appStartupDataCollector.run {
                if (applicationInitStartMs != null) {
                    applicationInitStart(applicationInitStartMs)
                }
                applicationInitEnd()
            }
        }
    }
}

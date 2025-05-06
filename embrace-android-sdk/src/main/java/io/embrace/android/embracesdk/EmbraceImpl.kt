package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalApi
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.FlutterInternalInterface
import io.embrace.android.embracesdk.internal.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.ReactNativeInternalInterface
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.InstrumentationApi
import io.embrace.android.embracesdk.internal.api.InternalWebViewApi
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
import io.embrace.android.embracesdk.internal.api.delegate.InternalWebViewApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.fromFramework
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.EmbTrace.end
import io.embrace.android.embracesdk.internal.utils.EmbTrace.start
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi
import io.opentelemetry.api.common.AttributeKey
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceImpl @JvmOverloads constructor(
    private val bootstrapper: ModuleInitBootstrapper = EmbTrace.trace(
        "bootstrapper-init",
        ::ModuleInitBootstrapper
    ),
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
    private val webviewApiDelegate: InternalWebViewApiDelegate =
        InternalWebViewApiDelegate(bootstrapper, sdkCallChecker),
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
    InternalWebViewApi by webviewApiDelegate,
    InstrumentationApi by instrumentationApiDelegate,
    InternalInterfaceApi {

    init {
        EmbraceInternalApi.internalTracingApi = bootstrapper.openTelemetryModule.internalTracer
        EmbraceInternalApi.internalInterfaceApi = this
        EmbraceInternalApi.isStarted = sdkCallChecker.started::get
    }

    private val sdkClock by lazy { bootstrapper.initModule.clock }
    private val logger by lazy { bootstrapper.initModule.logger }
    private val sdkShuttingDown = AtomicBoolean(false)

    /**
     * The application being instrumented by the SDK.
     */
    @Volatile
    var application: Application? = null
        private set

    private var internalInterfaceModule: InternalInterfaceModule? = null

    val metadataService by embraceImplInject { bootstrapper.payloadSourceModule.metadataService }
    val processStateService by embraceImplInject { bootstrapper.essentialServiceModule.processStateService }
    val activityLifecycleTracker by embraceImplInject { bootstrapper.essentialServiceModule.activityLifecycleTracker }

    private val configService by embraceImplInject { bootstrapper.configModule.configService }

    @Suppress("DEPRECATION")
    override fun start(context: Context) = start(context, io.embrace.android.embracesdk.AppFramework.NATIVE)

    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context)} instead.", ReplaceWith("start(context)"))
    override fun start(context: Context, appFramework: io.embrace.android.embracesdk.AppFramework) {
        try {
            start("sdk-start")
            startImpl(context, appFramework)
            end()
        } catch (t: Throwable) {
            runCatching {
                logger.trackInternalError(InternalErrorType.SDK_START_FAIL, t)
            }
        }
    }

    @Suppress("DEPRECATION", "CyclomaticComplexMethod", "ComplexMethod")
    private fun startImpl(
        context: Context,
        framework: io.embrace.android.embracesdk.AppFramework,
    ) {
        if (application != null) {
            return
        }

        val startTimeMs = sdkClock.now()

        val appFramework = fromFramework(framework)
        if (!bootstrapper.init(context, appFramework, startTimeMs)) {
            if (bootstrapper.configModule.configService.sdkModeBehavior.isSdkDisabled()) {
                stop()
            }
            return
        }
        start("post-services-setup")

        val coreModule = bootstrapper.coreModule
        application = coreModule.application

        val configModule = bootstrapper.configModule

        if (configModule.configService.autoDataCaptureBehavior.isComposeClickCaptureEnabled()) {
            registerComposeActivityListener(coreModule.application)
        }

        val crashModule = bootstrapper.crashModule

        crashModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
            bootstrapper.workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker)
        )

        val internalInterfaceModuleImpl =
            InternalInterfaceModuleImpl(
                bootstrapper.initModule,
                bootstrapper.openTelemetryModule,
                configModule,
                bootstrapper.payloadSourceModule,
                bootstrapper.logModule,
                this,
                crashModule
            )
        internalInterfaceModule = internalInterfaceModuleImpl

        when (configService?.appFramework) {
            AppFramework.NATIVE -> {}
            AppFramework.REACT_NATIVE -> internalInterfaceModuleImpl.reactNativeInternalInterface
            AppFramework.UNITY -> internalInterfaceModuleImpl.unityInternalInterface
            AppFramework.FLUTTER -> internalInterfaceModuleImpl.flutterInternalInterface
            null -> {}
        }
        val appId = configModule.configService.appId
        val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" + appId?.run { " for appId =  $this" }
        logger.logInfo(startMsg)

        val endTimeMs = sdkClock.now()
        sdkCallChecker.started.set(true)
        end()
        val inForeground = !bootstrapper.essentialServiceModule.processStateService.isInBackground
        start("startup-tracking")
        val dataCaptureServiceModule = bootstrapper.dataCaptureServiceModule
        dataCaptureServiceModule.startupService.setSdkStartupInfo(
            startTimeMs,
            endTimeMs,
            inForeground,
            Thread.currentThread().name
        )
        end()

        val worker = bootstrapper
            .workerThreadModule
            .backgroundWorker(Worker.Background.IoRegWorker)
        worker.submit {
            bootstrapper.payloadSourceModule.payloadResurrectionService?.resurrectOldPayloads(
                nativeCrashServiceProvider = { bootstrapper.nativeFeatureModule.nativeCrashService }
            )
        }
        worker.submit { // potentially trigger first delivery attempt by firing network status callback
            registerDeliveryNetworkListener()
            bootstrapper.deliveryModule.schedulingService?.onPayloadIntake()
        }
    }

    private fun registerDeliveryNetworkListener() {
        bootstrapper.deliveryModule.schedulingService?.let(
            bootstrapper.essentialServiceModule.networkConnectivityService::addNetworkConnectivityListener
        )
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        synchronized(sdkCallChecker) {
            if (sdkShuttingDown.compareAndSet(false, true)) {
                runCatching {
                    application?.let {
                        unregisterComposeActivityListener(it)
                    }
                    application = null
                    bootstrapper.stopServices()
                }
            }
        }
    }

    override fun disable() {
        if (sdkCallChecker.started.get()) {
            bootstrapper.openTelemetryModule.otelSdkConfig.disableDataExport()
            stop()
        }

        // delete any persisted data
        runCatching {
            Executors.newSingleThreadExecutor().execute {
                StorageLocation.values().map { it.asFile(bootstrapper.coreModule.context, logger).value }.forEach {
                    it.deleteRecursively()
                }
            }
        }
    }

    @JvmOverloads
    fun logMessage(
        severity: Severity,
        message: String,
        properties: Map<String, Any>? = null,
        stackTraceElements: Array<StackTraceElement>? = null,
        customStackTrace: String? = null,
        logExceptionType: LogExceptionType = LogExceptionType.NONE,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
        customLogAttrs: Map<AttributeKey<String>, String> = emptyMap(),
    ) {
        logsApiDelegate.logMessageImpl(
            severity = severity,
            message = message,
            properties = properties,
            stackTraceElements = stackTraceElements,
            customStackTrace = customStackTrace,
            logExceptionType = logExceptionType,
            exceptionName = exceptionName,
            exceptionMessage = exceptionMessage,
            customLogAttrs = customLogAttrs,
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
        attachment: ByteArray,
    ) {
        logsApiDelegate.logMessage(
            message,
            severity,
            properties,
            attachment
        )
    }

    override fun logMessage(
        message: String,
        severity: Severity,
        properties: Map<String, Any>?,
        attachmentId: String,
        attachmentUrl: String,
    ) {
        logsApiDelegate.logMessage(
            message,
            severity,
            properties,
            attachmentId,
            attachmentUrl,
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
}

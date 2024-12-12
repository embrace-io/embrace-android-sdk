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
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.Systrace.endSynchronous
import io.embrace.android.embracesdk.internal.Systrace.startSynchronous
import io.embrace.android.embracesdk.internal.UnityInternalInterface
import io.embrace.android.embracesdk.internal.anr.ndk.isUnityMainThread
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
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
import io.embrace.android.embracesdk.internal.api.delegate.InternalWebViewApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.delivery.storage.StorageLocation
import io.embrace.android.embracesdk.internal.fromFramework
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.worker.TaskPriority
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi
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
    private val bootstrapper: ModuleInitBootstrapper = Systrace.traceSynchronous(
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

    private val anrService by embraceImplInject { bootstrapper.anrModule.anrService }
    private val configService by embraceImplInject { bootstrapper.configModule.configService }
    private val nativeThreadSampler by embraceImplInject { bootstrapper.nativeFeatureModule.nativeThreadSamplerService }
    private val nativeThreadSamplerInstaller by embraceImplInject {
        bootstrapper.nativeFeatureModule.nativeThreadSamplerInstaller
    }

    @Suppress("DEPRECATION")
    override fun start(context: Context) = start(context, io.embrace.android.embracesdk.AppFramework.NATIVE)

    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context)} instead.", ReplaceWith("start(context)"))
    override fun start(context: Context, appFramework: io.embrace.android.embracesdk.AppFramework) {
        try {
            startSynchronous("sdk-start")
            startImpl(context, appFramework)
            endSynchronous()
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
        startSynchronous("post-services-setup")

        val coreModule = bootstrapper.coreModule
        application = coreModule.application

        val configModule = bootstrapper.configModule

        if (configModule.configService.autoDataCaptureBehavior.isComposeClickCaptureEnabled()) {
            registerComposeActivityListener(coreModule.application)
        }

        val crashModule = bootstrapper.crashModule

        // Send any sessions that were cached and not yet sent.
        startSynchronous("send-cached-sessions")

        if (useV1DeliveryLayer(configModule.configService)) {
            bootstrapper
                .workerThreadModule
                .priorityWorker<TaskPriority>(Worker.Priority.DeliveryCacheWorker)
                .submit(TaskPriority.NORMAL) {
                    if (useV1DeliveryLayer(configModule.configService)) {
                        val essentialServiceModule = bootstrapper.essentialServiceModule
                        bootstrapper.deliveryModule.deliveryService?.sendCachedSessions(
                            bootstrapper.nativeFeatureModule::nativeCrashService,
                            essentialServiceModule.sessionIdTracker
                        )
                    }
                }
        }

        endSynchronous()

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
        endSynchronous()
        val inForeground = !bootstrapper.essentialServiceModule.processStateService.isInBackground
        startSynchronous("startup-tracking")
        val dataCaptureServiceModule = bootstrapper.dataCaptureServiceModule
        dataCaptureServiceModule.startupService.setSdkStartupInfo(
            startTimeMs,
            endTimeMs,
            inForeground,
            Thread.currentThread().name
        )
        endSynchronous()

        if (!useV1DeliveryLayer(configModule.configService)) {
            val worker = bootstrapper
                .workerThreadModule
                .backgroundWorker(Worker.Background.IoRegWorker)
            worker.submit {
                if (!useV1DeliveryLayer(configModule.configService)) {
                    bootstrapper.payloadSourceModule.payloadResurrectionService?.resurrectOldPayloads(
                        nativeCrashServiceProvider = { bootstrapper.nativeFeatureModule.nativeCrashService }
                    )
                }
            }
            worker.submit { // potentially trigger first delivery attempt by firing network status callback
                registerDeliveryNetworkListener()
                bootstrapper.deliveryModule.schedulingService?.onPayloadIntake()
            }
        } else {
            registerDeliveryNetworkListener()
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
            bootstrapper.openTelemetryModule.openTelemetryConfiguration.disableDataExport()
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
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        logExceptionType: LogExceptionType,
        context: String?,
        library: String?,
        exceptionName: String? = null,
        exceptionMessage: String? = null,
    ) {
        logsApiDelegate.logMessage(
            severity,
            message,
            properties,
            stackTraceElements,
            customStackTrace,
            logExceptionType,
            context,
            library,
            exceptionName,
            exceptionMessage
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

    fun installUnityThreadSampler() {
        if (sdkCallChecker.check("install_unity_thread_sampler")) {
            sampleCurrentThreadDuringAnrs()
        }
    }

    private fun sampleCurrentThreadDuringAnrs() {
        try {
            val service = anrService ?: return
            val installer = nativeThreadSamplerInstaller ?: return
            val sampler = nativeThreadSampler ?: return
            val cfgService = configService ?: return

            // install the native thread sampler
            sampler.setupNativeSampler()

            // In Unity this should always run on the Unity thread.
            if (isUnityMainThread()) {
                try {
                    installer.monitorCurrentThread(sampler, cfgService, service)
                } catch (t: Throwable) {
                    logger.trackInternalError(InternalErrorType.NATIVE_THREAD_SAMPLE_FAIL, t)
                }
            }
        } catch (exc: Exception) {
            logger.trackInternalError(InternalErrorType.NATIVE_THREAD_SAMPLE_FAIL, exc)
        }
    }

    private fun useV1DeliveryLayer(cfgService: ConfigService?): Boolean {
        return cfgService?.autoDataCaptureBehavior?.isV2StorageEnabled() != true
    }
}

package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.Systrace.endSynchronous
import io.embrace.android.embracesdk.internal.Systrace.startSynchronous
import io.embrace.android.embracesdk.internal.anr.ndk.isUnityMainThread
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.InternalInterfaceApi
import io.embrace.android.embracesdk.internal.api.InternalWebViewApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.MomentsApi
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
import io.embrace.android.embracesdk.internal.api.delegate.MomentsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UninitializedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.fromFramework
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.internal.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.internal.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.internal.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.EventType
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.spans.TracingApi

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceImpl @JvmOverloads constructor(
    private val bootstrapper: ModuleInitBootstrapper = Systrace.traceSynchronous("bootstrapper-init", ::ModuleInitBootstrapper),
    private val sdkCallChecker: SdkCallChecker =
        SdkCallChecker(bootstrapper.initModule.logger, bootstrapper.initModule.telemetryService),
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(bootstrapper, sdkCallChecker),
    private val sessionApiDelegate: SessionApiDelegate = SessionApiDelegate(bootstrapper, sdkCallChecker),
    private val networkRequestApiDelegate: NetworkRequestApiDelegate =
        NetworkRequestApiDelegate(bootstrapper, sdkCallChecker),
    private val logsApiDelegate: LogsApiDelegate = LogsApiDelegate(bootstrapper, sdkCallChecker),
    private val momentsApiDelegate: MomentsApiDelegate = MomentsApiDelegate(bootstrapper, sdkCallChecker),
    private val viewTrackingApiDelegate: ViewTrackingApiDelegate =
        ViewTrackingApiDelegate(bootstrapper, sdkCallChecker),
    private val sdkStateApiDelegate: SdkStateApiDelegate = SdkStateApiDelegate(bootstrapper, sdkCallChecker),
    private val otelApiDelegate: OTelApiDelegate = OTelApiDelegate(bootstrapper, sdkCallChecker),
    private val breadcrumbApiDelegate: BreadcrumbApiDelegate = BreadcrumbApiDelegate(bootstrapper, sdkCallChecker),
    private val webviewApiDelegate: InternalWebViewApiDelegate =
        InternalWebViewApiDelegate(bootstrapper, sdkCallChecker),
) : SdkApi,
    LogsApi by logsApiDelegate,
    MomentsApi by momentsApiDelegate,
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

    private val uninitializedSdkInternalInterface by lazy<EmbraceInternalInterface> {
        UninitializedSdkInternalInterfaceImpl(bootstrapper.openTelemetryModule.internalTracer)
    }

    private val sdkClock by lazy { bootstrapper.initModule.clock }
    private val logger by lazy { bootstrapper.initModule.logger }
    private val customAppId: String?
        get() = sdkStateApiDelegate.customAppId

    /**
     * The application being instrumented by the SDK.
     */
    @Volatile
    var application: Application? = null
        private set

    private var embraceInternalInterface: EmbraceInternalInterface? = null
    private var internalInterfaceModule: InternalInterfaceModule? = null

    val metadataService by embraceImplInject { bootstrapper.payloadSourceModule.metadataService }
    val activityService by embraceImplInject { bootstrapper.essentialServiceModule.processStateService }
    val activityLifecycleTracker by embraceImplInject { bootstrapper.essentialServiceModule.activityLifecycleTracker }
    val internalErrorService by embraceImplInject { bootstrapper.initModule.internalErrorService }

    private val anrService by embraceImplInject { bootstrapper.anrModule.anrService }
    private val configService by embraceImplInject { bootstrapper.configModule.configService }
    private val nativeThreadSampler by embraceImplInject { bootstrapper.nativeFeatureModule.nativeThreadSamplerService }
    private val nativeThreadSamplerInstaller by embraceImplInject {
        bootstrapper.nativeFeatureModule.nativeThreadSamplerInstaller
    }

    @Suppress("DEPRECATION")
    override fun start(context: Context) = start(context, Embrace.AppFramework.NATIVE) { null }

    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context)} instead.")
    override fun start(context: Context, appFramework: Embrace.AppFramework) =
        start(context, appFramework) { null }

    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context)} instead. The isDevMode parameter has no effect.")
    override fun start(context: Context, isDevMode: Boolean) =
        start(context, Embrace.AppFramework.NATIVE) { null }

    @Suppress("DEPRECATION")
    @Deprecated("Use {@link #start(Context, Embrace.AppFramework)} instead. The isDevMode parameter has no effect.")
    override fun start(context: Context, isDevMode: Boolean, appFramework: Embrace.AppFramework) =
        start(context, appFramework) { null }

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     *
     * See [Embrace Docs](https://embrace.io/docs/android/) for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context                  an instance of context
     * @param appFramework             the AppFramework of the application
     * @param configServiceProvider    provider for the config service
     */
    @Suppress("DEPRECATION")
    fun start(
        context: Context,
        appFramework: Embrace.AppFramework,
        configServiceProvider: (framework: AppFramework) -> ConfigService? = { null }
    ) {
        try {
            startSynchronous("sdk-start")
            startImpl(context, appFramework, configServiceProvider)
            endSynchronous()
        } catch (t: Throwable) {
            runCatching {
                logger.trackInternalError(InternalErrorType.SDK_START_FAIL, t)
                logger.logError("Error occurred while initializing the Embrace SDK. Instrumentation may be disabled.", t)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun startImpl(
        context: Context,
        framework: Embrace.AppFramework,
        configServiceProvider: (framework: AppFramework) -> ConfigService?
    ) {
        if (application != null) {
            // We don't hard fail if the SDK has been already initialized.
            logger.logWarning("Embrace SDK has already been initialized", null)
            return
        }

        val startTimeMs = sdkClock.now()

        val appFramework = fromFramework(framework)
        bootstrapper.init(context, appFramework, startTimeMs, customAppId, configServiceProvider)
        startSynchronous("post-services-setup")

        val coreModule = bootstrapper.coreModule
        application = coreModule.application

        val configModule = bootstrapper.configModule
        if (configModule.configService.isSdkDisabled()) {
            stop()
            return
        }

        if (configModule.configService.autoDataCaptureBehavior.isComposeClickCaptureEnabled()) {
            registerComposeActivityListener(coreModule.application)
        }

        val momentsModule = bootstrapper.momentsModule
        val crashModule = bootstrapper.crashModule

        // Send any sessions that were cached and not yet sent.
        startSynchronous("send-cached-sessions")
        val worker = bootstrapper.workerThreadModule.prioritizedWorker(Worker.FileCacheWorker)
        worker.submit {
            val essentialServiceModule = bootstrapper.essentialServiceModule
            bootstrapper.deliveryModule.deliveryService.sendCachedSessions(
                bootstrapper.nativeFeatureModule::nativeCrashService,
                essentialServiceModule.sessionIdTracker
            )
        }
        endSynchronous()

        crashModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
            bootstrapper.workerThreadModule.backgroundWorker(Worker.IoRegWorker)
        )

        val internalInterfaceModuleImpl =
            InternalInterfaceModuleImpl(
                bootstrapper.initModule,
                bootstrapper.openTelemetryModule,
                configModule,
                bootstrapper.payloadSourceModule,
                bootstrapper.logModule,
                bootstrapper.momentsModule,
                this,
                crashModule
            )
        internalInterfaceModule = internalInterfaceModuleImpl

        embraceInternalInterface = internalInterfaceModuleImpl.embraceInternalInterface

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

        // Attempt to send the startup event if the app is already in the foreground. We registered to send this when
        // we went to the foreground, but if an activity had already gone to the foreground, we may have missed
        // sending this, so to ensure the startup message is sent, we force it to be sent here.
        if (inForeground) {
            momentsModule.eventService.sendStartupMoment()
        }

        startSynchronous("startup-tracking")
        val dataCaptureServiceModule = bootstrapper.dataCaptureServiceModule
        dataCaptureServiceModule.startupService.setSdkStartupInfo(startTimeMs, endTimeMs, inForeground, Thread.currentThread().name)
        endSynchronous()
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        if (sdkCallChecker.started.compareAndSet(true, false)) {
            logger.logInfo("Shutting down Embrace SDK")
            runCatching {
                application?.let {
                    unregisterComposeActivityListener(it)
                }
                application = null
                bootstrapper.stopServices()
            }
        }
    }

    @JvmOverloads
    fun logMessage(
        type: EventType,
        message: String,
        properties: Map<String, Any>?,
        stackTraceElements: Array<StackTraceElement>?,
        customStackTrace: String?,
        logExceptionType: LogExceptionType,
        context: String?,
        library: String?,
        exceptionName: String? = null,
        exceptionMessage: String? = null
    ) {
        logsApiDelegate.logMessage(
            type,
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
    override val internalInterface get(): EmbraceInternalInterface {
        val internalInterface = embraceInternalInterface
        return if (isStarted && internalInterface != null) {
            internalInterface
        } else {
            uninitializedSdkInternalInterface
        }
    }

    override val reactNativeInternalInterface get() = internalInterfaceModule?.reactNativeInternalInterface
    override val unityInternalInterface get() = internalInterfaceModule?.unityInternalInterface
    override val flutterInternalInterface get() = internalInterfaceModule?.flutterInternalInterface

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
                    logger.logError("Failed to sample current thread during ANRs", t)
                    logger.trackInternalError(InternalErrorType.NATIVE_THREAD_SAMPLE_FAIL, t)
                }
            }
        } catch (exc: Exception) {
            logger.logError("Failed to sample current thread during ANRs", exc)
        }
    }
}

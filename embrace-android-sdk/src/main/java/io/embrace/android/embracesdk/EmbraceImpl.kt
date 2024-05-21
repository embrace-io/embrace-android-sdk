package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.injection.InternalInterfaceModule
import io.embrace.android.embracesdk.injection.InternalInterfaceModuleImpl
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.Systrace.endSynchronous
import io.embrace.android.embracesdk.internal.Systrace.startSynchronous
import io.embrace.android.embracesdk.internal.api.BreadcrumbApi
import io.embrace.android.embracesdk.internal.api.LogsApi
import io.embrace.android.embracesdk.internal.api.MomentsApi
import io.embrace.android.embracesdk.internal.api.NetworkRequestApi
import io.embrace.android.embracesdk.internal.api.OtelExporterApi
import io.embrace.android.embracesdk.internal.api.SdkStateApi
import io.embrace.android.embracesdk.internal.api.SessionApi
import io.embrace.android.embracesdk.internal.api.UserApi
import io.embrace.android.embracesdk.internal.api.ViewTrackingApi
import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.MomentsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OtelExporterApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UninitializedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.spans.TracingApi
import io.embrace.android.embracesdk.worker.WorkerName

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 *
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
internal class EmbraceImpl @JvmOverloads constructor(
    private val bootstrapper: ModuleInitBootstrapper = ModuleInitBootstrapper(),
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
    private val otelExporterApiDelegate: OtelExporterApiDelegate =
        OtelExporterApiDelegate(bootstrapper, sdkCallChecker),
    private val breadcrumbApiDelegate: BreadcrumbApiDelegate = BreadcrumbApiDelegate(bootstrapper, sdkCallChecker),
) : UserApi by userApiDelegate,
    SessionApi by sessionApiDelegate,
    NetworkRequestApi by networkRequestApiDelegate,
    LogsApi by logsApiDelegate,
    MomentsApi by momentsApiDelegate,
    TracingApi by bootstrapper.openTelemetryModule.embraceTracer,
    ViewTrackingApi by viewTrackingApiDelegate,
    SdkStateApi by sdkStateApiDelegate,
    OtelExporterApi by otelExporterApiDelegate,
    BreadcrumbApi by breadcrumbApiDelegate {

    private val uninitializedSdkInternalInterface by lazy<EmbraceInternalInterface> {
        UninitializedSdkInternalInterfaceImpl(bootstrapper.openTelemetryModule.internalTracer)
    }

    private val sdkClock = bootstrapper.initModule.clock
    private val logger = bootstrapper.initModule.logger
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

    val metadataService by embraceImplInject { bootstrapper.essentialServiceModule.metadataService }
    val activityService by embraceImplInject { bootstrapper.essentialServiceModule.processStateService }
    val activityLifecycleTracker by embraceImplInject { bootstrapper.essentialServiceModule.activityLifecycleTracker }
    val internalErrorService by embraceImplInject {
        bootstrapper.initModule.internalErrorService.also {
            it.configService = bootstrapper.essentialServiceModule.configService
        }
    }

    private val breadcrumbService by embraceImplInject { bootstrapper.dataCaptureServiceModule.breadcrumbService }
    private val sessionOrchestrator by embraceImplInject { bootstrapper.sessionModule.sessionOrchestrator }
    private val anrService by embraceImplInject { bootstrapper.anrModule.anrService }
    private val configService by embraceImplInject { bootstrapper.essentialServiceModule.configService }
    private val webViewService by embraceImplInject { bootstrapper.dataCaptureServiceModule.webviewService }
    private val nativeThreadSampler by embraceImplInject { bootstrapper.nativeModule.nativeThreadSamplerService }
    private val nativeThreadSamplerInstaller by embraceImplInject { bootstrapper.nativeModule.nativeThreadSamplerInstaller }

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
    fun start(
        context: Context,
        appFramework: Embrace.AppFramework,
        configServiceProvider: () -> ConfigService? = { null }
    ) {
        try {
            startSynchronous("sdk-start")
            startImpl(context, appFramework, configServiceProvider)
            endSynchronous()
        } catch (t: Throwable) {
            logger.logError("Error occurred while initializing the Embrace SDK. Instrumentation may be disabled.", t)
        }
    }

    private fun startImpl(
        context: Context,
        framework: Embrace.AppFramework,
        configServiceProvider: () -> ConfigService?
    ) {
        if (application != null) {
            // We don't hard fail if the SDK has been already initialized.
            logger.logWarning("Embrace SDK has already been initialized", null)
            return
        }

        if (ApkToolsConfig.IS_SDK_DISABLED) {
            logger.logInfo("SDK disabled through ApkToolsConfig", null)
            stop()
            return
        }

        val startTimeMs = sdkClock.now()
        logger.logInfo("Starting SDK for framework " + framework.name, null)
        bootstrapper.init(context, framework, startTimeMs, customAppId, configServiceProvider)
        startSynchronous("post-services-setup")

        val coreModule = bootstrapper.coreModule
        application = coreModule.application

        val essentialServiceModule = bootstrapper.essentialServiceModule
        if (essentialServiceModule.configService.isSdkDisabled()) {
            logger.logInfo("Interrupting SDK start because it is disabled", null)
            stop()
            return
        }

        if (essentialServiceModule.configService.autoDataCaptureBehavior.isComposeOnClickEnabled()) {
            registerComposeActivityListener(coreModule.application)
        }

        val dataCaptureServiceModule = bootstrapper.dataCaptureServiceModule
        val deliveryModule = bootstrapper.deliveryModule
        val dataContainerModule = bootstrapper.dataContainerModule
        val crashModule = bootstrapper.crashModule

        startSynchronous("send-cached-sessions")
        // Send any sessions that were cached and not yet sent.
        deliveryModule.deliveryService.sendCachedSessions(crashModule::nativeCrashService, essentialServiceModule.sessionIdTracker)
        endSynchronous()

        crashModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
            bootstrapper.workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
        )

        val internalInterfaceModuleImpl =
            InternalInterfaceModuleImpl(
                bootstrapper.initModule,
                bootstrapper.openTelemetryModule,
                coreModule,
                essentialServiceModule,
                bootstrapper.customerLogModule,
                bootstrapper.dataContainerModule,
                this,
                crashModule
            )
        internalInterfaceModule = internalInterfaceModuleImpl

        embraceInternalInterface = internalInterfaceModuleImpl.embraceInternalInterface

        when (framework) {
            Embrace.AppFramework.NATIVE -> {}
            Embrace.AppFramework.REACT_NATIVE -> internalInterfaceModuleImpl.reactNativeInternalInterface
            Embrace.AppFramework.UNITY -> internalInterfaceModuleImpl.unityInternalInterface
            Embrace.AppFramework.FLUTTER -> internalInterfaceModuleImpl.flutterInternalInterface
        }
        val appId = essentialServiceModule.configService.sdkModeBehavior.appId
        val startMsg = "Embrace SDK started. App ID: " + appId + " Version: " + BuildConfig.VERSION_NAME
        logger.logInfo(startMsg, null)

        val endTimeMs = sdkClock.now()
        sdkCallChecker.started.set(true)
        endSynchronous()
        val inForeground = !essentialServiceModule.processStateService.isInBackground

        // Attempt to send the startup event if the app is already in the foreground. We registered to send this when
        // we went to the foreground, but if an activity had already gone to the foreground, we may have missed
        // sending this, so to ensure the startup message is sent, we force it to be sent here.
        if (inForeground) {
            dataContainerModule.eventService.sendStartupMoment()
        }

        startSynchronous("startup-tracking")
        dataCaptureServiceModule.startupService.setSdkStartupInfo(startTimeMs, endTimeMs, inForeground, Thread.currentThread().name)
        endSynchronous()

        // This should return immediately given that EmbraceSpansService initialization should be finished at this point
        // Put in emergency timeout just in case something unexpected happens so as to fail the SDK startup.
        bootstrapper.waitForAsyncInit()
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        if (sdkCallChecker.started.compareAndSet(true, false)) {
            logger.logInfo("Shutting down Embrace SDK.", null)
            try {
                application?.let {
                    unregisterComposeActivityListener(it)
                }
                application = null
                bootstrapper.stopServices()
            } catch (ex: Exception) {
                logger.logError("Error while shutting down Embrace SDK", ex)
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
     * Logs that a particular WebView URL was loaded.
     *
     * @param url the url to log
     */
    fun logWebView(url: String?) {
        if (sdkCallChecker.check("log_web_view")) {
            breadcrumbService?.logWebView(url, sdkClock.now())
            sessionOrchestrator?.reportBackgroundActivityStateChange()
        }
    }

    fun trackWebViewPerformance(tag: String, message: String) {
        if (isStarted() && configService?.webViewVitalsBehavior?.isWebViewVitalsEnabled() == true) {
            webViewService?.collectWebData(tag, message)
        }
    }

    /**
     * Gets the [EmbraceInternalInterface] that should be used as the sole source of
     * communication with other Android SDK modules.
     */
    fun getEmbraceInternalInterface(): EmbraceInternalInterface {
        val internalInterface = embraceInternalInterface
        return if (isStarted() && internalInterface != null) {
            internalInterface
        } else {
            uninitializedSdkInternalInterface
        }
    }

    fun getReactNativeInternalInterface(): ReactNativeInternalInterface? = internalInterfaceModule?.reactNativeInternalInterface

    fun getUnityInternalInterface(): UnityInternalInterface? = internalInterfaceModule?.unityInternalInterface

    fun installUnityThreadSampler() {
        if (sdkCallChecker.check("install_unity_thread_sampler")) {
            sampleCurrentThreadDuringAnrs()
        }
    }

    fun getFlutterInternalInterface(): FlutterInternalInterface? = internalInterfaceModule?.flutterInternalInterface

    private fun sampleCurrentThreadDuringAnrs() {
        try {
            val service = anrService
            if (service != null && nativeThreadSamplerInstaller != null) {
                nativeThreadSampler?.let { sampler ->
                    configService?.let { cfg ->
                        nativeThreadSamplerInstaller?.monitorCurrentThread(sampler, cfg, service)
                    }
                }
            } else {
                logger.logWarning("nativeThreadSamplerInstaller not started, cannot sample current thread", null)
            }
        } catch (exc: Exception) {
            logger.logError("Failed to sample current thread during ANRs", exc)
        }
    }
}

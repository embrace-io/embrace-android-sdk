package io.embrace.android.embracesdk

import android.annotation.SuppressLint
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import io.embrace.android.embracesdk.Embrace.LastRunEndState
import io.embrace.android.embracesdk.EventType.Companion.fromSeverity
import io.embrace.android.embracesdk.config.ConfigService
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior
import io.embrace.android.embracesdk.event.EmbraceEventService
import io.embrace.android.embracesdk.injection.CoreModule
import io.embrace.android.embracesdk.injection.CrashModule
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper
import io.embrace.android.embracesdk.injection.embraceImplInject
import io.embrace.android.embracesdk.internal.ApkToolsConfig
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface
import io.embrace.android.embracesdk.internal.IdGenerator.Companion.generateW3CTraceparent
import io.embrace.android.embracesdk.internal.Systrace.endSynchronous
import io.embrace.android.embracesdk.internal.Systrace.startSynchronous
import io.embrace.android.embracesdk.internal.api.delegate.SdkCallChecker
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer
import io.embrace.android.embracesdk.internal.utils.getSafeStackTrace
import io.embrace.android.embracesdk.logging.EmbLogger
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb
import io.embrace.android.embracesdk.payload.TapBreadcrumb.TapBreadcrumbType
import io.embrace.android.embracesdk.utils.PropertyUtils.sanitizeProperties
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.trace.export.SpanExporter
import java.util.regex.Pattern

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
    private val userApiDelegate: UserApiDelegate = UserApiDelegate(bootstrapper, sdkCallChecker)
) : UserApi by userApiDelegate {

    @JvmField
    val tracer: EmbraceTracer = bootstrapper.openTelemetryModule.embraceTracer

    private val uninitializedSdkInternalInterface by lazy<EmbraceInternalInterface> {
        UninitializedSdkInternalInterfaceImpl(bootstrapper.openTelemetryModule.internalTracer)
    }

    private val sdkClock = bootstrapper.initModule.clock
    private val logger = bootstrapper.initModule.logger

    /**
     * Custom app ID that overrides the one specified at build time
     */
    @Volatile
    private var customAppId: String? = null

    /**
     * The application being instrumented by the SDK.
     */
    @Volatile
    var application: Application? = null
        private set

    /**
     * Variable pointing to the composeActivityListener instance obtained using reflection
     */
    private var composeActivityListenerInstance: Any? = null
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

    /**
     * The type of application being instrumented by this SDK instance, whether it's directly used by an Android app, or used via a hosted
     * SDK like Flutter, React Native, or Unity.
     */
    private val appFramework by embraceImplInject { bootstrapper.coreModule.appFramework }
    private val breadcrumbService by embraceImplInject { bootstrapper.dataCaptureServiceModule.breadcrumbService }
    private val sessionOrchestrator by embraceImplInject { bootstrapper.sessionModule.sessionOrchestrator }
    private val sessionPropertiesService by embraceImplInject { bootstrapper.sessionModule.sessionPropertiesService }
    private val sessionIdTracker by embraceImplInject { bootstrapper.essentialServiceModule.sessionIdTracker }
    private val networkLoggingService by embraceImplInject { bootstrapper.customerLogModule.networkLoggingService }
    private val anrService by embraceImplInject { bootstrapper.anrModule.anrService }
    private val logMessageService by embraceImplInject { bootstrapper.customerLogModule.logMessageService }
    private val configService by embraceImplInject { bootstrapper.essentialServiceModule.configService }
    private val preferencesService by embraceImplInject { bootstrapper.androidServicesModule.preferencesService }
    private val eventService by embraceImplInject { bootstrapper.dataContainerModule.eventService }
    private val userService by embraceImplInject { bootstrapper.essentialServiceModule.userService }
    private val ndkService by embraceImplInject { bootstrapper.nativeModule.ndkService }
    private val networkCaptureService by embraceImplInject { bootstrapper.customerLogModule.networkCaptureService }
    private val webViewService by embraceImplInject { bootstrapper.dataCaptureServiceModule.webviewService }
    private val telemetryService by embraceImplInject { bootstrapper.initModule.telemetryService }
    private val nativeThreadSampler by embraceImplInject { bootstrapper.nativeModule.nativeThreadSamplerService }
    private val nativeThreadSamplerInstaller by embraceImplInject { bootstrapper.nativeModule.nativeThreadSamplerInstaller }
    private val pushNotificationService by embraceImplInject {
        bootstrapper.dataCaptureServiceModule.pushNotificationService
    }
    private val crashVerifier by embraceImplInject { bootstrapper.crashModule.lastRunCrashVerifier }

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
     */
    fun start(context: Context, appFramework: Embrace.AppFramework) {
        startInternal(context, appFramework) { null }
    }

    fun startInternal(
        context: Context,
        appFramework: Embrace.AppFramework,
        configServiceProvider: () -> ConfigService?
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
            registerComposeActivityListener(coreModule)
        }

        val dataCaptureServiceModule = bootstrapper.dataCaptureServiceModule
        val deliveryModule = bootstrapper.deliveryModule
        val dataContainerModule = bootstrapper.dataContainerModule
        val crashModule = bootstrapper.crashModule

        startSynchronous("send-cached-sessions")
        // Send any sessions that were cached and not yet sent.
        deliveryModule.deliveryService.sendCachedSessions(crashModule::nativeCrashService, essentialServiceModule.sessionIdTracker)
        endSynchronous()

        loadCrashVerifier(crashModule, bootstrapper.workerThreadModule)

        val internalInterfaceModuleImpl =
            InternalInterfaceModuleImpl(
                bootstrapper.initModule,
                bootstrapper.openTelemetryModule,
                coreModule,
                essentialServiceModule,
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
     * Loads the crash verifier to get the end state of the app crashed in the last run.
     * This method is called when the app starts.
     *
     * @param crashModule        an instance of [CrashModule]
     * @param workerThreadModule an instance of [WorkerThreadModule]
     */
    private fun loadCrashVerifier(crashModule: CrashModule, workerThreadModule: WorkerThreadModule) {
        crashModule.lastRunCrashVerifier.readAndCleanMarkerAsync(workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION))
    }

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param coreModule instance containing a required set of dependencies
     */
    private fun registerComposeActivityListener(coreModule: CoreModule) {
        try {
            val composeActivityListener = Class.forName("io.embrace.android.embracesdk.compose.ComposeActivityListener")
            composeActivityListenerInstance = composeActivityListener.newInstance()
            coreModule.application.registerActivityLifecycleCallbacks(composeActivityListenerInstance as ActivityLifecycleCallbacks?)
        } catch (e: Throwable) {
            logger.logError("registerComposeActivityListener error", e)
        }
    }

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param app Global application class
     */
    private fun unregisterComposeActivityListener(app: Application) {
        try {
            app.unregisterActivityLifecycleCallbacks(composeActivityListenerInstance as ActivityLifecycleCallbacks?)
        } catch (e: Throwable) {
            logger.logError("Instantiation error for ComposeActivityListener", e)
        }
    }

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    fun isStarted(): Boolean = sdkCallChecker.started.get()

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    fun setAppId(appId: String): Boolean {
        if (isStarted()) {
            logger.logError("You must set the custom app ID before the SDK is started.", null)
            return false
        }
        if (appId.isEmpty()) {
            logger.logError("App ID cannot be null or empty.", null)
            return false
        }
        if (!appIdPattern.matcher(appId).find()) {
            logger.logError(
                "Invalid app ID. Must be a 5-character string with characters from the set [A-Za-z0-9], but it was \"$appId\".",
                null
            )
            return false
        }

        customAppId = appId
        return true
    }

    /**
     * Shuts down the Embrace SDK.
     */
    fun stop() {
        if (sdkCallChecker.started.compareAndSet(true, false)) {
            logger.logInfo("Shutting down Embrace SDK.", null)
            try {
                application?.let {
                    if (composeActivityListenerInstance != null) {
                        unregisterComposeActivityListener(it)
                    }
                }
                application = null
                bootstrapper.stopServices()
            } catch (ex: Exception) {
                logger.logError("Error while shutting down Embrace SDK", ex)
            }
        }
    }

    /**
     * Adds a property to the current session.
     */
    fun addSessionProperty(key: String, value: String, permanent: Boolean): Boolean {
        if (sdkCallChecker.check("add_session_property")) {
            return sessionPropertiesService?.addProperty(key, value, permanent) ?: false
        }
        return false
    }

    /**
     * Removes a property from the current session.
     */
    fun removeSessionProperty(key: String): Boolean {
        if (sdkCallChecker.check("remove_session_property")) {
            return sessionPropertiesService?.removeProperty(key) ?: false
        }
        return false
    }

    fun getSessionProperties(): Map<String, String>? {
        if (sdkCallChecker.check("get_session_properties")) {
            return sessionPropertiesService?.getProperties()
        }
        return null
    }

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     *
     *
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    fun startMoment(name: String, identifier: String?, properties: Map<String, Any>?) {
        if (sdkCallChecker.check("start_moment")) {
            eventService?.startEvent(name, identifier, normalizeProperties(properties, logger))
            onActivityReported()
        }
    }

    /**
     * Signals the end of a moment with the specified name.
     *
     *
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    fun endMoment(name: String, identifier: String?, properties: Map<String, Any>?) {
        if (sdkCallChecker.check("end_moment")) {
            eventService?.endEvent(name, identifier, normalizeProperties(properties, logger))
            onActivityReported()
        }
    }

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    fun endAppStartup(properties: Map<String, Any>?) {
        endMoment(EmbraceEventService.STARTUP_EVENT_NAME, null, properties)
    }

    fun getTraceIdHeader(): String {
        if (configService != null && sdkCallChecker.check("get_trace_id_header")) {
            return configService?.networkBehavior?.getTraceIdHeader()
                ?: NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
        }
        return NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE
    }

    fun generateW3cTraceparent(): String = generateW3CTraceparent()

    fun recordNetworkRequest(request: EmbraceNetworkRequest) {
        if (sdkCallChecker.check("record_network_request")) {
            logNetworkRequest(request)
        }
    }

    private fun logNetworkRequest(request: EmbraceNetworkRequest) {
        if (configService?.networkBehavior?.isUrlEnabled(request.url) == true) {
            networkLoggingService?.logNetworkRequest(request)
            onActivityReported()
        }
    }

    fun logMessage(message: String, severity: Severity, properties: Map<String, Any>?) {
        logMessage(
            fromSeverity(severity),
            message,
            properties,
            null,
            null,
            LogExceptionType.NONE,
            null,
            null
        )
    }

    fun logException(throwable: Throwable, severity: Severity, properties: Map<String, Any>?, message: String?) {
        val exceptionMessage = if (throwable.message != null) throwable.message else ""
        logMessage(
            fromSeverity(severity),
            (
                message
                    ?: exceptionMessage
                ) ?: "",
            properties,
            throwable.getSafeStackTrace(),
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            throwable.javaClass.simpleName,
            exceptionMessage
        )
    }

    fun logCustomStacktrace(
        stacktraceElements: Array<StackTraceElement>,
        severity: Severity,
        properties: Map<String, Any>?,
        message: String?
    ) {
        logMessage(
            fromSeverity(severity),
            message
                ?: "",
            properties,
            stacktraceElements,
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            null,
            message
        )
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
        if (sdkCallChecker.check("log_message")) {
            try {
                appFramework?.let {
                    logMessageService?.log(
                        message,
                        type,
                        logExceptionType,
                        normalizeProperties(properties, logger),
                        stackTraceElements,
                        customStackTrace,
                        it,
                        context,
                        library,
                        exceptionName,
                        exceptionMessage
                    )
                }
                onActivityReported()
            } catch (ex: Exception) {
                logger.logDebug("Failed to log message using Embrace SDK.", ex)
            }
        }
    }

    /**
     * Logs a breadcrumb.
     *
     *
     * Breadcrumbs track a user's journey through the application and will be shown on the timeline.
     *
     * @param message the name of the breadcrumb to log
     */
    fun addBreadcrumb(message: String) {
        if (sdkCallChecker.check("add_breadcrumb")) {
            breadcrumbService?.logCustom(message, sdkClock.now())
            onActivityReported()
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(message: String?, details: String?) {
        if (sdkCallChecker.check("log_internal_error")) {
            if (message == null) {
                return
            }

            val messageWithDetails: String = if (details != null) {
                "$message: $details"
            } else {
                message
            }
            internalErrorService?.handleInternalError(RuntimeException(messageWithDetails))
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    fun logInternalError(error: Throwable) {
        if (sdkCallChecker.check("log_internal_error")) {
            internalErrorService?.handleInternalError(error)
        }
    }

    /**
     * Ends the current session and starts a new one.
     *
     *
     * Cleans all the user info on the device.
     */
    fun endSession(clearUserInfo: Boolean) {
        if (sdkCallChecker.check("end_session")) {
            sessionOrchestrator?.endSessionWithManual(clearUserInfo)
        }
    }

    fun getDeviceId(): String = when {
        sdkCallChecker.check("get_device_id") ->
            preferencesService?.deviceIdentifier
                ?: ""

        else -> ""
    }

    /**
     * Log the start of a fragment.
     *
     *
     * A matching call to endFragment must be made.
     *
     * @param name the name of the fragment to log
     */
    fun startView(name: String): Boolean {
        if (sdkCallChecker.check("start_view")) {
            return breadcrumbService?.startView(name) ?: false
        }
        return false
    }

    /**
     * Log the end of a fragment.
     *
     *
     * A matching call to startFragment must be made before this is called.
     *
     * @param name the name of the fragment to log
     */
    fun endView(name: String): Boolean {
        if (sdkCallChecker.check("end_view")) {
            return breadcrumbService?.endView(name) ?: false
        }
        return false
    }

    /**
     * Saves captured push notification information into session payload
     *
     * @param title                    the title of the notification as a string (or null)
     * @param body                     the body of the notification as a string (or null)
     * @param topic                    the notification topic (if a user subscribed to one), or null
     * @param id                       A unique ID identifying the message
     * @param notificationPriority     the notificationPriority of the message (as resolved on the device)
     * @param messageDeliveredPriority the priority of the message (as resolved on the server)
     */
    fun logPushNotification(
        title: String?,
        body: String?,
        topic: String?,
        id: String?,
        notificationPriority: Int?,
        messageDeliveredPriority: Int,
        type: PushNotificationBreadcrumb.NotificationType
    ) {
        if (sdkCallChecker.check("log_push_notification")) {
            pushNotificationService?.logPushNotification(title, body, topic, id, notificationPriority, messageDeliveredPriority, type)
            onActivityReported()
        }
    }

    /**
     * Logs that a particular WebView URL was loaded.
     *
     * @param url the url to log
     */
    fun logWebView(url: String?) {
        if (sdkCallChecker.check("log_web_view")) {
            breadcrumbService?.logWebView(url, sdkClock.now())
            onActivityReported()
        }
    }

    /**
     * Logs a tap on a screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     * @param type        the type of tap that occurred
     */
    fun logTap(point: Pair<Float?, Float?>, elementName: String, type: TapBreadcrumbType) {
        if (sdkCallChecker.check("log_tap")) {
            breadcrumbService?.logTap(point, elementName, sdkClock.now(), type)
            onActivityReported()
        }
    }

    fun trackWebViewPerformance(tag: String, message: String) {
        if (isStarted() && configService?.webViewVitalsBehavior?.isWebViewVitalsEnabled() == true) {
            webViewService?.collectWebData(tag, message)
        }
    }

    fun getCurrentSessionId(): String? {
        val localSessionIdTracker = sessionIdTracker
        if (localSessionIdTracker != null && sdkCallChecker.check("get_current_session_id")) {
            val sessionId = localSessionIdTracker.getActiveSessionId()
            if (sessionId != null) {
                return sessionId
            } else {
                logger.logInfo("Session ID is null", null)
            }
        }
        return null
    }

    fun getLastRunEndState(): LastRunEndState = if (isStarted() && crashVerifier != null) {
        if (crashVerifier?.didLastRunCrash() == true) {
            LastRunEndState.CRASH
        } else {
            LastRunEndState.CLEAN_EXIT
        }
    } else {
        LastRunEndState.INVALID
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

    fun shouldCaptureNetworkCall(url: String, method: String): Boolean {
        if (isStarted() && networkCaptureService != null) {
            return networkCaptureService?.getNetworkCaptureRules(url, method)?.isNotEmpty() ?: false
        }
        return false
    }

    fun setProcessStartedByNotification() {
        if (isStarted()) {
            eventService?.setProcessStartedByNotification()
        }
    }

    fun getReactNativeInternalInterface(): ReactNativeInternalInterface? = internalInterfaceModule?.reactNativeInternalInterface

    /**
     * Logs a React Native Redux Action.
     */
    fun logRnAction(
        name: String,
        startTime: Long,
        endTime: Long,
        properties: Map<String?, Any?>,
        bytesSent: Int,
        output: String
    ) {
        if (sdkCallChecker.check("log_react_native_action")) {
            breadcrumbService?.logRnAction(name, startTime, endTime, properties, bytesSent, output)
        }
    }

    /**
     * Logs the fact that a particular view was entered.
     *
     *
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    fun logRnView(screen: String) {
        if (appFramework != Embrace.AppFramework.REACT_NATIVE) {
            logger.logWarning("[Embrace] logRnView is only available on React Native", null)
            return
        }

        if (sdkCallChecker.check("log RN view")) {
            breadcrumbService?.logView(screen, sdkClock.now())
            onActivityReported()
        }
    }

    fun getUnityInternalInterface(): UnityInternalInterface? = internalInterfaceModule?.unityInternalInterface

    fun installUnityThreadSampler() {
        if (sdkCallChecker.check("install_unity_thread_sampler")) {
            sampleCurrentThreadDuringAnrs()
        }
    }

    fun getFlutterInternalInterface(): FlutterInternalInterface? = internalInterfaceModule?.flutterInternalInterface

    private fun onActivityReported() {
        val orchestrator = sessionOrchestrator
        orchestrator?.reportBackgroundActivityStateChange()
    }

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

    private fun normalizeProperties(properties: Map<String, Any>?, logger: EmbLogger): Map<String, Any>? {
        var normalizedProperties: Map<String, Any> = HashMap()
        if (properties != null) {
            try {
                normalizedProperties = sanitizeProperties(properties, logger)
            } catch (e: Exception) {
                this.logger.logError("Exception occurred while normalizing the properties.", e)
            }
            return normalizedProperties
        } else {
            return null
        }
    }

    fun addSpanExporter(spanExporter: SpanExporter) {
        if (isStarted()) {
            logger.logError("A SpanExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addSpanExporter(spanExporter)
    }

    fun addLogRecordExporter(logRecordExporter: LogRecordExporter) {
        if (isStarted()) {
            logger.logError("A LogRecordExporter can only be added before the SDK is started.", null)
            return
        }
        bootstrapper.openTelemetryModule.openTelemetryConfiguration.addLogExporter(logRecordExporter)
    }

    companion object {
        private val appIdPattern: Pattern = Pattern.compile("^[A-Za-z0-9]{5}$")
    }
}

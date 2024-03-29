package io.embrace.android.embracesdk;

import static io.embrace.android.embracesdk.event.EmbraceEventService.STARTUP_EVENT_NAME;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import io.embrace.android.embracesdk.anr.AnrService;
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller;
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService;
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService;
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService;
import io.embrace.android.embracesdk.capture.metadata.MetadataService;
import io.embrace.android.embracesdk.capture.user.UserService;
import io.embrace.android.embracesdk.capture.webview.WebViewService;
import io.embrace.android.embracesdk.config.ConfigService;
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior;
import io.embrace.android.embracesdk.event.EventService;
import io.embrace.android.embracesdk.event.LogMessageService;
import io.embrace.android.embracesdk.injection.AndroidServicesModule;
import io.embrace.android.embracesdk.injection.AnrModule;
import io.embrace.android.embracesdk.injection.CoreModule;
import io.embrace.android.embracesdk.injection.CrashModule;
import io.embrace.android.embracesdk.injection.CustomerLogModule;
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule;
import io.embrace.android.embracesdk.injection.DataContainerModule;
import io.embrace.android.embracesdk.injection.DeliveryModule;
import io.embrace.android.embracesdk.injection.EssentialServiceModule;
import io.embrace.android.embracesdk.injection.ModuleInitBootstrapper;
import io.embrace.android.embracesdk.injection.SdkObservabilityModule;
import io.embrace.android.embracesdk.injection.SessionModule;
import io.embrace.android.embracesdk.internal.ApkToolsConfig;
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface;
import io.embrace.android.embracesdk.internal.Systrace;
import io.embrace.android.embracesdk.internal.TraceparentGenerator;
import io.embrace.android.embracesdk.internal.clock.Clock;
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier;
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData;
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer;
import io.embrace.android.embracesdk.internal.utils.ThrowableUtilsKt;
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger;
import io.embrace.android.embracesdk.logging.InternalErrorLogger;
import io.embrace.android.embracesdk.logging.InternalErrorService;
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;
import io.embrace.android.embracesdk.ndk.NativeModule;
import io.embrace.android.embracesdk.ndk.NdkService;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService;
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService;
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb;
import io.embrace.android.embracesdk.payload.TapBreadcrumb;
import io.embrace.android.embracesdk.prefs.PreferencesService;
import io.embrace.android.embracesdk.session.id.SessionIdTracker;
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker;
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService;
import io.embrace.android.embracesdk.session.orchestrator.SessionOrchestrator;
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService;
import io.embrace.android.embracesdk.telemetry.TelemetryService;
import io.embrace.android.embracesdk.utils.PropertyUtils;
import io.embrace.android.embracesdk.worker.WorkerName;
import io.embrace.android.embracesdk.worker.WorkerThreadModule;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import kotlin.Lazy;
import kotlin.LazyKt;

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 * <p>
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
final class EmbraceImpl {

    private static final Pattern appIdPattern = Pattern.compile("^[A-Za-z0-9]{5}$");

    @NonNull
    final EmbraceTracer tracer;

    @NonNull
    private final Lazy<EmbraceInternalInterface> uninitializedSdkInternalInterface;

    /**
     * Whether the Embrace SDK has been started yet.
     */
    @NonNull
    private final AtomicBoolean started = new AtomicBoolean(false);

    @NonNull
    private final InternalEmbraceLogger internalEmbraceLogger = InternalStaticEmbraceLogger.logger;

    @NonNull
    private final ModuleInitBootstrapper moduleInitBootstrapper;

    @NonNull
    private final Clock sdkClock;

    /**
     * Custom app ID that overrides the one specified at build time
     */
    @Nullable
    private volatile String customAppId;

    /**
     * The application being instrumented by the SDK.
     */
    @Nullable
    private volatile Application application;

    /**
     * The type of application being instrumented by this SDK instance, whether it's directly used by an Android app, or used via a hosted
     * SDK like Flutter, React Native, or Unity.
     */
    @Nullable
    private volatile Embrace.AppFramework appFramework;

    @Nullable
    private volatile BreadcrumbService breadcrumbService;

    @Nullable
    private volatile SessionOrchestrator sessionOrchestrator;

    @Nullable
    private volatile SessionPropertiesService sessionPropertiesService;

    @Nullable
    private volatile MetadataService metadataService;

    @Nullable
    private volatile SessionIdTracker sessionIdTracker;

    @Nullable
    private volatile ProcessStateService processStateService;

    @Nullable
    private volatile ActivityTracker activityTracker;

    @Nullable
    private volatile NetworkLoggingService networkLoggingService;

    @Nullable
    private volatile AnrService anrService;

    @Nullable
    private volatile LogMessageService logMessageService;

    @Nullable
    private volatile ConfigService configService;

    @Nullable
    private volatile PreferencesService preferencesService;

    @Nullable
    private volatile EventService eventService;

    @Nullable
    private volatile UserService userService;

    @Nullable
    private volatile InternalErrorService internalErrorService;

    @Nullable
    private volatile NdkService ndkService;

    @Nullable
    private volatile NetworkCaptureService networkCaptureService;

    @Nullable
    private volatile WebViewService webViewService;

    @Nullable
    private volatile TelemetryService telemetryService;

    @Nullable
    private NativeThreadSamplerService nativeThreadSampler;

    @Nullable
    private NativeThreadSamplerInstaller nativeThreadSamplerInstaller;

    @Nullable
    private EmbraceInternalInterface embraceInternalInterface;

    @Nullable
    private InternalInterfaceModule internalInterfaceModule;

    @Nullable
    private PushNotificationCaptureService pushNotificationService;

    @Nullable
    private LastRunCrashVerifier crashVerifier;

    /**
     * Variable pointing to the composeActivityListener instance obtained using reflection
     */
    @Nullable
    private Object composeActivityListenerInstance;

    EmbraceImpl(@NonNull ModuleInitBootstrapper bs) {
        moduleInitBootstrapper = bs;
        sdkClock = moduleInitBootstrapper.getInitModule().getClock();
        tracer = moduleInitBootstrapper.getOpenTelemetryModule().getEmbraceTracer();
        uninitializedSdkInternalInterface =
            LazyKt.lazy(
                () -> new UninitializedSdkInternalInterfaceImpl(moduleInitBootstrapper.getOpenTelemetryModule().getInternalTracer())
            );
    }

    EmbraceImpl() {
        this(new ModuleInitBootstrapper());
    }

    /**
     * Starts instrumentation of the Android application using the Embrace SDK. This should be
     * called during creation of the application, as early as possible.
     * <p>
     * See <a href="https://embrace.io/docs/android/">Embrace Docs</a> for
     * integration instructions. For compatibility with other networking SDKs such as Akamai,
     * the Embrace SDK must be initialized after any other SDK.
     *
     * @param context                  an instance of context
     * @param enableIntegrationTesting if true, debug sessions (those which are not part of a
     *                                 release APK) will go to the live integration testing tab
     *                                 of the dashboard. If false, they will appear in 'recent
     *                                 sessions'.
     */
    void start(@NonNull Context context,
               boolean enableIntegrationTesting,
               @NonNull Embrace.AppFramework appFramework) {
        try {
            Systrace.startSynchronous("sdk-start");
            startImpl(context, enableIntegrationTesting, appFramework);
            Systrace.endSynchronous();
        } catch (Throwable t) {
            internalEmbraceLogger.logError(
                "Error occurred while initializing the Embrace SDK. Instrumentation may be disabled.", t, true);
        }
    }

    private void startImpl(@NonNull Context context,
                           boolean enableIntegrationTesting,
                           @NonNull Embrace.AppFramework framework) {
        if (application != null) {
            // We don't hard fail if the SDK has been already initialized.
            internalEmbraceLogger.logWarning("Embrace SDK has already been initialized");
            return;
        }

        if (ApkToolsConfig.IS_SDK_DISABLED) {
            internalEmbraceLogger.logInfo("SDK disabled through ApkToolsConfig");
            stop();
            return;
        }

        final long startTimeMs = sdkClock.now();
        internalEmbraceLogger.logDeveloper("Embrace", "Starting SDK for framework " + framework.name());
        moduleInitBootstrapper.init(context, enableIntegrationTesting, framework, startTimeMs, customAppId);
        Systrace.startSynchronous("post-services-setup");
        telemetryService = moduleInitBootstrapper.getInitModule().getTelemetryService();

        final CoreModule coreModule = moduleInitBootstrapper.getCoreModule();
        application = coreModule.getApplication();
        appFramework = coreModule.getAppFramework();

        final AndroidServicesModule androidServicesModule = moduleInitBootstrapper.getAndroidServicesModule();
        preferencesService = androidServicesModule.getPreferencesService();

        final EssentialServiceModule essentialServiceModule = moduleInitBootstrapper.getEssentialServiceModule();
        if (essentialServiceModule.getConfigService().isSdkDisabled()) {
            internalEmbraceLogger.logInfo("Interrupting SDK start because it is disabled");
            stop();
            return;
        }

        if (essentialServiceModule.getConfigService().getAutoDataCaptureBehavior().isComposeOnClickEnabled()) {
            registerComposeActivityListener(coreModule);
        }

        processStateService = essentialServiceModule.getProcessStateService();
        metadataService = essentialServiceModule.getMetadataService();
        sessionIdTracker = essentialServiceModule.getSessionIdTracker();
        configService = essentialServiceModule.getConfigService();
        activityTracker = essentialServiceModule.getActivityLifecycleTracker();
        userService = essentialServiceModule.getUserService();

        final DataCaptureServiceModule dataCaptureServiceModule = moduleInitBootstrapper.getDataCaptureServiceModule();
        webViewService = dataCaptureServiceModule.getWebviewService();
        breadcrumbService = dataCaptureServiceModule.getBreadcrumbService();
        pushNotificationService = dataCaptureServiceModule.getPushNotificationService();

        final AnrModule anrModule = moduleInitBootstrapper.getAnrModule();
        anrService = anrModule.getAnrService();

        final SdkObservabilityModule sdkObservabilityModule = moduleInitBootstrapper.getSdkObservabilityModule();
        internalErrorService = sdkObservabilityModule.getInternalErrorService();
        sdkObservabilityModule.getInternalErrorService().setConfigService(configService);

        final DeliveryModule deliveryModule = moduleInitBootstrapper.getDeliveryModule();

        final CustomerLogModule customerLogModule = moduleInitBootstrapper.getCustomerLogModule();
        logMessageService = customerLogModule.getLogMessageService();
        networkCaptureService = customerLogModule.getNetworkCaptureService();
        networkLoggingService = customerLogModule.getNetworkLoggingService();

        final NativeModule nativeModule = moduleInitBootstrapper.getNativeModule();
        ndkService = nativeModule.getNdkService();
        nativeThreadSampler = nativeModule.getNativeThreadSamplerService();
        nativeThreadSamplerInstaller = nativeModule.getNativeThreadSamplerInstaller();

        final DataContainerModule dataContainerModule = moduleInitBootstrapper.getDataContainerModule();
        eventService = dataContainerModule.getEventService();

        final SessionModule sessionModule = moduleInitBootstrapper.getSessionModule();
        sessionOrchestrator = sessionModule.getSessionOrchestrator();
        sessionPropertiesService = sessionModule.getSessionPropertiesService();

        Systrace.startSynchronous("send-cached-sessions");
        // Send any sessions that were cached and not yet sent.
        deliveryModule.getDeliveryService().sendCachedSessions(
            nativeModule.getNdkService(),
            essentialServiceModule.getSessionIdTracker()
        );
        Systrace.endSynchronous();

        final CrashModule crashModule = moduleInitBootstrapper.getCrashModule();
        loadCrashVerifier(crashModule, moduleInitBootstrapper.getWorkerThreadModule());

        internalInterfaceModule = new InternalInterfaceModuleImpl(
            moduleInitBootstrapper.getInitModule(),
            moduleInitBootstrapper.getOpenTelemetryModule(),
            coreModule,
            essentialServiceModule,
            this,
            crashModule
        );

        embraceInternalInterface = internalInterfaceModule.getEmbraceInternalInterface();

        // Only preemptively initialize internal dependencies service for the current framework being used
        switch (framework) {
            case NATIVE:
                break;
            case REACT_NATIVE:
                internalInterfaceModule.getReactNativeInternalInterface();
                break;
            case UNITY:
                internalInterfaceModule.getUnityInternalInterface();
                break;
            case FLUTTER:
                internalInterfaceModule.getFlutterInternalInterface();
                break;
        }


        final String startMsg = "Embrace SDK started. App ID: " +
            essentialServiceModule.getConfigService().getSdkModeBehavior().getAppId() + " Version: " + BuildConfig.VERSION_NAME;
        internalEmbraceLogger.logInfo(startMsg);

        final long endTimeMs = sdkClock.now();
        started.set(true);
        Systrace.endSynchronous();
        Systrace.startSynchronous("startup-tracking");
        dataCaptureServiceModule.getStartupService().setSdkStartupInfo(startTimeMs, endTimeMs);
        Systrace.endSynchronous();

        // Attempt to send the startup event if the app is already in the foreground. We registered to send this when
        // we went to the foreground, but if an activity had already gone to the foreground, we may have missed
        // sending this, so to ensure the startup message is sent, we force it to be sent here.
        if (!essentialServiceModule.getProcessStateService().isInBackground()) {
            internalEmbraceLogger.logDeveloper("Embrace", "Sending startup moment");
            dataContainerModule.getEventService().sendStartupMoment();
        }

        // This should return immediately given that EmbraceSpansService initialization should be finished at this point
        // Put in emergency timeout just in case something unexpected happens so as to fail the SDK startup.
        moduleInitBootstrapper.waitForAsyncInit();
    }

    /**
     * Loads the crash verifier to get the end state of the app crashed in the last run.
     * This method is called when the app starts.
     *
     * @param crashModule        an instance of {@link CrashModule}
     * @param workerThreadModule an instance of {@link WorkerThreadModule}
     */
    private void loadCrashVerifier(CrashModule crashModule, WorkerThreadModule workerThreadModule) {
        crashVerifier = crashModule.getLastRunCrashVerifier();
        crashVerifier.readAndCleanMarkerAsync(
            workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
        );
    }

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param coreModule instance containing a required set of dependencies
     */
    private void registerComposeActivityListener(@NonNull CoreModule coreModule) {
        try {
            Class<?> composeActivityListener = Class.forName("io.embrace.android.embracesdk.compose.ComposeActivityListener");
            composeActivityListenerInstance = composeActivityListener.newInstance();
            coreModule.getApplication().registerActivityLifecycleCallbacks((Application.ActivityLifecycleCallbacks) composeActivityListenerInstance);
        } catch (Throwable e) {
            internalEmbraceLogger.logError("registerComposeActivityListener error", e);
        }
    }

    /**
     * Register ComposeActivityListener as Activity Lifecycle Callbacks into the Application
     *
     * @param app Global application class
     */
    private void unregisterComposeActivityListener(@NonNull Application app) {
        try {
            app.unregisterActivityLifecycleCallbacks((Application.ActivityLifecycleCallbacks) composeActivityListenerInstance);
        } catch (Throwable e) {
            internalEmbraceLogger.logError("Instantiation error for ComposeActivityListener", e);
        }
    }

    /**
     * Whether or not the SDK has been started.
     *
     * @return true if the SDK is started, false otherwise
     */
    boolean isStarted() {
        return started.get();
    }

    /**
     * Sets a custom app ID that overrides the one specified at build time. Must be called before
     * the SDK is started.
     *
     * @param appId custom app ID
     * @return true if the app ID could be set, false otherwise.
     */
    boolean setAppId(@NonNull String appId) {
        if (isStarted()) {
            internalEmbraceLogger.logError("You must set the custom app ID before the SDK is started.");
            return false;
        }
        if (appId.isEmpty()) {
            internalEmbraceLogger.logError("App ID cannot be null or empty.");
            return false;
        }
        if (!appIdPattern.matcher(appId).find()) {
            internalEmbraceLogger.logError("Invalid app ID. Must be a 5-character string with " +
                "characters from the set [A-Za-z0-9], but it was \"" + appId + "\".");
            return false;
        }

        customAppId = appId;
        return true;
    }

    /**
     * Shuts down the Embrace SDK.
     */
    void stop() {
        if (started.compareAndSet(true, false)) {
            internalEmbraceLogger.logInfo("Shutting down Embrace SDK.");
            try {
                if (composeActivityListenerInstance != null && application != null) {
                    unregisterComposeActivityListener(application);
                }

                application = null;
                moduleInitBootstrapper.stopServices();
            } catch (Exception ex) {
                internalEmbraceLogger.logError("Error while shutting down Embrace SDK", ex);
            }
        }
    }

    /**
     * Sets the user ID. This would typically be some form of unique identifier such as a UUID or
     * database key for the user.
     *
     * @param userId the unique identifier for the user
     */
    void setUserIdentifier(@Nullable String userId) {
        if (checkSdkStartedAndLogPublicApiUsage("set_user_identifier")) {
            userService.setUserIdentifier(userId);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the currently set user ID. For example, if the user logs out.
     */
    void clearUserIdentifier() {
        if (checkSdkStartedAndLogPublicApiUsage("clear_user_identifier")) {
            userService.clearUserIdentifier();
        }
    }

    /**
     * Sets the current user's email address.
     *
     * @param email the email address of the current user
     */
    void setUserEmail(@Nullable String email) {
        if (checkSdkStartedAndLogPublicApiUsage("set_user_email")) {
            userService.setUserEmail(email);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the currently set user's email address.
     */
    void clearUserEmail() {
        if (checkSdkStartedAndLogPublicApiUsage("clear_user_email")) {
            userService.clearUserEmail();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Sets this user as a paying user. This adds a persona to the user's identity.
     */
    void setUserAsPayer() {
        if (checkSdkStartedAndLogPublicApiUsage("set_user_as_payer")) {
            userService.setUserAsPayer();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears this user as a paying user. This would typically be called if a user is no longer
     * paying for the service and has reverted back to a basic user.
     */
    void clearUserAsPayer() {
        if (checkSdkStartedAndLogPublicApiUsage("clear_user_as_payer")) {
            userService.clearUserAsPayer();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Sets a custom user persona. A persona is a trait associated with a given user.
     *
     * @param persona the persona to set
     */
    void addUserPersona(@NonNull String persona) {
        if (checkSdkStartedAndLogPublicApiUsage("add_user_persona")) {
            userService.addUserPersona(persona);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the custom user persona, if it is set.
     *
     * @param persona the persona to clear
     */
    void clearUserPersona(@NonNull String persona) {
        if (checkSdkStartedAndLogPublicApiUsage("clear_user_persona")) {
            userService.clearUserPersona(persona);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears all custom user personas from the user.
     */
    void clearAllUserPersonas() {
        if (checkSdkStartedAndLogPublicApiUsage("clear_user_personas")) {
            userService.clearAllUserPersonas();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Adds a property to the current session.
     */
    boolean addSessionProperty(@NonNull String key, @NonNull String value, boolean permanent) {
        if (checkSdkStartedAndLogPublicApiUsage("add_session_property")) {
            return sessionPropertiesService.addProperty(key, value, permanent);
        }
        return false;
    }

    /**
     * Removes a property from the current session.
     */
    boolean removeSessionProperty(@NonNull String key) {
        if (checkSdkStartedAndLogPublicApiUsage("remove_session_property")) {
            return sessionPropertiesService.removeProperty(key);
        }
        return false;
    }

    /**
     * Retrieves a map of the current session properties.
     */
    @Nullable
    Map<String, String> getSessionProperties() {
        if (checkSdkStartedAndLogPublicApiUsage("get_session_properties")) {
            return sessionPropertiesService.getProperties();
        }
        return null;
    }

    /**
     * Sets the username of the currently logged in user.
     *
     * @param username the username to set
     */
    void setUsername(@Nullable String username) {
        if (checkSdkStartedAndLogPublicApiUsage("set_username")) {
            userService.setUsername(username);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the username of the currently logged in user, for example if the user has logged out.
     */
    void clearUsername() {
        if (checkSdkStartedAndLogPublicApiUsage("clear_username")) {
            userService.clearUsername();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     * <p>
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    void startMoment(@NonNull String name,
                     @Nullable String identifier,
                     @Nullable Map<String, Object> properties) {
        if (checkSdkStartedAndLogPublicApiUsage("start_moment")) {
            eventService.startEvent(name, identifier, normalizeProperties(properties));
            onActivityReported();
        }
    }

    /**
     * Signals the end of a moment with the specified name.
     * <p>
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    void endMoment(@NonNull String name, @Nullable String identifier, @Nullable Map<String, Object> properties) {
        if (checkSdkStartedAndLogPublicApiUsage("end_moment")) {
            eventService.endEvent(name, identifier, normalizeProperties(properties));
            onActivityReported();
        }
    }

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    void endAppStartup(@Nullable Map<String, Object> properties) {
        endMoment(STARTUP_EVENT_NAME, null, properties);
    }

    /**
     * Retrieve the HTTP request header to extract trace ID from.
     *
     * @return the Trace ID header.
     */
    @NonNull
    String getTraceIdHeader() {
        if (configService != null && checkSdkStarted("get_trace_id_header", false)) {
            return configService.getNetworkBehavior().getTraceIdHeader();
        }
        return NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE;
    }

    @NonNull
    String generateW3cTraceparent() {
        return TraceparentGenerator.generateW3CTraceparent();
    }

    void recordNetworkRequest(@NonNull EmbraceNetworkRequest request) {
        if (embraceInternalInterface != null && checkSdkStartedAndLogPublicApiUsage("record_network_request")) {
            embraceInternalInterface.recordAndDeduplicateNetworkRequest(UUID.randomUUID().toString(), request);
        }
    }

    void recordAndDeduplicateNetworkRequest(@NonNull String callId, @NonNull EmbraceNetworkRequest request) {
        if (checkSdkStartedAndLogPublicApiUsage("record_network_request")) {
            logNetworkRequestImpl(
                callId,
                request.getNetworkCaptureData(),
                request.getUrl(),
                request.getHttpMethod(),
                request.getStartTime(),
                request.getResponseCode(),
                request.getEndTime(),
                request.getErrorType(),
                request.getErrorMessage(),
                request.getTraceId(),
                request.getW3cTraceparent(),
                request.getBytesOut(),
                request.getBytesIn()
            );
        }
    }

    private void logNetworkRequestImpl(@NonNull String callId,
                                       @Nullable NetworkCaptureData networkCaptureData,
                                       String url,
                                       String httpMethod,
                                       Long startTime,
                                       Integer responseCode,
                                       Long endTime,
                                       String errorType,
                                       String errorMessage,
                                       String traceId,
                                       @Nullable String w3cTraceparent,
                                       Long bytesOut,
                                       Long bytesIn) {
        if (configService.getNetworkBehavior().isUrlEnabled(url)) {
            if (errorType != null &&
                errorMessage != null &&
                !errorType.isEmpty() &&
                !errorMessage.isEmpty()) {
                networkLoggingService.logNetworkError(
                    callId,
                    url,
                    httpMethod,
                    startTime,
                    endTime != null ? endTime : 0,
                    errorType,
                    errorMessage,
                    traceId,
                    w3cTraceparent,
                    networkCaptureData);
            } else {
                networkLoggingService.logNetworkCall(
                    callId,
                    url,
                    httpMethod,
                    responseCode != null ? responseCode : 0,
                    startTime,
                    endTime != null ? endTime : 0,
                    bytesOut,
                    bytesIn,
                    traceId,
                    w3cTraceparent,
                    networkCaptureData);
            }
            onActivityReported();
        }
    }

    void logMessage(@NonNull String message,
                    @NonNull Severity severity,
                    @Nullable Map<String, ?> properties) {
        logMessage(
            EventType.Companion.fromSeverity(severity),
            message,
            properties,
            null,
            null,
            LogExceptionType.NONE,
            null,
            null
        );
    }

    void logException(@NonNull Throwable throwable,
                      @NonNull Severity severity,
                      @Nullable Map<String, ?> properties,
                      @Nullable String message) {
        String exceptionMessage = throwable.getMessage() != null ? throwable.getMessage() : "";
        logMessage(
            EventType.Companion.fromSeverity(severity),
            message != null ? message : exceptionMessage,
            properties,
            ThrowableUtilsKt.getSafeStackTrace(throwable),
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            throwable.getClass().getSimpleName(),
            exceptionMessage);
    }

    void logCustomStacktrace(@NonNull StackTraceElement[] stacktraceElements,
                             @NonNull Severity severity,
                             @Nullable Map<String, ?> properties,
                             @Nullable String message) {
        logMessage(
            EventType.Companion.fromSeverity(severity),
            message != null ? message : "",
            properties,
            stacktraceElements,
            null,
            LogExceptionType.HANDLED,
            null,
            null,
            null,
            message);
    }

    void logMessage(
        @NonNull EventType type,
        @NonNull String message,
        @Nullable Map<String, ?> properties,
        @Nullable StackTraceElement[] stackTraceElements,
        @Nullable String customStackTrace,
        @NonNull LogExceptionType logExceptionType,
        @Nullable String context,
        @Nullable String library) {
        logMessage(type,
            message,
            properties,
            stackTraceElements,
            customStackTrace,
            logExceptionType,
            context,
            library,
            null,
            null);
    }

    void logMessage(
        @NonNull EventType type,
        @NonNull String message,
        @Nullable Map<String, ?> properties,
        @Nullable StackTraceElement[] stackTraceElements,
        @Nullable String customStackTrace,
        @NonNull LogExceptionType logExceptionType,
        @Nullable String context,
        @Nullable String library,
        @Nullable String exceptionName,
        @Nullable String exceptionMessage) {
        if (checkSdkStartedAndLogPublicApiUsage("log_message")) {
            try {
                logMessageService.log(
                    message,
                    type,
                    logExceptionType,
                    normalizeProperties(properties),
                    stackTraceElements,
                    customStackTrace,
                    appFramework,
                    context,
                    library,
                    exceptionName,
                    exceptionMessage);
                onActivityReported();
            } catch (Exception ex) {
                internalEmbraceLogger.logDebug("Failed to log message using Embrace SDK.", ex);
            }
        }
    }

    /**
     * Logs a breadcrumb.
     * <p>
     * Breadcrumbs track a user's journey through the application and will be shown on the timeline.
     *
     * @param message the name of the breadcrumb to log
     */
    void addBreadcrumb(@NonNull String message) {
        if (checkSdkStartedAndLogPublicApiUsage("add_breadcrumb")) {
            breadcrumbService.logCustom(message, sdkClock.now());
            onActivityReported();
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    void logInternalError(@Nullable String message, @Nullable String details) {
        if (checkSdkStartedAndLogPublicApiUsage("log_internal_error")) {
            if (message == null) {
                return;
            }
            String messageWithDetails;

            if (details != null) {
                messageWithDetails = message + ": " + details;
            } else {
                messageWithDetails = message;
            }
            internalErrorService.handleInternalError(new InternalErrorLogger.InternalError(messageWithDetails));
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    void logInternalError(@NonNull Throwable error) {
        if (checkSdkStartedAndLogPublicApiUsage("log_internal_error")) {
            internalErrorService.handleInternalError(error);
        }
    }

    /**
     * Ends the current session and starts a new one.
     * <p>
     * Cleans all the user info on the device.
     */
    void endSession(boolean clearUserInfo) {
        if (checkSdkStartedAndLogPublicApiUsage("end_session")) {
            sessionOrchestrator.endSessionWithManual(clearUserInfo);
        }
    }

    /**
     * Get the user identifier assigned to the device by Embrace
     *
     * @return the device identifier created by Embrace
     */
    @NonNull
    String getDeviceId() {
        if (checkSdkStartedAndLogPublicApiUsage("get_device_id")) {
            return preferencesService.getDeviceIdentifier();
        } else {
            return "";
        }
    }

    /**
     * Log the start of a fragment.
     * <p>
     * A matching call to endFragment must be made.
     *
     * @param name the name of the fragment to log
     */
    boolean startView(@NonNull String name) {
        if (checkSdkStartedAndLogPublicApiUsage("start_view")) {
            return breadcrumbService.startView(name);
        }
        return false;
    }

    /**
     * Log the end of a fragment.
     * <p>
     * A matching call to startFragment must be made before this is called.
     *
     * @param name the name of the fragment to log
     */
    boolean endView(@NonNull String name) {
        if (checkSdkStartedAndLogPublicApiUsage("end_view")) {
            return breadcrumbService.endView(name);
        }
        return false;
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
    void logPushNotification(
        @Nullable String title,
        @Nullable String body,
        @Nullable String topic,
        @Nullable String id,
        @Nullable Integer notificationPriority,
        Integer messageDeliveredPriority,
        PushNotificationBreadcrumb.NotificationType type) {

        if (checkSdkStartedAndLogPublicApiUsage("log_push_notification")) {
            pushNotificationService.logPushNotification(
                title,
                body,
                topic,
                id,
                notificationPriority,
                messageDeliveredPriority,
                type
            );
            onActivityReported();
        }
    }

    /**
     * Logs that a particular WebView URL was loaded.
     *
     * @param url the url to log
     */
    void logWebView(String url) {
        if (checkSdkStartedAndLogPublicApiUsage("log_web_view")) {
            breadcrumbService.logWebView(url, sdkClock.now());
            onActivityReported();
        }
    }

    /**
     * Logs a tap on a screen element.
     *
     * @param point       the coordinates of the screen tap
     * @param elementName the name of the element which was tapped
     * @param type        the type of tap that occurred
     */
    void logTap(Pair<Float, Float> point, String elementName, TapBreadcrumb.TapBreadcrumbType type) {
        if (checkSdkStartedAndLogPublicApiUsage("log_tap")) {
            breadcrumbService.logTap(point, elementName, sdkClock.now(), type);
            onActivityReported();
        }
    }

    void trackWebViewPerformance(@NonNull String tag, @NonNull String message) {
        if (isStarted() && configService.getWebViewVitalsBehavior().isWebViewVitalsEnabled()) {
            webViewService.collectWebData(tag, message);
        }
    }

    /**
     * Get the ID for the current session.
     * Returns null if a session has not been started yet or the SDK hasn't been initialized.
     *
     * @return The ID for the current Session, if available.
     */
    @Nullable
    String getCurrentSessionId() {
        SessionIdTracker localSessionIdTracker = sessionIdTracker;
        if (localSessionIdTracker != null && checkSdkStarted("get_current_session_id", false)) {
            String sessionId = localSessionIdTracker.getActiveSessionId();
            if (sessionId != null) {
                return sessionId;
            } else {
                internalEmbraceLogger.logInfo("Session ID is null");
            }
        }
        return null;
    }

    /**
     * Get the end state of the last run of the application.
     *
     * @return LastRunEndState enum value representing the end state of the last run.
     */
    @NonNull
    Embrace.LastRunEndState getLastRunEndState() {
        if (isStarted() && crashVerifier != null) {
            if (crashVerifier.didLastRunCrash()) {
                return Embrace.LastRunEndState.CRASH;
            } else {
                return Embrace.LastRunEndState.CLEAN_EXIT;
            }
        } else {
            return Embrace.LastRunEndState.INVALID;
        }
    }

    @Nullable
    ProcessStateService getActivityService() {
        return processStateService;
    }

    @Nullable
    ActivityTracker getActivityLifecycleTracker() {
        return activityTracker;
    }

    @Nullable
    InternalErrorService getInternalErrorService() {
        return internalErrorService;
    }

    @Nullable
    MetadataService getMetadataService() {
        return metadataService;
    }

    @Nullable
    Application getApplication() {
        return application;
    }

    /**
     * Gets the {@link EmbraceInternalInterface} that should be used as the sole source of
     * communication with other Android SDK modules.
     */
    @NonNull
    EmbraceInternalInterface getEmbraceInternalInterface() {
        if (isStarted() && embraceInternalInterface != null) {
            return embraceInternalInterface;
        } else {
            return uninitializedSdkInternalInterface.getValue();
        }
    }

    boolean shouldCaptureNetworkCall(@NonNull String url, @NonNull String method) {
        if (isStarted() && networkCaptureService != null) {
            return !networkCaptureService.getNetworkCaptureRules(url, method).isEmpty();
        }
        return false;
    }

    void setProcessStartedByNotification() {
        if (isStarted()) {
            eventService.setProcessStartedByNotification();
        }
    }

    /**
     * Gets the {@link ReactNativeInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for React Native.
     */
    @Nullable
    ReactNativeInternalInterface getReactNativeInternalInterface() {
        return internalInterfaceModule != null ? internalInterfaceModule.getReactNativeInternalInterface() : null;
    }

    /**
     * Logs a React Native Redux Action.
     */
    void logRnAction(@NonNull String name, long startTime, long endTime,
                     @NonNull Map<String, Object> properties, int bytesSent, @NonNull String output) {
        if (checkSdkStartedAndLogPublicApiUsage("log_react_native_action")) {
            breadcrumbService.logRnAction(name, startTime, endTime, properties, bytesSent, output);
        }
    }

    /**
     * Logs the fact that a particular view was entered.
     * <p>
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    void logRnView(@NonNull String screen) {
        if (appFramework != Embrace.AppFramework.REACT_NATIVE) {
            internalEmbraceLogger.logWarning("[Embrace] logRnView is only available on React Native");
            return;
        }

        if (checkSdkStartedAndLogPublicApiUsage("log RN view")) {
            breadcrumbService.logView(screen, sdkClock.now());
            onActivityReported();
        }
    }

    /**
     * Gets the {@link UnityInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Unity.
     */
    @Nullable
    UnityInternalInterface getUnityInternalInterface() {
        return internalInterfaceModule != null ? internalInterfaceModule.getUnityInternalInterface() : null;
    }

    void installUnityThreadSampler() {
        if (checkSdkStartedAndLogPublicApiUsage("install_unity_thread_sampler")) {
            sampleCurrentThreadDuringAnrs();
        }
    }

    /**
     * Gets the {@link FlutterInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Flutter.
     */
    @Nullable
    FlutterInternalInterface getFlutterInternalInterface() {
        return internalInterfaceModule != null ? internalInterfaceModule.getFlutterInternalInterface() : null;
    }

    private void onActivityReported() {
        SessionOrchestrator orchestrator = sessionOrchestrator;
        if (orchestrator != null) {
            orchestrator.reportBackgroundActivityStateChange();
        }
    }

    private void sampleCurrentThreadDuringAnrs() {
        try {
            AnrService service = anrService;
            if (service != null && nativeThreadSamplerInstaller != null) {
                nativeThreadSamplerInstaller.monitorCurrentThread(
                    nativeThreadSampler,
                    configService,
                    service
                );
            } else {
                internalEmbraceLogger.logWarning("nativeThreadSamplerInstaller not started, cannot sample current thread");
            }
        } catch (Exception exc) {
            internalEmbraceLogger.logError("Failed to sample current thread during ANRs", exc);
        }
    }

    @Nullable
    private Map<String, Object> normalizeProperties(@Nullable Map<String, ?> properties) {
        Map<String, Object> normalizedProperties = new HashMap<>();
        if (properties != null) {
            try {
                normalizedProperties = PropertyUtils.sanitizeProperties(properties);
            } catch (Exception e) {
                internalEmbraceLogger.logError("Exception occurred while normalizing the properties.", e);
            }
            return normalizedProperties;
        } else {
            return null;
        }
    }

    /**
     * Checks if the SDK is started and logs the public API usage.
     * <p>
     * Every public API usage should go through this method, except the ones that are called too often and may cause a performance hit.
     * For instance, get_current_session_id and get_trace_id_header go directly through checkSdkStarted.
     */
    private boolean checkSdkStartedAndLogPublicApiUsage(@NonNull String action) {
        return checkSdkStarted(action, true);
    }

    private boolean checkSdkStarted(@NonNull String action, boolean logPublicApiUsage) {
        boolean isStarted = isStarted();
        if (!isStarted) {
            internalEmbraceLogger.logSDKNotInitialized(action);
        }
        if (telemetryService != null && logPublicApiUsage) {
            telemetryService.onPublicApiCalled(action);
        }
        return isStarted;
    }

    public void addSpanExporter(@NonNull SpanExporter spanExporter) {
        moduleInitBootstrapper.getOpenTelemetryModule().getOpenTelemetryConfiguration().addSpanExporter(spanExporter);
    }
}

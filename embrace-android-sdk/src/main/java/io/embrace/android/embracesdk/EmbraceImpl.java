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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import io.embrace.android.embracesdk.annotation.InternalApi;
import io.embrace.android.embracesdk.anr.AnrService;
import io.embrace.android.embracesdk.anr.ndk.EmbraceNativeThreadSamplerServiceKt;
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerInstaller;
import io.embrace.android.embracesdk.anr.ndk.NativeThreadSamplerService;
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService;
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService;
import io.embrace.android.embracesdk.capture.memory.ComponentCallbackService;
import io.embrace.android.embracesdk.capture.memory.MemoryService;
import io.embrace.android.embracesdk.capture.metadata.MetadataService;
import io.embrace.android.embracesdk.capture.user.UserService;
import io.embrace.android.embracesdk.capture.webview.WebViewService;
import io.embrace.android.embracesdk.config.ConfigService;
import io.embrace.android.embracesdk.config.behavior.NetworkBehavior;
import io.embrace.android.embracesdk.event.EmbraceRemoteLogger;
import io.embrace.android.embracesdk.event.EventService;
import io.embrace.android.embracesdk.injection.AndroidServicesModule;
import io.embrace.android.embracesdk.injection.AndroidServicesModuleImpl;
import io.embrace.android.embracesdk.injection.AnrModuleImpl;
import io.embrace.android.embracesdk.injection.CoreModule;
import io.embrace.android.embracesdk.injection.CoreModuleImpl;
import io.embrace.android.embracesdk.injection.CrashModule;
import io.embrace.android.embracesdk.injection.CrashModuleImpl;
import io.embrace.android.embracesdk.injection.CustomerLogModuleImpl;
import io.embrace.android.embracesdk.injection.DataCaptureServiceModule;
import io.embrace.android.embracesdk.injection.DataCaptureServiceModuleImpl;
import io.embrace.android.embracesdk.injection.DataContainerModule;
import io.embrace.android.embracesdk.injection.DataContainerModuleImpl;
import io.embrace.android.embracesdk.injection.DeliveryModule;
import io.embrace.android.embracesdk.injection.DeliveryModuleImpl;
import io.embrace.android.embracesdk.injection.EssentialServiceModule;
import io.embrace.android.embracesdk.injection.EssentialServiceModuleImpl;
import io.embrace.android.embracesdk.injection.InitModule;
import io.embrace.android.embracesdk.injection.InitModuleImpl;
import io.embrace.android.embracesdk.injection.SdkObservabilityModule;
import io.embrace.android.embracesdk.injection.SdkObservabilityModuleImpl;
import io.embrace.android.embracesdk.injection.SessionModule;
import io.embrace.android.embracesdk.injection.SessionModuleImpl;
import io.embrace.android.embracesdk.injection.SystemServiceModule;
import io.embrace.android.embracesdk.injection.SystemServiceModuleImpl;
import io.embrace.android.embracesdk.internal.ApkToolsConfig;
import io.embrace.android.embracesdk.internal.BuildInfo;
import io.embrace.android.embracesdk.internal.DeviceArchitecture;
import io.embrace.android.embracesdk.internal.DeviceArchitectureImpl;
import io.embrace.android.embracesdk.internal.EmbraceInternalInterface;
import io.embrace.android.embracesdk.internal.EmbraceInternalInterfaceKt;
import io.embrace.android.embracesdk.internal.MessageType;
import io.embrace.android.embracesdk.internal.TraceparentGenerator;
import io.embrace.android.embracesdk.internal.clock.Clock;
import io.embrace.android.embracesdk.internal.crash.LastRunCrashVerifier;
import io.embrace.android.embracesdk.internal.network.http.HttpUrlConnectionTracker;
import io.embrace.android.embracesdk.internal.network.http.NetworkCaptureData;
import io.embrace.android.embracesdk.internal.spans.EmbraceSpansService;
import io.embrace.android.embracesdk.internal.spans.EmbraceTracer;
import io.embrace.android.embracesdk.internal.utils.ThrowableUtilsKt;
import io.embrace.android.embracesdk.logging.EmbraceInternalErrorService;
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger;
import io.embrace.android.embracesdk.logging.InternalErrorLogger;
import io.embrace.android.embracesdk.logging.InternalStaticEmbraceLogger;
import io.embrace.android.embracesdk.ndk.NativeModule;
import io.embrace.android.embracesdk.ndk.NativeModuleImpl;
import io.embrace.android.embracesdk.ndk.NdkService;
import io.embrace.android.embracesdk.network.EmbraceNetworkRequest;
import io.embrace.android.embracesdk.network.logging.NetworkCaptureService;
import io.embrace.android.embracesdk.network.logging.NetworkLoggingService;
import io.embrace.android.embracesdk.payload.PushNotificationBreadcrumb;
import io.embrace.android.embracesdk.payload.TapBreadcrumb;
import io.embrace.android.embracesdk.prefs.PreferencesService;
import io.embrace.android.embracesdk.registry.ServiceRegistry;
import io.embrace.android.embracesdk.session.BackgroundActivityService;
import io.embrace.android.embracesdk.session.SessionService;
import io.embrace.android.embracesdk.session.lifecycle.ActivityTracker;
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService;
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties;
import io.embrace.android.embracesdk.session.properties.SessionPropertiesService;
import io.embrace.android.embracesdk.utils.PropertyUtils;
import io.embrace.android.embracesdk.worker.ExecutorName;
import io.embrace.android.embracesdk.worker.WorkerThreadModule;
import io.embrace.android.embracesdk.worker.WorkerThreadModuleImpl;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function11;
import kotlin.jvm.functions.Function2;
import kotlin.jvm.functions.Function3;
import kotlin.jvm.functions.Function5;

/**
 * Implementation class of the SDK. Embrace.java forms our public API and calls functions in this
 * class.
 * <p>
 * Any non-public APIs or functionality related to the Embrace.java client should ideally be put
 * here instead.
 */
@SuppressLint("EmbracePublicApiPackageRule")
final class EmbraceImpl {

    private static final String ERROR_USER_UPDATES_DISABLED = "User updates are disabled, ignoring user persona update.";

    private static final Pattern appIdPattern = Pattern.compile("^[A-Za-z0-9]{5}$");

    @NonNull
    final Lazy<EmbraceTracer> tracer;

    /**
     * Whether the Embrace SDK has been started yet.
     */
    @NonNull
    private final AtomicBoolean started = new AtomicBoolean(false);

    @NonNull
    private final InternalEmbraceLogger internalEmbraceLogger = InternalStaticEmbraceLogger.logger;

    @NonNull
    private final Clock sdkClock;

    @NonNull
    private final InitModule initModule;

    @NonNull
    private final Function2<Context, Embrace.AppFramework, CoreModule> coreModuleSupplier;

    @NonNull
    private final Function1<CoreModule, SystemServiceModule> systemServiceModuleSupplier;

    @NonNull
    private final Function3<InitModule, CoreModule, WorkerThreadModule, AndroidServicesModule> androidServicesModuleSupplier;

    @NonNull
    private final Function0<WorkerThreadModule> workerThreadModuleSupplier;

    @NonNull
    private final Function11<InitModule, CoreModule, WorkerThreadModule, SystemServiceModule, AndroidServicesModule, BuildInfo, String,
        Boolean, Function0<Unit>, Function0<ConfigService>, DeviceArchitecture, EssentialServiceModule> essentialServiceModuleSupplier;

    @NonNull
    private final Function5<InitModule, CoreModule, SystemServiceModule, EssentialServiceModule, WorkerThreadModule,
        DataCaptureServiceModule> dataCaptureServiceModuleSupplier;

    @NonNull
    private final Function3<CoreModule, EssentialServiceModule, WorkerThreadModule, DeliveryModule>
        deliveryModuleSupplier;

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
    private volatile SessionService sessionService;

    @Nullable
    private volatile SessionPropertiesService sessionPropertiesService;

    @Nullable
    private volatile BackgroundActivityService backgroundActivityService;

    @Nullable
    private volatile MetadataService metadataService;

    @Nullable
    private volatile ProcessStateService processStateService;

    @Nullable
    private volatile ActivityTracker activityTracker;

    @Nullable
    private volatile NetworkLoggingService networkLoggingService;

    @Nullable
    private volatile AnrService anrService;

    /**
     * TODO: rename to match convention
     */
    @Nullable
    private volatile EmbraceRemoteLogger remoteLogger;

    @Nullable
    private volatile ConfigService configService;

    @Nullable
    private volatile PreferencesService preferencesService;

    @Nullable
    private volatile EventService eventService;

    @Nullable
    private volatile UserService userService;

    @Nullable
    private volatile EmbraceInternalErrorService exceptionsService;

    @Nullable
    private volatile NdkService ndkService;

    @Nullable
    private volatile NetworkCaptureService networkCaptureService;

    @Nullable
    private volatile WebViewService webViewService;

    @Nullable
    private NativeThreadSamplerService nativeThreadSampler;

    @Nullable
    private NativeThreadSamplerInstaller nativeThreadSamplerInstaller;

    @Nullable
    private EmbraceInternalInterface embraceInternalInterface;

    @Nullable
    private ReactNativeInternalInterface reactNativeInternalInterface;

    @Nullable
    private UnityInternalInterface unityInternalInterface;

    @Nullable
    private FlutterInternalInterface flutterInternalInterface;

    @Nullable
    private PushNotificationCaptureService pushNotificationService;

    @Nullable
    private WorkerThreadModule workerThreadModule;

    @Nullable
    private ServiceRegistry serviceRegistry;

    @Nullable
    private LastRunCrashVerifier crashVerifier;

    /**
     * Variable pointing to the composeActivityListener instance obtained using reflection
     */
    @Nullable
    private Object composeActivityListenerInstance;

    EmbraceImpl(@NonNull Function0<InitModule> initModuleSupplier,
                @NonNull Function2<Context, Embrace.AppFramework, CoreModule> coreModuleSupplier,
                @NonNull Function0<WorkerThreadModule> workerThreadModuleSupplier,
                @NonNull Function1<CoreModule, SystemServiceModule> systemServiceModuleSupplier,
                @NonNull Function3<InitModule, CoreModule, WorkerThreadModule, AndroidServicesModule> androidServiceModuleSupplier,
                @NonNull Function11<InitModule, CoreModule, WorkerThreadModule, SystemServiceModule, AndroidServicesModule, BuildInfo,
                    String, Boolean, Function0<Unit>, Function0<ConfigService>, DeviceArchitecture, EssentialServiceModule>
                    essentialServiceModuleSupplier,
                @NonNull Function5<InitModule, CoreModule, SystemServiceModule, EssentialServiceModule, WorkerThreadModule,
                    DataCaptureServiceModule> dataCaptureServiceModuleSupplier,
                @NonNull Function3<CoreModule, EssentialServiceModule, WorkerThreadModule,
                    DeliveryModule> deliveryModuleSupplier) {
        initModule = initModuleSupplier.invoke();
        sdkClock = initModule.getClock();
        this.coreModuleSupplier = coreModuleSupplier;
        this.workerThreadModuleSupplier = workerThreadModuleSupplier;
        this.systemServiceModuleSupplier = systemServiceModuleSupplier;
        this.androidServicesModuleSupplier = androidServiceModuleSupplier;
        this.essentialServiceModuleSupplier = essentialServiceModuleSupplier;
        this.dataCaptureServiceModuleSupplier = dataCaptureServiceModuleSupplier;
        this.deliveryModuleSupplier = deliveryModuleSupplier;
        this.tracer = LazyKt.lazy(() -> new EmbraceTracer(initModule.getSpansService()));
    }

    EmbraceImpl() {
        this(
            InitModuleImpl::new,
            CoreModuleImpl::new,
            WorkerThreadModuleImpl::new,
            SystemServiceModuleImpl::new,
            AndroidServicesModuleImpl::new,
            EssentialServiceModuleImpl::new,
            DataCaptureServiceModuleImpl::new,
            DeliveryModuleImpl::new
        );
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
            startImpl(context, enableIntegrationTesting, appFramework);
        } catch (Exception ex) {
            internalEmbraceLogger.logError(
                "Exception occurred while initializing the Embrace SDK. Instrumentation may be disabled.", ex, true);
        }
    }

    private void startImpl(@NonNull Context context,
                           boolean enableIntegrationTesting,
                           @NonNull Embrace.AppFramework framework) {
        if (application != null) {
            // We don't hard fail if the SDK has been already initialized.
            InternalStaticEmbraceLogger.logWarning("Embrace SDK has already been initialized");
            return;
        }
        if (ApkToolsConfig.IS_SDK_DISABLED) {
            internalEmbraceLogger.logInfo("SDK disabled through ApkToolsConfig");
            stop();
            return;
        }

        final long startTime = sdkClock.now();
        internalEmbraceLogger.logDeveloper("Embrace", "Starting SDK for framework " + framework.name());

        final CoreModule coreModule = coreModuleSupplier.invoke(context, framework);
        serviceRegistry = coreModule.getServiceRegistry();
        serviceRegistry.registerService(initModule.getSpansService());
        application = coreModule.getApplication();
        appFramework = coreModule.getAppFramework();

        final WorkerThreadModule nonNullWorkerThreadModule = workerThreadModuleSupplier.invoke();
        workerThreadModule = nonNullWorkerThreadModule;

        final SystemServiceModule systemServiceModule = systemServiceModuleSupplier.invoke(coreModule);
        final AndroidServicesModule androidServicesModule =
            androidServicesModuleSupplier.invoke(initModule, coreModule, workerThreadModule);
        preferencesService = androidServicesModule.getPreferencesService();
        serviceRegistry.registerService(preferencesService);

        // bootstrap initialization. ConfigService not created yet...
        final EssentialServiceModule essentialServiceModule = essentialServiceModuleSupplier.invoke(
            initModule,
            coreModule,
            nonNullWorkerThreadModule,
            systemServiceModule,
            androidServicesModule,
            BuildInfo.fromResources(coreModule.getResources(), coreModule.getContext().getPackageName()),
            customAppId,
            enableIntegrationTesting,
            () -> {
                Embrace.getImpl().stop();
                return null;
            },
            () -> null,
            new DeviceArchitectureImpl());

        final ProcessStateService nonNullProcessStateService = essentialServiceModule.getProcessStateService();
        processStateService = nonNullProcessStateService;
        final MetadataService nonNullMetadataService = essentialServiceModule.getMetadataService();
        metadataService = nonNullMetadataService;
        final ConfigService nonNullConfigService = essentialServiceModule.getConfigService();
        configService = nonNullConfigService;

        // example usage.
        ActivityTracker nonNullLifecycleTracker = essentialServiceModule.getActivityLifecycleTracker();
        this.activityTracker = nonNullLifecycleTracker;
        serviceRegistry.registerServices(
            processStateService,
            metadataService,
            configService,
            nonNullLifecycleTracker
        );

        // only call after ConfigService has initialized.
        nonNullMetadataService.precomputeValues();

        DataCaptureServiceModule dataCaptureServiceModule = dataCaptureServiceModuleSupplier.invoke(
            initModule,
            coreModule,
            systemServiceModule,
            essentialServiceModule,
            workerThreadModule
        );

        webViewService = dataCaptureServiceModule.getWebviewService();
        MemoryService memoryService = dataCaptureServiceModule.getMemoryService();
        ComponentCallbackService componentCallbackService = dataCaptureServiceModule.getComponentCallbackService();
        serviceRegistry.registerServices(
            webViewService,
            memoryService,
            componentCallbackService
        );

        /*
         * Since onForeground() is called sequential in the order that services registered for it,
         * it is important to initialize the `EmbraceAnrService`, and thus register the `onForeground()
         * listener for it, before the `EmbraceSessionService`.
         * The onForeground() call inside the EmbraceAnrService should be called before the
         * EmbraceSessionService call. This is necessary since the EmbraceAnrService should be able to
         * force a Main thread health check and close the pending ANR intervals that happened on the
         * background before the next session is created.
         */
        AnrModuleImpl anrModule = new AnrModuleImpl(
            initModule,
            coreModule,
            essentialServiceModule
        );
        AnrService nonNullAnrService = anrModule.getAnrService();
        anrService = nonNullAnrService;
        serviceRegistry.registerServices(anrService, anrModule.getResponsivenessMonitorService());

        // set callbacks and pass in non-placeholder config.
        nonNullAnrService.finishInitialization(
            essentialServiceModule.getConfigService()
        );

        serviceRegistry.registerService(dataCaptureServiceModule.getPowerSaveModeService());

        // initialize the logger early so that logged exceptions have a good chance of
        // being appended to the exceptions service rather than logcat
        SdkObservabilityModule sdkObservabilityModule = new SdkObservabilityModuleImpl(
            initModule,
            essentialServiceModule
        );

        final EmbraceInternalErrorService nonNullExceptionsService = sdkObservabilityModule.getExceptionService();
        exceptionsService = nonNullExceptionsService;
        serviceRegistry.registerService(exceptionsService);
        internalEmbraceLogger.addLoggerAction(sdkObservabilityModule.getInternalErrorLogger());

        serviceRegistry.registerService(essentialServiceModule.getNetworkConnectivityService());

        final DeliveryModule deliveryModule = deliveryModuleSupplier.invoke(
            coreModule,
            essentialServiceModule,
            nonNullWorkerThreadModule
        );

        serviceRegistry.registerService(deliveryModule.getDeliveryService());

        final EmbraceSessionProperties sessionProperties = new EmbraceSessionProperties(
            androidServicesModule.getPreferencesService(),
            essentialServiceModule.getConfigService(), coreModule.getLogger()
        );

        if (essentialServiceModule.getConfigService().isSdkDisabled()) {
            internalEmbraceLogger.logInfo("the SDK is disabled");
            stop();
            return;
        }

        nonNullExceptionsService.setConfigService(configService);
        breadcrumbService = dataCaptureServiceModule.getBreadcrumbService();
        pushNotificationService = dataCaptureServiceModule.getPushNotificationService();
        serviceRegistry.registerServices(breadcrumbService, pushNotificationService);

        userService = essentialServiceModule.getUserService();
        serviceRegistry.registerServices(userService);

        CustomerLogModuleImpl customerLogModule = new CustomerLogModuleImpl(
            initModule,
            coreModule,
            androidServicesModule,
            essentialServiceModule,
            deliveryModule,
            sessionProperties,
            nonNullWorkerThreadModule
        );
        remoteLogger = customerLogModule.getRemoteLogger();
        networkCaptureService = customerLogModule.getNetworkCaptureService();
        networkLoggingService = customerLogModule.getNetworkLoggingService();
        serviceRegistry.registerServices(
            remoteLogger,
            networkCaptureService,
            networkLoggingService
        );

        NativeModule nativeModule = new NativeModuleImpl(
            coreModule,
            essentialServiceModule,
            deliveryModule,
            sessionProperties,
            nonNullWorkerThreadModule
        );

        DataContainerModule dataContainerModule = new DataContainerModuleImpl(
            initModule,
            coreModule,
            nonNullWorkerThreadModule,
            systemServiceModule,
            androidServicesModule,
            essentialServiceModule,
            dataCaptureServiceModule,
            anrModule,
            customerLogModule,
            deliveryModule,
            nativeModule,
            sessionProperties,
            startTime
        );

        final EventService nonNullEventService = dataContainerModule.getEventService();
        eventService = nonNullEventService;
        serviceRegistry.registerServices(
            dataContainerModule.getPerformanceInfoService(),
            eventService,
            dataContainerModule.getApplicationExitInfoService()
        );

        ndkService = nativeModule.getNdkService();
        nativeThreadSampler = nativeModule.getNativeThreadSamplerService();
        nativeThreadSamplerInstaller = nativeModule.getNativeThreadSamplerInstaller();

        serviceRegistry.registerServices(
            ndkService,
            nativeThreadSampler
        );

        if (nativeThreadSampler != null && nativeThreadSamplerInstaller != null) {
            // install the native thread sampler
            nativeThreadSampler.setupNativeSampler();

            // In Unity this should always run on the Unity thread.
            if (coreModule.getAppFramework() == Embrace.AppFramework.UNITY && EmbraceNativeThreadSamplerServiceKt.isUnityMainThread()) {
                sampleCurrentThreadDuringAnrs();
            }
        } else {
            internalEmbraceLogger.logWarning("Failed to load SO file embrace-native");
        }

        SessionModule sessionModule = new SessionModuleImpl(
            initModule,
            coreModule,
            androidServicesModule,
            essentialServiceModule,
            nativeModule,
            dataContainerModule,
            deliveryModule,
            sessionProperties,
            dataCaptureServiceModule,
            customerLogModule,
            sdkObservabilityModule,
            nonNullWorkerThreadModule
        );

        sessionService = sessionModule.getSessionService();
        sessionPropertiesService = sessionModule.getSessionPropertiesService();
        backgroundActivityService = sessionModule.getBackgroundActivityService();
        serviceRegistry.registerServices(sessionService, backgroundActivityService);

        if (backgroundActivityService != null) {
            internalEmbraceLogger.logInfo("Background activity capture enabled");
        } else {
            internalEmbraceLogger.logInfo("Background activity capture disabled");
        }

        CrashModule crashModule = new CrashModuleImpl(
            initModule,
            essentialServiceModule,
            deliveryModule,
            nativeModule,
            sessionModule,
            anrModule,
            dataContainerModule
        );

        loadCrashVerifier(crashModule, nonNullWorkerThreadModule);

        Thread.setDefaultUncaughtExceptionHandler(crashModule.getAutomaticVerificationExceptionHandler());
        serviceRegistry.registerService(crashModule.getCrashService());

        serviceRegistry.registerService(dataCaptureServiceModule.getThermalStatusService());

        if (nonNullConfigService.getAutoDataCaptureBehavior().isComposeOnClickEnabled()) {
            registerComposeActivityListener(coreModule);
        }

        // initialize internal interfaces
        InternalInterfaceModuleImpl internalInterfaceModule = new InternalInterfaceModuleImpl(
            initModule,
            coreModule,
            androidServicesModule,
            essentialServiceModule,
            this,
            crashModule
        );

        embraceInternalInterface = internalInterfaceModule.getEmbraceInternalInterface();
        reactNativeInternalInterface = internalInterfaceModule.getReactNativeInternalInterface();
        unityInternalInterface = internalInterfaceModule.getUnityInternalInterface();
        flutterInternalInterface = internalInterfaceModule.getFlutterInternalInterface();

        String startMsg = "Embrace SDK started. App ID: " + nonNullConfigService.getSdkModeBehavior().getAppId() +
            " Version: " + BuildConfig.VERSION_NAME;
        internalEmbraceLogger.logInfo(startMsg);

        NetworkBehavior networkBehavior = nonNullConfigService.getNetworkBehavior();
        if (networkBehavior.isNativeNetworkingMonitoringEnabled()) {
            HttpUrlConnectionTracker.registerFactory(networkBehavior.isRequestContentLengthCaptureEnabled());
        }

        final long endTime = sdkClock.now();
        started.set(true);

        nonNullWorkerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION).submit(() -> {
            ((EmbraceSpansService) initModule.getSpansService()).initializeService(TimeUnit.MILLISECONDS.toNanos(startTime),
                TimeUnit.MILLISECONDS.toNanos(endTime));
            return null;
        });

        long startupDuration = endTime - startTime;
        sessionModule.getSessionHandler().setSdkStartupDuration(startupDuration);
        internalEmbraceLogger.logDeveloper("Embrace", "Startup duration: " + startupDuration + " millis");

        // Sets up the registered services. This method is called after the SDK has been started and
        // no more services can be added to the registry. It sets listeners for any services that were
        // registered.
        serviceRegistry.closeRegistration();
        serviceRegistry.registerActivityListeners(nonNullProcessStateService);
        serviceRegistry.registerConfigListeners(nonNullConfigService);
        serviceRegistry.registerMemoryCleanerListeners(essentialServiceModule.getMemoryCleanerService());
        serviceRegistry.registerActivityLifecycleListeners(nonNullLifecycleTracker);

        // Attempt to send the startup event if the app is already in the foreground. We registered to send this when
        // we went to the foreground, but if an activity had already gone to the foreground, we may have missed
        // sending this, so to ensure the startup message is sent, we force it to be sent here.
        if (!nonNullProcessStateService.isInBackground()) {
            internalEmbraceLogger.logDeveloper("Embrace", "Sending startup moment");
            nonNullEventService.sendStartupMoment();
        }
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
            workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)
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
                if (composeActivityListenerInstance != null) {
                    unregisterComposeActivityListener(application);
                }

                application = null;
                internalEmbraceLogger.logDeveloper("Embrace", "Attempting to close services...");
                serviceRegistry.close();
                internalEmbraceLogger.logDeveloper("Embrace", "Services closed");
                workerThreadModule.close();
                processStateService.close();
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
        if (checkSdkStarted("set user identifier")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring identifier update.");
                return;
            }
            userService.setUserIdentifier(userId);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the currently set user ID. For example, if the user logs out.
     */
    void clearUserIdentifier() {
        if (checkSdkStarted("clear user identifier")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring identifier update.");
                return;
            }
            userService.clearUserIdentifier();
        }
    }

    /**
     * Sets the current user's email address.
     *
     * @param email the email address of the current user
     */
    void setUserEmail(@Nullable String email) {
        if (checkSdkStarted("set user email")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring email update.");
                return;
            }
            userService.setUserEmail(email);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the currently set user's email address.
     */
    void clearUserEmail() {
        if (checkSdkStarted("clear user email")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring email update.");
                return;
            }
            userService.clearUserEmail();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Sets this user as a paying user. This adds a persona to the user's identity.
     */
    void setUserAsPayer() {
        if (checkSdkStarted("set user as payer")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring payer user update.");
                return;
            }
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
        if (checkSdkStarted("clear user as payer")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring payer user update.");
                return;
            }
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
        if (checkSdkStarted("set user persona")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning(ERROR_USER_UPDATES_DISABLED);
                return;
            }
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
        if (checkSdkStarted("clear user persona")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning(ERROR_USER_UPDATES_DISABLED);
                return;
            }
            userService.clearUserPersona(persona);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears all custom user personas from the user.
     */
    void clearAllUserPersonas() {
        if (checkSdkStarted("clear user personas")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning(ERROR_USER_UPDATES_DISABLED);
                return;
            }
            userService.clearAllUserPersonas();
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Adds a property to the current session.
     */
    boolean addSessionProperty(@NonNull String key, @NonNull String value, boolean permanent) {
        if (checkSdkStarted("add session property")) {
            return sessionPropertiesService.addProperty(key, value, permanent);
        }
        return false;
    }

    /**
     * Removes a property from the current session.
     */
    boolean removeSessionProperty(@NonNull String key) {
        if (checkSdkStarted("remove session property")) {
            return sessionPropertiesService.removeProperty(key);
        }
        return false;
    }

    /**
     * Retrieves a map of the current session properties.
     */
    @Nullable
    Map<String, String> getSessionProperties() {
        if (checkSdkStarted("get session properties")) {
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
        if (checkSdkStarted("set username")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring username update.");
                return;
            }
            userService.setUsername(username);
            // Update user info in NDK service
            ndkService.onUserInfoUpdate();
        }
    }

    /**
     * Clears the username of the currently logged in user, for example if the user has logged out.
     */
    void clearUsername() {
        if (checkSdkStarted("clear username")) {
            if (!configService.getDataCaptureEventBehavior().isMessageTypeEnabled(MessageType.USER)) {
                internalEmbraceLogger.logWarning("User updates are disabled, ignoring username update.");
                return;
            }
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
        if (checkSdkStarted("start moment")) {
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
        if (checkSdkStarted("end moment")) {
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
        if (checkSdkStarted("get traceIdHeader") && configService != null) {
            return configService.getNetworkBehavior().getTraceIdHeader();
        }
        return NetworkBehavior.CONFIG_TRACE_ID_HEADER_DEFAULT_VALUE;
    }

    @NonNull
    String generateW3cTraceparent() {
        return TraceparentGenerator.generateW3CTraceparent();
    }

    void recordNetworkRequest(@NonNull EmbraceNetworkRequest request) {
        if (checkSdkStarted("record network request") && embraceInternalInterface != null) {
            embraceInternalInterface.recordAndDeduplicateNetworkRequest(UUID.randomUUID().toString(), request);
        }
    }

    void recordAndDeduplicateNetworkRequest(@NonNull String callId, @NonNull EmbraceNetworkRequest request) {
        if (checkSdkStarted("record network request")) {
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
                    @Nullable Map<String, Object> properties) {
        logMessage(
            EmbraceEvent.Type.Companion.fromSeverity(severity),
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
                      @Nullable Map<String, Object> properties,
                      @Nullable String message) {
        String exceptionMessage = throwable.getMessage() != null ? throwable.getMessage() : "";
        logMessage(
            EmbraceEvent.Type.Companion.fromSeverity(severity),
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
                             @Nullable Map<String, Object> properties,
                             @Nullable String message) {
        logMessage(
            EmbraceEvent.Type.Companion.fromSeverity(severity),
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
        @NonNull EmbraceEvent.Type type,
        @NonNull String message,
        @Nullable Map<String, Object> properties,
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
        @NonNull EmbraceEvent.Type type,
        @NonNull String message,
        @Nullable Map<String, Object> properties,
        @Nullable StackTraceElement[] stackTraceElements,
        @Nullable String customStackTrace,
        @NonNull LogExceptionType logExceptionType,
        @Nullable String context,
        @Nullable String library,
        @Nullable String exceptionName,
        @Nullable String exceptionMessage) {
        if (checkSdkStarted("log message")) {
            try {
                remoteLogger.log(
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
        if (checkSdkStarted("log breadcrumb")) {
            breadcrumbService.logCustom(message, sdkClock.now());
            onActivityReported();
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    void logInternalError(@Nullable String message, @Nullable String details) {
        if (checkSdkStarted("log internal error")) {
            if (message == null) {
                return;
            }
            String messageWithDetails;

            if (details != null) {
                messageWithDetails = message + ": " + details;
            } else {
                messageWithDetails = message;
            }
            exceptionsService.handleInternalError(new InternalErrorLogger.InternalError(messageWithDetails));
        }
    }

    /**
     * Logs an internal error to the Embrace SDK - this is not intended for public use.
     */
    void logInternalError(@NonNull Throwable error) {
        if (checkSdkStarted("log internal error")) {
            exceptionsService.handleInternalError(error);
        }
    }

    /**
     * Ends the current session and starts a new one.
     * <p>
     * Cleans all the user info on the device.
     */
    void endSession(boolean clearUserInfo) {
        if (checkSdkStarted("end session")) {
            sessionService.endSessionManually(clearUserInfo);
        }
    }

    /**
     * Get the user identifier assigned to the device by Embrace
     *
     * @return the device identifier created by Embrace
     */
    @NonNull
    String getDeviceId() {
        if (checkSdkStarted("get device ID")) {
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
        if (checkSdkStarted("start view")) {
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
        if (checkSdkStarted("end view")) {
            return breadcrumbService.endView(name);
        }
        return false;
    }

    /**
     * Logs the fact that a particular view was entered.
     * <p>
     * If the previously logged view has the same name, a duplicate view breadcrumb will not be
     * logged.
     *
     * @param screen the name of the view to log
     */
    void logView(String screen) {
        if (checkSdkStarted("log view")) {
            breadcrumbService.logView(screen, sdkClock.now());
            onActivityReported();
        }
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

        if (checkSdkStarted("log push notification")) {
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
        if (checkSdkStarted("log WebView")) {
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
        if (checkSdkStarted("log tap")) {
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
        MetadataService localMetaDataService = metadataService;
        if (checkSdkStarted("get current session ID") && localMetaDataService != null) {
            String sessionId = localMetaDataService.getActiveSessionId();
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
    @InternalApi
    ConfigService getConfigService() {
        return configService;
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
    EmbraceInternalErrorService getExceptionsService() {
        return exceptionsService;
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
            return EmbraceInternalInterfaceKt.getDefaultImpl();
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
        return reactNativeInternalInterface;
    }

    /**
     * Logs a React Native Redux Action.
     */
    void logRnAction(@NonNull String name, long startTime, long endTime,
                     @NonNull Map<String, Object> properties, int bytesSent, @NonNull String output) {
        if (checkSdkStarted("log React Native action")) {
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

        logView(screen);
    }

    /**
     * Gets the {@link UnityInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Unity.
     */
    @Nullable
    UnityInternalInterface getUnityInternalInterface() {
        return unityInternalInterface;
    }

    void installUnityThreadSampler() {
        if (checkSdkStarted("install Unity thread sampler")) {
            sampleCurrentThreadDuringAnrs();
        }
    }

    /**
     * Gets the {@link FlutterInternalInterface} that should be used as the sole source of
     * communication with the Android SDK for Flutter.
     */
    @Nullable
    FlutterInternalInterface getFlutterInternalInterface() {
        return flutterInternalInterface;
    }

    /**
     * Logs a Dart error to the Embrace SDK - this is not intended for public use.
     */
    void logDartException(
        @Nullable String stack,
        @Nullable String name,
        @Nullable String message,
        @Nullable String context,
        @Nullable String library,
        @NonNull LogExceptionType logExceptionType
    ) {
        if (flutterInternalInterface != null) {
            if (logExceptionType == LogExceptionType.HANDLED) {
                flutterInternalInterface.logHandledDartException(stack, name, message, context, library);
            } else if (logExceptionType == LogExceptionType.UNHANDLED) {
                flutterInternalInterface.logUnhandledDartException(stack, name, message, context, library);
            }
            onActivityReported();
        }
    }

    /**
     * Sets the Embrace Flutter SDK version - this is not intended for public use.
     */
    void setEmbraceFlutterSdkVersion(@Nullable String version) {
        if (flutterInternalInterface != null) {
            flutterInternalInterface.setEmbraceFlutterSdkVersion(version);
        }
    }

    /**
     * Sets the Dart version - this is not intended for public use.
     */
    void setDartVersion(@Nullable String version) {
        if (flutterInternalInterface != null) {
            flutterInternalInterface.setDartVersion(version);
        }
    }

    private void onActivityReported() {
        if (backgroundActivityService != null) {
            backgroundActivityService.save();
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
    private Map<String, Object> normalizeProperties(@Nullable Map<String, Object> properties) {
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

    private boolean checkSdkStarted(@NonNull String action) {
        boolean isStarted = isStarted();
        if (!isStarted) {
            internalEmbraceLogger.logSDKNotInitialized(action);
        }
        return isStarted;
    }
}

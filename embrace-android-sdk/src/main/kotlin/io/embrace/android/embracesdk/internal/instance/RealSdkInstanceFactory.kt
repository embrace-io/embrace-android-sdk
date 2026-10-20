package io.embrace.android.embracesdk.internal.instance

import io.embrace.android.embracesdk.internal.api.delegate.BreadcrumbApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ExperimentApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.HostedSdkInternalInterfaceImpl
import io.embrace.android.embracesdk.internal.api.delegate.InstrumentationApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.LogsApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.NetworkRequestApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.OTelApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.SdkStateApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.UserSessionApiDelegate
import io.embrace.android.embracesdk.internal.api.delegate.ViewTrackingApiDelegate
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.injection.InitializedModuleGraph

/**
 * Assembles a [RealSdkInstance] by wiring each of its API delegates to the services in the [InitializedModuleGraph].
 */
internal fun createRealSdkInstance(
    moduleGraph: InitializedModuleGraph,
    clock: Clock,
): RealSdkInstance {
    val logsApiDelegate = LogsApiDelegate(
        logService = moduleGraph.logService,
        attachmentService = moduleGraph.attachmentService,
        errorHandler = moduleGraph.initModule.errorHandler,
        payloadStore = moduleGraph.payloadStore,
        serializer = moduleGraph.initModule.jsonSerializer,
        telemetryService = moduleGraph.initModule.telemetryService,
    )
    return RealSdkInstance(
        clock = clock,
        errorHandler = moduleGraph.initModule.errorHandler,
        primaryAbi = moduleGraph.initModule.systemInfo.primaryAbi,
        configService = moduleGraph.configService,
        workerThreadPool = moduleGraph.workerThreadPool,
        eventBus = moduleGraph.eventBus,
        appContext = moduleGraph.appContext,
        spanRepository = moduleGraph.spanRepository,
        logSink = moduleGraph.logSink,
        otelSdkConfig = moduleGraph.otelSdkConfig,
        processStateTracker = moduleGraph.processStateTracker,
        sessionPartTracker = moduleGraph.sessionPartTracker,
        networkConnectivityService = moduleGraph.networkConnectivityService,
        telemetryDestination = moduleGraph.telemetryDestination,
        instrumentationRegistry = moduleGraph.instrumentationRegistry,
        instrumentationArgs = moduleGraph.instrumentationArgs,
        sessionOrchestrator = moduleGraph.sessionOrchestrator,
        sessionPartReader = moduleGraph.sessionPartReader,
        logOrchestrator = moduleGraph.logOrchestrator,
        attachmentService = moduleGraph.attachmentService,
        logLimitingService = moduleGraph.logLimitingService,
        metadataService = moduleGraph.metadataService,
        payloadResurrectionService = moduleGraph.payloadResurrectionService,
        payloadCachingService = moduleGraph.payloadCachingService,
        payloadStore = moduleGraph.payloadStore,
        schedulingService = moduleGraph.schedulingService,
        cacheStorageService = moduleGraph.cacheStorageService,
        threadBlockageService = moduleGraph.threadBlockageService,
        lastRunCrashVerifier = moduleGraph.lastRunCrashVerifier,
        crashMarker = moduleGraph.crashMarker,
        startupTracker = moduleGraph.startupTracker,
        startupService = moduleGraph.startupService,
        appStartupDataCollector = moduleGraph.appStartupDataCollector,
        sdkStartTimeMs = moduleGraph.sdkStartTimeMs,
        sdkInitResourceUsageTracker = moduleGraph.sdkInitResourceUsageTracker,
        userApiDelegate = UserApiDelegate(
            userService = moduleGraph.userService,
        ),
        sessionApiDelegate = UserSessionApiDelegate(
            userSessionPropertiesService = moduleGraph.userSessionPropertiesService,
            sessionOrchestrator = moduleGraph.sessionOrchestrator,
        ),
        networkRequestApiDelegate = NetworkRequestApiDelegate(
            registry = moduleGraph.instrumentationRegistry,
            httpRequestInfoModifierChain = moduleGraph.instrumentationArgs.httpRequestInfoModifierChain,
            sessionOrchestrator = moduleGraph.sessionOrchestrator,
        ),
        logsApiDelegate = logsApiDelegate,
        hostedSdkInternalInterface = HostedSdkInternalInterfaceImpl(
            logsApi = logsApiDelegate,
            configService = moduleGraph.configService,
            resourceSource = moduleGraph.resourceSource,
            instrumentationRegistry = moduleGraph.instrumentationRegistry,
            serializer = moduleGraph.initModule.jsonSerializer,
            rnBundleIdTracker = moduleGraph.rnBundleIdTracker,
            hostedSdkVersionInfo = moduleGraph.hostedSdkVersionInfo,
            errorHandler = moduleGraph.initModule.errorHandler,
            telemetryService = moduleGraph.initModule.telemetryService,
        ),
        viewTrackingApiDelegate = ViewTrackingApiDelegate(
            registry = moduleGraph.instrumentationRegistry,
        ),
        sdkStateApiDelegate = SdkStateApiDelegate(
            deviceIdentifier = lazy { moduleGraph.configService.deviceId },
            sessionIdsProvider = moduleGraph.sessionIdsProvider,
            crashVerifier = moduleGraph.lastRunCrashVerifier,
        ),
        otelApiDelegate = OTelApiDelegate(
            otelSdkWrapper = moduleGraph.otelSdkWrapper,
        ),
        breadcrumbApiDelegate = BreadcrumbApiDelegate(
            clock = moduleGraph.initModule.clock,
            breadcrumbDataSource = moduleGraph.breadcrumbDataSource,
        ),
        instrumentationApiDelegate = InstrumentationApiDelegate(
            clock = moduleGraph.initModule.clock,
            uiLoadTraceEmitter = moduleGraph.uiLoadDataListener,
            appStartupDataCollector = moduleGraph.appStartupDataCollector,
            navigationTrackingService = moduleGraph.navigationTrackingService,
        ),
        experimentApiDelegate = ExperimentApiDelegate(
            clock = moduleGraph.initModule.clock,
            experimentTrackingService = moduleGraph.experimentTrackingService,
        ),
        tracingApi = moduleGraph.tracingApi,
    )
}

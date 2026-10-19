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
        logger = moduleGraph.initModule.logger,
        payloadStore = moduleGraph.deliveryModule?.payloadStore,
        serializer = moduleGraph.initModule.jsonSerializer,
        telemetryService = moduleGraph.initModule.telemetryService,
    )
    val deliveryModule = moduleGraph.deliveryModule
    return RealSdkInstance(
        clock = clock,
        logger = moduleGraph.initModule.logger,
        errorHandler = moduleGraph.initModule.errorHandler,
        primaryAbi = moduleGraph.initModule.systemInfo.primaryAbi,
        configService = moduleGraph.configService,
        workerThreadPool = moduleGraph.workerThreadPool,
        appContext = moduleGraph.coreModule.context,
        spanRepository = moduleGraph.openTelemetryModule.spanRepository,
        logSink = moduleGraph.openTelemetryModule.logSink,
        otelSdkConfig = lazy { moduleGraph.openTelemetryModule.otelSdkConfig },
        processStateTracker = moduleGraph.essentialServiceModule.processStateTracker,
        sessionPartTracker = moduleGraph.essentialServiceModule.sessionPartTracker,
        networkConnectivityService = moduleGraph.essentialServiceModule.networkConnectivityService,
        telemetryDestination = moduleGraph.essentialServiceModule.telemetryDestination,
        instrumentationRegistry = moduleGraph.instrumentationModule.instrumentationRegistry,
        instrumentationArgs = moduleGraph.instrumentationModule.instrumentationArgs,
        sessionOrchestrator = moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
        sessionPartReader = moduleGraph.userSessionOrchestrationModule.sessionPartReader,
        logOrchestrator = moduleGraph.logOrchestrator,
        attachmentService = moduleGraph.attachmentService,
        logLimitingService = moduleGraph.logLimitingService,
        metadataService = moduleGraph.payloadSourceModule.metadataService,
        payloadResurrectionService = moduleGraph.payloadSourceModule.payloadResurrectionService,
        payloadCachingService = deliveryModule?.payloadCachingService,
        payloadStore = deliveryModule?.payloadStore,
        schedulingService = deliveryModule?.schedulingService,
        cacheStorageService = deliveryModule?.cacheStorageService,
        threadBlockageService = moduleGraph.threadBlockageService,
        lastRunCrashVerifier = moduleGraph.lastRunCrashVerifier,
        crashMarker = moduleGraph.crashMarker,
        startupTracker = moduleGraph.dataCaptureServiceModule.startupTracker,
        startupService = moduleGraph.dataCaptureServiceModule.startupService,
        appStartupDataCollector = moduleGraph.dataCaptureServiceModule.appStartupDataCollector,
        sdkStartTimeMs = moduleGraph.sdkStartTimeMs,
        sdkInitResourceUsageTracker = moduleGraph.sdkInitResourceUsageTracker,
        userApiDelegate = UserApiDelegate(
            userService = lazy { moduleGraph.essentialServiceModule.userService },
        ),
        sessionApiDelegate = UserSessionApiDelegate(
            userSessionPropertiesService = moduleGraph.essentialServiceModule.userSessionPropertiesService,
            sessionOrchestrator = moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
        ),
        networkRequestApiDelegate = NetworkRequestApiDelegate(
            registry = moduleGraph.instrumentationModule.instrumentationRegistry,
            httpRequestInfoModifierChain = moduleGraph.instrumentationModule.instrumentationArgs.httpRequestInfoModifierChain,
            sessionOrchestrator = moduleGraph.userSessionOrchestrationModule.sessionOrchestrator,
        ),
        logsApiDelegate = logsApiDelegate,
        hostedSdkInternalInterface = HostedSdkInternalInterfaceImpl(
            logsApi = logsApiDelegate,
            configService = moduleGraph.configService,
            resourceSource = moduleGraph.payloadSourceModule.resourceSource,
            instrumentationRegistry = moduleGraph.instrumentationModule.instrumentationRegistry,
            serializer = moduleGraph.initModule.jsonSerializer,
            rnBundleIdTracker = lazy { moduleGraph.payloadSourceModule.rnBundleIdTracker },
            hostedSdkVersionInfo = moduleGraph.payloadSourceModule.hostedSdkVersionInfo,
            logger = moduleGraph.initModule.logger,
            telemetryService = moduleGraph.initModule.telemetryService,
        ),
        viewTrackingApiDelegate = ViewTrackingApiDelegate(
            registry = moduleGraph.instrumentationModule.instrumentationRegistry,
        ),
        sdkStateApiDelegate = SdkStateApiDelegate(
            deviceIdentifier = lazy { moduleGraph.configService.deviceId },
            sessionIdsProvider = moduleGraph.userSessionOrchestrationModule.sessionIdsProvider,
            crashVerifier = moduleGraph.lastRunCrashVerifier,
        ),
        otelApiDelegate = OTelApiDelegate(
            otelSdkWrapper = lazy { moduleGraph.openTelemetryModule.otelSdkWrapper },
        ),
        breadcrumbApiDelegate = BreadcrumbApiDelegate(
            clock = moduleGraph.initModule.clock,
            breadcrumbDataSource = moduleGraph.breadcrumbDataSource,
        ),
        instrumentationApiDelegate = InstrumentationApiDelegate(
            clock = moduleGraph.initModule.clock,
            uiLoadTraceEmitter = moduleGraph.dataCaptureServiceModule.uiLoadDataListener,
            appStartupDataCollector = moduleGraph.dataCaptureServiceModule.appStartupDataCollector,
            navigationTrackingService = moduleGraph.essentialServiceModule.navigationTrackingService,
        ),
        experimentApiDelegate = ExperimentApiDelegate(
            clock = moduleGraph.initModule.clock,
            experimentTrackingService = lazy { moduleGraph.essentialServiceModule.experimentTrackingService },
        ),
        tracingApi = moduleGraph.openTelemetryModule.tracingApi,
    )
}

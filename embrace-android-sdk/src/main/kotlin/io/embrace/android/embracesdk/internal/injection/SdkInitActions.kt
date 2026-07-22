package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.delivery.caching.SessionPartRecorder
import io.embrace.android.embracesdk.internal.envelope.metadata.EnvelopeMetadataSourceImpl
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.EmbTrace.end
import io.embrace.android.embracesdk.internal.utils.EmbTrace.start
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.SessionAttributes
import io.opentelemetry.kotlin.semconv.UserAttributes
import java.util.ServiceLoader

/**
 * Performs bootstrapping by setting required values where there is an interdependency
 * between modules.
 */
internal fun ModuleGraph.postInit() {
    openTelemetryModule.eventService.setMetadataProvider(eventMetadataSupplierProvider())

    openTelemetryModule.applyConfiguration(
        sensitiveKeysBehavior = configService.sensitiveKeysBehavior,
        bypassValidation = configService.isOnlyUsingOtelExporters(),
        otelBehavior = configService.otelBehavior,
    )

    initModule.logger.errorHandlerProvider = { featureModule.internalErrorDataSource.dataSource }

    val sessionPartRecorder = createSessionPartRecorder()
    deliveryModule?.payloadCachingService?.run {
        openTelemetryModule.spanRepository.setSpanUpdateNotifier {
            reportBackgroundActivityStateChange()
            sessionPartRecorder?.onSpanUpdate()
        }
    }
    sessionPartRecorder?.let { recorder ->
        essentialServiceModule.userService.addUserInfoListener(recorder::onUserInfoUpdate)
        essentialServiceModule.sessionPartTracker.addSessionPartEndListener(recorder::onSessionEnd)
    }
    startSessionPartRecording(sessionPartRecorder)

    payloadSourceModule.metadataService.precomputeValues()

    // Start the log orchestrator
    openTelemetryModule.logSink.registerLogStoredCallback {
        logModule.logOrchestrator.onLogsAdded()
    }

    essentialServiceModule.telemetryDestination.sessionUpdateAction =
        userSessionOrchestrationModule.sessionOrchestrator::onSessionDataUpdate
    essentialServiceModule.telemetryDestination.currentStatesProvider =
        instrumentationModule.instrumentationRegistry::getCurrentStates

    openTelemetryModule.setSessionIdsProvider(userSessionOrchestrationModule.sessionIdsProvider)
    openTelemetryModule.setUserIdProvider { essentialServiceModule.userService.getUserInfo().userId }

    // Start the orchestrator and create the first session part once all the module dependencies have been created and wired up
    userSessionOrchestrationModule.sessionOrchestrator.start()
}

/**
 * Registers listeners for various lifecycle/system callbacks.
 */
internal fun ModuleGraph.registerListeners() {
    EmbTrace.trace("service-registration") {
        val ctx = coreModule.application
        ctx.registerActivityLifecycleCallbacks(dataCaptureServiceModule.startupTracker)

        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
            essentialServiceModule.networkConnectivityService.register()
        }

        val sessionPartTracker = essentialServiceModule.sessionPartTracker
        val appStateTracker = essentialServiceModule.appStateTracker

        sessionPartTracker.addSessionPartChangeListener {
            configService.networkBehavior.domainCountLimiter.reset()
        }

        appStateTracker.addListener(dataCaptureServiceModule.appStartupDataCollector)

        threadBlockageService?.let {
            appStateTracker.addListener(it)
            sessionPartTracker.addSessionPartChangeListener(it)
        }

        sessionPartTracker.addSessionPartChangeListener(logModule.attachmentService)
        sessionPartTracker.addSessionPartChangeListener(logModule.logLimitingService)
    }
}

/**
 * Loads instrumentation via SPI and legacy methods.
 */
internal fun ModuleGraph.loadInstrumentation() {
    val registry = instrumentationModule.instrumentationRegistry
    val instrumentationProviders = ServiceLoader.load(InstrumentationProvider::class.java)
    registry.loadInstrumentations(instrumentationProviders, instrumentationModule.instrumentationArgs)

    threadBlockageService?.startCapture()

    featureModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
        workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
    )
}

/**
 * Performs post-load instrumentation tasks such as setting listeners.
 */
internal fun ModuleGraph.postLoadInstrumentation() {
    // setup crash teardown handlers
    val registry = instrumentationModule.instrumentationRegistry
    registry.findByType(JvmCrashDataSource::class)?.apply {
        threadBlockageService?.let(::addCrashTeardownHandler)
        addCrashTeardownHandler(logModule.logOrchestrator)
        addCrashTeardownHandler(userSessionOrchestrationModule.sessionOrchestrator)
        addCrashTeardownHandler(featureModule.crashMarker)
        deliveryModule?.payloadStore?.let(::addCrashTeardownHandler)
    }
    registry.findByType(NetworkStatusDataSource::class)?.let {
        essentialServiceModule.networkConnectivityService.addNetworkConnectivityListener(it)
    }
    registry.findByType(NetworkStateDataSource::class)?.let {
        essentialServiceModule.networkConnectivityService.addNetworkConnectivityListener(it)
    }
}

/**
 * Trigger sending cached data on disk.
 */
internal fun ModuleGraph.triggerPayloadSend() {
    val worker = workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker)
    worker.submit {
        val resurrectionService = payloadSourceModule.payloadResurrectionService
        var resurrectionAttempted = false
        if (resurrectionService != null) {
            deliveryModule?.schedulingService?.let { scheduler ->
                resurrectionService.addResurrectionCompleteListener(scheduler::onResurrectionComplete)
            }
            resurrectionService.resurrectOldPayloads(
                nativeCrashServiceProvider = {
                    instrumentationModule.instrumentationRegistry.findByType(NativeCrashDataSource::class)
                },
                userSessionRestoreDecisionProvider = {
                    userSessionOrchestrationModule.sessionOrchestrator.userSessionRestoreDecision
                },
            )
            resurrectionAttempted = true
        } else {
            val payloadCount = deliveryModule?.cacheStorageService?.getUndeliveredPayloads()?.size ?: 0
            initModule.logger.trackInternalError(
                type = InternalErrorType.PayloadResurrectionFail,
                throwable = IllegalStateException(
                    "Resurrection service not found. Undelivered payloads not processed: $payloadCount",
                ),
            )
        }

        // Unblock scheduler if no resurrection was attempted
        if (!resurrectionAttempted) {
            deliveryModule?.schedulingService?.onResurrectionComplete()
        }
    }
    worker.submit { // potentially trigger first delivery attempt by firing network status callback
        deliveryModule?.schedulingService?.let(
            essentialServiceModule.networkConnectivityService::addNetworkConnectivityListener,
        )
        deliveryModule?.schedulingService?.onPayloadIntake()
    }
}

/**
 * Mark SDK initialization as complete.
 */
internal fun ModuleGraph.markSdkInitComplete() {
    start("startup-tracking")
    dataCaptureServiceModule.startupService.setSdkStartupInfo(
        coreModule.sdkStartTime,
        initModule.clock.now(),
        essentialServiceModule.appStateTracker.getAppState(),
        Thread.currentThread().name,
    )
    end()
    val appId = configService.appId
    val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" +
        (appId?.let { " for appId = $it" } ?: " without an app ID")
    initModule.logger.logInfo(startMsg)
}

/**
 * Builds the recorder that mirrors each session part into a directory of Wire files, or null if
 * there is no delivery module (e.g. OTel-export-only configurations).
 */
private fun ModuleGraph.createSessionPartRecorder(): SessionPartRecorder? {
    val store = deliveryModule?.sessionPartStore ?: return null
    val metadataSource = EnvelopeMetadataSourceImpl(essentialServiceModule.userService::getUserInfo)
    return SessionPartRecorder(
        store = store,
        spanRepository = openTelemetryModule.spanRepository,
        spanSink = openTelemetryModule.spanSink,
        resourceProvider = payloadSourceModule.resourceSource::getEnvelopeResource,
        metadataProvider = metadataSource::getEnvelopeMetadata,
        sessionIdsProvider = essentialServiceModule.sessionIdsProvider::getActiveSessionIds,
        clock = initModule.clock,
        uuidProvider = initModule.uuidSource::createUuid,
        symbolMapProvider = configService::nativeSymbolMap,
    )
}

/**
 * Recovers any session-part directories left on disk by a previous process. The list is captured
 * before the first session part of this process is created so the current part is never treated as
 * recoverable.
 */
private fun ModuleGraph.startSessionPartRecording(recorder: SessionPartRecorder?) {
    val store = deliveryModule?.sessionPartStore
    if (recorder == null || store == null) return

    val leftovers = store.incompleteDirectories()
    workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker).submit {
        recorder.resurrect(leftovers)
    }
}

private fun ModuleGraph.eventMetadataSupplierProvider(): Provider<Map<String, String>> {
    return {
        mutableMapOf<String, String>().apply {
            val sessionPart = essentialServiceModule.sessionPartTracker.getActiveSessionPart()
            val sessionState = sessionPart?.appState ?: essentialServiceModule.appStateTracker.getAppState()
            val sessionIds = userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds()

            put(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionIds.sessionPartId)
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, sessionIds.userSessionId)
            put(SessionAttributes.SESSION_ID, sessionIds.userSessionId)
            put(EmbSessionAttributes.EMB_STATE, sessionState.description)
            essentialServiceModule.userService.getUserInfo().userId?.let {
                put(UserAttributes.USER_ID, it)
            }
            putAll(
                essentialServiceModule.userSessionPropertiesService
                    .getProperties()
                    .mapKeys { property ->
                        property.key.toEmbraceAttributeName()
                    },
            )
            instrumentationModule.instrumentationRegistry.getCurrentStates().forEach {
                put(it.key, it.value.toString())
            }
        }
    }
}

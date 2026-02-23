package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.attrs.embState
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.arch.state.AppState
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.EmbTrace.end
import io.embrace.android.embracesdk.internal.utils.EmbTrace.start
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.opentelemetry.kotlin.semconv.IncubatingApi
import io.opentelemetry.kotlin.semconv.SessionAttributes
import java.util.ServiceLoader

/**
 * Performs bootstrapping by setting required values where there is an interdependency
 * between modules.
 */
internal fun ModuleGraph.postInit() {
    openTelemetryModule.applyConfiguration(
        sensitiveKeysBehavior = configService.sensitiveKeysBehavior,
        bypassValidation = configService.isOnlyUsingOtelExporters(),
        otelBehavior = configService.otelBehavior
    )

    initModule.logger.errorHandlerProvider = { featureModule.internalErrorDataSource.dataSource }
    deliveryModule?.payloadCachingService?.run {
        openTelemetryModule.spanRepository.setSpanUpdateNotifier {
            reportBackgroundActivityStateChange()
        }
    }

    payloadSourceModule.metadataService.precomputeValues()

    // Start the log orchestrator
    openTelemetryModule.logSink.registerLogStoredCallback {
        logModule.logOrchestrator.onLogsAdded()
    }

    essentialServiceModule.telemetryDestination.sessionUpdateAction = sessionOrchestrator::onSessionDataUpdate
    essentialServiceModule.telemetryDestination.currentStatesProvider =
        instrumentationModule.instrumentationRegistry::getCurrentStates
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

        val sessionTracker = essentialServiceModule.sessionTracker
        val appStateTracker = essentialServiceModule.appStateTracker

        sessionTracker.addSessionChangeListener {
            configService.networkBehavior.domainCountLimiter.reset()
        }

        appStateTracker.addListener(dataCaptureServiceModule.appStartupDataCollector)

        threadBlockageService?.let {
            appStateTracker.addListener(it)
            sessionTracker.addSessionChangeListener(it)
        }

        sessionTracker.addSessionChangeListener(logModule.attachmentService)
        sessionTracker.addSessionChangeListener(logModule.logLimitingService)
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
        workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker)
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
        addCrashTeardownHandler(sessionOrchestrator)
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
        instrumentationModule.instrumentationRegistry.findByType(NativeCrashDataSource::class)?.let {
            payloadSourceModule.payloadResurrectionService?.resurrectOldPayloads(
                nativeCrashServiceProvider = { it }
            )
        }
    }
    worker.submit { // potentially trigger first delivery attempt by firing network status callback
        deliveryModule?.schedulingService?.let(
            essentialServiceModule.networkConnectivityService::addNetworkConnectivityListener
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
        Thread.currentThread().name
    )
    end()
    val appId = configService.appId
    val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" + appId?.run { " for appId = $this" }
    initModule.logger.logInfo(startMsg)
}

/**
 * Set the provider of metadata for the event service
 */
internal fun ModuleGraph.setupMetadataProvider() {
    openTelemetryModule.eventService.setMetadataProvider(eventMetadataSupplierProvider())
}

@OptIn(IncubatingApi::class)
private fun ModuleGraph.eventMetadataSupplierProvider(): Provider<Map<String, String>> {
    return {
        mutableMapOf<String, String>().apply {
            var sessionState: AppState? = null
            essentialServiceModule.sessionTracker.getActiveSession()?.let { session ->
                if (session.sessionId.isNotBlank()) {
                    put(SessionAttributes.SESSION_ID, session.sessionId)
                }
                sessionState = session.appState
            }
            val state = sessionState ?: essentialServiceModule.appStateTracker.getAppState()
            put(embState.name, state.description)
            putAll(
                essentialServiceModule.sessionPropertiesService
                    .getProperties()
                    .mapKeys { property ->
                        property.key.toEmbraceAttributeName()
                    }
            )
            instrumentationModule.instrumentationRegistry.getCurrentStates().forEach {
                put(it.key.name, it.value.toString())
            }
        }
    }
}

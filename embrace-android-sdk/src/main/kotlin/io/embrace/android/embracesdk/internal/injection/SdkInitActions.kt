package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.EmbTrace.end
import io.embrace.android.embracesdk.internal.utils.EmbTrace.start
import io.embrace.android.embracesdk.internal.worker.Worker
import java.util.ServiceLoader

/**
 * Performs bootstrapping by setting required values where there is an interdependency
 * between modules.
 */
internal fun ModuleGraph.postInit() {
    openTelemetryModule.applyConfiguration(
        sensitiveKeysBehavior = configModule.configService.sensitiveKeysBehavior,
        bypassValidation = configModule.configService.isOnlyUsingOtelExporters(),
        otelBehavior = configModule.configService.otelBehavior
    )

    initModule.logger.errorHandlerProvider = { featureModule.internalErrorDataSource.dataSource }
    deliveryModule.payloadCachingService?.run {
        openTelemetryModule.spanRepository.setSpanUpdateNotifier {
            reportBackgroundActivityStateChange()
        }
    }

    payloadSourceModule.metadataService.precomputeValues()

    // Start the log orchestrator
    openTelemetryModule.logSink.registerLogStoredCallback {
        logModule.logOrchestrator.onLogsAdded()
    }

    essentialServiceModule.telemetryDestination.sessionUpdateAction =
        sessionOrchestrationModule.sessionOrchestrator::onSessionDataUpdate
}

/**
 * Registers listeners for various lifecycle/system callbacks.
 */
internal fun ModuleGraph.registerListeners() {
    EmbTrace.trace("service-registration") {
        val ctx = coreModule.application
        ctx.registerActivityLifecycleCallbacks(dataCaptureServiceModule.startupTracker)
        ctx.registerActivityLifecycleCallbacks(essentialServiceModule.activityLifecycleTracker)

        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
            essentialServiceModule.networkConnectivityService.register()
        }
        with(coreModule.serviceRegistry) {
            registerService(lazy { configModule.configService.networkBehavior.domainCountLimiter })

            registerServices(
                lazy { essentialServiceModule.networkConnectivityService }
            )
            registerServices(
                lazy { dataCaptureServiceModule.appStartupDataCollector },
            )
            registerServices(
                lazy { threadBlockageService }
            )
            registerService(lazy { logModule.attachmentService })
            registerService(lazy { logModule.logService })

            // registration ignored after this point
            registerAppStateListeners(essentialServiceModule.appStateTracker)
            registerMemoryCleanerListeners(sessionOrchestrationModule.memoryCleanerService)
        }
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
        addCrashTeardownHandler(sessionOrchestrationModule.sessionOrchestrator)
        addCrashTeardownHandler(featureModule.crashMarker)
        deliveryModule.payloadStore?.let(::addCrashTeardownHandler)
    }
    registry.findByType(NetworkStatusDataSource::class)?.let {
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
        deliveryModule.schedulingService?.let(
            essentialServiceModule.networkConnectivityService::addNetworkConnectivityListener
        )
        deliveryModule.schedulingService?.onPayloadIntake()
    }
}

/**
 * Mark SDK initialization as complete.
 */
internal fun ModuleGraph.markSdkInitComplete() {
    start("startup-tracking")
    val dataCaptureServiceModule = dataCaptureServiceModule
    dataCaptureServiceModule.startupService.setSdkStartupInfo(
        coreModule.sdkStartTime,
        initModule.clock.now(),
        essentialServiceModule.appStateTracker.getAppState(),
        Thread.currentThread().name
    )
    end()
    val appId = configModule.configService.appId
    val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" + appId?.run { " for appId =  $this" }
    initModule.logger.logInfo(startMsg)
}

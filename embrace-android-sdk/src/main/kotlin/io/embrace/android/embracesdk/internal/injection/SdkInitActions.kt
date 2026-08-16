package io.embrace.android.embracesdk.internal.injection

import android.content.Context
import io.embrace.android.embracesdk.core.BuildConfig
import io.embrace.android.embracesdk.internal.arch.InstrumentationProvider
import io.embrace.android.embracesdk.internal.arch.attrs.toEmbraceAttributeName
import io.embrace.android.embracesdk.internal.instrumentation.crash.jvm.JvmCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.crash.ndk.NativeCrashDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStateDataSource
import io.embrace.android.embracesdk.internal.instrumentation.network.NetworkStatusDataSource
import io.embrace.android.embracesdk.internal.instrumentation.startup.sdkInitEnvironmentAttributes
import io.embrace.android.embracesdk.internal.instrumentation.startup.toSdkInitDurationAttributes
import io.embrace.android.embracesdk.internal.logging.InternalErrorType
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.Provider
import io.embrace.android.embracesdk.internal.worker.Worker
import io.embrace.android.embracesdk.semconv.EmbSessionAttributes
import io.opentelemetry.kotlin.semconv.UserAttributes
import java.io.File
import java.util.ServiceLoader
import java.util.concurrent.TimeUnit

/**
 * Performs bootstrapping by setting required values where there is an interdependency
 * between modules.
 */
internal fun ModuleGraph.postInit() = EmbTrace.trace(sectionName = "post-init", recordDuration = true) {
    openTelemetryModule.setEventMetadataProvider(eventMetadataSupplierProvider())

    // note: otelBehavior is not applied here - it decides which OTel SDK is built, so it is set
    // right after the persisted config is read in ModuleInitBootstrapper.init.
    openTelemetryModule.applyConfiguration(
        sensitiveKeysBehavior = configService.sensitiveKeysBehavior,
        bypassValidation = configService.isOnlyUsingOtelExporters(),
        otelBehavior = configService.otelBehavior,
        breadcrumbBehavior = configService.breadcrumbBehavior,
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

        // periodically fail any in-flight spans that have exceeded their timeout, so leaked spans
        // are terminated and their memory released even during a long-running session.
        val spanRepository = openTelemetryModule.spanRepository
        val clock = initModule.clock
        val logger = initModule.logger
        workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).scheduleWithFixedDelay(
            {
                try {
                    spanRepository.stopTimedOutSpans(clock.now())
                } catch (exc: Throwable) {
                    logger.trackInternalError(InternalErrorType.SpanTimeoutSweepFail, exc)
                }
            },
            SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
            SPAN_TIMEOUT_SWEEP_INTERVAL_MS,
            TimeUnit.MILLISECONDS,
        )

        val sessionPartTracker = essentialServiceModule.sessionPartTracker
        val appStateTracker = essentialServiceModule.processStateTracker

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
internal fun ModuleGraph.loadInstrumentation() =
    EmbTrace.trace(sectionName = "load-instrumentation", recordDuration = true) {
        val registry = instrumentationModule.instrumentationRegistry
        registry.loadInstrumentations(loadInstrumentationProviders(), instrumentationModule.instrumentationArgs)

        threadBlockageService?.startCapture()

        featureModule.lastRunCrashVerifier.readAndCleanMarkerAsync(
            workerThreadModule.backgroundWorker(Worker.Background.IoRegWorker),
        )
    }

/**
 * Loads the [InstrumentationProvider] implementations declared via SPI. Before making changes
 * to this function please study R8, as currently it optimizes out lookup and reflection from
 * the startup path.
 *
 * See R8 for further details on how ServiceLoaderRewriter optimizes SPI:
 * https://r8.googlesource.com/r8/+/refs/heads/main/src/main/java/com/android/tools/r8/ir/optimize/ServiceLoaderRewriter.java
 */
private fun loadInstrumentationProviders(): List<InstrumentationProvider> {
    val providers = mutableListOf<InstrumentationProvider>()
    for (provider in ServiceLoader.load(InstrumentationProvider::class.java, InstrumentationProvider::class.java.classLoader)) {
        providers.add(provider)
    }
    return providers
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
    workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
        with(essentialServiceModule.networkConnectivityService) {
            registry.findByType(NetworkStatusDataSource::class)?.let(::addNetworkConnectivityListener)
            registry.findByType(NetworkStateDataSource::class)?.let(::addNetworkConnectivityListener)
            register()
        }
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
internal fun ModuleGraph.markSdkInitComplete(sdkInitDurations: Map<String, Long>) {
    val startupService = dataCaptureServiceModule.startupService
    EmbTrace.trace("startup-tracking") {
        val resourceUsageTracker = sdkInitResourceUsageTracker
        resourceUsageTracker.captureEnd()
        startupService.setSdkStartupInfo(
            startTimeMs = sdkStartTimeMs,
            endTimeMs = initModule.clock.now(),
            endState = essentialServiceModule.processStateTracker.getAppState(),
            threadName = Thread.currentThread().name,
            attributesProvider = {
                sdkInitDurations.toSdkInitDurationAttributes() +
                    resourceUsageTracker.buildAttributes() +
                    sdkInitEnvironmentAttributes(
                        activityManagerProvider = {
                            instrumentationModule.instrumentationArgs.systemService(Context.ACTIVITY_SERVICE)
                        },
                        powerManagerProvider = {
                            instrumentationModule.instrumentationArgs.systemService(Context.POWER_SERVICE)
                        },
                        packageInfo = instrumentationModule.instrumentationArgs.packageInfo,
                        nowMs = initModule.clock.now(),
                        prefsFileSizeProvider = { defaultPrefsFile(coreModule.context)?.length() },
                    )
            },
        )
    }
    workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker).submit {
        startupService.recordSdkInitSpan()
    }
    val appId = configService.appId
    val startMsg = "Embrace SDK version ${BuildConfig.VERSION_NAME} started" +
        (appId?.let { " for appId = $it" } ?: " without an app ID")
    initModule.logger.logInfo(startMsg)
}

private fun ModuleGraph.eventMetadataSupplierProvider(): Provider<Map<String, String>> {
    return {
        mutableMapOf<String, String>().apply {
            val sessionPart = essentialServiceModule.sessionPartTracker.getActiveSessionPart()
            val sessionState = sessionPart?.processState ?: essentialServiceModule.processStateTracker.getAppState()
            val sessionIds = userSessionOrchestrationModule.sessionIdsProvider.getActiveSessionIds()

            put(EmbSessionAttributes.EMB_SESSION_PART_ID, sessionIds.sessionPartId)
            put(EmbSessionAttributes.EMB_USER_SESSION_ID, sessionIds.userSessionId)
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

private const val SPAN_TIMEOUT_SWEEP_INTERVAL_MS = 30_000L

/**
 * Attempt to identify the size of the host app's default `SharedPreferences` file without actually opening it.
 * This file currently hosts the SDK's key-value store, and the bigger it is, the more impact it will have on
 * SDK init time if the SDK is the thing that opens it first.
 *
 * Returns null if the file is absent, so a missing file is not reported.
 */
private fun defaultPrefsFile(context: Context): File? =
    File(File(context.applicationInfo.dataDir, "shared_prefs"), "${context.packageName}_preferences.xml")
        .takeIf(File::exists)

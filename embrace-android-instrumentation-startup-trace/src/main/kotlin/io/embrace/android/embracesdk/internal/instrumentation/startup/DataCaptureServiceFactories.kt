package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.arch.startup.StartupClassifier
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.createActivityLoadEventEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.createDrawEventEmitter
import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.BackgroundWorker

fun createStartupService(
    destination: TelemetryDestination,
    errorHandler: ErrorHandler,
    appVersionStartupCounterProvider: () -> Int?,
): StartupService = StartupServiceImpl(
    destination = destination,
    errorHandler = errorHandler,
    appVersionStartupCounterProvider = appVersionStartupCounterProvider,
)

fun createAppStartupDataCollector(
    clock: Clock,
    errorHandler: ErrorHandler,
    destination: TelemetryDestination,
    configService: ConfigService,
    startupService: StartupService,
    startupClassifier: StartupClassifier,
    versionChecker: VersionChecker = BuildVersionChecker,
    activityManager: ActivityManager? = null,
    backgroundWorker: BackgroundWorker? = null,
): AppStartupDataCollector {
    val processInfo = EmbTrace.trace("process-info-init") {
        ProcessInfoImpl(
            deviceStartTimeMs = clock.now() - SystemClock.elapsedRealtime(),
            versionChecker = versionChecker,
            activityManager = activityManager,
        ).apply {
            // resolve the launch reason as close to the process starting as we can, but off the thread the app is starting up on
            if (backgroundWorker != null) {
                backgroundWorker.submit { prefetchLaunchReason() }
            } else {
                prefetchLaunchReason()
            }
        }
    }
    return AppStartupTraceEmitter(
        clock = clock,
        startupServiceProvider = { startupService },
        destination = destination,
        versionChecker = versionChecker,
        errorHandler = errorHandler,
        startupClassifier = startupClassifier,
        manualEnd = configService.config.autoDataCapture.endStartupWithAppReadyEnabled,
        processInfo = processInfo,
    )
}

fun createUiLoadDataListener(
    destination: TelemetryDestination,
    configService: ConfigService,
    versionChecker: VersionChecker = BuildVersionChecker,
): UiLoadDataListener? = if (configService.config.autoDataCapture.uiLoadTracingEnabled) {
    UiLoadTraceEmitter(
        destination = destination,
        versionChecker = versionChecker,
    )
} else {
    null
}

fun createStartupTracker(
    clock: Clock,
    errorHandler: ErrorHandler,
    configService: ConfigService,
    appStartupDataCollector: AppStartupDataCollector,
    uiLoadDataListener: UiLoadDataListener?,
    versionChecker: VersionChecker = BuildVersionChecker,
): StartupTracker {
    val activityLoadEventEmitter = EmbTrace.trace("activity-load-event-emitter-init") {
        uiLoadDataListener?.let { uiLoadEventListener ->
            createActivityLoadEventEmitter(
                uiLoadEventListener = uiLoadEventListener,
                firstDrawDetector = createDrawEventEmitter(versionChecker, errorHandler),
                autoTraceEnabled = configService.config.autoDataCapture.uiLoadTracingTraceAll,
                clock = clock,
                versionChecker = versionChecker,
                errorHandler = errorHandler,
            )
        }
    }
    return StartupTracker(
        appStartupDataCollector = appStartupDataCollector,
        activityLoadEventEmitter = activityLoadEventEmitter,
        drawEventEmitter = createDrawEventEmitter(versionChecker, errorHandler),
        errorHandler = errorHandler,
    )
}

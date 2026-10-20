package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.ActivityManager
import android.app.Application
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

class DataCaptureServiceModuleImpl(
    clock: Clock,
    errorHandler: ErrorHandler,
    destination: TelemetryDestination,
    configService: ConfigService,
    appVersionStartupCounterProvider: () -> Int?,
    private val startupClassifier: StartupClassifier,
    versionChecker: VersionChecker = BuildVersionChecker,
    activityManager: ActivityManager? = null,
    backgroundWorker: BackgroundWorker? = null,
) : DataCaptureServiceModule {

    override val startupService: StartupService = EmbTrace.trace("startup-service-init") {
        StartupServiceImpl(
            destination = destination,
            appVersionStartupCounterProvider = appVersionStartupCounterProvider,
        )
    }

    private val processInfo: ProcessInfo = EmbTrace.trace("process-info-init") {
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

    override val appStartupDataCollector: AppStartupDataCollector = EmbTrace.trace("app-startup-data-collector-init") {
        AppStartupTraceEmitter(
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

    override val uiLoadDataListener: UiLoadDataListener? = EmbTrace.trace("ui-load-data-listener-init") {
        if (configService.config.autoDataCapture.uiLoadTracingEnabled) {
            UiLoadTraceEmitter(
                destination = destination,
                versionChecker = versionChecker,
            )
        } else {
            null
        }
    }

    override val activityLoadEventEmitter: Application.ActivityLifecycleCallbacks? = EmbTrace.trace("activity-load-event-emitter-init") {
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

    override val startupTracker: StartupTracker = EmbTrace.trace("startup-tracker-init") {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            activityLoadEventEmitter = activityLoadEventEmitter,
            drawEventEmitter = createDrawEventEmitter(versionChecker, errorHandler),
            errorHandler = errorHandler,
        )
    }
}

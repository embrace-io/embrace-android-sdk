package io.embrace.android.embracesdk.internal.instrumentation.startup

import android.app.Application
import android.os.SystemClock
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.clock.Clock
import io.embrace.android.embracesdk.internal.clock.millisToNanos
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.createActivityLoadEventEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.createDrawEventEmitter
import io.embrace.android.embracesdk.internal.logging.EmbLogger
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

class DataCaptureServiceModuleImpl(
    clock: Clock,
    logger: EmbLogger,
    destination: TelemetryDestination,
    configService: ConfigService,
    versionChecker: VersionChecker = BuildVersionChecker,
) : DataCaptureServiceModule {

    override val startupService: StartupService by lazy {
        StartupServiceImpl(destination)
    }

    override val appStartupDataCollector: AppStartupDataCollector by lazy {
        val deviceStartTimeMs = (clock.now().millisToNanos() - SystemClock.elapsedRealtimeNanos()).millisToNanos()

        AppStartupTraceEmitter(
            clock = clock,
            startupServiceProvider = { startupService },
            destination = destination,
            versionChecker = versionChecker,
            logger = logger,
            manualEnd = configService.autoDataCaptureBehavior.isEndStartupWithAppReadyEnabled(),
            processInfo = ProcessInfoImpl(
                deviceStartTimeMs = deviceStartTimeMs,
                versionChecker = versionChecker,
            )
        )
    }

    override val startupTracker: StartupTracker by lazy {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            activityLoadEventEmitter = activityLoadEventEmitter,
            drawEventEmitter = createDrawEventEmitter(versionChecker, logger)
        )
    }

    override val uiLoadDataListener: UiLoadDataListener? by lazy {
        if (configService.autoDataCaptureBehavior.isUiLoadTracingEnabled()) {
            UiLoadTraceEmitter(
                destination = destination,
                versionChecker = versionChecker,
            )
        } else {
            null
        }
    }

    override val activityLoadEventEmitter: Application.ActivityLifecycleCallbacks? by lazy {
        val uiLoadEventListener = uiLoadDataListener
        if (uiLoadEventListener != null) {
            createActivityLoadEventEmitter(
                uiLoadEventListener = uiLoadEventListener,
                firstDrawDetector = createDrawEventEmitter(versionChecker, logger),
                autoTraceEnabled = configService.autoDataCaptureBehavior.isUiLoadTracingTraceAll(),
                clock = clock,
                versionChecker = versionChecker
            )
        } else {
            null
        }
    }
}

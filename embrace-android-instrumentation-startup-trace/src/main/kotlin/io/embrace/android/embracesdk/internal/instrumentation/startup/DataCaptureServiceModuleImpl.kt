package io.embrace.android.embracesdk.internal.instrumentation.startup

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
import io.embrace.android.embracesdk.internal.logging.InternalLogger
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

class DataCaptureServiceModuleImpl(
    clock: Clock,
    logger: InternalLogger,
    destination: TelemetryDestination,
    configService: ConfigService,
    appVersionStartupCounterProvider: () -> Int?,
    private val startupClassifier: StartupClassifier,
    versionChecker: VersionChecker = BuildVersionChecker,
) : DataCaptureServiceModule {

    override val startupService: StartupService = StartupServiceImpl(
        destination = destination,
        appVersionStartupCounterProvider = appVersionStartupCounterProvider,
    )

    override val appStartupDataCollector: AppStartupDataCollector = AppStartupTraceEmitter(
        clock = clock,
        startupServiceProvider = { startupService },
        destination = destination,
        versionChecker = versionChecker,
        logger = logger,
        startupClassifier = startupClassifier,
        manualEnd = configService.autoDataCaptureBehavior.isEndStartupWithAppReadyEnabled(),
        processInfo = ProcessInfoImpl(
            deviceStartTimeMs = clock.now() - SystemClock.elapsedRealtime(),
            versionChecker = versionChecker,
        ),
    )

    override val uiLoadDataListener: UiLoadDataListener? =
        if (configService.autoDataCaptureBehavior.isUiLoadTracingEnabled()) {
            UiLoadTraceEmitter(
                destination = destination,
                versionChecker = versionChecker,
            )
        } else {
            null
        }

    override val activityLoadEventEmitter: Application.ActivityLifecycleCallbacks? =
        uiLoadDataListener?.let { uiLoadEventListener ->
            createActivityLoadEventEmitter(
                uiLoadEventListener = uiLoadEventListener,
                firstDrawDetector = createDrawEventEmitter(versionChecker, logger),
                autoTraceEnabled = configService.autoDataCaptureBehavior.isUiLoadTracingTraceAll(),
                clock = clock,
                versionChecker = versionChecker,
            )
        }

    override val startupTracker: StartupTracker = StartupTracker(
        appStartupDataCollector = appStartupDataCollector,
        activityLoadEventEmitter = activityLoadEventEmitter,
        drawEventEmitter = createDrawEventEmitter(versionChecker, logger),
    )
}

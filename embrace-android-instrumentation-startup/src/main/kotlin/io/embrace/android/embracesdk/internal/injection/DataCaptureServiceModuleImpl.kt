package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.ProcessInfoImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupService
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.createActivityLoadEventEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.createDrawEventEmitter
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class DataCaptureServiceModuleImpl(
    args: InstrumentationArgs,
    versionChecker: VersionChecker = BuildVersionChecker,
) : DataCaptureServiceModule {

    override val startupService: StartupService by lazy {
        StartupServiceImpl(
            spanService = openTelemetryModule.spanService
        )
    }

    override val appStartupDataCollector: AppStartupDataCollector by lazy {
        AppStartupTraceEmitter(
            clock = args.clock,
            startupServiceProvider = { startupService },
            spanService = openTelemetryModule.spanService,
            versionChecker = versionChecker,
            logger = args.logger,
            manualEnd = args.configService.autoDataCaptureBehavior.isEndStartupWithAppReadyEnabled(),
            processInfo = ProcessInfoImpl(
                deviceStartTimeMs = openTelemetryModule.deviceStartTimeMs(),
                versionChecker = versionChecker,
            )
        )
    }

    override val startupTracker: StartupTracker by lazy {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            activityLoadEventEmitter = activityLoadEventEmitter,
            drawEventEmitter = createDrawEventEmitter(versionChecker, args.logger)
        )
    }

    override val uiLoadDataListener: UiLoadDataListener? by lazy {
        if (args.configService.autoDataCaptureBehavior.isUiLoadTracingEnabled()) {
            UiLoadTraceEmitter(
                spanService = openTelemetryModule.spanService,
                versionChecker = versionChecker,
            )
        } else {
            null
        }
    }

    override val activityLoadEventEmitter: ActivityLifecycleListener? by lazy {
        val uiLoadEventListener = uiLoadDataListener
        if (uiLoadEventListener != null) {
            createActivityLoadEventEmitter(
                uiLoadEventListener = uiLoadEventListener,
                firstDrawDetector = createDrawEventEmitter(versionChecker, args.logger),
                autoTraceEnabled = args.configService.autoDataCaptureBehavior.isUiLoadTracingTraceAll(),
                clock = args.clock,
                versionChecker = versionChecker
            )
        } else {
            null
        }
    }
}

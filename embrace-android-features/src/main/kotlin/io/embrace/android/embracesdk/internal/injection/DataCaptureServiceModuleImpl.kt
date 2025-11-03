package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.instrumentation.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.StartupTracker
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.UiLoadTraceEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.activity.createActivityLoadEventEmitter
import io.embrace.android.embracesdk.internal.instrumentation.startup.ui.createDrawEventEmitter
import io.embrace.android.embracesdk.internal.process.ProcessInfoImpl
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class DataCaptureServiceModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    versionChecker: VersionChecker = BuildVersionChecker,
) : DataCaptureServiceModule {

    override val startupService: StartupService by singleton {
        StartupServiceImpl(
            spanService = openTelemetryModule.spanService
        )
    }

    override val appStartupDataCollector: AppStartupDataCollector by singleton {
        AppStartupTraceEmitter(
            clock = initModule.clock,
            startupServiceProvider = { startupService },
            spanService = openTelemetryModule.spanService,
            versionChecker = versionChecker,
            logger = initModule.logger,
            manualEnd = configService.autoDataCaptureBehavior.isEndStartupWithAppReadyEnabled(),
            processInfo = ProcessInfoImpl(
                deviceStartTimeMs = openTelemetryModule.deviceStartTimeMs(),
                versionChecker = versionChecker,
            )
        )
    }

    override val startupTracker: StartupTracker by singleton {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            activityLoadEventEmitter = activityLoadEventEmitter,
            drawEventEmitter = createDrawEventEmitter(versionChecker, initModule.logger)
        )
    }

    override val uiLoadDataListener: UiLoadDataListener? by singleton {
        if (configService.autoDataCaptureBehavior.isUiLoadTracingEnabled()) {
            UiLoadTraceEmitter(
                spanService = openTelemetryModule.spanService,
                versionChecker = versionChecker,
            )
        } else {
            null
        }
    }

    override val activityLoadEventEmitter: ActivityLifecycleListener? by singleton {
        val uiLoadEventListener = uiLoadDataListener
        if (uiLoadEventListener != null) {
            createActivityLoadEventEmitter(
                uiLoadEventListener = uiLoadEventListener,
                firstDrawDetector = createDrawEventEmitter(versionChecker, initModule.logger),
                autoTraceEnabled = configService.autoDataCaptureBehavior.isUiLoadTracingTraceAll(),
                clock = initModule.clock,
                versionChecker = versionChecker
            )
        } else {
            null
        }
    }
}

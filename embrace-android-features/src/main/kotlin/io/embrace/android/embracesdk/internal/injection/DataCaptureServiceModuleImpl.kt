package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.capture.activity.UiLoadDataListener
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadTraceEmitter
import io.embrace.android.embracesdk.internal.capture.activity.createActivityLoadEventEmitter
import io.embrace.android.embracesdk.internal.capture.crumbs.ActivityBreadcrumbTracker
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.session.lifecycle.ActivityLifecycleListener
import io.embrace.android.embracesdk.internal.ui.createDrawEventEmitter
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.EmbTrace
import io.embrace.android.embracesdk.internal.utils.VersionChecker

internal class DataCaptureServiceModuleImpl @JvmOverloads constructor(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    versionChecker: VersionChecker = BuildVersionChecker,
    featureModule: FeatureModule,
) : DataCaptureServiceModule {

    override val webviewService: WebViewService by singleton {
        EmbraceWebViewService(
            configService,
            initModule.jsonSerializer,
            initModule.logger,
        ) { featureModule.webViewDataSource.dataSource }
    }

    override val activityBreadcrumbTracker: ActivityBreadcrumbTracker by singleton {
        EmbTrace.trace("breadcrumb-service-init") {
            ActivityBreadcrumbTracker(configService) { featureModule.viewDataSource.dataSource }
        }
    }

    override val pushNotificationService: PushNotificationCaptureService by singleton {
        PushNotificationCaptureService(featureModule.pushNotificationDataSource.dataSource)
    }

    override val startupService: StartupService by singleton {
        StartupServiceImpl(
            spanService = openTelemetryModule.spanService
        )
    }

    override val appStartupDataCollector: AppStartupDataCollector by singleton {
        AppStartupTraceEmitter(
            clock = openTelemetryModule.openTelemetryClock,
            startupServiceProvider = { startupService },
            spanService = openTelemetryModule.spanService,
            versionChecker = versionChecker,
            logger = initModule.logger,
            manualEnd = configService.autoDataCaptureBehavior.isEndStartupWithAppReadyEnabled()
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
                clock = openTelemetryModule.openTelemetryClock,
                versionChecker = versionChecker
            )
        } else {
            null
        }
    }
}

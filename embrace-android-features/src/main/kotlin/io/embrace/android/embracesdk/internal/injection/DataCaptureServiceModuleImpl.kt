package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadEventEmitter
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadEvents
import io.embrace.android.embracesdk.internal.capture.activity.UiLoadTraceEmitter
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
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.Worker

internal class DataCaptureServiceModuleImpl @JvmOverloads constructor(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule,
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
        Systrace.traceSynchronous("breadcrumb-service-init") {
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
            backgroundWorker = workerThreadModule.backgroundWorker(Worker.Background.NonIoRegWorker),
            versionChecker = versionChecker,
            logger = initModule.logger
        )
    }

    override val startupTracker: StartupTracker by singleton {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            uiLoadEventEmitter = uiLoadEventEmitter,
            logger = initModule.logger,
            versionChecker = versionChecker,
        )
    }

    override val uiLoadEvents: UiLoadEvents by singleton {
        UiLoadTraceEmitter(
            spanService = openTelemetryModule.spanService,
            versionChecker = versionChecker,
        )
    }

    override val uiLoadEventEmitter: UiLoadEventEmitter? by singleton {
        if (configService.autoDataCaptureBehavior.isUiLoadPerfCaptureEnabled()) {
            UiLoadEventEmitter(
                uiLoadEvents = uiLoadEvents,
                clock = openTelemetryModule.openTelemetryClock,
                versionChecker = versionChecker,
            )
        } else {
            null
        }
    }
}

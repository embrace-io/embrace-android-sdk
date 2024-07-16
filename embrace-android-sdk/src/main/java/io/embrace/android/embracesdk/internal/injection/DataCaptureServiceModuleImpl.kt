package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.internal.capture.crumbs.EmbraceBreadcrumbService
import io.embrace.android.embracesdk.internal.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.internal.capture.memory.ComponentCallbackService
import io.embrace.android.embracesdk.internal.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.internal.capture.memory.MemoryService
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.internal.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.internal.capture.startup.StartupService
import io.embrace.android.embracesdk.internal.capture.startup.StartupServiceImpl
import io.embrace.android.embracesdk.internal.capture.startup.StartupTracker
import io.embrace.android.embracesdk.internal.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.internal.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.internal.worker.WorkerName
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModule

internal class DataCaptureServiceModuleImpl @JvmOverloads constructor(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker = BuildVersionChecker,
    dataSourceModule: DataSourceModule
) : DataCaptureServiceModule {

    private val configService = essentialServiceModule.configService

    override val memoryService: MemoryService? by singleton {
        if (configService.autoDataCaptureBehavior.isMemoryServiceEnabled()) {
            EmbraceMemoryService(initModule.clock) { dataSourceModule }
        } else {
            null
        }
    }

    override val componentCallbackService: ComponentCallbackService by singleton {
        Systrace.traceSynchronous("component-callback-service-init") {
            ComponentCallbackService(coreModule.application, memoryService, initModule.logger)
        }
    }

    override val webviewService: WebViewService by singleton {
        EmbraceWebViewService(
            configService,
            initModule.jsonSerializer,
            initModule.logger,
        ) { dataSourceModule }
    }

    override val breadcrumbService: BreadcrumbService by singleton {
        Systrace.traceSynchronous("breadcrumb-service-init") {
            EmbraceBreadcrumbService(
                initModule.clock,
                configService
            ) { dataSourceModule }
        }
    }

    override val pushNotificationService: PushNotificationCaptureService by singleton {
        PushNotificationCaptureService(
            breadcrumbService,
            initModule.logger
        )
    }

    override val startupService: StartupService by singleton {
        StartupServiceImpl(
            spanService = openTelemetryModule.spanService,
            backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
        )
    }

    override val appStartupDataCollector: AppStartupDataCollector by singleton {
        AppStartupTraceEmitter(
            clock = initModule.openTelemetryClock,
            startupServiceProvider = { startupService },
            spanService = openTelemetryModule.spanService,
            backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION),
            versionChecker = versionChecker,
            logger = initModule.logger
        )
    }

    override val startupTracker: StartupTracker by singleton {
        StartupTracker(
            appStartupDataCollector = appStartupDataCollector,
            logger = initModule.logger,
            versionChecker = versionChecker,
        )
    }
}

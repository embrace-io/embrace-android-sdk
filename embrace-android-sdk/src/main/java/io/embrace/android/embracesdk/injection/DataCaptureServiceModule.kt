package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.EmbraceBreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.capture.memory.ComponentCallbackService
import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.memory.NoOpMemoryService
import io.embrace.android.embracesdk.capture.startup.AppStartupDataCollector
import io.embrace.android.embracesdk.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.startup.StartupServiceImpl
import io.embrace.android.embracesdk.capture.startup.StartupTracker
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.internal.Systrace
import io.embrace.android.embracesdk.internal.utils.BuildVersionChecker
import io.embrace.android.embracesdk.internal.utils.VersionChecker
import io.embrace.android.embracesdk.worker.WorkerName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * This modules provides services that capture data from within an application. It could be argued
 * that a lot of classes could fit in this module, so to keep it small (<15 properties) it's best
 * to only include services whose main responsibility is just capturing data. It would be well
 * worth reassessing the grouping once this module grows larger.
 */
internal interface DataCaptureServiceModule {

    /**
     * Captures breadcrumbs
     */
    val breadcrumbService: BreadcrumbService

    /**
     * Captures memory events
     */
    val memoryService: MemoryService

    /**
     * Captures information from webviews
     */
    val webviewService: WebViewService

    /**
     * Captures push notifications
     */
    val pushNotificationService: PushNotificationCaptureService

    /**
     * Registers for the component callback to capture memory events
     */
    val componentCallbackService: ComponentCallbackService

    /**
     * Captures the startup time of the SDK
     */
    val startupService: StartupService

    val startupTracker: StartupTracker

    val appStartupDataCollector: AppStartupDataCollector
}

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

    override val memoryService: MemoryService by singleton {
        if (configService.autoDataCaptureBehavior.isMemoryServiceEnabled()) {
            EmbraceMemoryService(initModule.clock) { dataSourceModule }
        } else {
            NoOpMemoryService()
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

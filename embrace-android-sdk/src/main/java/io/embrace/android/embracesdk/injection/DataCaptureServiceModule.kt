package io.embrace.android.embracesdk.injection

import android.os.Build
import io.embrace.android.embracesdk.capture.crumbs.BreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.EmbraceBreadcrumbService
import io.embrace.android.embracesdk.capture.crumbs.PushNotificationCaptureService
import io.embrace.android.embracesdk.capture.memory.ComponentCallbackService
import io.embrace.android.embracesdk.capture.memory.EmbraceMemoryService
import io.embrace.android.embracesdk.capture.memory.MemoryService
import io.embrace.android.embracesdk.capture.memory.NoOpMemoryService
import io.embrace.android.embracesdk.capture.powersave.EmbracePowerSaveModeService
import io.embrace.android.embracesdk.capture.powersave.NoOpPowerSaveModeService
import io.embrace.android.embracesdk.capture.powersave.PowerSaveModeService
import io.embrace.android.embracesdk.capture.startup.AppStartupTraceEmitter
import io.embrace.android.embracesdk.capture.startup.StartupService
import io.embrace.android.embracesdk.capture.startup.StartupServiceImpl
import io.embrace.android.embracesdk.capture.startup.StartupTracker
import io.embrace.android.embracesdk.capture.thermalstate.EmbraceThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
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
     * Captures intervals where power save mode was enabled
     */
    val powerSaveModeService: PowerSaveModeService

    /**
     * Captures information from webviews
     */
    val webviewService: WebViewService

    /**
     * Captures push notifications
     */
    val pushNotificationService: PushNotificationCaptureService

    /**
     * Captures thermal state events
     */
    val thermalStatusService: ThermalStatusService

    /**
     * Registers for the component callback to capture memory events
     */
    val componentCallbackService: ComponentCallbackService

    /**
     * Captures the startup time of the SDK
     */
    val startupService: StartupService

    val startupTracker: StartupTracker

    val appStartupTraceEmitter: AppStartupTraceEmitter
}

internal class DataCaptureServiceModuleImpl @JvmOverloads constructor(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    systemServiceModule: SystemServiceModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker = BuildVersionChecker,
    dataSourceModule: DataSourceModule
) : DataCaptureServiceModule {

    private val backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
    private val configService = essentialServiceModule.configService
    private val powerManagerProvider = {
        Systrace.traceSynchronous("power-manager-load") {
            systemServiceModule.powerManager
        }
    }

    override val memoryService: MemoryService by singleton {
        if (configService.autoDataCaptureBehavior.isMemoryServiceEnabled()) {
            EmbraceMemoryService(initModule.clock)
        } else {
            NoOpMemoryService()
        }
    }

    override val componentCallbackService: ComponentCallbackService by singleton {
        Systrace.traceSynchronous("component-callback-service-init") {
            ComponentCallbackService(coreModule.application, memoryService, initModule.logger)
        }
    }

    override val powerSaveModeService: PowerSaveModeService by singleton {
        Systrace.traceSynchronous("power-service-init") {
            if (configService.autoDataCaptureBehavior.isPowerSaveModeServiceEnabled()) {
                EmbracePowerSaveModeService(
                    coreModule.context,
                    backgroundWorker,
                    initModule.clock,
                    initModule.logger,
                    powerManagerProvider,
                )
            } else {
                NoOpPowerSaveModeService()
            }
        }
    }

    override val webviewService: WebViewService by singleton {
        EmbraceWebViewService(configService, coreModule.jsonSerializer, initModule.logger)
    }

    override val breadcrumbService: BreadcrumbService by singleton {
        Systrace.traceSynchronous("breadcrumb-service-init") {
            EmbraceBreadcrumbService(
                initModule.clock,
                configService,
                essentialServiceModule.activityLifecycleTracker,
                { dataSourceModule.breadcrumbDataSource.dataSource },
                { dataSourceModule.tapDataSource.dataSource },
                { dataSourceModule.fragmentViewDataSource.dataSource },
                initModule.logger
            )
        }
    }

    override val pushNotificationService: PushNotificationCaptureService by singleton {
        PushNotificationCaptureService(
            breadcrumbService,
            initModule.logger
        )
    }

    override val thermalStatusService: ThermalStatusService by singleton {
        Systrace.traceSynchronous("thermal-service-init") {
            if (configService.autoDataCaptureBehavior.isThermalStatusCaptureEnabled() && versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
                val backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
                EmbraceThermalStatusService(
                    backgroundWorker,
                    initModule.clock,
                    powerManagerProvider
                )
            } else {
                NoOpThermalStatusService()
            }
        }
    }

    override val startupService: StartupService by singleton {
        StartupServiceImpl(
            spanService = openTelemetryModule.spanService,
            backgroundWorker = workerThreadModule.backgroundWorker(WorkerName.BACKGROUND_REGISTRATION)
        )
    }

    override val appStartupTraceEmitter: AppStartupTraceEmitter by singleton {
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
            appStartupTraceEmitter = appStartupTraceEmitter,
            logger = initModule.logger,
            versionChecker = versionChecker,
        )
    }
}

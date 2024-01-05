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
import io.embrace.android.embracesdk.capture.thermalstate.EmbraceThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.NoOpThermalStatusService
import io.embrace.android.embracesdk.capture.thermalstate.ThermalStatusService
import io.embrace.android.embracesdk.capture.webview.EmbraceWebViewService
import io.embrace.android.embracesdk.capture.webview.WebViewService
import io.embrace.android.embracesdk.utils.BuildVersionChecker
import io.embrace.android.embracesdk.utils.VersionChecker
import io.embrace.android.embracesdk.worker.ExecutorName
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
}

internal class DataCaptureServiceModuleImpl @JvmOverloads constructor(
    initModule: InitModule,
    coreModule: CoreModule,
    systemServiceModule: SystemServiceModule,
    essentialServiceModule: EssentialServiceModule,
    workerThreadModule: WorkerThreadModule,
    versionChecker: VersionChecker = BuildVersionChecker
) : DataCaptureServiceModule {

    private val backgroundExecutorService = workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION)
    private val scheduledExecutor = workerThreadModule.scheduledExecutor(ExecutorName.BACKGROUND_REGISTRATION)
    private val configService = essentialServiceModule.configService

    override val memoryService: MemoryService by singleton {
        if (configService.autoDataCaptureBehavior.isMemoryServiceEnabled()) {
            EmbraceMemoryService(initModule.clock)
        } else {
            NoOpMemoryService()
        }
    }

    override val componentCallbackService: ComponentCallbackService by singleton {
        ComponentCallbackService(coreModule.application, memoryService)
    }

    override val powerSaveModeService: PowerSaveModeService by singleton {
        if (configService.autoDataCaptureBehavior.isPowerSaveModeServiceEnabled() && versionChecker.isAtLeast(
                Build.VERSION_CODES.LOLLIPOP
            )
        ) {
            EmbracePowerSaveModeService(
                coreModule.context,
                backgroundExecutorService,
                initModule.clock,
                systemServiceModule.powerManager
            )
        } else {
            NoOpPowerSaveModeService()
        }
    }

    override val webviewService: WebViewService by singleton {
        EmbraceWebViewService(configService, coreModule.jsonSerializer)
    }

    override val breadcrumbService: BreadcrumbService by singleton {
        EmbraceBreadcrumbService(
            initModule.clock,
            configService,
            coreModule.logger
        )
    }

    override val pushNotificationService: PushNotificationCaptureService by singleton {
        PushNotificationCaptureService(
            breadcrumbService,
            coreModule.logger
        )
    }

    override val thermalStatusService: ThermalStatusService by singleton {
        if (configService.sdkModeBehavior.isBetaFeaturesEnabled() && versionChecker.isAtLeast(Build.VERSION_CODES.Q)) {
            EmbraceThermalStatusService(
                scheduledExecutor,
                initModule.clock,
                coreModule.logger,
                systemServiceModule.powerManager
            )
        } else {
            NoOpThermalStatusService()
        }
    }
}

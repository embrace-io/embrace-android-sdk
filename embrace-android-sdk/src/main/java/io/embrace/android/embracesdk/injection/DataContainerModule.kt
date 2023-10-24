package io.embrace.android.embracesdk.injection

import android.os.Build
import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.capture.aei.ApplicationExitInfoService
import io.embrace.android.embracesdk.capture.aei.EmbraceApplicationExitInfoService
import io.embrace.android.embracesdk.capture.aei.NoOpApplicationExitInfoService
import io.embrace.android.embracesdk.event.EmbraceEventService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.EmbraceSessionProperties
import io.embrace.android.embracesdk.utils.BuildVersionChecker
import io.embrace.android.embracesdk.worker.ExecutorName
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Holds dependencies that normally act as a 'container' for other data. For example,
 * a span, an Event, PerformanceInfo, etc.
 */
internal interface DataContainerModule {
    val applicationExitInfoService: ApplicationExitInfoService
    val performanceInfoService: PerformanceInfoService
    val eventService: EventService
}

internal class DataContainerModuleImpl(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    essentialServiceModule: EssentialServiceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    anrModule: AnrModule,
    customerLogModule: CustomerLogModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sessionProperties: EmbraceSessionProperties,
    startTime: Long
) : DataContainerModule {

    override val applicationExitInfoService: ApplicationExitInfoService by singleton {
        if (BuildVersionChecker.isAtLeast(Build.VERSION_CODES.R)) {
            EmbraceApplicationExitInfoService(
                workerThreadModule.backgroundExecutor(ExecutorName.BACKGROUND_REGISTRATION),
                essentialServiceModule.configService,
                systemServiceModule.activityManager,
                androidServicesModule.preferencesService,
                deliveryModule.deliveryService
            )
        } else {
            NoOpApplicationExitInfoService()
        }
    }

    override val performanceInfoService: PerformanceInfoService by singleton {
        EmbracePerformanceInfoService(
            anrModule.anrService,
            essentialServiceModule.networkConnectivityService,
            customerLogModule.networkLoggingService,
            dataCaptureServiceModule.powerSaveModeService,
            dataCaptureServiceModule.memoryService,
            essentialServiceModule.metadataService,
            anrModule.googleAnrTimestampRepository,
            applicationExitInfoService,
            dataCaptureServiceModule.strictModeService,
            nativeModule.nativeThreadSamplerService
        )
    }

    override val eventService: EventService by singleton {
        EmbraceEventService(
            startTime,
            deliveryModule.deliveryService,
            essentialServiceModule.configService,
            essentialServiceModule.metadataService,
            performanceInfoService,
            essentialServiceModule.userService,
            sessionProperties,
            coreModule.logger,
            workerThreadModule,
            initModule.clock,
            initModule.spansService
        )
    }
}

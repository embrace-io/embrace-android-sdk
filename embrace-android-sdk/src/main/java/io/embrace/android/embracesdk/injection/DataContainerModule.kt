package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.event.EmbraceEventService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.ndk.NativeModule
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties
import io.embrace.android.embracesdk.worker.WorkerThreadModule

/**
 * Holds dependencies that normally act as a 'container' for other data. For example,
 * a span, an Event, PerformanceInfo, etc.
 */
internal interface DataContainerModule {
    val performanceInfoService: PerformanceInfoService
    val eventService: EventService
}

internal class DataContainerModuleImpl(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    dataCaptureServiceModule: DataCaptureServiceModule,
    anrModule: AnrModule,
    customerLogModule: CustomerLogModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sessionProperties: EmbraceSessionProperties,
    sdkStartTimeMs: Long
) : DataContainerModule {

    override val performanceInfoService: PerformanceInfoService by singleton {
        EmbracePerformanceInfoService(
            anrModule.anrService,
            essentialServiceModule.networkConnectivityService,
            customerLogModule.networkLoggingService,
            dataCaptureServiceModule.powerSaveModeService,
            dataCaptureServiceModule.memoryService,
            essentialServiceModule.metadataService,
            anrModule.googleAnrTimestampRepository,
            nativeModule.nativeThreadSamplerService,
            anrModule.responsivenessMonitorService
        )
    }

    override val eventService: EventService by singleton {
        EmbraceEventService(
            sdkStartTimeMs,
            deliveryModule.deliveryService,
            essentialServiceModule.configService,
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            performanceInfoService,
            essentialServiceModule.userService,
            sessionProperties,
            coreModule.logger,
            workerThreadModule,
            initModule.clock,
            openTelemetryModule.spanService
        )
    }
}

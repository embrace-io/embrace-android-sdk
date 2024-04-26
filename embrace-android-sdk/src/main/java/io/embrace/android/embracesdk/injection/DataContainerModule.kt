package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.event.EmbraceEventService
import io.embrace.android.embracesdk.event.EventService
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
    @Suppress("UNUSED_PARAMETER")
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    @Suppress("UNUSED_PARAMETER")
    dataCaptureServiceModule: DataCaptureServiceModule,
    anrModule: AnrModule,
    @Suppress("UNUSED_PARAMETER")
    customerLogModule: CustomerLogModule,
    deliveryModule: DeliveryModule,
    sdkStartTimeMs: Long
) : DataContainerModule {

    override val performanceInfoService: PerformanceInfoService by singleton {
        EmbracePerformanceInfoService(
            essentialServiceModule.metadataService,
            anrModule.googleAnrTimestampRepository,
            initModule.logger
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
            essentialServiceModule.sessionProperties,
            initModule.logger,
            workerThreadModule,
            initModule.clock
        )
    }
}

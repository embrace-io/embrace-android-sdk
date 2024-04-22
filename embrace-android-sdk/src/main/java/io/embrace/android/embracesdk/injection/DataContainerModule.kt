package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.capture.EmbracePerformanceInfoService
import io.embrace.android.embracesdk.capture.PerformanceInfoService
import io.embrace.android.embracesdk.event.EmbraceEventService
import io.embrace.android.embracesdk.event.EventService
import io.embrace.android.embracesdk.ndk.NativeModule
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
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    nativeModule: NativeModule,
    sdkStartTimeMs: Long
) : DataContainerModule {

    override val performanceInfoService: PerformanceInfoService by singleton {
        EmbracePerformanceInfoService(
            essentialServiceModule.metadataService,
            nativeModule.nativeThreadSamplerService,
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

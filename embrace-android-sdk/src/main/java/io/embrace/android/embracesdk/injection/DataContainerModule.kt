package io.embrace.android.embracesdk.injection

import io.embrace.android.embracesdk.internal.event.EmbraceEventService
import io.embrace.android.embracesdk.internal.event.EventService
import io.embrace.android.embracesdk.internal.worker.WorkerThreadModule

/**
 * Holds dependencies that normally act as a 'container' for other data. For example,
 * a span, an Event, PerformanceInfo, etc.
 */
internal interface DataContainerModule {
    val eventService: EventService
}

internal class DataContainerModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    sdkStartTimeMs: Long
) : DataContainerModule {

    override val eventService: EventService by singleton {
        EmbraceEventService(
            sdkStartTimeMs,
            deliveryModule.deliveryService,
            essentialServiceModule.configService,
            essentialServiceModule.metadataService,
            essentialServiceModule.sessionIdTracker,
            essentialServiceModule.userService,
            essentialServiceModule.sessionProperties,
            initModule.logger,
            workerThreadModule,
            initModule.clock
        )
    }
}

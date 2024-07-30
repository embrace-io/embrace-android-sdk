package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.event.EmbraceEventService
import io.embrace.android.embracesdk.internal.event.EventService

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

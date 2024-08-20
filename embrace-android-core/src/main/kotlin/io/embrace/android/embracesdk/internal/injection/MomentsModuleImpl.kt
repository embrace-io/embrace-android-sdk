package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.event.EmbraceEventService
import io.embrace.android.embracesdk.internal.event.EventService

internal class MomentsModuleImpl(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    deliveryModule: DeliveryModule,
    sdkStartTimeMs: Long
) : MomentsModule {

    override val eventService: EventService by singleton {
        EmbraceEventService(
            sdkStartTimeMs,
            deliveryModule.deliveryService,
            configModule.configService,
            payloadSourceModule.metadataService,
            essentialServiceModule.processStateService,
            essentialServiceModule.sessionIdTracker,
            essentialServiceModule.userService,
            essentialServiceModule.sessionPropertiesService,
            initModule.logger,
            workerThreadModule,
            initModule.clock
        )
    }
}

package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [MomentsModule]. Matches the signature of the constructor for [MomentsModuleImpl]
 */
internal typealias MomentsModuleSupplier = (
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    deliveryModule: DeliveryModule,
    startTime: Long
) -> MomentsModule

internal fun createMomentsModule(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    deliveryModule: DeliveryModule,
    startTime: Long
): MomentsModule = MomentsModuleImpl(
    initModule,
    workerThreadModule,
    essentialServiceModule,
    configModule,
    payloadSourceModule,
    deliveryModule,
    startTime
)

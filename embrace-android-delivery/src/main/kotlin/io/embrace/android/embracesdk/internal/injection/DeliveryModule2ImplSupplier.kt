package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DeliveryModule2]. Matches the signature of the constructor
 * for [DeliveryModule2Impl]
 */
typealias DeliveryModule2Supplier = (
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule
) -> DeliveryModule2

fun createDeliveryModule2(
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule
): DeliveryModule2 = DeliveryModule2Impl(
    configModule,
    initModule,
    workerThreadModule,
    coreModule
)

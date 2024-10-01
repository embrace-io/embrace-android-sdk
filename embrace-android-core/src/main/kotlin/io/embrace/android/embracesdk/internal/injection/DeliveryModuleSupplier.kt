package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DeliveryModule]. Matches the signature of the constructor for [DeliveryModuleImpl]
 */
typealias DeliveryModuleSupplier = (
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
) -> DeliveryModule

fun createDeliveryModule(
    configModule: ConfigModule,
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
): DeliveryModule = DeliveryModuleImpl(
    configModule,
    initModule,
    workerThreadModule,
    coreModule,
    storageModule,
    essentialServiceModule
)

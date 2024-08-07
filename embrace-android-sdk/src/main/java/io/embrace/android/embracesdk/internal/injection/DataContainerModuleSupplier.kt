package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DataContainerModule]. Matches the signature of the constructor for [DataContainerModuleImpl]
 */
internal typealias DataContainerModuleSupplier = (
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    startTime: Long
) -> DataContainerModule

internal fun createDataContainerModule(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    essentialServiceModule: EssentialServiceModule,
    deliveryModule: DeliveryModule,
    startTime: Long
): DataContainerModule = DataContainerModuleImpl(
    initModule,
    workerThreadModule,
    essentialServiceModule,
    deliveryModule,
    startTime
)

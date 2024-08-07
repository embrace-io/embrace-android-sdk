package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [AndroidServicesModule]. Matches the signature of the constructor for [AndroidServicesModuleImpl]
 */
public typealias AndroidServicesModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> AndroidServicesModule

public fun createAndroidServicesModule(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
): AndroidServicesModule = AndroidServicesModuleImpl(initModule, coreModule, workerThreadModule)

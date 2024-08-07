package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [StorageModule]. Matches the signature of the constructor for [StorageModuleImpl]
 */
internal typealias StorageModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
) -> StorageModule

internal fun createStorageModuleSupplier(
    initModule: InitModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
): StorageModule = StorageModuleImpl(
    initModule,
    coreModule,
    workerThreadModule
)

package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [WorkerThreadModule]. Matches the signature of the constructor for [WorkerThreadModuleImpl]
 */
typealias WorkerThreadModuleSupplier = (
    initModule: InitModule,
) -> WorkerThreadModule

fun createWorkerThreadModule(
    initModule: InitModule,
): WorkerThreadModule = WorkerThreadModuleImpl(initModule)

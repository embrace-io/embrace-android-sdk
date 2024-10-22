package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [AnrModule]. Matches the signature of the constructor for [AnrModuleImpl]
 */
typealias AnrModuleSupplier = (
    initModule: InitModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule
) -> AnrModule

fun createAnrModule(
    initModule: InitModule,
    configService: ConfigService,
    workerModule: WorkerThreadModule
): AnrModule = AnrModuleImpl(initModule, configService, workerModule)

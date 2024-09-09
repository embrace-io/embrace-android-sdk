package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [DataSourceModule]. Matches the signature of the constructor for [DataSourceModuleImpl]
 */
typealias DataSourceModuleSupplier = (
    initModule: InitModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule
) -> DataSourceModule

fun createDataSourceModule(
    initModule: InitModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule
): DataSourceModule = DataSourceModuleImpl(
    initModule,
    configService,
    workerThreadModule
)

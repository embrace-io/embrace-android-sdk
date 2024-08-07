package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [DataSourceModule]. Matches the signature of the constructor for [DataSourceModuleImpl]
 */
public typealias DataSourceModuleSupplier = (
    initModule: InitModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule
) -> DataSourceModule

public fun createDataSourceModule(
    initModule: InitModule,
    configService: ConfigService,
    workerThreadModule: WorkerThreadModule
): DataSourceModule = DataSourceModuleImpl(
    initModule,
    configService,
    workerThreadModule
)

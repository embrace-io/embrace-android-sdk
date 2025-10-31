package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DataSourceModule]. Matches the signature of the constructor for [DataSourceModuleImpl]
 */
typealias DataSourceModuleSupplier = (
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
) -> DataSourceModule

fun createDataSourceModule(
    initModule: InitModule,
    workerThreadModule: WorkerThreadModule,
    configModule: ConfigModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    coreModule: CoreModule,
): DataSourceModule = DataSourceModuleImpl(
    initModule,
    workerThreadModule,
    configModule,
    essentialServiceModule,
    androidServicesModule,
    coreModule,
)

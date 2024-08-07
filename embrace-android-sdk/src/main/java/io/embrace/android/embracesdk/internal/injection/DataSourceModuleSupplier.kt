package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [DataSourceModule]. Matches the signature of the constructor for [DataSourceModuleImpl]
 */
internal typealias DataSourceModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    anrModule: AnrModule
) -> DataSourceModule

internal fun createDataSourceModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    essentialServiceModule: EssentialServiceModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    anrModule: AnrModule
): DataSourceModule = DataSourceModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    essentialServiceModule,
    systemServiceModule,
    androidServicesModule,
    workerThreadModule,
    anrModule
)

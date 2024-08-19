package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [EssentialServiceModule]. Matches the signature of the constructor for [EssentialServiceModuleImpl]
 */
public typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule
) -> EssentialServiceModule

public fun createEssentialServiceModule(
    initModule: InitModule,
    configModule: ConfigModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule
): EssentialServiceModule = EssentialServiceModuleImpl(
    initModule,
    configModule,
    openTelemetryModule,
    coreModule,
    workerThreadModule,
    systemServiceModule,
    androidServicesModule,
    storageModule
)

package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [ConfigModule]. Matches the signature of the constructor for [ConfigModuleImpl]
 */
typealias ConfigModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
) -> ConfigModule

fun createConfigModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
): ConfigModule = ConfigModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
)

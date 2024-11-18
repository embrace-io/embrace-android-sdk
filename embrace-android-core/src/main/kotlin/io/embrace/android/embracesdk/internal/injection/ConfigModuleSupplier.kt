package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Function that returns an instance of [ConfigModule]. Matches the signature of the constructor for [ConfigModuleImpl]
 */
typealias ConfigModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
) -> ConfigModule

fun createConfigModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
): ConfigModule = ConfigModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
    framework,
)

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Function that returns an instance of [ConfigModule]. Matches the signature of the constructor for [ConfigModuleImpl]
 */
typealias ConfigModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    foregroundAction: () -> Unit,
) -> ConfigModule

fun createConfigModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    foregroundAction: () -> Unit,
): ConfigModule = ConfigModuleImpl(
    initModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
    framework,
    foregroundAction
)

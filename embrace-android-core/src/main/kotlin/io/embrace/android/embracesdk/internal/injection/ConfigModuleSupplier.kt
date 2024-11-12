package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.source.RemoteConfigSource
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.Provider

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
    foregroundAction: () -> Unit,
    remoteConfigSourceProvider: Provider<RemoteConfigSource?>,
) -> ConfigModule

fun createConfigModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    foregroundAction: () -> Unit,
    remoteConfigSourceProvider: Provider<RemoteConfigSource?>,
): ConfigModule = ConfigModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
    framework,
    foregroundAction,
    remoteConfigSourceProvider
)

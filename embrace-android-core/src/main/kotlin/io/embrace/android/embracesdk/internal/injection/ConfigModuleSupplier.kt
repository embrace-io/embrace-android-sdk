package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.store.RemoteConfigStore
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
    remoteConfigStoreProvider: Provider<RemoteConfigStore?>,
) -> ConfigModule

fun createConfigModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    framework: AppFramework,
    remoteConfigStoreProvider: Provider<RemoteConfigStore?>,
): ConfigModule = ConfigModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
    framework,
    remoteConfigStoreProvider
)

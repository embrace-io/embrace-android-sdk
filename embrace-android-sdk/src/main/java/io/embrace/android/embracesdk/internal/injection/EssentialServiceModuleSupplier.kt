package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.utils.Provider

/**
 * Function that returns an instance of [EssentialServiceModule]. Matches the signature of the constructor for [EssentialServiceModuleImpl]
 */
internal typealias EssentialServiceModuleSupplier = (
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule,
    customAppId: String?,
    customerLogModuleProvider: Provider<CustomerLogModule>,
    dataSourceModuleProvider: Provider<DataSourceModule>,
    framework: AppFramework,
    configServiceProvider: (framework: AppFramework) -> ConfigService?
) -> EssentialServiceModule

internal fun createEssentialServiceModule(
    initModule: InitModule,
    openTelemetryModule: OpenTelemetryModule,
    coreModule: CoreModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    storageModule: StorageModule,
    customAppId: String?,
    customerLogModuleProvider: Provider<CustomerLogModule>,
    dataSourceModuleProvider: Provider<DataSourceModule>,
    framework: AppFramework,
    configServiceProvider: (framework: AppFramework) -> ConfigService?
): EssentialServiceModule = EssentialServiceModuleImpl(
    initModule,
    openTelemetryModule,
    coreModule,
    workerThreadModule,
    systemServiceModule,
    androidServicesModule,
    storageModule,
    customAppId,
    customerLogModuleProvider,
    dataSourceModuleProvider,
    framework,
    configServiceProvider
)

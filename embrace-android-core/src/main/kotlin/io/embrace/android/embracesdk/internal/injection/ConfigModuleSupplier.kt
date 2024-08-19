package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService
import io.embrace.android.embracesdk.internal.payload.AppFramework

/**
 * Function that returns an instance of [ConfigModule]. Matches the signature of the constructor for [ConfigModuleImpl]
 */
public typealias ConfigModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    customAppId: String?,
    framework: AppFramework,
    configServiceProvider: (framework: AppFramework) -> ConfigService?,
    foregroundAction: ConfigService.() -> Unit
) -> ConfigModule

public fun createConfigModule(
    initModule: InitModule,
    coreModule: CoreModule,
    openTelemetryModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    androidServicesModule: AndroidServicesModule,
    customAppId: String?,
    framework: AppFramework,
    configServiceProvider: (framework: AppFramework) -> ConfigService? = { null },
    foregroundAction: ConfigService.() -> Unit
): ConfigModule = ConfigModuleImpl(
    initModule,
    coreModule,
    openTelemetryModule,
    workerThreadModule,
    androidServicesModule,
    customAppId,
    framework,
    configServiceProvider,
    foregroundAction
)
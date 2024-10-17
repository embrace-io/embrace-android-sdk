package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [FeatureModule]. Matches the signature of the constructor for [FeatureModuleImpl]
 */
typealias FeatureModuleSupplier = (
    featureRegistry: EmbraceFeatureRegistry,
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    logWriter: LogWriter,
    configService: ConfigService
) -> FeatureModule

fun createFeatureModule(
    featureRegistry: EmbraceFeatureRegistry,
    coreModule: CoreModule,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    workerThreadModule: WorkerThreadModule,
    systemServiceModule: SystemServiceModule,
    androidServicesModule: AndroidServicesModule,
    logWriter: LogWriter,
    configService: ConfigService,
): FeatureModule = FeatureModuleImpl(
    featureRegistry = featureRegistry,
    coreModule = coreModule,
    initModule = initModule,
    otelModule = otelModule,
    workerThreadModule = workerThreadModule,
    systemServiceModule = systemServiceModule,
    androidServicesModule = androidServicesModule,
    logWriter = logWriter,
    configService = configService,
)

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.destination.LogWriter
import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [FeatureModule]. Matches the signature of the constructor for [FeatureModuleImpl]
 */
typealias FeatureModuleSupplier = (
    featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    logWriter: LogWriter,
    configService: ConfigService,
) -> FeatureModule

fun createFeatureModule(
    featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    otelModule: OpenTelemetryModule,
    logWriter: LogWriter,
    configService: ConfigService,
): FeatureModule = FeatureModuleImpl(
    featureRegistry = featureRegistry,
    initModule = initModule,
    otelModule = otelModule,
    logWriter = logWriter,
    configService = configService,
)

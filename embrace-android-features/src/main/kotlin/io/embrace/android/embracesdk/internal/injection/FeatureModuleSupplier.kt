package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.arch.EmbraceFeatureRegistry
import io.embrace.android.embracesdk.internal.arch.datasource.TelemetryDestination
import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [FeatureModule]. Matches the signature of the constructor for [FeatureModuleImpl]
 */
typealias FeatureModuleSupplier = (
    featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    destination: TelemetryDestination,
    configService: ConfigService,
) -> FeatureModule

fun createFeatureModule(
    featureRegistry: EmbraceFeatureRegistry,
    initModule: InitModule,
    destination: TelemetryDestination,
    configService: ConfigService,
): FeatureModule = FeatureModuleImpl(
    featureRegistry = featureRegistry,
    initModule = initModule,
    destination = destination,
    configService = configService,
)

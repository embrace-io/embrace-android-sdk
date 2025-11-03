package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [FeatureModule]. Matches the signature of the constructor for [FeatureModuleImpl]
 */
typealias FeatureModuleSupplier = (
    instrumentationModule: InstrumentationModule,
    configService: ConfigService,
) -> FeatureModule

fun createFeatureModule(
    instrumentationModule: InstrumentationModule,
    configService: ConfigService,
): FeatureModule = FeatureModuleImpl(
    instrumentationModule = instrumentationModule,
    configService = configService,
)

package io.embrace.android.embracesdk.internal.injection

import io.embrace.android.embracesdk.internal.config.ConfigService

/**
 * Function that returns an instance of [FeatureModule]. Matches the signature of the constructor for [FeatureModuleImpl]
 */
typealias FeatureModuleSupplier = (
    dataSourceModule: DataSourceModule,
    configService: ConfigService,
) -> FeatureModule

fun createFeatureModule(
    dataSourceModule: DataSourceModule,
    configService: ConfigService,
): FeatureModule = FeatureModuleImpl(
    dataSourceModule = dataSourceModule,
    configService = configService,
)

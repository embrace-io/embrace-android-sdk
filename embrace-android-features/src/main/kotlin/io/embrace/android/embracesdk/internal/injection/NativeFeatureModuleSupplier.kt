package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
    dataSourceModule: DataSourceModule,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    initModule: InitModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
    dataSourceModule: DataSourceModule,
): NativeFeatureModule = NativeFeatureModuleImpl(
    initModule,
    configModule,
    androidServicesModule,
    nativeCoreModule,
    dataSourceModule,
)

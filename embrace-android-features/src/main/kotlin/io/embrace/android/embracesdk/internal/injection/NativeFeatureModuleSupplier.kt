package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    initModule: InitModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
): NativeFeatureModule = NativeFeatureModuleImpl(
    initModule,
    essentialServiceModule,
    configModule,
    androidServicesModule,
    nativeCoreModule
)

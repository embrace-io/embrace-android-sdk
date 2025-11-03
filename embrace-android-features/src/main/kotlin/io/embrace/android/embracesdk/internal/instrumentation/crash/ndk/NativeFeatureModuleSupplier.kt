package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.injection.AndroidServicesModule
import io.embrace.android.embracesdk.internal.injection.ConfigModule
import io.embrace.android.embracesdk.internal.injection.InitModule
import io.embrace.android.embracesdk.internal.injection.InstrumentationModule
import io.embrace.android.embracesdk.internal.injection.NativeCoreModule

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    initModule: InitModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    initModule: InitModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
): NativeFeatureModule = NativeFeatureModuleImpl(
    initModule,
    configModule,
    androidServicesModule,
    nativeCoreModule,
    instrumentationModule,
)

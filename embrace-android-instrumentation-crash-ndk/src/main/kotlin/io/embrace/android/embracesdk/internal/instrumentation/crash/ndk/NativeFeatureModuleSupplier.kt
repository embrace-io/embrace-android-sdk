package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.injection.InstrumentationModule

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    nativeCoreModule: NativeCoreModule,
    instrumentationModule: InstrumentationModule,
): NativeFeatureModule = NativeFeatureModuleImpl(
    nativeCoreModule,
    instrumentationModule,
)

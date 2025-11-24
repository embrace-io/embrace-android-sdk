package io.embrace.android.embracesdk.internal.instrumentation.crash.ndk

import io.embrace.android.embracesdk.internal.arch.InstrumentationArgs

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    nativeCoreModule: NativeCoreModule,
    instrumentationArgs: InstrumentationArgs,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    nativeCoreModule: NativeCoreModule,
    instrumentationArgs: InstrumentationArgs,
): NativeFeatureModule = NativeFeatureModuleImpl(nativeCoreModule, instrumentationArgs)

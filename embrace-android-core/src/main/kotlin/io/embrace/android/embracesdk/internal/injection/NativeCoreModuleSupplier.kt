package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeCoreModule]. Matches the signature of the constructor for [NativeCoreModuleImpl]
 */
public typealias NativeCoreModuleSupplier = (initModule: InitModule) -> NativeCoreModule

public fun createNativeCoreModule(initModule: InitModule): NativeCoreModule =
    NativeCoreModuleImpl(initModule)

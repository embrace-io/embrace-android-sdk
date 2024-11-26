package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeCoreModule]. Matches the signature of the constructor for [NativeCoreModuleImpl]
 */
typealias NativeCoreModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    storageModule: StorageModule,
) -> NativeCoreModule

fun createNativeCoreModule(
    initModule: InitModule,
    coreModule: CoreModule,
    payloadSourceModule: PayloadSourceModule,
    storageModule: StorageModule,
): NativeCoreModule = NativeCoreModuleImpl(
    initModule,
    coreModule,
    payloadSourceModule,
    storageModule,
)

package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
typealias NativeFeatureModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule,
) -> NativeFeatureModule

fun createNativeFeatureModule(
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule,
): NativeFeatureModule = NativeFeatureModuleImpl(
    initModule,
    coreModule,
    storageModule,
    essentialServiceModule,
    configModule,
    payloadSourceModule,
    androidServicesModule,
    workerThreadModule,
    nativeCoreModule
)

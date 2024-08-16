package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [NativeFeatureModule]. Matches the signature of the constructor for [NativeFeatureModuleImpl]
 */
internal typealias NativeFeatureModuleSupplier = (
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    deliveryModule: DeliveryModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule
) -> NativeFeatureModule

internal fun createNativeFeatureModule(
    initModule: InitModule,
    coreModule: CoreModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    payloadSourceModule: PayloadSourceModule,
    deliveryModule: DeliveryModule,
    androidServicesModule: AndroidServicesModule,
    workerThreadModule: WorkerThreadModule,
    nativeCoreModule: NativeCoreModule
): NativeFeatureModule = NativeFeatureModuleImpl(
    initModule,
    coreModule,
    storageModule,
    essentialServiceModule,
    configModule,
    payloadSourceModule,
    deliveryModule,
    androidServicesModule,
    workerThreadModule,
    nativeCoreModule
)

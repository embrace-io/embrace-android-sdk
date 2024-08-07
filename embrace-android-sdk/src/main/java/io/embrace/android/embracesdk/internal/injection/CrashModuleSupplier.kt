package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
internal typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    unityCrashIdProvider: () -> String?
) -> CrashModule

internal fun createCrashModule(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    unityCrashIdProvider: () -> String?
): CrashModule {
    return CrashModuleImpl(
        initModule,
        storageModule,
        essentialServiceModule,
        androidServicesModule,
        unityCrashIdProvider,
    )
}

package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    unityCrashIdProvider: () -> String?
) -> CrashModule

fun createCrashModule(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    configModule: ConfigModule,
    androidServicesModule: AndroidServicesModule,
    unityCrashIdProvider: () -> String?
): CrashModule {
    return CrashModuleImpl(
        initModule,
        storageModule,
        essentialServiceModule,
        configModule,
        androidServicesModule,
        unityCrashIdProvider,
    )
}

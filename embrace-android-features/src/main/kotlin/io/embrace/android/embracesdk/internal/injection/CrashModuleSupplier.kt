package io.embrace.android.embracesdk.internal.injection

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    dataSourceModule: DataSourceModule,
) -> CrashModule

fun createCrashModule(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    dataSourceModule: DataSourceModule,
): CrashModule {
    return CrashModuleImpl(
        initModule,
        storageModule,
        essentialServiceModule,
        androidServicesModule,
        dataSourceModule,
    )
}

package io.embrace.android.embracesdk.internal.instrumentation.crash

/**
 * Function that returns an instance of [CrashModule]. Matches the signature of the constructor for [CrashModuleImpl]
 */
typealias CrashModuleSupplier = (
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    instrumentationModule: InstrumentationModule,
) -> CrashModule

fun createCrashModule(
    initModule: InitModule,
    storageModule: StorageModule,
    essentialServiceModule: EssentialServiceModule,
    androidServicesModule: AndroidServicesModule,
    instrumentationModule: InstrumentationModule,
): CrashModule {
    return CrashModuleImpl(
        initModule,
        storageModule,
        essentialServiceModule,
        androidServicesModule,
        instrumentationModule,
    )
}
